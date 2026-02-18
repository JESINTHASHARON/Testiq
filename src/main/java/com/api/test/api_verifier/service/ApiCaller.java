package com.api.test.api_verifier.service;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
public class ApiCaller {

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public static class ApiResponse {
        private final int statusCode;
        private final String responseBody;

        public ApiResponse(int statusCode, String responseBody) {
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getResponseBody() {
            return responseBody;
        }
    }

    public ApiResponse callApi(String urlStr, String method, String cookie, Map<String, String> headers) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(urlStr))
                    .timeout(Duration.ofSeconds(10));

            if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
                builder.method(method, HttpRequest.BodyPublishers.ofString("{}"));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            if (headers != null) {
                headers.forEach(builder::header);
            }

            if (cookie != null && !cookie.isEmpty()) {
                builder.header("Cookie", cookie);
            }

            HttpRequest request = builder.build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new ApiResponse(response.statusCode(), response.body());

        } catch (Exception e) {
            e.printStackTrace();
            return new ApiResponse(0, "Error: " + e.getMessage());
        }
    }
}
