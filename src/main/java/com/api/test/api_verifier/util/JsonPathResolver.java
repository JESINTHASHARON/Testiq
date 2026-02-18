package com.api.test.api_verifier.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public final class JsonPathResolver {

    private JsonPathResolver() {
    }

    public static List<JsonNode> resolve(JsonNode root, String path) {
        if (root == null) {
            return List.of();
        }
        if (path == null || path.trim().isEmpty() || path.equals("$")) {
            return List.of(root);
        }
        if (path.startsWith("$")) {
            path = path.substring(1);
            if (path.startsWith(".")) {
                path = path.substring(1);
            }
        }
        List<JsonNode> current = List.of(root);

        for (String segment : path.split("\\.")) {
            current = resolveSegment(current, segment);
            if (current.isEmpty()) break;
        }
        return current;
    }

    private static List<JsonNode> resolveSegment(List<JsonNode> nodes, String segment) {
        List<JsonNode> result = new ArrayList<>();

        String field = segment;
        List<String> brackets = new ArrayList<>();

        while (field.contains("[")) {
            int start = field.indexOf('[');
            int end = field.indexOf(']');
            brackets.add(field.substring(start + 1, end));
            field = field.substring(0, start);
        }

        for (JsonNode node : nodes) {
            JsonNode base = field.isEmpty() ? node : node.path(field);
            if (base.isMissingNode()) continue;

            List<JsonNode> working = List.of(base);
            for (String bracket : brackets) {
                working = applyBracket(working, bracket);
            }
            result.addAll(working);
        }

        return result;
    }

    private static List<JsonNode> applyBracket(List<JsonNode> nodes, String expr) {
        List<JsonNode> out = new ArrayList<>();

        for (JsonNode node : nodes) {
            if (!node.isArray()) continue;

            if (expr.equals("*")) {
                node.forEach(out::add);
                continue;
            }

            if (expr.matches("\\d+")) {
                int index = Integer.parseInt(expr);
                if (index < node.size()) {
                    out.add(node.get(index));
                }
                continue;
            }

            String[] kv = expr.split("=", 2);
            if (kv.length == 2) {
                String key = kv[0].trim();
                String value = kv[1].replaceAll("^\"|\"$", "");
                for (JsonNode el : node) {
                    if (el.has(key) && value.equals(el.path(key).asText())) {
                        out.add(el);
                    }
                }
            }
        }
        return out;
    }

}
