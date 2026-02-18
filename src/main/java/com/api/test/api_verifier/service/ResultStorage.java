package com.api.test.api_verifier.service;

import java.io.File;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ResultStorage {
    public void store(Map<String, Object> result) {
        try {
            String resultsDir = System.getProperty("user.home") + "/testiq/results";

            File dir = new File(resultsDir);
            if (!dir.exists())
                dir.mkdirs();

            String fileName = result.get("runId") + ".json";
            File outputFile = new File(dir, fileName);

            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, result);

            System.out.println("Saved run file at: " + outputFile.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
