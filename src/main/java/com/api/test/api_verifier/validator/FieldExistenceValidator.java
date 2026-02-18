package com.api.test.api_verifier.validator;

import com.api.test.api_verifier.util.JsonPathResolver;
import com.api.test.api_verifier.service.ApiCaller;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public class FieldExistenceValidator implements Validator {

    private final ObjectMapper mapper = new ObjectMapper();
    private static final ThreadLocal<String> lastMessage = new ThreadLocal<>();

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

            JsonNode root = mapper.readTree(response.getResponseBody());

            String path = checkNode.path("path").asText("$");

            List<String> requiredFields = new ArrayList<>();
            checkNode.path("fields").forEach(n -> requiredFields.add(n.asText()));

            if (requiredFields.isEmpty()) {
                lastMessage.set("Missing 'fields' array in fieldExistence check.");
                return false;
            }

            List<String> anyFields = new ArrayList<>();
            checkNode.path("fieldAny").forEach(n -> anyFields.add(n.asText()));
            List<JsonNode> targetNodes = JsonPathResolver.resolve(root, path);

            if (targetNodes.isEmpty()) {
                lastMessage.set("Path not found: " + path);
                return false;
            }

            for (JsonNode node : targetNodes) {

                if (node.isArray()) {
                    for (JsonNode el : node) {
                        if (!validateElement(el, requiredFields, anyFields)) {
                            lastMessage.set(
                                    "Element failed fieldExistence at path: " + path
                            );
                            return false;
                        }
                    }

                } else if (node.isObject()) {
                    if (!validateElement(node, requiredFields, anyFields)) {
                        lastMessage.set(
                                "Element failed fieldExistence at path: " + path
                        );
                        return false;
                    }

                } else {
                    lastMessage.set("Target is not object or array at path: " + path);
                    return false;
                }
            }

            lastMessage.set(
                    "All elements contain required fields " + requiredFields +
                            (anyFields.isEmpty() ? "" : " and any of " + anyFields) +
                            " at path: " + path
            );
            return true;

        } catch (Exception e) {
            lastMessage.set("Error during fieldExistence validation: " + e.getMessage());
            return false;
        }
    }

    private boolean validateElement(
            JsonNode element,
            List<String> requiredFields,
            List<String> anyFields) {

        for (String field : requiredFields) {
            if (!element.has(field)) {
                return false;
            }
        }

        if (!anyFields.isEmpty()) {
            for (String field : anyFields) {
                if (element.has(field)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }
}
