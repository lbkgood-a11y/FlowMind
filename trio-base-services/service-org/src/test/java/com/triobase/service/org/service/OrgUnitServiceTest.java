package com.triobase.service.org.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.service.org.dto.CreateOrgUnitRequest;
import com.triobase.service.org.dto.OrgTreeNodeResponse;
import com.triobase.service.org.dto.SaveOrgRelationRequest;
import com.triobase.service.org.dto.UserOrgAssignmentRequest;
import com.triobase.service.org.entity.SysOrgDimension;
import com.triobase.service.org.entity.SysOrgRelation;
import com.triobase.service.org.entity.SysOrgUnit;
import com.triobase.service.org.mapper.OrgDimensionMapper;
import com.triobase.service.org.mapper.OrgRelationMapper;
import com.triobase.service.org.mapper.OrgUnitMapper;
import com.triobase.service.org.mapper.UserOrgUnitMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrgUnitServiceTest {

    @Mock
    private OrgUnitMapper orgUnitMapper;

    @Mock
    private OrgDimensionMapper orgDimensionMapper;

    @Mock
    private OrgRelationMapper orgRelationMapper;

    @Mock
    private UserOrgUnitMapper userOrgUnitMapper;

    @InjectMocks
    private OrgUnitService orgUnitService;

    @Test
    void createOrgUnit_shouldRejectDuplicateUnitCode() {
        CreateOrgUnitRequest request = new CreateOrgUnitRequest();
        request.setUnitCode("TECH");
        request.setUnitName("技术中心");

        when(orgUnitMapper.selectCount(any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> orgUnitService.createOrgUnit(request));

        assertEquals(40042, ex.getCode());
    }

    @Test
    void listOrgTree_shouldMergeRelationsAndUnits() {
        SysOrgDimension dimension = new SysOrgDimension();
        dimension.setId("ORG_DIM_ADMIN");
        dimension.setDimensionCode("ADMIN");

        SysOrgRelation relation = new SysOrgRelation();
        relation.setId("REL1");
        relation.setDimensionId("ORG_DIM_ADMIN");
        relation.setChildUnitId("OU1");
        relation.setTreePath("/OU1");
        relation.setLevel(1);
        relation.setSortOrder(10);
        relation.setStatus((short) 1);

        SysOrgUnit unit = new SysOrgUnit();
        unit.setId("OU1");
        unit.setUnitCode("HQ");
        unit.setUnitName("总部");
        unit.setUnitType("COMPANY");

        when(orgDimensionMapper.selectOne(any())).thenReturn(dimension);
        when(orgRelationMapper.selectList(any())).thenReturn(List.of(relation));
        when(orgUnitMapper.selectBatchIds(any())).thenReturn(List.of(unit));

        List<OrgTreeNodeResponse> tree = orgUnitService.listOrgTree("ADMIN");

        assertEquals(1, tree.size());
        assertEquals("OU1", tree.get(0).getId());
        assertEquals("总部", tree.get(0).getUnitName());
    }

    @Test
    void saveRelation_shouldRejectCycle() {
        SysOrgDimension dimension = new SysOrgDimension();
        dimension.setId("ORG_DIM_ADMIN");
        dimension.setDimensionCode("ADMIN");

        SysOrgUnit child = new SysOrgUnit();
        child.setId("OU1");
        child.setSortOrder(10);
        SysOrgUnit parent = new SysOrgUnit();
        parent.setId("OU2");

        SysOrgRelation parentRelation = new SysOrgRelation();
        parentRelation.setChildUnitId("OU2");
        parentRelation.setTreePath("/OU1/OU2");
        parentRelation.setLevel(2);

        SysOrgRelation current = new SysOrgRelation();
        current.setChildUnitId("OU1");
        current.setTreePath("/OU1");
        current.setLevel(1);

        SaveOrgRelationRequest request = new SaveOrgRelationRequest();
        request.setParentUnitId("OU2");

        when(orgDimensionMapper.selectOne(any())).thenReturn(dimension);
        when(orgUnitMapper.selectById("OU1")).thenReturn(child);
        when(orgUnitMapper.selectById("OU2")).thenReturn(parent);
        when(orgRelationMapper.selectOne(any())).thenReturn(parentRelation, current);

        BizException ex = assertThrows(BizException.class,
                () -> orgUnitService.saveRelation("ADMIN", "OU1", request));

        assertEquals(40046, ex.getCode());
    }

    @Test
    void assignUserOrgUnits_shouldRejectPrimaryOutsideAssignments() {
        SysOrgDimension dimension = new SysOrgDimension();
        dimension.setId("ORG_DIM_ADMIN");
        dimension.setDimensionCode("ADMIN");

        UserOrgAssignmentRequest request = new UserOrgAssignmentRequest();
        request.setDimensionCode("ADMIN");
        request.setOrgUnitIds(List.of("OU1"));
        request.setPrimaryOrgUnitId("OU2");

        when(orgDimensionMapper.selectOne(any())).thenReturn(dimension);

        BizException ex = assertThrows(BizException.class,
                () -> orgUnitService.assignUserOrgUnits("U001", request));

        assertEquals(40047, ex.getCode());
    }
}
