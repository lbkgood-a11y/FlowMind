package com.triobase.service.action.service;

import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.ActionCandidate;
import com.triobase.common.action.model.GlobalActionResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentActionToolAdapterTest {

    @Test
    void attributesAgentSourceBeforeDispatch() {
        ActionCandidateService candidateService = mock(ActionCandidateService.class);
        GlobalActionResult expected = new GlobalActionResult();
        expected.setStatus(ActionStatus.SUCCEEDED);
        when(candidateService.dispatch(argThat(candidate -> candidate.getSource() == ActionSource.AGENT)))
                .thenReturn(expected);

        GlobalActionResult result = new AgentActionToolAdapter(candidateService)
                .dispatch(new ActionCandidate());

        assertThat(result.getStatus()).isEqualTo(ActionStatus.SUCCEEDED);
        verify(candidateService).dispatch(argThat(candidate -> candidate.getSource() == ActionSource.AGENT));
    }
}
