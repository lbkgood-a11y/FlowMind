package com.triobase.service.openapi.dto;
import com.triobase.service.openapi.domain.enums.AuthenticationType;
import com.triobase.service.openapi.domain.enums.CredentialBindingState;
import java.time.LocalDateTime;
import java.util.Map;
public record CredentialBindingResponse(String bindingId,String applicationClientId,AuthenticationType authenticationType,
        Integer credentialVersion,String secretReference,CredentialBindingState lifecycleState,LocalDateTime validFrom,
        LocalDateTime expiresAt,LocalDateTime retirementAt,Map<String,String> oneTimeSecret) { }
