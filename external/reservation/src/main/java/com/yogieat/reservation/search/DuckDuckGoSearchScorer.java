package com.yogieat.reservation.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DuckDuckGoSearchScorer {

    private static final Pattern NON_ALPHANUMERIC_PATTERN = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]");

    double score(String restaurantName, String address, String title, String snippet, String url) {
        String normalizedRestaurantName = normalize(restaurantName);
        if (normalizedRestaurantName.isBlank()) {
            return 0.0d;
        }

        String titleText = normalize(title);
        String snippetText = normalize(snippet);
        String urlText = normalize(url);

        double score = 0.0d;
        if (titleText.contains(normalizedRestaurantName)) {
            score += 0.65d;
        } else if (snippetText.contains(normalizedRestaurantName)) {
            score += 0.45d;
        } else {
            return 0.0d;
        }

        if (urlText.contains(normalizedRestaurantName)) {
            score += 0.10d;
        }

        String areaToken = extractAreaToken(address);
        if (areaToken != null) {
            String normalizedAreaToken = normalize(areaToken);
            if (titleText.contains(normalizedAreaToken) || snippetText.contains(normalizedAreaToken)) {
                score += 0.15d;
            }
        }

        for (String token : extractAddressTokens(address)) {
            String normalizedToken = normalize(token);
            if (normalizedToken.isBlank()) {
                continue;
            }
            if (snippetText.contains(normalizedToken)) {
                score += 0.05d;
            }
        }

        return Math.min(1.0d, score);
    }

    String extractAreaToken(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }

        for (String token : address.split("\\s+")) {
            if (token.endsWith("구") || token.endsWith("동") || token.endsWith("읍") || token.endsWith("면") || token.endsWith("로")) {
                return token;
            }
        }

        return address.split("\\s+").length > 1 ? address.split("\\s+")[1] : null;
    }

    String extractMatchedAddress(String address, String snippet) {
        if (address == null || snippet == null || snippet.isBlank()) {
            return null;
        }

        List<String> matchedTokens = new ArrayList<>();
        String normalizedSnippet = normalize(snippet);

        for (String token : extractAddressTokens(address)) {
            String normalizedToken = normalize(token);
            if (!normalizedToken.isBlank() && normalizedSnippet.contains(normalizedToken)) {
                matchedTokens.add(token);
            }
            if (matchedTokens.size() >= 4) {
                break;
            }
        }

        if (matchedTokens.isEmpty()) {
            return null;
        }
        return String.join(" ", matchedTokens);
    }

    String extractPattern(String source, String regex) {
        if (source == null || source.isBlank()) {
            return null;
        }

        Matcher matcher = Pattern.compile(regex).matcher(source);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private List<String> extractAddressTokens(String address) {
        if (address == null || address.isBlank()) {
            return List.of();
        }

        return List.of(address.split("\\s+")).stream()
                .filter(token -> token.length() >= 2)
                .limit(6)
                .toList();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return NON_ALPHANUMERIC_PATTERN.matcher(value.toLowerCase(Locale.ROOT))
                .replaceAll("");
    }
}
