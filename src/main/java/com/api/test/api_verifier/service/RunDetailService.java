package com.api.test.api_verifier.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Paths;
import java.util.Map;

@Service
public class RunDetailService {

    private static final String RESULTS_DIR = Paths.get(System.getProperty("user.home"), "testiq", "results")
            .toString();

    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, Object> getRunDetails(String runId) {
        try {
            File file = new File(RESULTS_DIR + "/" + runId + ".json");
            if (!file.exists()) {
                return null;
            }
            return mapper.readValue(file, Map.class);
        } catch (Exception e) {
            return null;
        }
    }
}
