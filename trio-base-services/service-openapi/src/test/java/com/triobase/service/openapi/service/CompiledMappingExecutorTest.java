package com.triobase.service.openapi.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.entity.MappingVersion;
import com.triobase.service.openapi.domain.entity.StructureVersion;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.dto.TransformationResult;
import com.triobase.service.openapi.infrastructure.mapper.MappingVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.StructureVersionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
class CompiledMappingExecutorTest {
 @Mock MappingVersionMapper mappings;@Mock StructureVersionMapper structures;@Mock JsonPayloadValidator validator;@Mock MappingTransformationEngine engine;ObjectMapper json=new ObjectMapper();
 @Test void validatesBothContractsAroundCompiledTransformation()throws Exception{MappingVersion mapping=mapping();StructureVersion source=structure("source");StructureVersion target=structure("target");when(mappings.selectById("mapping-v1")).thenReturn(mapping);when(structures.selectById("source")).thenReturn(source);when(structures.selectById("target")).thenReturn(target);when(validator.validate(any(),any())).thenReturn(new JsonPayloadValidator.ValidationResult(true,List.of()));var output=json.readTree("{\"externalId\":\"1\"}");when(engine.transform(any(),any())).thenReturn(new TransformationResult(output,List.of(),List.of()));var result=new CompiledMappingExecutor(mappings,structures,validator,engine).execute("mapping-v1",json.readTree("{\"id\":\"1\"}"));assertThat(result).isEqualTo(output);}
 @Test void rejectsInvalidSourceBeforePartnerCall(){when(mappings.selectById("mapping-v1")).thenReturn(mapping());when(structures.selectById("source")).thenReturn(structure("source"));when(structures.selectById("target")).thenReturn(structure("target"));when(validator.validate(any(),any())).thenReturn(new JsonPayloadValidator.ValidationResult(false,List.of("REQUIRED_FIELD_MISSING:/id")));assertThatThrownBy(()->new CompiledMappingExecutor(mappings,structures,validator,engine).execute("mapping-v1",json.createObjectNode())).isInstanceOf(BizException.class).hasMessageContaining("OPENAPI_RUNTIME_SOURCE_SCHEMA_INVALID");}
 private MappingVersion mapping(){MappingVersion m=new MappingVersion();m.setId("mapping-v1");m.setLifecycleState(VersionLifecycleState.PUBLISHED);m.setSourceStructureVersionId("source");m.setTargetStructureVersionId("target");m.setCompiledPlan(json.createObjectNode().putArray("rules"));return m;}private StructureVersion structure(String id){StructureVersion s=new StructureVersion();s.setId(id);s.setSchemaContent(json.createObjectNode().put("type","object"));return s;}
}
