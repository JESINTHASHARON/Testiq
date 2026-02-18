package com.api.test.api_verifier.validator;

import com.api.test.api_verifier.util.JsonPathResolver;
import com.api.test.api_verifier.service.ApiCaller;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class KeyPresenceValidator implements Validator {

    private final ObjectMapper mapper = new ObjectMapper();
    private static final ThreadLocal<String> lastMessage = new ThreadLocal<>();

    public static String getLastMessage() {
        return lastMessage.get();
    }

    @Override
    public boolean validate(ApiCaller.ApiResponse response, JsonNode checkNode) {
        try {
            String path = checkNode.has("path")
                    ? checkNode.path("path").asText()
                    : "$";

            if (path == null || path.trim().isEmpty()) {
                path = "$";
            }

            if (response == null || response.getResponseBody() == null || response.getResponseBody().isEmpty()) {
                lastMessage.set("Empty API response");
                return false;
            }
            JsonNode root = mapper.readTree(response.getResponseBody());
            boolean exists = !JsonPathResolver.resolve(root, path.trim()).isEmpty();

            if (exists) {
                lastMessage.set("Key found at path: " + path);
            } else {
                lastMessage.set("Key not found at path: " + path);
            }

            return exists;

        } catch (Exception e) {
            lastMessage.set("Exception in keyPresence validation: " + e.getMessage());
            return false;
        }
    }
}
