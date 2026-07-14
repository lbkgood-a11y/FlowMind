package com.triobase.data.analytics.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CreateDatasetRequest {
    @NotBlank
    private String datasetKey;
    @NotBlank
    private String name;
    private String datasetType = "STRUCTURED";
    private String ownerId;
    private String ownerName;
    private String backingTable;
    private String description;
    @Valid
    private List<DatasetFieldRequest> fields = new ArrayList<>();
}
