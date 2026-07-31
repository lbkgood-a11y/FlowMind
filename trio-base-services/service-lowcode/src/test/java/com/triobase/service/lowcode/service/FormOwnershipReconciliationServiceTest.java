package com.triobase.service.lowcode.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.triobase.common.dto.internal.OrgOwnershipResponse;
import com.triobase.service.lowcode.entity.LcFormInstance;
import com.triobase.service.lowcode.mapper.FormInstanceMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FormOwnershipReconciliationServiceTest {

    @Mock private FormInstanceMapper formInstanceMapper;
    @Mock private OrgOwnershipClient ownershipClient;
    @InjectMocks private FormOwnershipReconciliationService service;

    @BeforeAll
    static void initMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        assistant.setCurrentNamespace(FormInstanceMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, LcFormInstance.class);
    }

    @Test
    void reconciliationIsIdempotentAndUsesOwnerServiceEvidence() {
        LcFormInstance first = unresolved("I1", "U1");
        LcFormInstance second = unresolved("I2", "U1");
        when(formInstanceMapper.selectCount(any())).thenReturn(2L, 0L);
        when(formInstanceMapper.selectList(any())).thenReturn(List.of(first, second));
        when(ownershipClient.primaryOwnership("tenant-a", "U1"))
                .thenReturn(new OrgOwnershipResponse("tenant-a", "U1", "ORG-1", true));
        when(formInstanceMapper.update(any(), any())).thenReturn(2);

        var result = service.reconcile("tenant-a", 100);

        assertThat(result.getScannedRecords()).isEqualTo(2);
        assertThat(result.getResolvedUsers()).isEqualTo(1);
        assertThat(result.getUpdatedRecords()).isEqualTo(2);
        assertThat(result.getUnresolvedAfter()).isZero();
        verify(ownershipClient, times(1)).primaryOwnership("tenant-a", "U1");
        verify(formInstanceMapper).update(any(), any());
    }

    private LcFormInstance unresolved(String id, String userId) {
        LcFormInstance instance = new LcFormInstance();
        instance.setId(id);
        instance.setTenantId("tenant-a");
        instance.setSubmittedBy(userId);
        instance.setOwnerOrgProvenance("UNRESOLVED");
        return instance;
    }
}
