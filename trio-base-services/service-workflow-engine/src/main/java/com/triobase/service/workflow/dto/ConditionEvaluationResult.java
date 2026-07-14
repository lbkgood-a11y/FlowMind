package com.triobase.service.workflow.dto;

import lombok.Data;

@Data
public class ConditionEvaluationResult {
    private String status;
    private String expression;
    private boolean matched;
    private boolean defaultBranch;
    private String errorCode;

    public static ConditionEvaluationResult matched(String expression, boolean defaultBranch) {
        ConditionEvaluationResult result = new ConditionEvaluationResult();
        result.setStatus(defaultBranch ? "DEFAULT" : "MATCHED");
        result.setExpression(expression);
        result.setMatched(true);
        result.setDefaultBranch(defaultBranch);
        return result;
    }

    public static ConditionEvaluationResult notMatched(String expression) {
        ConditionEvaluationResult result = new ConditionEvaluationResult();
        result.setStatus("NOT_MATCHED");
        result.setExpression(expression);
        return result;
    }

    public static ConditionEvaluationResult error(String expression, String errorCode) {
        ConditionEvaluationResult result = new ConditionEvaluationResult();
        result.setStatus("ERROR");
        result.setExpression(expression);
        result.setErrorCode(errorCode);
        return result;
    }
}
