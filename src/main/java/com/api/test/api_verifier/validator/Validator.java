package com.api.test.api_verifier.validator;

import com.api.test.api_verifier.service.ApiCaller;
import com.fasterxml.jackson.databind.JsonNode;

public interface Validator {

    boolean validate(ApiCaller.ApiResponse response, JsonNode checkNode);

    default String getMessage() {
        return "";
    }
}
