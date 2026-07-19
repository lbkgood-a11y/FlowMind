package com.triobase.service.action.dto;

import com.triobase.common.action.model.ActionCandidate;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ActionCandidateBatchRequest {
    private List<ActionCandidate> candidates = new ArrayList<>();
}
