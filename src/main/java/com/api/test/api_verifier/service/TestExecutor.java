package com.api.test.api_verifier.service;

import com.api.test.api_verifier.model.ApiResult;
import com.api.test.api_verifier.validator.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

enum TestStatus {
    PASSED, FAILED, SKIPPED
}

@Component
public class TestExecutor {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApiCaller apiCaller = new ApiCaller();

    public boolean validateCookie(String baseUrl, String cookie, Map<String, String> headers) {
        try {
            File file = new File(
                    System.getProperty("user.home")
                            + "/testiq/config/cookie-validator.json"
            );

            if (!file.exists()) {
                throw new IllegalStateException("Cookie validator not configured");
            }

            Map<String, String> cfg =
                    objectMapper.readValue(file, Map.class);

            String endpoint = cfg.get("endpoint");
            String method = cfg.getOrDefault("method", "GET");

            if (endpoint == null || endpoint.isBlank()) {
                throw new IllegalStateException("Validator endpoint missing");
            }

            String fullUrl = buildURL(baseUrl, endpoint);
            ApiCaller.ApiResponse resp =
                    apiCaller.callApi(fullUrl, method, cookie, headers);

            return resp.getStatusCode() == 200;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private final Map<String, JsonNode> precheckRules = new HashMap<>();

    @PostConstruct
    public void loadPrechecks() {
        try {
            File file = new File(System.getProperty("user.home") + "/testiq/config/prechecks.json");
            JsonNode json = objectMapper.readTree(file);

            json.fields().forEachRemaining(e -> precheckRules.put(e.getKey(), e.getValue()));
        } catch (Exception e) {
            System.out.println("No prechecks.json found. Prechecks disabled.");
        }
    }

    public Map<String, JsonNode> getPrechecks() {
        return precheckRules;
    }

    private File getPrecheckFile() {
        return new File(System.getProperty("user.home") + "/testiq/config/prechecks.json");
    }

    private synchronized void savePrechecks() throws Exception {
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(getPrecheckFile(), precheckRules);
    }

    public synchronized void addPrecheck(Map<String, Object> payload) throws Exception {
        String name = payload.get("name").toString();
        JsonNode rule = objectMapper.valueToTree(payload.get("rule"));
        precheckRules.put(name, rule);
        savePrechecks();
    }

    public synchronized void updatePrecheck(String name, Map<String, Object> rule) throws Exception {
        JsonNode node = objectMapper.valueToTree(rule);
        precheckRules.put(name, node);
        savePrechecks();
    }

    public synchronized void deletePrecheck(String name) throws Exception {
        precheckRules.remove(name);
        savePrechecks();
    }

    public List<ApiResult> runTestsFromJson(MultipartFile file, String cookie, Map<String, String> headers,
                                            String baseUrl) {
        List<ApiResult> results = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream()) {
            JsonNode root = objectMapper.readTree(inputStream);
            JsonNode testCases = root.get("testCases");
            if (testCases == null || !testCases.isArray())
                throw new IllegalArgumentException("Invalid JSON: Missing 'testCases' array");

            int threads = Math.min(Runtime.getRuntime().availableProcessors() * 2, 8);
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            List<Callable<ApiResult>> tasks = new ArrayList<>();

            for (JsonNode test : testCases) {
                tasks.add(() -> runSingleTest(test, cookie, headers, baseUrl));
            }

            List<Future<ApiResult>> futures = executor.invokeAll(tasks);
            executor.shutdown();

            for (Future<ApiResult> f : futures) {
                ApiResult res = f.get();
                if (res != null)
                    results.add(res);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }

    public List<ApiResult> runSuiteChained(List<JsonNode> tests, String cookie, Map<String, String> headers,
                                           String baseUrl) {

        Map<Integer, JsonNode> map = new LinkedHashMap<>();
        Map<Integer, List<Integer>> childMap = new HashMap<>();
        Map<String, Object> shared = new ConcurrentHashMap<>();
        Map<Integer, TestStatus> status = new ConcurrentHashMap<>();
        Set<String> requiredKeys = new HashSet<>();

        for (JsonNode t : tests) {
            int id = t.get("id").asInt();
            map.put(id, t);

            if (t.has("parentId") && !t.get("parentId").isNull()) {
                JsonNode p = t.get("parentId");

                if (p.isArray()) {
                    for (JsonNode pid : p)
                        childMap.computeIfAbsent(pid.asInt(), a -> new ArrayList<>()).add(id);
                } else {
                    childMap.computeIfAbsent(p.asInt(), a -> new ArrayList<>()).add(id);
                }
            }
            if (t.has("requires")) {
                for (JsonNode r : t.get("requires"))
                    requiredKeys.add(r.isTextual() ? r.asText() : r.get("name").asText());
            }
        }

        List<ApiResult> results = Collections.synchronizedList(new ArrayList<>());
        Map<String, List<Integer>> suiteRoots = new LinkedHashMap<>();
        for (Integer id : map.keySet()) {
            JsonNode t = map.get(id);
            boolean isRoot = !t.has("parentId") || t.get("parentId").isNull();

            if (!isRoot)
                continue;

            String rawSuite = t.has("suite") ? t.get("suite").asText() : "DEFAULT";
            String suite = rawSuite.contains("/") ? rawSuite.substring(0, rawSuite.indexOf("/")).trim() : rawSuite;
            suiteRoots.computeIfAbsent(suite, a -> new ArrayList<>()).add(id);
        }
        int cpuCores = Runtime.getRuntime().availableProcessors();
        ExecutorService suiteExecutor = Executors.newFixedThreadPool(Math.min(suiteRoots.size(), cpuCores));

        List<Future<List<ApiResult>>> suiteFutures = new ArrayList<>();

        for (var entry : suiteRoots.entrySet()) {
            List<Integer> roots = entry.getValue();

            suiteFutures.add(suiteExecutor.submit(() -> {
                List<ApiResult> suiteResults = Collections.synchronizedList(new ArrayList<>());

                int threads = Runtime.getRuntime().availableProcessors();
                ExecutorService rootExecutor = Executors.newFixedThreadPool(Math.min(roots.size(), threads));

                List<Future<?>> rootFutures = new ArrayList<>();

                for (Integer root : roots) {
                    rootFutures.add(rootExecutor.submit(() -> executeChain(root, map, childMap, shared, status,
                            requiredKeys, cookie, headers, baseUrl, suiteResults)));
                }
                for (Future<?> rf : rootFutures) {
                    rf.get();
                }
                rootExecutor.shutdown();
                return suiteResults;
            }));
        }
        try {
            for (Future<List<ApiResult>> f : suiteFutures) {
                results.addAll(f.get());
            }
        } catch (Exception ignored) {
        }
        suiteExecutor.shutdown();
        return results;
    }

    private void executeChain(Integer id,
                              Map<Integer, JsonNode> all,
                              Map<Integer, List<Integer>> child,
                              Map<String, Object> shared,
                              Map<Integer, TestStatus> status,
                              Set<String> requiredKeys,
                              String cookie,
                              Map<String, String> headers,
                              String baseUrl,
                              List<ApiResult> out) {

        JsonNode test = all.get(id);
        if (test == null) return;
        if (status.containsKey(id)) return;

        if (test.has("parentId") && !test.get("parentId").isNull()) {

            List<Integer> parents = new ArrayList<>();
            JsonNode p = test.get("parentId");

            if (p.isArray())
                p.forEach(x -> parents.add(x.asInt()));
            else
                parents.add(p.asInt());

            boolean anyParentFailed =
                    parents.stream().anyMatch(pid -> status.get(pid) == TestStatus.FAILED);

            boolean anyParentSkipped =
                    parents.stream().anyMatch(pid -> status.get(pid) == TestStatus.SKIPPED);

            boolean allParentsPassed =
                    parents.stream().allMatch(pid -> status.get(pid) == TestStatus.PASSED);

            if (anyParentFailed) {
                addSkip(id, test, out, status,
                        "Skipped because one of the parent testcase failed");
                skipDescendants(id, all, child, out, status);
                return;
            }

            if (anyParentSkipped && !allParentsPassed) {
                addSkip(id, test, out, status,
                        "Skipped because Parent testcase skipped");
                skipDescendants(id, all, child, out, status);
                return;
            }

            if (!allParentsPassed) return;
        }


        Boolean pre = runPrecheck(test, cookie, headers, baseUrl);
        if (Boolean.FALSE.equals(pre)) {
            addSkip(id, test, out, status,
                    "Skipped because Precheck Condition Failed");
            skipDescendants(id, all, child, out, status);
            return;
        }
        String method = test.path("method").asText("GET");

        List<Map<String, Object>> expandedVars = expandVariables(shared);
        boolean multipleExecutions = expandedVars.size() > 1;

        List<ApiResult.CheckResult> combinedChecks = new ArrayList<>();
        int totalPass = 0;
        int totalFail = 0;

        for (Map<String, Object> vars : expandedVars) {

            String endpoint =
                    injectDynamic(test.get("endpoint").asText(), vars);

            String fullUrl = buildURL(baseUrl, endpoint);

            if (multipleExecutions) {
                combinedChecks.add(
                        new ApiResult.CheckResult(
                                "EXECUTION",
                                "INFO",
                                "Endpoint: " + fullUrl
                        )
                );
            }


            ApiCaller.ApiResponse resp =
                    apiCaller.callApi(fullUrl, method, cookie, headers);

            int passCount = 0;
            int failCount = 0;

            if (test.has("checks")) {
                for (JsonNode c : test.get("checks")) {

                    String type = c.get("type").asText();
                    Validator v = ValidatorFactory.getValidator(type);
                    boolean ok = v != null && v.validate(resp, c);

                    String msg = switch (type) {
                        case "fieldExistence" -> FieldExistenceValidator.getLastMessage();
                        case "keyPresence" -> KeyPresenceValidator.getLastMessage();
                        case "patternMatch" -> PatternMatchValidator.getLastMessage();
                        case "valueMatch" -> ValueMatchValidator.getLastMessage();
                        default -> ok ? "PASS" : "FAIL";
                    };

                    combinedChecks.add(
                            new ApiResult.CheckResult(
                                    type,
                                    ok ? "PASS" : "FAIL",
                                    msg
                            )
                    );

                    if (ok) passCount++;
                    else failCount++;
                }
            }
            totalPass += passCount;
            totalFail += failCount;

            if (failCount == 0) {
                extractRequired(test, resp, requiredKeys, shared);
            }
        }
        String originalEndpoint = test.get("endpoint").asText();

        String displayEndpoint;
        String displayFullUrl;

        if (multipleExecutions) {
            displayEndpoint = originalEndpoint;
            displayFullUrl = buildURL(baseUrl, originalEndpoint);
        } else {
            displayEndpoint = injectDynamic(originalEndpoint, expandedVars.get(0));
            displayFullUrl = buildURL(baseUrl, displayEndpoint);
        }

        ApiResult finalResult = new ApiResult(
                test.has("id") ? test.get("id").asInt() : -1,
                test.path("name").asText(),
                displayEndpoint,
                displayFullUrl,
                new ApiResult.Summary(totalPass + totalFail, totalPass, totalFail),
                combinedChecks,
                test.has("path") ? test.get("path").asText() : null,
                null,
                test.has("suite") ? test.get("suite").asText() : null
        );

        out.add(finalResult);

        boolean overallPass = totalFail == 0;
        status.put(id, overallPass ? TestStatus.PASSED : TestStatus.FAILED);

        if (child.containsKey(id)) {
            for (Integer c : child.get(id)) {
                executeChain(c, all, child, shared,
                        status, requiredKeys,
                        cookie, headers, baseUrl, out);
            }
        }
    }

    private void addSkip(Integer id, JsonNode test, List<ApiResult> out, Map<Integer, TestStatus> status,
                         String reason) {

        if (status.containsKey(id))
            return;

        out.add(ApiResult.skipped(id, test.get("name").asText(), test.has("path") ? test.get("path").asText() : null,
                null, test.has("suite") ? test.get("suite").asText() : null, reason));

        status.put(id, TestStatus.SKIPPED);
    }

    private void skipDescendants(Integer parent, Map<Integer, JsonNode> all, Map<Integer, List<Integer>> child,
                                 List<ApiResult> out, Map<Integer, TestStatus> status) {

        if (!child.containsKey(parent))
            return;

        for (Integer c : child.get(parent)) {
            JsonNode t = all.get(c);
            addSkip(c, t, out, status, "Skipped because Parent testcase skipped");
            skipDescendants(c, all, child, out, status);
        }
    }

    private ApiResult runSingleTest(JsonNode test, String cookie, Map<String, String> headers, String baseUrl) {

        String endpoint = test.get("endpoint").asText();
        endpoint = injectDynamic(endpoint, new HashMap<>());

        String fullUrl = buildURL(baseUrl, endpoint);

        ApiCaller.ApiResponse resp = apiCaller.callApi(fullUrl, test.path("method").asText("GET"), cookie, headers);

        return buildResult(test, endpoint, fullUrl, resp);
    }

    private ApiResult buildResult(JsonNode test, String resolvedEndpoint, String fullUrl, ApiCaller.ApiResponse resp) {

        int testId = test.has("id") ? test.get("id").asInt() : -1;
        String testPath = test.has("path") ? test.get("path").asText() : "UNKNOWN_PATH";

        Integer parentId = null;
        if (test.has("parentId") && !test.get("parentId").isNull()) {
            JsonNode pid = test.get("parentId");

            if (pid.isArray()) {
                parentId = pid.get(0).asInt();
            } else {
                parentId = pid.asInt();
            }
        }

        String rawSuite = test.has("suite") ? test.get("suite").asText() : "";
        String suite = (rawSuite != null && rawSuite.contains("/"))
                ? rawSuite.substring(0, rawSuite.indexOf("/")).trim()
                : rawSuite;

        List<ApiResult.CheckResult> checks = new ArrayList<>();
        int pass = 0, fail = 0;

        if (test.has("checks")) {
            for (JsonNode c : test.get("checks")) {
                Validator validator = ValidatorFactory.getValidator(c.get("type").asText());
                boolean ok = validator != null && validator.validate(resp, c);
                String msg = ok ? "PASS" : "FAIL";
                checks.add(new ApiResult.CheckResult(c.get("type").asText(), msg, msg));
                if (ok)
                    pass++;
                else
                    fail++;
            }
        }

        return new ApiResult(testId, test.path("name").asText(), resolvedEndpoint, fullUrl,
                new ApiResult.Summary(pass + fail, pass, fail), checks, testPath, parentId, suite);
    }

    private ApiResult validateAndBuild(JsonNode test, ApiCaller.ApiResponse resp, String resolvedEndpoint,
                                       String fullUrl) {

        int testId = test.has("id") ? test.get("id").asInt() : -1;
        String testPath = test.has("path") ? test.get("path").asText() : "UNKNOWN_PATH";

        Integer parentId = null;
        if (test.has("parentId") && !test.get("parentId").isNull()) {
            JsonNode pid = test.get("parentId");

            if (pid.isArray()) {
                parentId = pid.get(0).asInt();
            } else {
                parentId = pid.asInt();
            }
        }

        String rawSuite = test.has("suite") ? test.get("suite").asText() : "";
        String suite = (rawSuite != null && rawSuite.contains("/"))
                ? rawSuite.substring(0, rawSuite.indexOf("/")).trim()
                : rawSuite;

        List<ApiResult.CheckResult> list = new ArrayList<>();
        int p = 0, f = 0;

        if (test.has("checks")) {
            for (JsonNode c : test.get("checks")) {
                String type = c.get("type").asText();
                Validator v = ValidatorFactory.getValidator(type);
                boolean ok = v != null && v.validate(resp, c);

                String msg = switch (type) {
                    case "fieldExistence" -> FieldExistenceValidator.getLastMessage();
                    case "keyPresence" -> KeyPresenceValidator.getLastMessage();
                    case "patternMatch" -> PatternMatchValidator.getLastMessage();
                    case "valueMatch" -> ValueMatchValidator.getLastMessage();
                    default -> ok ? "PASS" : "FAIL";
                };

                list.add(new ApiResult.CheckResult(type, ok ? "PASS" : "FAIL", msg));
                if (ok)
                    p++;
                else
                    f++;
            }
        }
        return new ApiResult(testId, test.path("name").asText(), resolvedEndpoint, fullUrl,
                new ApiResult.Summary(p + f, p, f), list, testPath, parentId, suite);
    }

    private String buildURL(String base, String endpoint) {
        if (!base.endsWith("/"))
            base += "/";
        return endpoint.startsWith("http") ? endpoint
                : base + (endpoint.startsWith("/") ? endpoint.substring(1) : endpoint);
    }

    private String injectDynamic(String endpoint, Map<String, Object> var) {
        for (var entry : var.entrySet()) {
            endpoint = endpoint.replace("{" + entry.getKey() + "}", entry.getValue().toString());
        }
        return endpoint;
    }

    private void extractRequired(JsonNode test, ApiCaller.ApiResponse resp, Set<String> keys,
                                 Map<String, Object> store) {
        if (!test.has("requires"))
            return;

        try {
            JsonNode body = objectMapper.readTree(resp.getResponseBody());
            if (body == null)
                return;

            for (JsonNode req : test.get("requires")) {
                String key, path;
                if (req.isTextual()) {
                    key = req.asText();
                    List<JsonNode> list = resolvePath(body, key);
                    if (!list.isEmpty())
                        store.put(key, list.get(0).asText());
                    continue;
                }

                key = req.get("name").asText();
                path = req.get("path").asText();
                List<JsonNode> values = resolvePath(body, path);

                if (!values.isEmpty()) {
                    if (values.size() == 1 && values.get(0).isValueNode()) {
                        store.put(key, values.get(0).asText());
                    } else {
                        List<String> arr = new ArrayList<>();
                        for (JsonNode v : values)
                            if (v.isValueNode())
                                arr.add(v.asText());
                        store.put(key, arr);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private List<JsonNode> resolvePath(JsonNode root, String path) {
        List<JsonNode> results = new ArrayList<>();
        if (root == null || path == null)
            return results;

        path = path.replace("$", "");
        if (path.startsWith("."))
            path = path.substring(1);

        String[] segments = path.split("\\.")
                ;
        results.add(root);

        for (String seg : segments) {
            List<JsonNode> next = new ArrayList<>();
            boolean hasArray = seg.contains("[");
            String field = hasArray ? seg.substring(0, seg.indexOf("[")) : seg;
            String inside = hasArray ? seg.substring(seg.indexOf("[") + 1, seg.indexOf("]")) : null;

            for (JsonNode node : results) {
                JsonNode fieldNode = field.isEmpty() ? node : node.get(field);
                if (fieldNode == null)
                    continue;

                if (hasArray) {

                    if ("*".equals(inside)) {
                        if (fieldNode.isArray()) {
                            fieldNode.forEach(next::add);
                        }
                    } else {
                        try {
                            int idx = Integer.parseInt(inside);
                            if (fieldNode.isArray() && fieldNode.size() > idx) {
                                next.add(fieldNode.get(idx));
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                } else {
                    next.add(fieldNode);
                }
            }

            results = next;
        }
        return results;
    }

    private JsonNode findField(JsonNode node, String key) {
        if (node.isObject()) {
            if (node.has(key))
                return node.get(key);
            for (JsonNode c : node) {
                JsonNode x = findField(c, key);
                if (x != null)
                    return x;
            }
        }
        if (node.isArray())
            for (JsonNode c : node) {
                JsonNode x = findField(c, key);
                if (x != null)
                    return x;
            }
        return null;
    }

    private void debugPrintHierarchy(Map<Integer, List<Integer>> childMap, Map<Integer, JsonNode> map) {
        System.out.println("\n================= TESTCASE HIERARCHY (DEBUG) =================");
        Set<Integer> roots = new LinkedHashSet<>();
        for (Integer id : map.keySet()) {
            JsonNode t = map.get(id);
            if (!t.has("parentId") || t.get("parentId").isNull()) {
                roots.add(id);
            }
        }
        for (Integer root : roots) {
            System.out.println("ROOT → " + root + " (" + map.get(root).get("name").asText() + ")");
            printChildrenRecursive(root, childMap, map, 1);
        }
    }

    private void printChildrenRecursive(Integer parent, Map<Integer, List<Integer>> childMap,
                                        Map<Integer, JsonNode> map, int level) {

        if (!childMap.containsKey(parent))
            return;
        for (Integer child : childMap.get(parent)) {
            String indent = "   ".repeat(level);
            System.out.println(
                    indent + "└── Level " + level + ": " + child + " (" + map.get(child).get("name").asText() + ")");

            printChildrenRecursive(child, childMap, map, level + 1);
        }
    }

    private JsonNode resolvePrecheckConfig(JsonNode precheckNode) {
        if (precheckNode == null)
            return null;
        if (precheckNode.isObject())
            return precheckNode;
        if (precheckNode.isTextual())
            return precheckRules.get(precheckNode.asText());
        return null;
    }

    private Boolean runPrecheck(JsonNode test, String cookie, Map<String, String> headers, String baseUrl) {
        if (!test.has("precheck"))
            return true;

        JsonNode pc = resolvePrecheckConfig(test.get("precheck"));
        if (pc == null)
            return true;

        if (pc.isTextual()) {
            JsonNode rule = precheckRules.get(pc.asText());
            if (rule == null)
                return true;
            return executePrecheckRule(rule, cookie, headers, baseUrl);
        }
        return executePrecheckRule(pc, cookie, headers, baseUrl);
    }

    private Boolean executePrecheckRule(JsonNode rule, String cookie, Map<String, String> headers, String baseUrl) {
        try {
            String endpoint = rule.get("endpoint").asText();
            String method = rule.path("method").asText("GET");

            ApiCaller.ApiResponse resp = apiCaller.callApi(buildURL(baseUrl, endpoint), method, cookie, headers);

            JsonNode body = objectMapper.readTree(resp.getResponseBody());
            if (body == null)
                return false;

            if (rule.has("conditions")) {
                String logic = rule.path("logic").asText("AND");
                boolean result = logic.equalsIgnoreCase("AND");

                for (JsonNode cond : rule.get("conditions")) {
                    List<JsonNode> values = resolvePath(body, cond.get("path").asText());
                    boolean condResult = logic.equalsIgnoreCase("AND");

                    for (JsonNode v : values) {
                        String act = v.isValueNode() ? v.asText() : "";
                        boolean ok = evaluatePrecheckCondition(act, cond.get("operator").asText(),
                                cond.get("value").asText());
                        condResult = logic.equalsIgnoreCase("AND") ? (condResult && ok) : (condResult || ok);
                    }
                    result = logic.equalsIgnoreCase("AND") ? (result && condResult) : (result || condResult);
                }
                return result;
            }

            List<JsonNode> list = resolvePath(body, rule.get("extractPath").asText());
            if (list.isEmpty())
                return false;

            for (JsonNode v : list) {
                if (evaluatePrecheckCondition(v.asText(), rule.get("operator").asText(), rule.get("value").asText())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private List<Map<String, Object>> expandVariables(Map<String, Object> shared) {
        List<Map<String, Object>> expanded = new ArrayList<>();

        boolean hasList = shared.values().stream().anyMatch(v -> v instanceof List);

        if (!hasList) {
            expanded.add(shared);
            return expanded;
        }

        for (Map.Entry<String, Object> entry : shared.entrySet()) {
            if (entry.getValue() instanceof List<?> list) {
                for (Object val : list) {
                    Map<String, Object> copy = new HashMap<>(shared);
                    copy.put(entry.getKey(), val);
                    expanded.add(copy);
                }
                return expanded;
            }
        }

        expanded.add(shared);
        return expanded;
    }

    private boolean evaluatePrecheckCondition(String actual, String operator, String expected) {
        switch (operator) {

            case "==":
                return actual.equals(expected);
            case "!=":
                return !actual.equals(expected);
            case ">":
                return Double.parseDouble(actual) > Double.parseDouble(expected);
            case "<":
                return Double.parseDouble(actual) < Double.parseDouble(expected);
            case ">=":
                return Double.parseDouble(actual) >= Double.parseDouble(expected);
            case "<=":
                return Double.parseDouble(actual) <= Double.parseDouble(expected);
            case "contains":
                return actual.contains(expected);
            case "notContains":
                return !actual.contains(expected);
            case "startsWith":
                return actual.startsWith(expected);
            case "endsWith":
                return actual.endsWith(expected);
        }
        return false;
    }
}
