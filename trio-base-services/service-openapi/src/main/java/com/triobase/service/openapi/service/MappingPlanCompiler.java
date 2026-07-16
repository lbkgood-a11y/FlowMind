package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.triobase.service.openapi.domain.entity.MappingVersion;
import com.triobase.service.openapi.dto.MappingRuleRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MappingPlanCompiler {

    private final ObjectMapper objectMapper;

    public CompiledPlan compile(MappingVersion version, List<MappingRuleRequest> rules) {
        ObjectNode plan = objectMapper.createObjectNode();
        plan.put("formatVersion", "1");
        plan.put("mappingVersionId", version.getId());
        plan.put("sourceStructureVersionId", version.getSourceStructureVersionId());
        plan.put("targetStructureVersionId", version.getTargetStructureVersionId());
        ArrayNode compiledRules = plan.putArray("rules");
        (rules == null ? List.<MappingRuleRequest>of() : rules).stream()
                .sorted(Comparator.comparingInt(MappingRuleRequest::order))
                .forEach(rule -> {
                    ObjectNode node = compiledRules.addObject();
                    node.put("order", rule.order());
                    node.put("operation", rule.operation().name());
                    if (rule.sourcePointer() != null) {
                        node.put("sourcePointer", rule.sourcePointer());
                    }
                    node.put("targetPointer", rule.targetPointer());
                    node.put("required", rule.required());
                    node.set("config", rule.config() == null
                            ? objectMapper.createObjectNode() : rule.config().deepCopy());
                });
        String canonical = plan.toString();
        return new CompiledPlan(plan, sha256(canonical), canonical.getBytes(StandardCharsets.UTF_8));
    }

    public JsonNode deserialize(byte[] serialized) {
        try {
            return objectMapper.readTree(serialized);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid compiled mapping plan", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record CompiledPlan(JsonNode plan, String hash, byte[] serialized) {
    }
}
