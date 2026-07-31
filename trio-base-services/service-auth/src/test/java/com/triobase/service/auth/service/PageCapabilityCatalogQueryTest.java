package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.service.auth.entity.SysAuthPageCapability;
import com.triobase.service.auth.entity.SysAuthPageCatalog;
import com.triobase.service.auth.mapper.AuthActionMapper;
import com.triobase.service.auth.mapper.AuthFieldMapper;
import com.triobase.service.auth.mapper.AuthGuardTemplateMapper;
import com.triobase.service.auth.mapper.AuthPageCapabilityDependencyMapper;
import com.triobase.service.auth.mapper.AuthPageCapabilityMapper;
import com.triobase.service.auth.mapper.AuthPageCapabilityTargetMapper;
import com.triobase.service.auth.mapper.AuthPageCatalogMapper;
import com.triobase.service.auth.mapper.AuthResourceMapper;
import com.triobase.service.auth.mapper.MenuMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageCapabilityCatalogQueryTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        MybatisPlusTestMetadata.initialize();
    }

    @Mock private AuthPageCatalogMapper catalogMapper;
    @Mock private AuthPageCapabilityMapper capabilityMapper;
    @Mock private AuthPageCapabilityTargetMapper targetMapper;
    @Mock private AuthPageCapabilityDependencyMapper dependencyMapper;
    @Mock private AuthResourceMapper resourceMapper;
    @Mock private AuthActionMapper actionMapper;
    @Mock private AuthGuardTemplateMapper guardTemplateMapper;
    @Mock private AuthFieldMapper fieldMapper;
    @Mock private MenuMapper menuMapper;
    @Mock private AuthorizationRegistryService authorizationRegistryService;
    @Mock private RoleAuthorizationDriftService driftService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private PageCapabilityCatalogService service;

    @Test
    void implementationCatalogScopesAndFiltersByExactPageCode() {
        SysAuthPageCatalog catalog = new SysAuthPageCatalog();
        catalog.setId("CAT001");
        when(authorizationRegistryService.effectiveTenant("tenant-a")).thenReturn("tenant-a");
        when(catalogMapper.selectOne(any())).thenReturn(catalog);
        when(capabilityMapper.selectList(any())).thenReturn(List.of());

        var result = service.implementationCatalog("tenant-a", null, " SYSTEM.MENU ");

        assertTrue(result.isEmpty());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<SysAuthPageCapability>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(capabilityMapper).selectList(captor.capture());
        var query = captor.getValue();
        String sql = query.getSqlSegment().toLowerCase();
        var values = query.getParamNameValuePairs().values();
        assertTrue(values.contains("tenant-a"));
        assertTrue(values.contains("CAT001"));
        assertTrue(values.contains("SYSTEM.MENU"));
        assertTrue(sql.contains("order by"));
    }

    @Test
    void implementationCatalogReturnsEmptyWithoutInferringMenuButtons() {
        SysAuthPageCatalog catalog = new SysAuthPageCatalog();
        catalog.setId("CAT001");
        when(authorizationRegistryService.effectiveTenant(null)).thenReturn("default");
        when(catalogMapper.selectOne(any())).thenReturn(catalog);
        when(capabilityMapper.selectList(any())).thenReturn(List.of());

        assertTrue(service.implementationCatalog(null, null, "UNKNOWN.PAGE").isEmpty());
    }

    @Test
    void catalogsAreTenantScoped() {
        SysAuthPageCatalog catalog = new SysAuthPageCatalog();
        catalog.setId("CAT002");
        when(authorizationRegistryService.effectiveTenant(null)).thenReturn("default");
        when(catalogMapper.selectList(any())).thenReturn(List.of(catalog));

        var result = service.catalogs(null);

        assertEquals(List.of(catalog), result);
        verify(catalogMapper).selectList(any());
        verify(authorizationRegistryService).effectiveTenant(null);
    }
}
