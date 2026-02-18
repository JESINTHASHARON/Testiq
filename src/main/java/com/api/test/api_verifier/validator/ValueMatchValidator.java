package com.api.test.api_verifier.validator;

import com.api.test.api_verifier.util.JsonPathResolver;
import com.api.test.api_verifier.service.ApiCaller;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public class ValueMatchValidator implements Validator {

    private static final ThreadLocal<String> lastMessage = new ThreadLocal<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public static String getLastMessage() {
        return lastMessage.get();
    }

    @Override
    public boolean validate(ApiCaller.ApiResponse response, JsonNode checkNode) {
        try {
            if (response == null || response.getResponseBody() == null) {
                fail("Response is null.");
                return false;
            }

            String path = checkNode.path("path").asText("");
            String operator = checkNode.path("operator").asText("==").toUpperCase();
            String logic = checkNode.path("logic").asText("AND").toUpperCase();
            String expectedType = checkNode.path("expectedType").asText(null);
            JsonNode expectedNode = checkNode.get("expected");

            if (path.isEmpty()) {
                fail("Missing 'path' in valueMatch check.");
                return false;
            }

            JsonNode body = mapper.readTree(response.getResponseBody());
            List<JsonNode> actualValues = JsonPathResolver.resolve(body, path);

            if (actualValues.isEmpty()) {
                fail("Path not found: " + path);
                return false;
            }

            List<String> expectedList = new ArrayList<>();
            if (expectedNode != null && !expectedNode.isNull()) {
                if (expectedNode.isArray()) {
                    expectedNode.forEach(e -> expectedList.add(e.asText()));
                } else {
                    expectedList.add(expectedNode.asText());
                }
            }

            if (!expectedList.isEmpty() && !checkNode.has("logic") && expectedList.size() > 1) {
                logic = "OR";
            }

            boolean isAndLogic = logic.equals("AND");

            if (expectedType != null) {
                for (JsonNode node : actualValues) {
                    String actualType = detectType(node);
                    if (!expectedType.equalsIgnoreCase(actualType)) {
                        lastMessage.set("FAIL: " + path + " → expected " + expectedType.toUpperCase() + " but found " + actualType.toUpperCase());
                        return false;
                    }
                }
            }

            for (JsonNode node : actualValues) {
                String actualValue = node.isValueNode() ? node.asText() : node.toString();

                boolean result = evaluateMultiple(expectedList, actualValue, operator, isAndLogic);

                String actualType = detectType(node);

                if (result && !isAndLogic) {
                    setMessage(true, path, actualValue, actualType, operator, expectedList, expectedType, false);
                    return true;
                }

                if (!result && isAndLogic) {
                    setMessage(false, path, actualValue, actualType, operator, expectedList, expectedType, true);
                    return false;
                }
            }

            if (isAndLogic) {
                setMessage(true, path, "-", expectedType, operator, expectedList, expectedType, true);
                return true;
            }

            setMessage(false, path, "-", expectedType, operator, expectedList, expectedType, false);
            return false;

        } catch (Exception e) {
            fail("Validation error: " + e.getMessage());
            return false;
        }
    }

    private boolean evaluateMultiple(List<String> expectedList, String actual, String operator, boolean isAndLogic) {
        for (String expected : expectedList) {
            boolean match = evaluateValue(actual, operator, expected);
            if (isAndLogic && !match) return false;
            if (!isAndLogic && match) return true;
        }
        return isAndLogic;
    }

    private boolean evaluateValue(String actual, String operator, String expected) {
        try {
            switch (operator) {
                case "==":
                    return actual.equals(expected);
                case "!=":
                    return !actual.equals(expected);
                case ">":
                    return num(actual) > num(expected);
                case "<":
                    return num(actual) < num(expected);
                case ">=":
                    return num(actual) >= num(expected);
                case "<=":
                    return num(actual) <= num(expected);
                case "CONTAINS":
                    return actual.contains(expected);
                case "NOTCONTAINS":
                    return !actual.contains(expected);
                case "STARTSWITH":
                    return actual.startsWith(expected);
                case "ENDSWITH":
                    return actual.endsWith(expected);
                case "EMPTY":
                    return actual == null || actual.isEmpty();
                case "NOTEMPTY":
                    return actual != null && !actual.isEmpty();
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private Double num(String v) {
        return Double.parseDouble(v.trim());
    }

    private String detectType(JsonNode node) {
        if (node.isBoolean()) return "boolean";
        if (node.isNumber()) return "number";
        if (node.isTextual()) return "string";
        if (node.isArray()) return "array";
        if (node.isObject()) return "object";
        if (node.isNull()) return "null";
        return "unknown";
    }

    private void setMessage(boolean pass, String path, String actualValue, String actualType, String operator, List<String> expectedList, String expectedType, boolean isLogicAND) {

        String expType = expectedType != null ? expectedType.toUpperCase() : "ANY";
        String expValues = expectedList.isEmpty() ? "-" : String.join(", ", expectedList);
        String actualValText = actualValue == null || actualValue.equals("-") ? "null" : actualValue;

        String msg = pass ? "PASS: " + path + " → matched expected " + expType + " \"" + expValues + "\"" : "FAIL: " + path + " → expected " + expType + " \"" + expValues + "\" but found \"" + actualValText + "\" (type=" + actualType + ")";

        lastMessage.set(msg);
    }

    private void fail(String msg) {
        lastMessage.set(msg);
    }
}
