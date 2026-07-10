package com.triobase.service.lowcode.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.service.lowcode.dto.FormInstanceResponse;
import com.triobase.service.lowcode.dto.SubmitFormInstanceRequest;
import com.triobase.service.lowcode.entity.LcFormDefinition;
import com.triobase.service.lowcode.entity.LcFormInstance;
import com.triobase.service.lowcode.mapper.FormInstanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FormInstanceService {

    private final FormDefinitionService formDefinitionService;
    private final FormInstanceMapper formInstanceMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public FormInstanceResponse submit(String formKey, SubmitFormInstanceRequest request) {
        LcFormDefinition definition = formDefinitionService.findLatestByFormKey(formKey);
        LcFormInstance instance = new LcFormInstance();
        instance.setId(UlidGenerator.nextUlid());
        instance.setFormDefinitionId(definition.getId());
        instance.setFormKey(definition.getFormKey());
        instance.setStatus("SUBMITTED");
        instance.setSubmittedBy(StringUtils.hasText(request.getSubmittedBy()) ? request.getSubmittedBy() : "anonymous");
        instance.setSubmittedAt(LocalDateTime.now());
        instance.setCreatedAt(LocalDateTime.now());
        instance.setDataJson(toJson(request));
        formInstanceMapper.insert(instance);
        return toResponse(instance);
    }

    public FormInstanceResponse getById(String id) {
        LcFormInstance instance = formInstanceMapper.selectById(id);
        if (instance == null) {
            throw new BizException(40411, "FORM_INSTANCE_NOT_FOUND");
        }
        return toResponse(instance);
    }

    private String toJson(SubmitFormInstanceRequest request) {
        try {
            return objectMapper.writeValueAsString(request.getData());
        } catch (JsonProcessingException e) {
            throw new BizException(40003, "FORM_INSTANCE_JSON_INVALID");
        }
    }

    private FormInstanceResponse toResponse(LcFormInstance instance) {
        FormInstanceResponse response = new FormInstanceResponse();
        response.setId(instance.getId());
        response.setFormDefinitionId(instance.getFormDefinitionId());
        response.setFormKey(instance.getFormKey());
        response.setStatus(instance.getStatus());
        response.setDataJson(instance.getDataJson());
        response.setSubmittedBy(instance.getSubmittedBy());
        response.setSubmittedAt(instance.getSubmittedAt());
        return response;
    }

}
