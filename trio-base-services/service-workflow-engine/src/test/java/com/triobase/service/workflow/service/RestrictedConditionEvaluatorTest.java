package com.triobase.service.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.workflow.dto.ConditionEvaluationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestrictedConditionEvaluatorTest {

    private RestrictedConditionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new RestrictedConditionEvaluator(new ObjectMapper());
    }

    @Test
    void evaluatesFeeReportNumericBranches() {
        assertFalse(evaluator.evaluate("amount > 5000", "{\"amount\":3000}").isMatched());
        assertTrue(evaluator.evaluate("amount > 5000", "{\"amount\":8000}").isMatched());
    }

    @Test
    void supportsBooleanNullAndArithmeticExpressions() {
        ConditionEvaluationResult result = evaluator.evaluate(
                "approved && remark == null && amount + tax >= 100",
                "{\"approved\":true,\"remark\":null,\"amount\":90,\"tax\":10}");

        assertEquals("MATCHED", result.getStatus());
    }

    @Test
    void reportsDefaultBranchExplicitly() {
        ConditionEvaluationResult result = evaluator.evaluate("true", "{}");

        assertEquals("DEFAULT", result.getStatus());
        assertTrue(result.isDefaultBranch());
    }

    @Test
    void rejectsConstructorsReflectionMethodsAndSideEffects() {
        assertUnsafe("new('java.io.File', '/tmp/test')");
        assertUnsafe("value.getClass()");
        assertUnsafe("java.lang.Runtime");
        assertUnsafe("amount = 1");
    }

    @Test
    void reportsInvalidAndNonBooleanExpressions() {
        assertEquals("INVALID_CONDITION_EXPRESSION",
                evaluator.evaluate("amount >", "{\"amount\":1}").getErrorCode());
        assertEquals("CONDITION_RESULT_NOT_BOOLEAN",
                evaluator.evaluate("amount + 1", "{\"amount\":1}").getErrorCode());
    }

    @Test
    void rejectsOversizedExpressionBeforeParsing() {
        String expression = "a".repeat(RestrictedConditionEvaluator.MAX_EXPRESSION_LENGTH + 1);

        BizException exception = assertThrows(BizException.class,
                () -> evaluator.validate(expression));
        assertEquals("CONDITION_EXPRESSION_TOO_LONG", exception.getMessage());
    }

    private void assertUnsafe(String expression) {
        BizException exception = assertThrows(BizException.class,
                () -> evaluator.validate(expression));
        assertEquals("UNSAFE_CONDITION_EXPRESSION", exception.getMessage());
    }
}
