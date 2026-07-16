package com.triobase.service.openapi.dto;

import java.util.List;

public record ContractTestRunResult(
        boolean passed,
        int executed,
        int failed,
        List<TestResult> results) {

    public record TestResult(String testId, String testName, boolean passed, boolean required, String message) {
    }
}
