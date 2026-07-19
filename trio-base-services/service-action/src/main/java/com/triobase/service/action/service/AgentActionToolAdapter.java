package com.triobase.service.action.service;

import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.model.ActionCandidate;
import com.triobase.common.action.model.GlobalActionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentActionToolAdapter {

    private final ActionCandidateService candidateService;

    public GlobalActionResult dispatch(ActionCandidate candidate) {
        ActionCandidate actual = candidate != null ? candidate : new ActionCandidate();
        if (actual.getSource() == null) {
            actual.setSource(ActionSource.AGENT);
        }
        return candidateService.dispatch(actual);
    }
}
