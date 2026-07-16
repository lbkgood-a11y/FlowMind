package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.entity.ValueMapEntry;
import com.triobase.service.openapi.domain.entity.ValueMapSet;
import com.triobase.service.openapi.domain.entity.ValueMapVersion;
import com.triobase.service.openapi.domain.enums.UnmappedValuePolicy;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.dto.CreateValueMapRequest;
import com.triobase.service.openapi.infrastructure.mapper.ValueMapEntryMapper;
import com.triobase.service.openapi.infrastructure.mapper.ValueMapSetMapper;
import com.triobase.service.openapi.infrastructure.mapper.ValueMapVersionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
class ValueMapServiceTest {

    @Mock private ValueMapSetMapper setMapper;
    @Mock private ValueMapVersionMapper versionMapper;
    @Mock private ValueMapEntryMapper entryMapper;
    @Mock private IntegrationAuditService auditService;
    private ValueMapService service;

    @BeforeEach
    void setUp() {
        service = new ValueMapService(setMapper, versionMapper, entryMapper, auditService);
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "user-1", "owner", "tenant-a", List.of(), List.of(), 1L, 1L, 1L));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void rejectsCaseInsensitiveDuplicateEntries() {
        when(setMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        var request = request(UnmappedValuePolicy.FAIL, List.of(
                new CreateValueMapRequest.Entry("OPEN", "O", 1),
                new CreateValueMapRequest.Entry("open", "OPEN_CODE", 2)));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_VALUE_MAP_ENTRY_DUPLICATE");
    }

    @Test
    void performsForwardAndReverseLookup() {
        ValueMapVersion version = publishedVersion(UnmappedValuePolicy.FAIL);
        when(versionMapper.selectById("version-1")).thenReturn(version);
        when(setMapper.selectById("set-1")).thenReturn(set());
        ValueMapEntry entry = new ValueMapEntry();
        entry.setCanonicalValue("OPEN");
        entry.setExternalValue("O");
        when(entryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(entry));

        assertThat(service.lookup("version-1", "open", true)).isEqualTo("O");
        assertThat(service.lookup("version-1", "o", false)).isEqualTo("OPEN");
    }

    @Test
    void usesDefaultForUnmappedValue() {
        ValueMapVersion version = publishedVersion(UnmappedValuePolicy.USE_DEFAULT);
        version.setDefaultCanonicalValue("UNKNOWN");
        version.setDefaultExternalValue("U");
        when(versionMapper.selectById("version-1")).thenReturn(version);
        when(setMapper.selectById("set-1")).thenReturn(set());
        when(entryMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertThat(service.lookup("version-1", "missing", true)).isEqualTo("U");
    }

    private CreateValueMapRequest request(
            UnmappedValuePolicy policy,
            List<CreateValueMapRequest.Entry> entries) {
        return new CreateValueMapRequest(
                null, "status-map", "Status map", null, "team", false,
                policy, "UNKNOWN", "U", entries);
    }

    private ValueMapVersion publishedVersion(UnmappedValuePolicy policy) {
        ValueMapVersion version = new ValueMapVersion();
        version.setId("version-1");
        version.setValueMapSetId("set-1");
        version.setLifecycleState(VersionLifecycleState.PUBLISHED);
        version.setCaseSensitive(false);
        version.setUnmappedPolicy(policy);
        return version;
    }

    private ValueMapSet set() {
        ValueMapSet set = new ValueMapSet();
        set.setId("set-1");
        set.setTenantId("tenant-a");
        return set;
    }
}
