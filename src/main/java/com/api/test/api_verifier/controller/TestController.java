package com.api.test.api_verifier.controller;

import com.api.test.api_verifier.service.ReportService;
import com.api.test.api_verifier.service.ResultStorage;
import com.api.test.api_verifier.service.RunAllService;
import com.api.test.api_verifier.service.TestExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/api")
public class TestController {

    @Autowired
    private TestExecutor testExecutor;

    @Autowired
    private RunAllService runAllService;

    @Autowired
    private ResultStorage resultStorage;

    @Autowired
    private ReportService reportService;

    @PostMapping("/runAll")
    public ResponseEntity<?> runAllTests(@RequestParam("headersFile") MultipartFile headersFile,
                                         @RequestParam("testFiles") MultipartFile[] testFiles, @RequestParam("csvRows") String csvRowsJson) {

        try {
            Map<String, Object> result = runAllService.runAllTests(headersFile, testFiles);
            resultStorage.store(result);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> csvRows = new ObjectMapper().readValue(csvRowsJson, List.class);

            String runId = (String) result.get("runId");
            result.put("csvRows", csvRows);

            byte[] pdf = reportService.generatePdfReport(result);
            String pdfPath = reportService.savePdfToDisk(pdf, runId);
            result.put("pdfPath", pdfPath);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/validateCookie")
    public ResponseEntity<Map<String, Object>> validateCookieApi(
            @RequestBody Map<String, Object> row
    ) {
        String baseUrl = (String) row.get("baseUrl");
        String cookie = (String) row.get("cookie");

        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) row.get("headers");

        boolean valid = testExecutor.validateCookie(baseUrl, cookie, headers);
        return ResponseEntity.ok(Map.of("isValid", valid));
    }

    @PostMapping("/downloadReport")
    public ResponseEntity<byte[]> downloadPdf(@RequestBody Map<String, Object> input) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> backendOutput = (Map<String, Object>) input.get("backendOutput");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> csvRows = (List<Map<String, Object>>) input.get("csvRows");

            backendOutput.put("csvRows", csvRows);

            byte[] pdfBytes = reportService.generatePdfReport(backendOutput);

            String runId = backendOutput.get("runId").toString();
            String fileName = "Testiq_" + runId + ".pdf";

            return ResponseEntity.ok().header("Content-Type", "application/pdf")
                    .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"").body(pdfBytes);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(("PDF generation error: " + e.getMessage()).getBytes());
        }
    }

    @GetMapping("/report/{runId}")
    public ResponseEntity<byte[]> getReport(@PathVariable String runId) {
        try {
            Path reportPath = Paths.get(System.getProperty("user.home"), "testiq", "report", runId + ".pdf");

            if (!Files.exists(reportPath)) {
                return ResponseEntity.status(404).body(("Report not found for runId: " + runId).getBytes());
            }

            byte[] pdfBytes = Files.readAllBytes(reportPath);

            return ResponseEntity.ok().header("Content-Type", "application/pdf")
                    .header("Content-Disposition", "attachment; filename=\"Testiq_" + runId + ".pdf\"").body(pdfBytes);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
