package com.api.test.api_verifier.controller;

import com.api.test.api_verifier.service.CookieValidatorService;
import com.api.test.api_verifier.service.TestExecutor;
import com.api.test.api_verifier.service.TestSuiteService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.core5.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tests")
public class TestSuiteController {


    private static final String BASE_PATH = System.getProperty("user.home") + "/testiq/testcases/";
    private final TestSuiteService service;

    public TestSuiteController(TestSuiteService service) {
        this.service = service;
    }

    @Autowired
    private TestExecutor testExecutor;
    @Autowired
    private CookieValidatorService cookieValidatorService;

    @GetMapping("/prechecks")
    public Map<String, JsonNode> listPrechecks() {
        return testExecutor.getPrechecks();
    }

    @PostMapping("/prechecks")
    public ResponseEntity<?> addPrecheck(@RequestBody Map<String, Object> body) throws Exception {
        testExecutor.addPrecheck(body);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/prechecks/{name}")
    public ResponseEntity<?> updatePrecheck(
            @PathVariable String name,
            @RequestBody Map<String, Object> body) throws Exception {
        testExecutor.updatePrecheck(name, body);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/prechecks/{name}")
    public ResponseEntity<?> deletePrecheck(@PathVariable String name) throws Exception {
        testExecutor.deletePrecheck(name);
        return ResponseEntity.ok().build();
    }


    @GetMapping("/suites")
    public Map<String, Object> getAllSuites() {
        return service.getAllSuites();
    }

    @GetMapping("/test")
    public Map<String, Object> readTest(@RequestParam String path) throws IOException {
        return service.readTest(path);
    }

    @PostMapping("/test")
    public String createTest(@RequestParam String path, @RequestBody Map<String, Object> testcase) throws Exception {
        return service.createTest(path, testcase);
    }

    @PutMapping("/test")
    public String updateTest(@RequestParam String path, @RequestBody Map<String, Object> testcase) throws Exception {
        return service.updateTest(path, testcase);
    }

    @DeleteMapping("/test")
    public String deleteTest(@RequestParam String path) {
        return service.deleteTest(path);
    }

    @PutMapping("/test/rename")
    public String renameTest(@RequestParam String oldPath, @RequestParam String newName) {
        return service.renameTest(oldPath, newName);
    }

    @PutMapping("/test/move")
    public String moveTest(@RequestParam String oldPath, @RequestParam String newPath) {
        return service.moveTest(oldPath, newPath);
    }

    @PostMapping("/folder")
    public String createFolder(@RequestParam String path) {
        return service.createFolder(path);
    }

    @PutMapping("/folder/rename")
    public String renameFolder(@RequestParam String oldPath, @RequestParam String newName) {
        return service.renameFolder(oldPath, newName);
    }

    @DeleteMapping("/folder")
    public String deleteFolder(@RequestParam String path) {
        return service.deleteFolder(path);
    }

    @PostMapping("/cookie-validator")
    public void setCookieValidator(@RequestBody Map<String, String> body) throws Exception {
        cookieValidatorService.setValidatorFromTestcase(body.get("path"));
    }

    @GetMapping("/cookie-validator")
    public Map<String, String> getCookieValidator() throws Exception {
        return cookieValidatorService.getValidator();
    }

    @PutMapping("/order")
    public ResponseEntity<?> updateOrder(
            @RequestParam String folder,
            @RequestBody Map<String, List<String>> body) {

        List<String> orderList = body.get("order");
        if (orderList == null || orderList.isEmpty()) {
            return ResponseEntity.badRequest().body("Invalid order list");
        }

        try {
            Path folderPath = Paths.get(BASE_PATH)
                    .resolve(folder.replace("/", File.separator))
                    .normalize();

            if (!Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
                return ResponseEntity.status(HttpStatus.SC_NOT_FOUND)
                        .body("Folder not found: " + folderPath);
            }

            Path orderFilePath = folderPath.resolve("order.txt");

            Files.write(orderFilePath,
                    orderList,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            return ResponseEntity.ok("Order updated");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

}
