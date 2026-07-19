package com.triobase.service.action.controller;

import com.triobase.common.action.model.ActionCandidate;
import com.triobase.common.action.model.ActionCandidateValidationResult;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.core.result.R;
import com.triobase.service.action.dto.ActionCandidateBatchRequest;
import com.triobase.service.action.dto.ActionCandidateBatchValidationResult;
import com.triobase.service.action.service.ActionCandidateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/actions/candidates")
@RequiredArgsConstructor
public class ActionCandidateController {

    private final ActionCandidateService candidateService;

    @PostMapping("/validate")
    public R<ActionCandidateValidationResult> validate(@RequestBody ActionCandidate candidate) {
        return R.ok(candidateService.validate(candidate));
    }

    @PostMapping("/batch-validate")
    public R<ActionCandidateBatchValidationResult> batchValidate(
            @RequestBody ActionCandidateBatchRequest request) {
        ActionCandidateBatchValidationResult result = new ActionCandidateBatchValidationResult();
        result.setResults(candidateService.validateBatch(request != null ? request.getCandidates() : null));
        return R.ok(result);
    }

    @PostMapping("/dispatch")
    public R<GlobalActionResult> dispatch(@RequestBody ActionCandidate candidate) {
        return R.ok(candidateService.dispatch(candidate));
    }
}
