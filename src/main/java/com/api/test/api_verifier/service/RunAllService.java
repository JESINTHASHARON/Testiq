package com.api.test.api_verifier.service;

import com.api.test.api_verifier.model.ApiResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.*;

@Service
public class RunAllService {

    private final TestExecutor testExecutor;
    private final ObjectMapper mapper = new ObjectMapper();

    public RunAllService(TestExecutor testExecutor) {
        this.testExecutor = testExecutor;
    }

    @Autowired
    TestSuiteService testSuiteService;

    @SuppressWarnings("unchecked")
    public Map<String, Object> runAllTests(MultipartFile headersFile, MultipartFile[] testFiles) throws Exception {

        long startTime = System.currentTimeMillis();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("runId", "run_" + startTime);
        response.put("runName", "Run " + startTime);

        Map<String, List<Map<String, Object>>> detailsMap = new LinkedHashMap<>();
        Map<String, Map<String, Object>> suiteSummaries = new LinkedHashMap<>();
        Map<String, Map<String, Object>> cookieSummaries = new LinkedHashMap<>();
        Map<String, List<JsonNode>> suiteToTests = new LinkedHashMap<>();

        for (MultipartFile mf : testFiles) {
            if (mf == null || mf.isEmpty())
                continue;

            String fname = mf.getOriginalFilename();
            JsonNode node = mapper.readTree(mf.getInputStream());
            String rawSuite = node.has("suite") ? node.get("suite").asText() : "";
            String suiteKey;

            if (rawSuite != null && !rawSuite.isBlank()) {
                suiteKey = rawSuite;
            } else if (node.has("path") && node.get("path") != null) {
                suiteKey = node.get("path").asText();
            } else {
                suiteKey = fname != null ? fname.replaceAll("\\.json$", "") : "DEFAULT";
            }

            suiteToTests.computeIfAbsent(suiteKey, k -> new ArrayList<>()).add(node);
        }

        Map<String, Object> suiteTree = testSuiteService.getAllSuites();
        List<String> globalFileOrder = new ArrayList<>();
        extractFileOrder(suiteTree, globalFileOrder);
        List<String> orderedSuites = extractSuiteOrder(suiteTree);

        Map<String, List<JsonNode>> sortedSuites = new LinkedHashMap<>();
        for (String key : orderedSuites) {
            if (suiteToTests.containsKey(key)) {
                sortedSuites.put(key, suiteToTests.get(key));
            }
        }

        suiteToTests.forEach(sortedSuites::putIfAbsent);
        suiteToTests.clear();
        suiteToTests.putAll(sortedSuites);

        int rowThreads = Math.min(Runtime.getRuntime().availableProcessors() * 2, 16);
        ExecutorService rowExecutor = Executors.newFixedThreadPool(rowThreads);

        try (CSVReader reader = new CSVReader(new InputStreamReader(headersFile.getInputStream()))) {

            String[] headerKeys = reader.readNext();
            if (headerKeys == null) {
                long endTimeEmpty = System.currentTimeMillis();
                response.put("details", detailsMap);
                response.put("suites", suiteSummaries);
                response.put("cookies", cookieSummaries);
                Map<String, Object> overallEmpty = new LinkedHashMap<>();
                overallEmpty.put("totalSuites", 0);
                overallEmpty.put("totalCookies", 0);
                overallEmpty.put("uniqueTestcases", 0);
                overallEmpty.put("totalExecutionsObserved", 0);
                overallEmpty.put("passedExecutionsObserved", 0);
                overallEmpty.put("failedExecutionsObserved", 0);
                overallEmpty.put("skippedExecutionsObserved", 0);
                overallEmpty.put("executionPassRateObserved", 0);
                overallEmpty.put("passedTestcases", 0);
                overallEmpty.put("failedTestcases", 0);
                overallEmpty.put("skippedTestcases", 0);
                overallEmpty.put("executionTimeMs", endTimeEmpty - startTime);
                overallEmpty.put("executionTimeSec", (endTimeEmpty - startTime) / 1000.0);
                response.put("overall", overallEmpty);
                response.put("startTime", new Date(startTime).toString());
                response.put("endTime", new Date(endTimeEmpty).toString());
                return response;
            }

            List<Callable<Void>> rowTasks = new ArrayList<>();
            String[] dataRow;
            int[] rowIndex = {0};

            while ((dataRow = reader.readNext()) != null) {

                final String[] rowCopy = Arrays.copyOf(dataRow, dataRow.length);
                final int currentIndex = rowIndex[0]++;

                rowTasks.add(() -> {
                    try {
                        long cookieStartTime = System.currentTimeMillis();
                        Map<String, String> headers = new LinkedHashMap<>();

                        for (int i = 0; i < headerKeys.length; i++) {
                            if (i < rowCopy.length && rowCopy[i] != null && !rowCopy[i].trim().isEmpty()) {
                                headers.put(headerKeys[i].trim(), rowCopy[i].trim());
                            }
                        }

                        String baseUrl = headers.getOrDefault("baseUrl", "").trim();
                        if (baseUrl.isEmpty())
                            return null;

                        String userAgent = headers.getOrDefault("userAgent", "").trim();
                        if (!userAgent.isEmpty()) {
                            headers.put("User-Agent", userAgent);
                        }

                        String cookie = headers.remove("cookie");
                        String cookieName = headers.getOrDefault("cookieName", "Unknown");
                        String uniqueCookieId = cookieName + "_"
                                + (cookie != null ? Integer.toString(cookie.hashCode()) : "r" + currentIndex);

                        for (Map.Entry<String, List<JsonNode>> suiteEntry : suiteToTests.entrySet()) {

                            String suiteKey = suiteEntry.getKey();
                            List<JsonNode> testsForSuite = suiteEntry.getValue();
                            long suiteStartTime = System.currentTimeMillis();
                            List<ApiResult> suiteResults = testExecutor.runSuiteChained(testsForSuite, cookie, headers,
                                    baseUrl);
                            long suiteEndTime = System.currentTimeMillis();
                            long suiteExecutionTimeMs = suiteEndTime - suiteStartTime;
                            List<Map<String, Object>> serialized = new ArrayList<>();
                            for (ApiResult ar : suiteResults) {
                                if (ar == null)
                                    continue;

                                Map<String, Object> m = new LinkedHashMap<>();
                                m.put("id", ar.getId());
                                m.put("parentId", ar.getParentId());
                                m.put("suite", ar.getSuite());
                                m.put("path", ar.getPath());
                                m.put("name", ar.getName());
                                m.put("endpoint", ar.getEndpoint());
                                m.put("fullUrl", ar.getFullUrl());

                                Map<String, Object> summ = new LinkedHashMap<>();
                                summ.put("totalChecks", ar.getSummary().getTotalChecks());
                                summ.put("passed", ar.getSummary().getPassed());
                                summ.put("failed", ar.getSummary().getFailed());
                                m.put("summary", summ);

                                List<Map<String, Object>> checks = new ArrayList<>();
                                boolean skipped = false;
                                for (ApiResult.CheckResult cr : ar.getResults()) {
                                    Map<String, Object> c = new LinkedHashMap<>();
                                    c.put("type", cr.getType());
                                    c.put("status", cr.getStatus());
                                    c.put("message", cr.getMessage());
                                    checks.add(c);

                                    if (cr.getStatus() != null && "SKIPPED".equalsIgnoreCase(cr.getStatus())) {
                                        skipped = true;
                                    }
                                    if (cr.getType() != null && "SKIPPED".equalsIgnoreCase(cr.getType())) {
                                        skipped = true;
                                    }
                                }
                                m.put("results", checks);
                                m.put("skipped", skipped);

                                serialized.add(m);
                            }

                            serialized.sort(Comparator.comparingInt(tc -> {
                                String name = (String) tc.get("name");
                                int idx = globalFileOrder.indexOf(name);
                                return idx == -1 ? Integer.MAX_VALUE : idx;
                            }));
                            long cookieEndTime = System.currentTimeMillis();
                            long executionTimeMs = cookieEndTime - cookieStartTime;

                            Map<String, Object> cookieEntry = new LinkedHashMap<>();
                            cookieEntry.put("cookie", cookie);
                            cookieEntry.put("cookieName", cookieName);
                            cookieEntry.put("uniqueId", uniqueCookieId);
                            cookieEntry.put("baseUrl", baseUrl);
                            cookieEntry.put("executionTimeMs", suiteExecutionTimeMs);
                            cookieEntry.put("executionTimeSec", suiteExecutionTimeMs / 1000.0);

                            cookieEntry.put("results", serialized);

                            Map<String, List<Map<String, Object>>> childFolders = new LinkedHashMap<>();

                            for (Map<String, Object> tc : serialized) {
                                String path = (String) tc.get("path");

                                if (path != null && path.contains("/")) {
                                    String[] parts = path.split("/");
                                    StringBuilder folderBuilder = new StringBuilder();

                                    for (int i = 1; i < parts.length; i++) {
                                        if (i > 1)
                                            folderBuilder.append("/");

                                        folderBuilder.append(parts[i]);
                                        String folderName = folderBuilder.toString();
                                        childFolders.computeIfAbsent(folderName, k -> new ArrayList<>()).add(tc);
                                    }
                                }

                            }

                            for (String child : childFolders.keySet()) {
                                List<Map<String, Object>> list = childFolders.get(child);

                                Map<String, Object> childSummary = new LinkedHashMap<>();
                                childSummary.put("results", list);

                                ((Map<String, Object>) cookieEntry.computeIfAbsent("childFolders",
                                        k -> new LinkedHashMap<String, Object>())).put(child, childSummary);
                            }

                            synchronized (detailsMap) {
                                detailsMap
                                        .computeIfAbsent(suiteKey, k -> Collections.synchronizedList(new ArrayList<>()))
                                        .add(cookieEntry);
                            }
                        }

                    } catch (Exception ignored) {
                    }

                    return null;
                });
            }

            List<Future<Void>> futures = rowExecutor.invokeAll(rowTasks);
            rowExecutor.shutdown();

            for (Future<Void> f : futures) {
                try {
                    f.get();
                } catch (Exception ignored) {
                }
            }

            for (Map.Entry<String, List<Map<String, Object>>> e : detailsMap.entrySet()) {

                String suiteKey = e.getKey();
                List<Map<String, Object>> cookieList = e.getValue();

                int totalCookies = cookieList.size();
                int cookiesAllPassed = 0;
                Set<String> uniqueTcSet = new HashSet<>();
                int totalExecObserved = 0;
                int passedExecObserved = 0;
                int failedExecObserved = 0;
                int skippedExecObserved = 0;

                Map<String, List<Map<String, Object>>> childFoldersAgg = new LinkedHashMap<>();

                for (Map<String, Object> c : cookieList) {
                    List<Map<String, Object>> tcs = (List<Map<String, Object>>) c.get("results");
                    boolean cookieAllPass = true;

                    for (Map<String, Object> tc : tcs) {

                        Map<String, Object> summ = (Map<String, Object>) tc.get("summary");
                        int failed = ((Number) summ.get("failed")).intValue();
                        boolean skipped = Boolean.TRUE.equals(tc.get("skipped"));

                        totalExecObserved++;
                        if (failed > 0) {
                            failedExecObserved++;
                            cookieAllPass = false;
                        } else if (skipped) {
                            skippedExecObserved++;
                        } else {
                            passedExecObserved++;
                        }

                        uniqueTcSet.add(tc.get("id") + "::" + tc.get("name"));

                        String path = (String) tc.get("path");
                        if (path != null && path.contains("/")) {
                            String[] parts = path.split("/");
                            StringBuilder folderBuilder = new StringBuilder();
                            for (int i = 1; i < parts.length; i++) {
                                if (i > 1)
                                    folderBuilder.append("/");
                                folderBuilder.append(parts[i]);
                                String folderName = folderBuilder.toString();
                                childFoldersAgg.computeIfAbsent(folderName, k -> new ArrayList<>()).add(tc);
                            }
                        }
                    }
                    if (skippedExecObserved == totalExecObserved)
                        cookieAllPass = false;
                    if (cookieAllPass)
                        cookiesAllPassed++;
                }

                Map<String, Object> suiteSummary = new LinkedHashMap<>();
                suiteSummary.put("suiteKey", suiteKey);
                suiteSummary.put("totalCookies", totalCookies);
                suiteSummary.put("passedCookies", cookiesAllPassed);
                suiteSummary.put("failedCookies", totalCookies - cookiesAllPassed);
                suiteSummary.put("uniqueTestcases", uniqueTcSet.size());
                suiteSummary.put("totalExecutionsObserved", totalExecObserved);
                suiteSummary.put("passedExecutionsObserved", passedExecObserved);
                suiteSummary.put("failedExecutionsObserved", failedExecObserved);
                suiteSummary.put("skippedExecutionsObserved", skippedExecObserved);

                Map<String, Object> childSummaryMap = new LinkedHashMap<>();
                for (Map.Entry<String, List<Map<String, Object>>> cf : childFoldersAgg.entrySet()) {
                    String folderName = cf.getKey();
                    List<Map<String, Object>> list = cf.getValue();

                    Set<Integer> uniqueIds = new HashSet<>();
                    int totalExec = list.size();
                    int passed = 0;
                    int failed = 0;
                    int skipped = 0;

                    for (Map<String, Object> tc : list) {
                        Object idObj = tc.get("id");
                        if (idObj instanceof Number)
                            uniqueIds.add(((Number) idObj).intValue());

                        Map<String, Object> summ = (Map<String, Object>) tc.get("summary");
                        int f = ((Number) summ.get("failed")).intValue();
                        boolean s = Boolean.TRUE.equals(tc.get("skipped"));

                        if (f > 0)
                            failed++;
                        else if (s)
                            skipped++;
                        else
                            passed++;
                    }

                    Map<String, Object> childSummary = new LinkedHashMap<>();
                    childSummary.put("uniqueTestcases", uniqueIds.size());
                    childSummary.put("totalExecutionsObserved", totalExec);
                    childSummary.put("passedExecutionsObserved", passed);
                    childSummary.put("failedExecutionsObserved", failed);
                    childSummary.put("skippedExecutionsObserved", skipped);

                    childSummaryMap.put(folderName, childSummary);
                }

                if (!childSummaryMap.isEmpty())
                    suiteSummary.put("childFolders", childSummaryMap);

                suiteSummaries.put(suiteKey, suiteSummary);
            }

            Map<String, Set<String>> cookieToUniqueTcs = new LinkedHashMap<>();
            Map<String, Integer> cookiePassedTcCount = new LinkedHashMap<>();
            Map<String, Integer> cookieFailedTcCount = new LinkedHashMap<>();
            Map<String, Integer> cookieSkippedTcCount = new LinkedHashMap<>();
            Map<String, Set<String>> cookieToSuites = new LinkedHashMap<>();
            Map<String, String> cookieToName = new LinkedHashMap<>();

            for (Map.Entry<String, List<Map<String, Object>>> suiteEntry : detailsMap.entrySet()) {
                String suiteKey = suiteEntry.getKey();
                List<Map<String, Object>> cookieList = suiteEntry.getValue();

                for (Map<String, Object> cookieEntry : cookieList) {

                    String uid = (String) cookieEntry.get("uniqueId");
                    String cookieName = (String) cookieEntry.get("cookieName");

                    cookieToName.putIfAbsent(uid, cookieName);
                    cookieToUniqueTcs.computeIfAbsent(uid, k -> new LinkedHashSet<>());
                    cookiePassedTcCount.putIfAbsent(uid, 0);
                    cookieFailedTcCount.putIfAbsent(uid, 0);
                    cookieSkippedTcCount.putIfAbsent(uid, 0);
                    cookieToSuites.computeIfAbsent(uid, k -> new LinkedHashSet<>()).add(suiteKey);

                    List<Map<String, Object>> tcList = (List<Map<String, Object>>) cookieEntry.get("results");

                    for (Map<String, Object> tc : tcList) {

                        String tcKey = tc.get("id") + "::" + tc.get("name");

                        if (!cookieToUniqueTcs.get(uid).contains(tcKey)) {

                            cookieToUniqueTcs.get(uid).add(tcKey);

                            Map<String, Object> summ = (Map<String, Object>) tc.get("summary");
                            int failed = ((Number) summ.get("failed")).intValue();
                            boolean skipped = Boolean.TRUE.equals(tc.get("skipped"));

                            if (failed > 0)
                                cookieFailedTcCount.put(uid, cookieFailedTcCount.get(uid) + 1);
                            else if (skipped)
                                cookieSkippedTcCount.put(uid, cookieSkippedTcCount.get(uid) + 1);
                            else
                                cookiePassedTcCount.put(uid, cookiePassedTcCount.get(uid) + 1);
                        }
                    }
                }
            }

            cookieSummaries.clear();

            for (String uid : cookieToUniqueTcs.keySet()) {

                Map<String, Object> cs = new LinkedHashMap<>();
                cs.put("uniqueId", uid);
                cs.put("cookieName", cookieToName.getOrDefault(uid, uid));
                cs.put("testsRun", cookieToUniqueTcs.get(uid).size());
                cs.put("passed", cookiePassedTcCount.get(uid));
                cs.put("failed", cookieFailedTcCount.get(uid));
                cs.put("skipped", cookieSkippedTcCount.get(uid));
                cs.put("suites", new ArrayList<>(cookieToSuites.get(uid)));

                Map<String, Object> childAgg = new LinkedHashMap<>();

                for (Map.Entry<String, List<Map<String, Object>>> suiteDetail : detailsMap.entrySet()) {
                    List<Map<String, Object>> cookieEntries = suiteDetail.getValue();

                    for (Map<String, Object> cookieEntry : cookieEntries) {

                        if (!uid.equals(cookieEntry.get("uniqueId")))
                            continue;

                        Map<String, Object> childFolders = (Map<String, Object>) cookieEntry.get("childFolders");
                        if (childFolders == null)
                            continue;

                        for (Map.Entry<String, Object> childEntry : childFolders.entrySet()) {
                            String folderName = childEntry.getKey();

                            Map<String, Object> childNode = (Map<String, Object>) childEntry.getValue();
                            List<Map<String, Object>> childResults = (List<Map<String, Object>>) childNode
                                    .get("results");

                            Set<Integer> uniqueIds = new HashSet<>();
                            int passed = 0, failed = 0, skipped = 0;

                            for (Map<String, Object> tc : childResults) {
                                Map<String, Object> summ = (Map<String, Object>) tc.get("summary");
                                int f = ((Number) summ.get("failed")).intValue();
                                boolean s = Boolean.TRUE.equals(tc.get("skipped"));

                                Object idObj = tc.get("id");
                                if (idObj instanceof Number)
                                    uniqueIds.add(((Number) idObj).intValue());

                                if (f > 0)
                                    failed++;
                                else if (s)
                                    skipped++;
                                else
                                    passed++;
                            }

                            Map<String, Object> childSum = new LinkedHashMap<>();
                            childSum.put("uniqueTestcases", uniqueIds.size());
                            childSum.put("totalExecutionsObserved", childResults.size());
                            childSum.put("passedExecutionsObserved", passed);
                            childSum.put("failedExecutionsObserved", failed);
                            childSum.put("skippedExecutionsObserved", skipped);

                            childAgg.put(folderName, childSum);
                        }
                    }
                }

                if (!childAgg.isEmpty()) {
                    cs.put("childFolders", childAgg);
                }

                cookieSummaries.put(uid, cs);
            }

            Map<String, Integer> testcaseStatus = new LinkedHashMap<>();
            int totalExecutions = 0;
            int totalPassedExecutions = 0;
            int totalFailedExecutions = 0;
            int totalSkippedExecutions = 0;

            for (List<Map<String, Object>> cookieList : detailsMap.values()) {

                for (Map<String, Object> cookieEntry : cookieList) {

                    List<Map<String, Object>> tcs = (List<Map<String, Object>>) cookieEntry.get("results");

                    for (Map<String, Object> tc : tcs) {

                        String tcKey = tc.get("id") + "::" + tc.get("name");
                        Map<String, Object> summ = (Map<String, Object>) tc.get("summary");

                        int failed = ((Number) summ.get("failed")).intValue();
                        boolean skipped = Boolean.TRUE.equals(tc.get("skipped"));

                        totalExecutions++;
                        if (failed > 0)
                            totalFailedExecutions++;
                        else if (skipped)
                            totalSkippedExecutions++;
                        else
                            totalPassedExecutions++;

                        Integer cur = testcaseStatus.get(tcKey);
                        if (cur == null) {
                            if (failed > 0)
                                testcaseStatus.put(tcKey, -1);
                            else if (skipped)
                                testcaseStatus.put(tcKey, 0);
                            else
                                testcaseStatus.put(tcKey, 1);
                        } else {
                            if (cur != -1) {
                                if (failed > 0)
                                    testcaseStatus.put(tcKey, -1);
                                else if (cur == 0 && !skipped)
                                    testcaseStatus.put(tcKey, 1);
                            }
                        }
                    }
                }
            }

            int totalPassedTestcases = 0;
            int totalFailedTestcases = 0;
            int totalSkippedTestcases = 0;

            for (Integer v : testcaseStatus.values()) {
                if (v == null)
                    totalSkippedTestcases++;
                else if (v == 1)
                    totalPassedTestcases++;
                else if (v == -1)
                    totalFailedTestcases++;
                else
                    totalSkippedTestcases++;
            }

            Map<String, Object> overall = new LinkedHashMap<>();
            overall.put("totalSuites", detailsMap.size());
            overall.put("totalCookies", cookieSummaries.size());
            overall.put("uniqueTestcases", testcaseStatus.size());
            overall.put("totalExecutionsObserved", totalExecutions);
            overall.put("passedExecutionsObserved", totalPassedExecutions);
            overall.put("failedExecutionsObserved", totalFailedExecutions);
            overall.put("skippedExecutionsObserved", totalSkippedExecutions);
            overall.put("executionPassRateObserved",
                    totalExecutions == 0 ? 0 : Math.round((totalPassedExecutions * 10000.0) / totalExecutions) / 100.0);
            overall.put("passedTestcases", totalPassedTestcases);
            overall.put("failedTestcases", totalFailedTestcases);
            overall.put("skippedTestcases", totalSkippedTestcases);

            long endTime = System.currentTimeMillis();
            overall.put("executionTimeMs", endTime - startTime);
            overall.put("executionTimeSec", (endTime - startTime) / 1000.0);

            response.put("details", detailsMap);
            response.put("suites", suiteSummaries);
            response.put("cookies", cookieSummaries);
            response.put("overall", overall);
            response.put("startTime", new Date(startTime).toString());
            response.put("endTime", new Date(endTime).toString());

            return response;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> extractSuiteOrder(Map<String, Object> node) {
        List<String> order = new ArrayList<>();
        if (node == null)
            return order;

        Map<String, Object> folders = (Map<String, Object>) node.get("folders");
        List<String> tests = (List<String>) node.get("tests");

        if (tests != null) {
            for (String test : tests) {
                order.add(test.replace(".json", ""));
            }
        }

        if (folders != null) {
            for (Map.Entry<String, Object> e : folders.entrySet()) {
                List<String> childOrder = extractSuiteOrder((Map<String, Object>) e.getValue());
                for (String co : childOrder) {
                    order.add(e.getKey() + "/" + co);
                }
                order.add(e.getKey());
            }
        }
        return order;
    }


    @SuppressWarnings("unchecked")
    private void extractFileOrder(Map<String, Object> node, List<String> orderList) {
        if (node == null)
            return;

        List<String> tests = (List<String>) node.get("tests");
        if (tests != null) {
            for (String t : tests) {
                String clean = t.replace(".json", "").trim();
                orderList.add(clean);
            }
        }

        Map<String, Object> folders = (Map<String, Object>) node.get("folders");
        if (folders != null) {
            for (Map.Entry<String, Object> sub : folders.entrySet()) {
                Map<String, Object> childNode = (Map<String, Object>) sub.getValue();
                extractFileOrder(childNode, orderList);
            }
        }
    }
}
