package com.api.test.api_verifier.validator;

import com.api.test.api_verifier.util.JsonPathResolver;
import com.api.test.api_verifier.service.ApiCaller;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.regex.Pattern;

public class PatternMatchValidator implements Validator {

    private static final ThreadLocal<String> lastMessage = new ThreadLocal<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public static String getLastMessage() {
        return lastMessage.get();
    }

    @Override
    public boolean validate(ApiCaller.ApiResponse response, JsonNode checkNode) {
        try {
            if (response == null || response.getResponseBody() == null) {
                lastMessage.set("Response is null or empty.");
                return false;
            }

            String path = checkNode.path("path").asText();
            String patternString = checkNode.path("pattern").asText();

            if (path == null || path.trim().isEmpty()) {
                path = "$";
            }

            if (patternString == null || patternString.isEmpty()) {
                lastMessage.set("Missing or empty regex pattern.");
                return false;
            }

            JsonNode root = mapper.readTree(response.getResponseBody());
            List<JsonNode> targetNodes = JsonPathResolver.resolve(root, path);

            if (targetNodes.isEmpty()) {
                lastMessage.set("Path not found in response: " + path);
                return false;
            }

            List<String> values = new ArrayList<>();
            for (JsonNode node : targetNodes) {
                values.addAll(collectStringValues(node));
            }

            if (values.isEmpty()) {
                lastMessage.set("No string values found at path: " + path);
                return false;
            }

            Pattern pattern = Pattern.compile(patternString);

            for (String value : values) {
                if (pattern.matcher(value).find()) {
                    lastMessage.set("Pattern matched '" + patternString + "' in: " + value);
                    return true;
                }
            }

            lastMessage.set("No value matched the pattern '" + patternString + "'. Found: " + values);
            return false;

        } catch (Exception e) {
            lastMessage.set("Error during patternMatch validation: " + e.getMessage());
            return false;
        }
    }


    private List<String> collectStringValues(JsonNode node) {
        List<String> results = new ArrayList<>();

        if (node == null || node.isMissingNode() || node.isNull()) {
            return results;
        }

        if (node.isTextual()) {
            results.add(node.asText());
        } else if (node.isArray()) {
            for (JsonNode el : node) {
                results.addAll(collectStringValues(el));
            }
        } else if (node.isObject()) {
            node.fields().forEachRemaining(e -> results.addAll(collectStringValues(e.getValue())));
        }

        return results;
    }
}
