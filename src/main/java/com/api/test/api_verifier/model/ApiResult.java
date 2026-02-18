package com.api.test.api_verifier.model;

import java.util.List;

public class ApiResult {

    private int id;
    private String name;
    private String endpoint;
    private String fullUrl;
    private String path;
    private Summary summary;
    private List<CheckResult> results;

    private Integer parentId;
    private String suite;
    private boolean skipped = false;

    private String skipReason;

    public String getSkipReason() {
        return skipReason;
    }

    public void setSkipReason(String reason) {
        this.skipReason = reason;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public void setSkipped(boolean s) {
        this.skipped = s;
    }

    public ApiResult(int id, String name, String endpoint, String fullUrl, Summary summary, List<CheckResult> results,
                     String path, Integer parentId, String suite) {
        this.id = id;
        this.name = name;
        this.endpoint = endpoint;
        this.fullUrl = fullUrl;
        this.summary = summary;
        this.results = results;
        this.path = path;
        this.parentId = parentId;
        this.suite = suite;
    }

    public ApiResult(int id, String name, String endpoint, String fullUrl, Summary summary, List<CheckResult> results) {
        this(id, name, endpoint, fullUrl, summary, results, null, null, null);
    }

    public static ApiResult skipped(int id, String name, String path, Integer parentId, String suite, String message) {

        Summary summary = new Summary(1, 0, 0);
        CheckResult check = new CheckResult("SKIPPED", "SKIPPED", message != null ? message : "Skipped");
        ApiResult result = new ApiResult(id, name, null, null, summary, List.of(check), path, parentId, suite);

        result.setSkipped(true);
        return result;
    }

    public int getId() {
        return id;
    }
    public String getName() {
        return name;
    }
    public String getEndpoint() {
        return endpoint;
    }
    public String getFullUrl() {
        return fullUrl;
    }
    public Summary getSummary() {
        return summary;
    }
    public List<CheckResult> getResults() {
        return results;
    }
    public String getPath() {
        return path;
    }
    public Integer getParentId() {
        return parentId;
    }
    public String getSuite() {
        return suite;
    }

    public static class Summary {
        private int totalChecks;
        private int passed;
        private int failed;

        public Summary(int totalChecks, int passed, int failed) {
            this.totalChecks = totalChecks;
            this.passed = passed;
            this.failed = failed;
        }

        public int getTotalChecks() {
            return totalChecks;
        }
        public int getPassed() {
            return passed;
        }
        public int getFailed() {
            return failed;
        }
    }

    public static class CheckResult {
        private String type;
        private String status;
        private String message;

        public CheckResult(String type, String status, String message) {
            this.type = type;
            this.status = status;
            this.message = message;
        }

        public String getType() {
            return type;
        }

        public String getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }
    }
}
