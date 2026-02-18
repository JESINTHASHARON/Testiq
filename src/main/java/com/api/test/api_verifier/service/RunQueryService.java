package com.api.test.api_verifier.service;

import com.api.test.api_verifier.util.DateUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Paths;
import java.util.*;

@Service
public class RunQueryService {

    private static final String RESULTS_DIR = Paths.get(System.getProperty("user.home"), "testiq", "results")
            .toString();

    private final ObjectMapper mapper = new ObjectMapper();

    public List<Map<String, Object>> fetchRunList(String filter, String startDate, String endDate, String search,
                                                  int limit, int offset, String sort) {

        File dir = new File(RESULTS_DIR);
        if (!dir.exists())
            return Collections.emptyList();

        File[] files = dir.listFiles((d, name) -> name.startsWith("run_") && name.endsWith(".json"));

        if (files == null || files.length == 0)
            return Collections.emptyList();

        long now = System.currentTimeMillis();
        long last7 = now - 7L * 24 * 60 * 60 * 1000;
        long last30 = now - 30L * 24 * 60 * 60 * 1000;

        long startTs = startDate != null ? DateUtils.toTimestamp(startDate) : -1;
        long endTs = endDate != null ? DateUtils.toTimestamp(endDate) + 86399999 : -1;

        List<File> filtered = new ArrayList<>();

        for (File file : files) {

            String name = file.getName();
            long ts = Long.parseLong(name.substring(4, name.length() - 5));

            boolean include = switch (filter) {
                case "last7" -> ts >= last7;
                case "last30" -> ts >= last30;
                case "today" -> DateUtils.isToday(ts);
                case "yesterday" -> DateUtils.isYesterday(ts);
                default -> true;
            };

            if (startTs != -1 && endTs != -1) {
                include = ts >= startTs && ts <= endTs;
            }

            if (include)
                filtered.add(file);
        }

        if (filtered.isEmpty())
            return Collections.emptyList();

        filtered.sort((f1, f2) -> {
            long t1 = Long.parseLong(f1.getName().substring(4, f1.getName().length() - 5));
            long t2 = Long.parseLong(f2.getName().substring(4, f2.getName().length() - 5));

            return sort.equalsIgnoreCase("asc") ? Long.compare(t1, t2) : Long.compare(t2, t1);
        });

        int endIndex = Math.min(offset + limit, filtered.size());
        if (offset > filtered.size())
            return Collections.emptyList();

        List<File> pageFiles = filtered.subList(offset, endIndex);

        List<Map<String, Object>> resultList = new ArrayList<>();

        for (File f : pageFiles) {
            try {

                Map<String, Object> json = mapper.readValue(f, Map.class);
                Map<String, Object> overall = (Map<String, Object>) json.get("overall");

                int passed = ((Number) overall.get("passedExecutionsObserved")).intValue();
                int failed = ((Number) overall.get("failedExecutionsObserved")).intValue();
                int skipped = ((Number) overall.get("skippedExecutionsObserved")).intValue();

                String status;
                if (failed > 0) {
                    status = "Failed";
                } else if (skipped > 0) {
                    status = "Partial";
                } else {
                    status = "Passed";
                }

                double passRateVal = ((double) passed / (passed + failed + skipped)) * 100.0;

                if (search != null && !search.isBlank()) {

                    String term = search.toLowerCase();
                    String id = json.get("runId").toString().toLowerCase();
                    String name = json.get("runName").toString().toLowerCase();
                    String stat = status.toLowerCase();

                    boolean match = id.contains(term) || name.contains(term) || stat.contains(term);

                    if (!match)
                        continue;
                }

                Map<String, Object> info = new LinkedHashMap<>();
                info.put("runId", json.get("runId"));
                info.put("runName", json.get("runName"));
                info.put("startTime", json.get("startTime"));
                info.put("endTime", json.get("endTime"));
                info.put("passRate", passRateVal);
                info.put("status", status);

                info.put("durationSec", overall.get("executionTimeSec"));

                info.put("totalSuites", overall.get("totalSuites"));
                info.put("totalCookies", overall.get("totalCookies"));
                info.put("uniqueTestcases", overall.get("uniqueTestcases"));
                info.put("totalExecutionsObserved", overall.get("totalExecutionsObserved"));
                info.put("passedExecutionsObserved", overall.get("passedExecutionsObserved"));
                info.put("failedExecutionsObserved", overall.get("failedExecutionsObserved"));
                info.put("skippedExecutionsObserved", overall.get("skippedExecutionsObserved"));

                info.put("passedTestcases", overall.get("passedTestcases"));
                info.put("failedTestcases", overall.get("failedTestcases"));
                info.put("skippedTestcases", overall.get("skippedTestcases"));

                resultList.add(info);

            } catch (Exception ignored) {
            }
        }

        return resultList;
    }
}
