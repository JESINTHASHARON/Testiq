package com.api.test.api_verifier.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.api.test.api_verifier.service.RunDetailService;
import com.api.test.api_verifier.service.RunQueryService;

@RestController
@RequestMapping("/api/results")
public class ResultController {

    @Autowired
    private RunQueryService runQueryService;

    @Autowired
    private RunDetailService runDetailService;

    @GetMapping("/runs")
    public ResponseEntity<?> getRunHistory(@RequestParam(defaultValue = "all") String filter,
                                           @RequestParam(required = false) String startDate, @RequestParam(required = false) String endDate,
                                           @RequestParam(required = false) String search,
                                           @RequestParam(defaultValue = "10") int limit, @RequestParam(defaultValue = "0") int offset,
                                           @RequestParam(defaultValue = "desc") String sort) {

        return ResponseEntity.ok(runQueryService.fetchRunList(filter, startDate, endDate, search, limit, offset, sort));
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<?> getRunDetails(@PathVariable String runId) {
        Map<String, Object> result = runDetailService.getRunDetails(runId);

        if (result == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Run not found"));
        }

        return ResponseEntity.ok(result);
    }

}
