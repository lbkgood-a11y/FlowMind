package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.service.openapi.domain.entity.MappingContractTest;
import com.triobase.service.openapi.domain.entity.MappingVersion;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.dto.ContractTestRunResult;
import com.triobase.service.openapi.dto.MappingRuleRequest;
import com.triobase.service.openapi.dto.SaveMappingContractTestRequest;
import com.triobase.service.openapi.dto.TransformationResult;
import com.triobase.service.openapi.infrastructure.mapper.MappingContractTestMapper;
import com.triobase.service.openapi.infrastructure.mapper.MappingVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MappingContractTestService {

    private final MappingContractTestMapper contractTestMapper;
    private final MappingVersionMapper mappingVersionMapper;
    private final MappingTransformationEngine transformationEngine;

    @Transactional
    public MappingContractTest save(String mappingVersionId, SaveMappingContractTestRequest request) {
        MappingVersion version = mappingVersionMapper.selectById(mappingVersionId);
        if (version == null) {
            throw new BizException(40421, "OPENAPI_MAPPING_VERSION_NOT_FOUND");
        }
        if (version.getLifecycleState() != VersionLifecycleState.DRAFT) {
            throw new BizException(40921, "OPENAPI_PUBLISHED_MAPPING_VERSION_IMMUTABLE");
        }
        if (request == null || !StringUtils.hasText(request.testName()) || request.inputPayload() == null
                || (request.expectedOutput() == null && !StringUtils.hasText(request.expectedErrorCode()))) {
            throw new BizException(40024, "OPENAPI_MAPPING_CONTRACT_TEST_INVALID");
        }
        MappingContractTest test = new MappingContractTest();
        test.setId(UlidGenerator.nextUlid());
        test.setMappingVersionId(mappingVersionId);
        test.setTestName(request.testName().trim());
        test.setInputPayload(request.inputPayload().deepCopy());
        test.setExpectedOutput(request.expectedOutput() == null ? null : request.expectedOutput().deepCopy());
        test.setExpectedErrorCode(request.expectedErrorCode());
        test.setRequiredTest(request.required());
        test.setEnabled(request.enabled());
        test.setCreatedBy(currentOperator());
        test.setCreatedAt(LocalDateTime.now());
        test.setUpdatedBy(currentOperator());
        test.setUpdatedAt(LocalDateTime.now());
        contractTestMapper.insert(test);
        return test;
    }

    public ContractTestRunResult run(String mappingVersionId, List<MappingRuleRequest> rules) {
        List<MappingContractTest> tests = contractTestMapper.selectList(
                new LambdaQueryWrapper<MappingContractTest>()
                        .eq(MappingContractTest::getMappingVersionId, mappingVersionId)
                        .eq(MappingContractTest::getEnabled, true)
                        .orderByAsc(MappingContractTest::getTestName));
        List<ContractTestRunResult.TestResult> results = new ArrayList<>();
        int failed = 0;
        boolean requiredFailed = false;
        for (MappingContractTest test : tests) {
            ContractTestRunResult.TestResult result = execute(test, rules);
            results.add(result);
            if (!result.passed()) {
                failed++;
                requiredFailed = requiredFailed || result.required();
            }
        }
        return new ContractTestRunResult(!requiredFailed, tests.size(), failed, List.copyOf(results));
    }

    public void requirePassing(ContractTestRunResult result) {
        if (result == null || !result.passed()) {
            throw new BizException(40924, "OPENAPI_MAPPING_REQUIRED_CONTRACT_TEST_FAILED");
        }
    }

    private ContractTestRunResult.TestResult execute(
            MappingContractTest test,
            List<MappingRuleRequest> rules) {
        try {
            TransformationResult transformed = transformationEngine.transform(test.getInputPayload(), rules);
            boolean passed = test.getExpectedOutput() != null
                    && test.getExpectedOutput().equals(transformed.output());
            String message = passed ? "PASSED" : "OUTPUT_MISMATCH";
            return result(test, passed, message);
        } catch (BizException exception) {
            boolean passed = StringUtils.hasText(test.getExpectedErrorCode())
                    && (test.getExpectedErrorCode().equals(String.valueOf(exception.getCode()))
                    || test.getExpectedErrorCode().equals(exception.getMessage()));
            return result(test, passed, passed ? "EXPECTED_ERROR" : exception.getMessage());
        }
    }

    private ContractTestRunResult.TestResult result(
            MappingContractTest test,
            boolean passed,
            String message) {
        return new ContractTestRunResult.TestResult(
                test.getId(), test.getTestName(), passed,
                Boolean.TRUE.equals(test.getRequiredTest()), message);
    }

    private String currentOperator() {
        return StringUtils.hasText(SecurityContextHolder.getUserId())
                ? SecurityContextHolder.getUserId() : "SYSTEM";
    }
}
