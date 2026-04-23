FROM mcr.microsoft.com/playwright/mcp:v0.0.70

USER root

WORKDIR /app
RUN mkdir -p /app /ms-playwright-profile && chown -R node:node /app /ms-playwright-profile

USER node

COPY --chown=node:node package.json ./
RUN npm install --omit=dev

COPY --chown=node:node src ./src

ENV NODE_ENV=production
ENV PORT=8090
ENV MCP_COMMAND=npx
ENV MCP_ARGS_JSON='["--yes","@playwright/mcp@0.0.70","--headless","--browser=chromium","--no-sandbox","--user-data-dir=/ms-playwright-profile","--allowed-origins=https://search.naver.com;https://booking.naver.com;https://waiting.booking.naver.com;https://app.catchtable.co.kr;https://catchtable.co.kr"]'

EXPOSE 8090

ENTRYPOINT []
CMD ["node", "src/server.js"]
