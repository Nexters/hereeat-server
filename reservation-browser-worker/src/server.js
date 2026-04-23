import http from 'node:http';
import { URL } from 'node:url';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';

const config = {
  port: numberEnv('PORT', 8090),
  requestTimeoutMs: numberEnv('BROWSER_WORKER_REQUEST_TIMEOUT_MS', 30000),
  navigationTimeoutMs: numberEnv('BROWSER_WORKER_NAVIGATION_TIMEOUT_MS', 15000),
  maxQueriesPerProvider: numberEnv('BROWSER_WORKER_MAX_QUERIES_PER_PROVIDER', 2),
  minMatchScore: numberEnv('BROWSER_WORKER_MIN_MATCH_SCORE', 0.6),
  maxConcurrency: Math.max(1, numberEnv('BROWSER_WORKER_MAX_CONCURRENCY', 1)),
  searchEngineBaseUrl: process.env.BROWSER_WORKER_SEARCH_ENGINE_BASE_URL || 'https://search.naver.com/search.naver',
  mcpCommand: process.env.MCP_COMMAND || 'npx',
  mcpArgs: jsonArrayEnv('MCP_ARGS_JSON', [
    '--yes',
    '@playwright/mcp@0.0.70',
    '--headless',
    '--browser=chromium',
    '--no-sandbox',
    '--user-data-dir=/ms-playwright-profile'
  ])
};

const providerSites = {
  NAVER_BOOKING: ['booking.naver.com/booking', 'waiting.booking.naver.com/bizes'],
  CATCHTABLE: ['app.catchtable.co.kr/ct/shop', 'catchtable.co.kr/ct/shop']
};

let mcpClientPromise;
let running = 0;
const queue = [];

const server = http.createServer(async (request, response) => {
  const startedAt = Date.now();
  try {
    const url = new URL(request.url, `http://${request.headers.host}`);
    if (request.method === 'GET' && url.pathname === '/health') {
      sendJson(response, 200, {
        status: 'UP',
        mcp: mcpClientPromise ? 'connected-or-connecting' : 'lazy',
        maxConcurrency: config.maxConcurrency
      });
      return;
    }

    if (request.method === 'POST' && url.pathname === '/reservation-candidates/search') {
      const body = await readJsonBody(request);
      const result = await withConcurrency(() => withTimeout(
        () => searchReservationCandidate(body),
        config.requestTimeoutMs,
        'TIMEOUT'
      ));
      logResult(body, result, Date.now() - startedAt);
      sendJson(response, 200, result);
      return;
    }

    sendJson(response, 404, { failureReason: 'NOT_FOUND' });
  } catch (error) {
    sendJson(response, 200, {
      reservationUrl: null,
      providerPlaceKey: null,
      matchedName: null,
      matchedAddress: null,
      matchScore: null,
      failureReason: error.message || 'UNKNOWN_ERROR'
    });
  }
});

server.listen(config.port, '0.0.0.0', () => {
  console.log(JSON.stringify({
    message: 'reservation browser worker started',
    port: config.port,
    maxConcurrency: config.maxConcurrency
  }));
});

async function searchReservationCandidate(request) {
  validateSearchRequest(request);

  const provider = request.provider;
  const queries = buildQueries(provider, request.restaurantName, request.address);
  const candidates = [];

  for (const query of queries) {
    const searchUrl = buildSearchUrl(query);
    const anchors = await collectAnchors(searchUrl);

    for (const anchor of anchors) {
      if (!supportsProviderUrl(provider, anchor.href)) {
        continue;
      }

      const score = scoreCandidate(request.restaurantName, request.address, anchor.text, anchor.href);
      if (score < config.minMatchScore) {
        continue;
      }

      candidates.push({
        reservationUrl: anchor.href,
        providerPlaceKey: extractProviderPlaceKey(provider, anchor.href),
        matchedName: emptyToNull(anchor.text),
        matchedAddress: extractMatchedAddress(request.address, `${anchor.text} ${anchor.href}`),
        matchScore: score,
        failureReason: null
      });
    }
  }

  candidates.sort((left, right) => right.matchScore - left.matchScore);
  return candidates[0] || emptyResult('NO_MATCH');
}

async function collectAnchors(searchUrl) {
  const client = await getMcpClient();
  await callTool(client, 'browser_navigate', { url: searchUrl });
  await callTool(client, 'browser_evaluate', {
    function: `() => new Promise(resolve => setTimeout(resolve, ${Math.min(2000, config.navigationTimeoutMs)}))`
  });
  const result = await callTool(client, 'browser_evaluate', {
    function: `() => JSON.stringify(Array.from(document.querySelectorAll('a[href]'))
      .map(anchor => ({
        href: anchor.href,
        text: (anchor.innerText || anchor.textContent || '').replace(/\\s+/g, ' ').trim()
      }))
      .filter(anchor => anchor.href)
      .slice(0, 200))`
  });

  const text = extractToolText(result);
  try {
    return JSON.parse(text);
  } catch {
    return [];
  }
}

async function getMcpClient() {
  if (!mcpClientPromise) {
    mcpClientPromise = connectMcpClient().catch(error => {
      mcpClientPromise = null;
      throw error;
    });
  }
  return mcpClientPromise;
}

async function connectMcpClient() {
  const transport = new StdioClientTransport({
    command: config.mcpCommand,
    args: config.mcpArgs,
    env: process.env
  });
  const client = new Client(
    { name: 'yogieat-reservation-browser-worker', version: '0.1.0' },
    { capabilities: {} }
  );
  await client.connect(transport);
  return client;
}

async function callTool(client, name, args) {
  try {
    return await client.callTool({ name, arguments: args });
  } catch (error) {
    if (String(error.message || '').includes('browser has been closed')) {
      mcpClientPromise = null;
    }
    throw error;
  }
}

function buildQueries(provider, restaurantName, address) {
  const areaToken = extractAreaToken(address);
  const queries = [];
  for (const site of providerSites[provider]) {
    queries.push(`${restaurantName.trim()} ${site}`);
    if (areaToken) {
      queries.push(`${restaurantName.trim()} ${areaToken} ${site}`);
    }
  }
  return queries.slice(0, config.maxQueriesPerProvider);
}

function buildSearchUrl(query) {
  const url = new URL(config.searchEngineBaseUrl);
  url.searchParams.set('query', query);
  return url.toString();
}

function supportsProviderUrl(provider, href) {
  if (!href) {
    return false;
  }
  if (provider === 'NAVER_BOOKING') {
    return href.includes('booking.naver.com/booking/') || href.includes('waiting.booking.naver.com/bizes/');
  }
  return href.includes('app.catchtable.co.kr/ct/shop/') || href.includes('catchtable.co.kr/ct/shop/');
}

function extractProviderPlaceKey(provider, href) {
  if (!href) {
    return null;
  }
  const regex = provider === 'NAVER_BOOKING' ? /\/bizes\/([0-9]+)/ : /\/ct\/shop\/([^?/#]+)/;
  const match = href.match(regex);
  return match ? match[1] : null;
}

function scoreCandidate(restaurantName, address, title, href) {
  const normalizedName = normalize(restaurantName);
  const text = normalize(`${title || ''} ${href || ''}`);
  if (!normalizedName || !text.includes(normalizedName)) {
    return 0;
  }

  let score = 0.7;
  const areaToken = extractAreaToken(address);
  if (areaToken && text.includes(normalize(areaToken))) {
    score += 0.15;
  }
  if (normalize(href).includes(normalizedName)) {
    score += 0.1;
  }
  return Math.min(1, score);
}

function extractMatchedAddress(address, source) {
  if (!address || !source) {
    return null;
  }
  const normalizedSource = normalize(source);
  const matchedTokens = address.split(/\s+/)
    .filter(token => token.length >= 2)
    .filter(token => normalizedSource.includes(normalize(token)))
    .slice(0, 4);
  return matchedTokens.length > 0 ? matchedTokens.join(' ') : null;
}

function extractAreaToken(address) {
  if (!address) {
    return null;
  }
  const tokens = address.split(/\s+/);
  return tokens.find(token => /[구동읍면로]$/.test(token)) || tokens[1] || null;
}

function normalize(value) {
  return String(value || '').toLowerCase().replace(/[^\p{Letter}\p{Number}]/gu, '');
}

function validateSearchRequest(request) {
  if (!request || !request.restaurantName || !request.provider) {
    throw new Error('INVALID_REQUEST');
  }
  if (!providerSites[request.provider]) {
    throw new Error('UNSUPPORTED_PROVIDER');
  }
}

function extractToolText(result) {
  const content = result?.content || [];
  const textContent = content.find(item => item.type === 'text');
  return textContent?.text || '[]';
}

function emptyResult(failureReason) {
  return {
    reservationUrl: null,
    providerPlaceKey: null,
    matchedName: null,
    matchedAddress: null,
    matchScore: null,
    failureReason
  };
}

function logResult(request, result, elapsedMs) {
  console.log(JSON.stringify({
    provider: request?.provider,
    restaurantId: request?.restaurantId || null,
    restaurantName: request?.restaurantName,
    status: result?.reservationUrl ? 'MATCHED' : 'NO_MATCH',
    failureReason: result?.failureReason || null,
    elapsedMs,
    candidateCount: result?.reservationUrl ? 1 : 0
  }));
}

function sendJson(response, statusCode, body) {
  response.writeHead(statusCode, { 'content-type': 'application/json; charset=utf-8' });
  response.end(JSON.stringify(body));
}

function readJsonBody(request) {
  return new Promise((resolve, reject) => {
    let body = '';
    request.setEncoding('utf8');
    request.on('data', chunk => {
      body += chunk;
      if (body.length > 65536) {
        reject(new Error('REQUEST_TOO_LARGE'));
        request.destroy();
      }
    });
    request.on('end', () => {
      try {
        resolve(body ? JSON.parse(body) : {});
      } catch {
        reject(new Error('INVALID_JSON'));
      }
    });
    request.on('error', reject);
  });
}

function withConcurrency(task) {
  return new Promise((resolve, reject) => {
    queue.push({ task, resolve, reject });
    drainQueue();
  });
}

function drainQueue() {
  if (running >= config.maxConcurrency || queue.length === 0) {
    return;
  }

  const item = queue.shift();
  running++;
  item.task()
    .then(item.resolve, item.reject)
    .finally(() => {
      running--;
      drainQueue();
    });
}

function withTimeout(promiseFactory, timeoutMs, failureReason) {
  return Promise.race([
    promiseFactory(),
    new Promise(resolve => setTimeout(() => resolve(emptyResult(failureReason)), timeoutMs))
  ]);
}

function numberEnv(name, defaultValue) {
  const value = Number(process.env[name]);
  return Number.isFinite(value) ? value : defaultValue;
}

function jsonArrayEnv(name, defaultValue) {
  try {
    const value = JSON.parse(process.env[name] || 'null');
    return Array.isArray(value) ? value.map(String) : defaultValue;
  } catch {
    return defaultValue;
  }
}

function emptyToNull(value) {
  return value && value.trim() ? value.trim() : null;
}
