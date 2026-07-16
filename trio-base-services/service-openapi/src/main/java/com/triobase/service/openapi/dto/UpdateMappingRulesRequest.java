package com.triobase.service.openapi.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateMappingRulesRequest(@NotNull List<@Valid MappingRuleRequest> rules) {
}
