package com.triobase.service.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.org.dto.CreateOrgUnitRequest;
import com.triobase.service.org.dto.UserOrgAssignmentRequest;
import com.triobase.service.org.entity.SysOrgUnit;
import com.triobase.service.org.entity.SysUserOrgUnit;
import com.triobase.service.org.mapper.OrgUnitMapper;
import com.triobase.service.org.mapper.UserOrgUnitMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrgUnitService {

    private final OrgUnitMapper orgUnitMapper;
    private final UserOrgUnitMapper userOrgUnitMapper;

    public List<SysOrgUnit> listOrgUnits() {
        return orgUnitMapper.selectList(new LambdaQueryWrapper<SysOrgUnit>()
                .orderByAsc(SysOrgUnit::getTreePath)
                .orderByAsc(SysOrgUnit::getSortOrder));
    }

    public List<SysUserOrgUnit> listUserOrgRelations() {
        return userOrgUnitMapper.selectList(new LambdaQueryWrapper<SysUserOrgUnit>()
                .orderByAsc(SysUserOrgUnit::getUserId)
                .orderByAsc(SysUserOrgUnit::getOrgUnitId));
    }

    @Transactional
    public SysOrgUnit createOrgUnit(CreateOrgUnitRequest request) {
        if (!StringUtils.hasText(request.getUnitCode()) || !StringUtils.hasText(request.getUnitName())) {
            throw new BizException(40041, "ORG_UNIT_CODE_NAME_REQUIRED");
        }

        if (orgUnitMapper.selectCount(new LambdaQueryWrapper<SysOrgUnit>()
                .eq(SysOrgUnit::getUnitCode, request.getUnitCode())) > 0) {
            throw new BizException(40042, "ORG_UNIT_CODE_ALREADY_EXISTS");
        }

        SysOrgUnit parent = null;
        if (StringUtils.hasText(request.getParentId())) {
            parent = orgUnitMapper.selectById(request.getParentId());
            if (parent == null) {
                throw new BizException(40441, "PARENT_ORG_UNIT_NOT_FOUND");
            }
        }

        SysOrgUnit unit = new SysOrgUnit();
        unit.setId("O" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        unit.setParentId(StringUtils.hasText(request.getParentId()) ? request.getParentId() : null);
        unit.setUnitCode(request.getUnitCode());
        unit.setUnitName(request.getUnitName());
        unit.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 100);
        unit.setStatus(Boolean.FALSE.equals(request.getEnabled()) ? (short) 0 : (short) 1);
        unit.setDescription(request.getDescription());
        unit.setTreePath(parent == null ? "/" + unit.getId() : parent.getTreePath() + "/" + unit.getId());
        orgUnitMapper.insert(unit);
        return unit;
    }

    @Transactional
    public void deleteOrgUnit(String id) {
        SysOrgUnit unit = orgUnitMapper.selectById(id);
        if (unit == null) {
            throw new BizException(40442, "ORG_UNIT_NOT_FOUND");
        }

        if (orgUnitMapper.selectCount(new LambdaQueryWrapper<SysOrgUnit>()
                .eq(SysOrgUnit::getParentId, id)) > 0) {
            throw new BizException(40043, "ORG_UNIT_HAS_CHILDREN");
        }

        if (userOrgUnitMapper.selectCount(new LambdaQueryWrapper<SysUserOrgUnit>()
                .eq(SysUserOrgUnit::getOrgUnitId, id)) > 0) {
            throw new BizException(40044, "ORG_UNIT_HAS_USERS");
        }

        orgUnitMapper.deleteById(id);
    }

    @Transactional
    public void assignUserOrgUnits(String userId, UserOrgAssignmentRequest request) {
        if (!StringUtils.hasText(userId)) {
            throw new BizException(40045, "USER_ID_REQUIRED");
        }

        List<String> orgUnitIds = request.getOrgUnitIds() == null ? List.of() : request.getOrgUnitIds();
        for (String orgUnitId : orgUnitIds) {
            if (orgUnitMapper.selectById(orgUnitId) == null) {
                throw new BizException(40443, "ORG_UNIT_NOT_FOUND");
            }
        }

        userOrgUnitMapper.delete(new LambdaUpdateWrapper<SysUserOrgUnit>()
                .eq(SysUserOrgUnit::getUserId, userId));

        for (String orgUnitId : orgUnitIds) {
            SysUserOrgUnit relation = new SysUserOrgUnit();
            relation.setId("UO" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
            relation.setUserId(userId);
            relation.setOrgUnitId(orgUnitId);
            userOrgUnitMapper.insert(relation);
        }
    }
}
