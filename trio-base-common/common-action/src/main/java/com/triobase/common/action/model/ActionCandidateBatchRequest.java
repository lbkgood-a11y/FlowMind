package com.triobase.common.action.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ActionCandidateBatchRequest {
    private List<ActionCandidate> candidates = new ArrayList<>();
}
