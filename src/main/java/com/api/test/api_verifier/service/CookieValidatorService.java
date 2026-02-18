package com.api.test.api_verifier.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Service
public class CookieValidatorService {
    private static final Path FILE = Paths.get(
            System.getProperty("user.home"),
            "testiq",
            "config",
            "cookie-validator.json"
    );

    private final ObjectMapper mapper = new ObjectMapper();

    public synchronized void setValidatorFromTestcase(String testPath) throws Exception {
        Path testcaseFile = Paths.get(
                System.getProperty("user.home"),
                "testiq",
                "testcases",
                testPath
        );

        if (!Files.exists(testcaseFile)) {
            throw new IllegalStateException("Testcase not found: " + testPath);
        }

        JsonNode tc = mapper.readTree(testcaseFile.toFile());
        String endpoint = tc.path("endpoint").asText(null);
        String method = tc.path("method").asText("GET");

        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("Testcase has no endpoint");
        }

        Files.createDirectories(FILE.getParent());
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(FILE.toFile(), Map.of(
                        "path", testPath,
                        "endpoint", endpoint,
                        "method", method
                ));
    }

    public synchronized Map<String, String> getValidator() throws Exception {
        if (!Files.exists(FILE)) return Map.of();
        return mapper.readValue(FILE.toFile(), Map.class);
    }
}
