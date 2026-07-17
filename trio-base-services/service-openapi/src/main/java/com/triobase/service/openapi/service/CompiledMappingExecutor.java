package com.triobase.service.openapi.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.entity.MappingVersion;
import com.triobase.service.openapi.domain.entity.StructureVersion;
import com.triobase.service.openapi.domain.enums.MappingOperation;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.dto.MappingRuleRequest;
import com.triobase.service.openapi.infrastructure.mapper.MappingVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.StructureVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
@Service @RequiredArgsConstructor
public class CompiledMappingExecutor {
 private final MappingVersionMapper mappingMapper;private final StructureVersionMapper structureMapper;
 private final JsonPayloadValidator validator;private final MappingTransformationEngine engine;
 public JsonNode execute(String mappingVersionId,JsonNode payload){MappingVersion mapping=mappingMapper.selectById(mappingVersionId);if(mapping==null||mapping.getLifecycleState()!=VersionLifecycleState.PUBLISHED||mapping.getCompiledPlan()==null)throw new BizException(40925,"OPENAPI_RUNTIME_MAPPING_NOT_PUBLISHED");StructureVersion source=structureMapper.selectById(mapping.getSourceStructureVersionId());StructureVersion target=structureMapper.selectById(mapping.getTargetStructureVersionId());if(source==null||target==null)throw new BizException(40925,"OPENAPI_RUNTIME_MAPPING_STRUCTURE_MISSING");requireValid(source.getSchemaContent(),payload,"OPENAPI_RUNTIME_SOURCE_SCHEMA_INVALID");JsonNode output=engine.transform(payload,rules(mapping.getCompiledPlan())).output();requireValid(target.getSchemaContent(),output,"OPENAPI_RUNTIME_TARGET_SCHEMA_INVALID");return output;}
 private List<MappingRuleRequest> rules(JsonNode plan){List<MappingRuleRequest> rules=new ArrayList<>();for(JsonNode r:plan.path("rules")){rules.add(new MappingRuleRequest(r.path("order").asInt(),MappingOperation.valueOf(r.path("operation").asText()),r.has("sourcePointer")?r.path("sourcePointer").asText():null,r.path("targetPointer").asText(),r.path("config"),r.path("required").asBoolean(false)));}return rules.stream().sorted(Comparator.comparingInt(MappingRuleRequest::order)).toList();}
 private void requireValid(JsonNode schema,JsonNode payload,String code){JsonPayloadValidator.ValidationResult result=validator.validate(schema,payload);if(!result.valid())throw new BizException(42230,code+":"+String.join(",",result.errors()));}
}
