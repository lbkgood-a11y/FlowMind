package com.triobase.common.action.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ActionCandidateBatchValidationResult {
    private List<ActionCandidateValidationResult> results = new ArrayList<>();
}
