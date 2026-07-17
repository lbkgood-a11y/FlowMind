package com.triobase.service.openapi.dto;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
public record ProductRouteMemberRequest(@NotBlank String routeKey, @NotBlank String releaseSnapshotId,
                                        JsonNode operations, JsonNode scopes, JsonNode canonicalStructureVersionIds) { }
