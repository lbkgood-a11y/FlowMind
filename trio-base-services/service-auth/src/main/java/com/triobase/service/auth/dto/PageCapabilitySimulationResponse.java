package com.triobase.service.auth.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class PageCapabilitySimulationResponse {
    boolean allowed;
    String outcome;
    String evaluationMode;
    String pageName;
    String capabilityName;
    String dataScopeSummary;
    List<String> fieldSummaries;
    List<String> guardSummaries;
    List<String> reasons;
}
