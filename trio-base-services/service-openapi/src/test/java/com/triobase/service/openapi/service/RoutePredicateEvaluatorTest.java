package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.dto.RouteResolutionContext;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutePredicateEvaluatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RoutePredicateEvaluator evaluator = new RoutePredicateEvaluator();

    @Test
    void evaluatesAllowListedPredicatesDeterministically() throws Exception {
        var predicate = objectMapper.readTree("""
                {"all":[
                  {"source":"HEADER","name":"X-Region","operator":"EQUALS","value":"CN"},
                  {"source":"CLAIM","name":"tier","operator":"IN","values":["gold","silver"]}
                ]}
                """);
        var context = new RouteResolutionContext(LocalDateTime.parse("2026-07-16T10:00:00"),
                Map.of("x-region", "CN"), Map.of(), Map.of("tier", "gold"));

        assertThat(evaluator.matches(predicate, context)).isTrue();
    }

    @Test
    void rejectsExpressionsAndDetectsProvablyDisjointPredicates() throws Exception {
        var unsafe = objectMapper.readTree("{\"all\":[{\"source\":\"HEADER\",\"name\":\"x\",\"operator\":\"SCRIPT\",\"value\":\"eval()\"}]}");
        assertThatThrownBy(() -> evaluator.validate(unsafe))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_ROUTE_PREDICATE_INVALID");

        var cn = objectMapper.readTree("{\"all\":[{\"source\":\"HEADER\",\"name\":\"x-region\",\"operator\":\"EQUALS\",\"value\":\"CN\"}]}");
        var us = objectMapper.readTree("{\"all\":[{\"source\":\"HEADER\",\"name\":\"x-region\",\"operator\":\"EQUALS\",\"value\":\"US\"}]}");
        assertThat(evaluator.canOverlap(cn, us)).isFalse();
        assertThat(evaluator.canOverlap(cn, objectMapper.createObjectNode())).isTrue();
    }
}
