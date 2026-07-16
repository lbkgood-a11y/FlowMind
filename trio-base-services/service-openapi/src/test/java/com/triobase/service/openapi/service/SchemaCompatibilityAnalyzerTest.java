package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaCompatibilityAnalyzerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final SchemaCompatibilityAnalyzer analyzer = new SchemaCompatibilityAnalyzer();

    @Test
    void optionalFieldAdditionIsCompatible() throws Exception {
        var previous = OBJECT_MAPPER.readTree("""
                {"type":"object","properties":{"id":{"type":"string"}}}
                """);
        var candidate = OBJECT_MAPPER.readTree("""
                {"type":"object","properties":{"id":{"type":"string"},"note":{"type":"string"}}}
                """);

        var report = analyzer.analyze(previous, candidate, OBJECT_MAPPER.createObjectNode());

        assertThat(report.compatible()).isTrue();
        assertThat(report.compatibleChanges()).contains("OPTIONAL_FIELD_ADDED:/note");
    }

    @Test
    void removalAndRequiredAdditionAreBreaking() throws Exception {
        var previous = OBJECT_MAPPER.readTree("""
                {"type":"object","properties":{"id":{"type":"string"}}}
                """);
        var candidate = OBJECT_MAPPER.readTree("""
                {"type":"object","required":["amount"],"properties":{"amount":{"type":"number"}}}
                """);

        var report = analyzer.analyze(previous, candidate, OBJECT_MAPPER.createObjectNode());

        assertThat(report.breaking()).isTrue();
        assertThat(report.breakingReasons())
                .contains("FIELD_REMOVED:/id", "REQUIRED_FIELD_ADDED:/amount");
    }

    @Test
    void sensitivityReductionRequiresSecurityReview() throws Exception {
        var previous = OBJECT_MAPPER.readTree("""
                {"type":"object","properties":{"phone":{"type":"string","x-triobase-sensitivity":"RESTRICTED"}}}
                """);
        var candidate = OBJECT_MAPPER.readTree("""
                {"type":"object","properties":{"phone":{"type":"string","x-triobase-sensitivity":"PUBLIC"}}}
                """);

        var report = analyzer.analyze(previous, candidate, OBJECT_MAPPER.createObjectNode());

        assertThat(report.securitySensitive()).isTrue();
        assertThat(report.securityReasons()).contains("SENSITIVITY_LOWERED:/phone");
    }
}
