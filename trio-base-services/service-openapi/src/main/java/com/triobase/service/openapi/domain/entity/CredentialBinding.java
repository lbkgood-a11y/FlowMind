package com.triobase.service.openapi.domain.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.openapi.enums.AuthenticationType;
import com.triobase.service.openapi.domain.enums.CredentialBindingState;
import com.triobase.common.openapi.entity.VersionedEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
@Data @EqualsAndHashCode(callSuper=true) @TableName("oa_credential_binding")
public class CredentialBinding extends VersionedEntity {
 private String applicationClientId; private AuthenticationType authenticationType; private Integer credentialVersion;
 private String secretReference; private CredentialBindingState lifecycleState; private LocalDateTime validFrom;
 private LocalDateTime expiresAt; private LocalDateTime retirementAt; private LocalDateTime revokedAt; private Boolean oneTimeSecretDelivered;
}
