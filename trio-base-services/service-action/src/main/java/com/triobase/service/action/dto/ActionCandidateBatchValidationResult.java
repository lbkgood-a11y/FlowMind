package com.triobase.service.action.dto;

import com.triobase.common.action.model.ActionCandidateValidationResult;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ActionCandidateBatchValidationResult {
    private List<ActionCandidateValidationResult> results = new ArrayList<>();
}
