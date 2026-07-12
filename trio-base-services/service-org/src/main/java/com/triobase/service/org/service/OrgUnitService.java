package com.triobase.service.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.service.org.dto.CreateOrgUnitRequest;
import com.triobase.service.org.dto.OrgUnitUserResponse;
import com.triobase.service.org.dto.OrgTreeNodeResponse;
import com.triobase.service.org.dto.SaveOrgRelationRequest;
import com.triobase.service.org.dto.UpdateOrgUnitRequest;
import com.triobase.service.org.dto.UserOrgAssignmentItem;
import com.triobase.service.org.dto.UserOrgAssignmentRequest;
import com.triobase.service.org.dto.UserOrgAssignmentResponse;
import com.triobase.service.org.entity.SysOrgDimension;
import com.triobase.service.org.entity.SysOrgRelation;
import com.triobase.service.org.entity.SysOrgUnit;
import com.triobase.service.org.entity.SysUserOrgUnit;
import com.triobase.service.org.entity.SysUserView;
import com.triobase.service.org.mapper.OrgDimensionMapper;
import com.triobase.service.org.mapper.OrgRelationMapper;
import com.triobase.service.org.mapper.OrgUnitMapper;
import com.triobase.service.org.mapper.UserOrgUnitMapper;
import com.triobase.service.org.mapper.UserViewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrgUnitService {

    private static final String DEFAULT_TENANT = "default";
    private static final String DEFAULT_DIMENSION_CODE = "ADMIN";
    private static final String DEFAULT_UNIT_TYPE = "DEPARTMENT";

    private final OrgUnitMapper orgUnitMapper;
    private final OrgDimensionMapper orgDimensionMapper;
    private final OrgRelationMapper orgRelationMapper;
    private final UserOrgUnitMapper userOrgUnitMapper;
    private final UserViewMapper userViewMapper;

    public List<SysOrgDimension> listDimensions() {
        return orgDimensionMapper.selectList(new LambdaQueryWrapper<SysOrgDimension>()
                .eq(SysOrgDimension::getTenantId, DEFAULT_TENANT)
                .eq(SysOrgDimension::getStatus, (short) 1)
                .orderByDesc(SysOrgDimension::getIsDefault)
                .orderByAsc(SysOrgDimension::getSortOrder)
                .orderByAsc(SysOrgDimension::getDimensionCode));
    }

    public List<SysOrgUnit> listOrgUnits() {
        return listOrgUnits(null, null, null);
    }

    public List<SysOrgUnit> listOrgUnits(String keyword, String unitType, Integer status) {
        LambdaQueryWrapper<SysOrgUnit> wrapper = new LambdaQueryWrapper<SysOrgUnit>()
                .eq(SysOrgUnit::getTenantId, DEFAULT_TENANT)
                .orderByAsc(SysOrgUnit::getTreePath)
                .orderByAsc(SysOrgUnit::getSortOrder);
        String normalizedKeyword = normalizeBlank(keyword);
        if (normalizedKeyword != null) {
            wrapper.and(query -> query
                    .like(SysOrgUnit::getUnitCode, normalizedKeyword)
                    .or()
                    .like(SysOrgUnit::getUnitName, normalizedKeyword)
                    .or()
                    .like(SysOrgUnit::getDescription, normalizedKeyword));
        }
        String normalizedUnitType = normalizeBlank(unitType);
        if (normalizedUnitType != null) {
            wrapper.eq(SysOrgUnit::getUnitType, normalizedUnitType);
        }
        if (status != null) {
            wrapper.eq(SysOrgUnit::getStatus, toStatus(status));
        }
        return orgUnitMapper.selectList(wrapper);
    }

    public List<SysUserOrgUnit> listUserOrgRelations() {
        return userOrgUnitMapper.selectList(new LambdaQueryWrapper<SysUserOrgUnit>()
                .orderByAsc(SysUserOrgUnit::getUserId)
                .orderByAsc(SysUserOrgUnit::getDimensionId)
                .orderByAsc(SysUserOrgUnit::getOrgUnitId));
    }

    public List<OrgTreeNodeResponse> listOrgTree(String dimensionCode) {
        SysOrgDimension dimension = findDimensionByCode(dimensionCode);
        List<SysOrgRelation> relations = orgRelationMapper.selectList(new LambdaQueryWrapper<SysOrgRelation>()
                .eq(SysOrgRelation::getTenantId, DEFAULT_TENANT)
                .eq(SysOrgRelation::getDimensionId, dimension.getId())
                .orderByAsc(SysOrgRelation::getTreePath)
                .orderByAsc(SysOrgRelation::getSortOrder));
        if (relations.isEmpty()) {
            return List.of();
        }
        Set<String> unitIds = relations.stream()
                .map(SysOrgRelation::getChildUnitId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, SysOrgUnit> units = orgUnitMapper.selectBatchIds(unitIds).stream()
                .collect(Collectors.toMap(SysOrgUnit::getId, Function.identity()));
        return relations.stream()
                .filter(relation -> units.containsKey(relation.getChildUnitId()))
                .map(relation -> OrgTreeNodeResponse.from(relation, units.get(relation.getChildUnitId())))
                .toList();
    }

    @Transactional
    public SysOrgUnit createOrgUnit(CreateOrgUnitRequest request) {
        if (!StringUtils.hasText(request.getUnitCode()) || !StringUtils.hasText(request.getUnitName())) {
            throw new BizException(40041, "ORG_UNIT_CODE_NAME_REQUIRED");
        }
        String dimensionCode = normalizeBlank(request.getDimensionCode()) != null
                ? request.getDimensionCode().trim()
                : DEFAULT_DIMENSION_CODE;

        if (orgUnitMapper.selectCount(new LambdaQueryWrapper<SysOrgUnit>()
                .eq(SysOrgUnit::getTenantId, DEFAULT_TENANT)
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
        unit.setId(UlidGenerator.nextUlid());
        unit.setTenantId(DEFAULT_TENANT);
        unit.setParentId(DEFAULT_DIMENSION_CODE.equals(dimensionCode) && StringUtils.hasText(request.getParentId())
                ? request.getParentId()
                : null);
        unit.setUnitCode(request.getUnitCode());
        unit.setUnitName(request.getUnitName());
        unit.setUnitType(normalizeBlank(request.getUnitType()) != null ? request.getUnitType().trim() : DEFAULT_UNIT_TYPE);
        unit.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 100);
        unit.setStatus(Boolean.FALSE.equals(request.getEnabled()) ? (short) 0 : (short) 1);
        unit.setDescription(request.getDescription());
        unit.setTreePath(DEFAULT_DIMENSION_CODE.equals(dimensionCode) && parent != null
                ? parent.getTreePath() + "/" + unit.getId()
                : "/" + unit.getId());
        orgUnitMapper.insert(unit);

        SaveOrgRelationRequest relationRequest = new SaveOrgRelationRequest();
        relationRequest.setParentUnitId(normalizeBlank(request.getParentId()));
        relationRequest.setSortOrder(unit.getSortOrder());
        relationRequest.setEnabled(unit.getStatus() == null || unit.getStatus() == 1);
        saveRelation(dimensionCode, unit.getId(), relationRequest);
        return unit;
    }

    @Transactional
    public SysOrgUnit updateOrgUnit(String id, UpdateOrgUnitRequest request) {
        SysOrgUnit unit = orgUnitMapper.selectById(id);
        if (unit == null) {
            throw new BizException(40442, "ORG_UNIT_NOT_FOUND");
        }
        if (!StringUtils.hasText(request.getUnitName())) {
            throw new BizException(40041, "ORG_UNIT_CODE_NAME_REQUIRED");
        }
        unit.setUnitName(request.getUnitName().trim());
        unit.setUnitType(normalizeBlank(request.getUnitType()) != null ? request.getUnitType().trim() : DEFAULT_UNIT_TYPE);
        unit.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 100);
        unit.setStatus(Boolean.FALSE.equals(request.getEnabled()) ? (short) 0 : (short) 1);
        unit.setDescription(normalizeBlank(request.getDescription()));
        orgUnitMapper.updateById(unit);
        return unit;
    }

    @Transactional
    public void deleteOrgUnit(String id) {
        SysOrgUnit unit = orgUnitMapper.selectById(id);
        if (unit == null) {
            throw new BizException(40442, "ORG_UNIT_NOT_FOUND");
        }

        if (orgRelationMapper.selectCount(new LambdaQueryWrapper<SysOrgRelation>()
                .eq(SysOrgRelation::getParentUnitId, id)) > 0) {
            throw new BizException(40043, "ORG_UNIT_HAS_CHILDREN");
        }

        if (userOrgUnitMapper.selectCount(new LambdaQueryWrapper<SysUserOrgUnit>()
                .eq(SysUserOrgUnit::getOrgUnitId, id)) > 0) {
            throw new BizException(40044, "ORG_UNIT_HAS_USERS");
        }

        orgRelationMapper.delete(new LambdaQueryWrapper<SysOrgRelation>()
                .eq(SysOrgRelation::getChildUnitId, id));
        orgUnitMapper.deleteById(id);
    }

    @Transactional
    public SysOrgRelation saveRelation(String dimensionCode, String childUnitId, SaveOrgRelationRequest request) {
        SysOrgDimension dimension = findDimensionByCode(dimensionCode);
        SysOrgUnit child = orgUnitMapper.selectById(childUnitId);
        if (child == null) {
            throw new BizException(40442, "ORG_UNIT_NOT_FOUND");
        }
        String parentUnitId = normalizeBlank(request.getParentUnitId());
        SysOrgRelation parentRelation = null;
        if (parentUnitId != null) {
            if (parentUnitId.equals(childUnitId)) {
                throw new BizException(40046, "ORG_RELATION_CYCLE");
            }
            if (orgUnitMapper.selectById(parentUnitId) == null) {
                throw new BizException(40441, "PARENT_ORG_UNIT_NOT_FOUND");
            }
            parentRelation = findRelation(dimension.getId(), parentUnitId);
            if (parentRelation == null) {
                throw new BizException(40444, "PARENT_ORG_RELATION_NOT_FOUND");
            }
        }

        SysOrgRelation current = findRelation(dimension.getId(), childUnitId);
        if (current != null && parentRelation != null
                && parentRelation.getTreePath().startsWith(current.getTreePath() + "/")) {
            throw new BizException(40046, "ORG_RELATION_CYCLE");
        }

        String oldPath = current != null ? current.getTreePath() : null;
        String treePath = parentRelation == null
                ? "/" + childUnitId
                : parentRelation.getTreePath() + "/" + childUnitId;
        int level = parentRelation == null ? 1 : parentRelation.getLevel() + 1;

        SysOrgRelation relation = current != null ? current : new SysOrgRelation();
        if (relation.getId() == null) {
            relation.setId(UlidGenerator.nextUlid());
            relation.setTenantId(DEFAULT_TENANT);
            relation.setDimensionId(dimension.getId());
            relation.setChildUnitId(childUnitId);
        }
        relation.setParentUnitId(parentUnitId);
        relation.setTreePath(treePath);
        relation.setLevel(level);
        relation.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : child.getSortOrder());
        relation.setStatus(Boolean.FALSE.equals(request.getEnabled()) ? (short) 0 : (short) 1);

        if (current == null) {
            orgRelationMapper.insert(relation);
        } else {
            orgRelationMapper.updateById(relation);
            updateDescendantPaths(dimension.getId(), oldPath, treePath, level);
        }

        if (DEFAULT_DIMENSION_CODE.equals(dimension.getDimensionCode())) {
            child.setParentId(parentUnitId);
            child.setTreePath(treePath);
            orgUnitMapper.updateById(child);
        }
        return relation;
    }

    @Transactional
    public void deleteRelation(String dimensionCode, String childUnitId) {
        SysOrgDimension dimension = findDimensionByCode(dimensionCode);
        SysOrgRelation relation = findRelation(dimension.getId(), childUnitId);
        if (relation == null) {
            throw new BizException(40445, "ORG_RELATION_NOT_FOUND");
        }
        if (orgRelationMapper.selectCount(new LambdaQueryWrapper<SysOrgRelation>()
                .eq(SysOrgRelation::getDimensionId, dimension.getId())
                .eq(SysOrgRelation::getParentUnitId, childUnitId)) > 0) {
            throw new BizException(40043, "ORG_UNIT_HAS_CHILDREN");
        }
        orgRelationMapper.deleteById(relation.getId());
        if (DEFAULT_DIMENSION_CODE.equals(dimension.getDimensionCode())) {
            SysOrgUnit child = orgUnitMapper.selectById(childUnitId);
            if (child != null) {
                child.setParentId(null);
                child.setTreePath("/" + childUnitId);
                orgUnitMapper.updateById(child);
            }
        }
    }

    public List<UserOrgAssignmentResponse> listUserAssignments(String userId, String dimensionCode) {
        LambdaQueryWrapper<SysUserOrgUnit> wrapper = new LambdaQueryWrapper<SysUserOrgUnit>()
                .eq(SysUserOrgUnit::getTenantId, DEFAULT_TENANT)
                .eq(SysUserOrgUnit::getUserId, userId)
                .orderByDesc(SysUserOrgUnit::getIsPrimary)
                .orderByAsc(SysUserOrgUnit::getOrgUnitId);
        SysOrgDimension dimension = null;
        if (StringUtils.hasText(dimensionCode)) {
            dimension = findDimensionByCode(dimensionCode);
            wrapper.eq(SysUserOrgUnit::getDimensionId, dimension.getId());
        }
        List<SysUserOrgUnit> relations = userOrgUnitMapper.selectList(wrapper);
        if (relations.isEmpty()) {
            return List.of();
        }
        Map<String, SysOrgDimension> dimensions = listDimensions().stream()
                .collect(Collectors.toMap(SysOrgDimension::getId, Function.identity()));
        if (dimension != null) {
            dimensions.put(dimension.getId(), dimension);
        }
        Set<String> orgUnitIds = relations.stream()
                .map(SysUserOrgUnit::getOrgUnitId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, SysOrgUnit> units = orgUnitMapper.selectBatchIds(orgUnitIds).stream()
                .collect(Collectors.toMap(SysOrgUnit::getId, Function.identity()));
        return relations.stream()
                .map(relation -> {
                    SysOrgDimension itemDimension = dimensions.get(relation.getDimensionId());
                    SysOrgUnit unit = units.get(relation.getOrgUnitId());
                    return UserOrgAssignmentResponse.from(
                            relation,
                            itemDimension != null ? itemDimension.getDimensionCode() : null,
                            unit != null ? unit.getUnitName() : null
                    );
                })
                .toList();
    }

    public List<OrgUnitUserResponse> listOrgUnitUsers(String orgUnitId, String dimensionCode) {
        if (!StringUtils.hasText(orgUnitId)) {
            return List.of();
        }
        SysOrgDimension dimension = findDimensionByCode(dimensionCode);
        SysOrgUnit orgUnit = orgUnitMapper.selectById(orgUnitId);
        if (orgUnit == null) {
            throw new BizException(40442, "ORG_UNIT_NOT_FOUND");
        }
        if (findRelation(dimension.getId(), orgUnitId) == null) {
            throw new BizException(40445, "ORG_RELATION_NOT_FOUND");
        }

        List<SysUserOrgUnit> relations = userOrgUnitMapper.selectList(new LambdaQueryWrapper<SysUserOrgUnit>()
                .eq(SysUserOrgUnit::getTenantId, DEFAULT_TENANT)
                .eq(SysUserOrgUnit::getDimensionId, dimension.getId())
                .eq(SysUserOrgUnit::getOrgUnitId, orgUnitId)
                .orderByDesc(SysUserOrgUnit::getIsPrimary)
                .orderByAsc(SysUserOrgUnit::getCreatedAt)
                .orderByAsc(SysUserOrgUnit::getUserId));
        if (relations.isEmpty()) {
            return List.of();
        }

        Set<String> userIds = relations.stream()
                .map(SysUserOrgUnit::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, SysUserView> users = userViewMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUserView::getId, Function.identity()));

        return relations.stream()
                .map(relation -> OrgUnitUserResponse.from(
                        relation,
                        dimension.getDimensionCode(),
                        orgUnit,
                        users.get(relation.getUserId())
                ))
                .toList();
    }

    @Transactional
    public void assignUserOrgUnits(String userId, UserOrgAssignmentRequest request) {
        if (!StringUtils.hasText(userId)) {
            throw new BizException(40045, "USER_ID_REQUIRED");
        }

        SysOrgDimension dimension = findDimensionByCode(
                StringUtils.hasText(request.getDimensionCode()) ? request.getDimensionCode() : DEFAULT_DIMENSION_CODE);
        List<UserOrgAssignmentItem> assignments = normalizeAssignments(request);
        validatePrimaryAssignment(assignments, request.getPrimaryOrgUnitId());

        for (UserOrgAssignmentItem assignment : assignments) {
            String orgUnitId = normalizeBlank(assignment.getOrgUnitId());
            if (orgUnitId == null || orgUnitMapper.selectById(orgUnitId) == null) {
                throw new BizException(40443, "ORG_UNIT_NOT_FOUND");
            }
            if (findRelation(dimension.getId(), orgUnitId) == null) {
                throw new BizException(40445, "ORG_RELATION_NOT_FOUND");
            }
        }

        userOrgUnitMapper.delete(new LambdaUpdateWrapper<SysUserOrgUnit>()
                .eq(SysUserOrgUnit::getTenantId, DEFAULT_TENANT)
                .eq(SysUserOrgUnit::getUserId, userId)
                .eq(SysUserOrgUnit::getDimensionId, dimension.getId()));

        for (UserOrgAssignmentItem assignment : assignments) {
            SysUserOrgUnit relation = new SysUserOrgUnit();
            relation.setId(UlidGenerator.nextUlid());
            relation.setTenantId(DEFAULT_TENANT);
            relation.setUserId(userId);
            relation.setDimensionId(dimension.getId());
            relation.setOrgUnitId(assignment.getOrgUnitId().trim());
            relation.setIsPrimary(Boolean.TRUE.equals(assignment.getPrimary()) ? (short) 1 : (short) 0);
            relation.setPositionId(normalizeBlank(assignment.getPositionId()));
            relation.setPositionName(normalizeBlank(assignment.getPositionName()));
            relation.setIsLeader(Boolean.TRUE.equals(assignment.getLeader()) ? (short) 1 : (short) 0);
            relation.setEffectiveFrom(assignment.getEffectiveFrom());
            relation.setEffectiveTo(assignment.getEffectiveTo());
            relation.setStatus(toStatus(assignment.getStatus()));
            userOrgUnitMapper.insert(relation);
        }
    }

    private void updateDescendantPaths(String dimensionId, String oldPath, String newPath, int newLevel) {
        if (oldPath == null || oldPath.equals(newPath)) {
            return;
        }
        List<SysOrgRelation> descendants = orgRelationMapper.selectList(new LambdaQueryWrapper<SysOrgRelation>()
                .eq(SysOrgRelation::getDimensionId, dimensionId)
                .likeRight(SysOrgRelation::getTreePath, oldPath + "/")
                .orderByAsc(SysOrgRelation::getTreePath));
        for (SysOrgRelation descendant : descendants) {
            String suffix = descendant.getTreePath().substring(oldPath.length());
            descendant.setTreePath(newPath + suffix);
            descendant.setLevel(newLevel + suffix.split("/").length - 1);
            orgRelationMapper.updateById(descendant);
        }
    }

    private SysOrgDimension findDimensionByCode(String dimensionCode) {
        String normalizedCode = normalizeBlank(dimensionCode);
        if (normalizedCode == null) {
            normalizedCode = DEFAULT_DIMENSION_CODE;
        }
        SysOrgDimension dimension = orgDimensionMapper.selectOne(new LambdaQueryWrapper<SysOrgDimension>()
                .eq(SysOrgDimension::getTenantId, DEFAULT_TENANT)
                .eq(SysOrgDimension::getDimensionCode, normalizedCode)
                .last("LIMIT 1"));
        if (dimension == null) {
            throw new BizException(40446, "ORG_DIMENSION_NOT_FOUND");
        }
        return dimension;
    }

    private SysOrgRelation findRelation(String dimensionId, String childUnitId) {
        return orgRelationMapper.selectOne(new LambdaQueryWrapper<SysOrgRelation>()
                .eq(SysOrgRelation::getDimensionId, dimensionId)
                .eq(SysOrgRelation::getChildUnitId, childUnitId)
                .last("LIMIT 1"));
    }

    private List<UserOrgAssignmentItem> normalizeAssignments(UserOrgAssignmentRequest request) {
        List<UserOrgAssignmentItem> assignments = new ArrayList<>();
        if (request.getAssignments() != null && !request.getAssignments().isEmpty()) {
            for (UserOrgAssignmentItem assignment : request.getAssignments()) {
                if (normalizeBlank(assignment.getOrgUnitId()) != null) {
                    assignment.setOrgUnitId(assignment.getOrgUnitId().trim());
                    assignments.add(assignment);
                }
            }
        } else if (request.getOrgUnitIds() != null) {
            LinkedHashSet<String> orgUnitIds = request.getOrgUnitIds().stream()
                    .map(this::normalizeBlank)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            String primaryOrgUnitId = normalizeBlank(request.getPrimaryOrgUnitId());
            boolean hasExplicitPrimary = primaryOrgUnitId != null;
            int index = 0;
            for (String orgUnitId : orgUnitIds) {
                UserOrgAssignmentItem item = new UserOrgAssignmentItem();
                item.setOrgUnitId(orgUnitId);
                item.setPrimary(hasExplicitPrimary ? orgUnitId.equals(primaryOrgUnitId) : index == 0);
                item.setStatus(1);
                assignments.add(item);
                index++;
            }
        }
        if (!assignments.isEmpty() && assignments.stream().noneMatch(item -> Boolean.TRUE.equals(item.getPrimary()))) {
            assignments.get(0).setPrimary(true);
        }
        return assignments;
    }

    private void validatePrimaryAssignment(List<UserOrgAssignmentItem> assignments, String primaryOrgUnitId) {
        String normalizedPrimaryOrgUnitId = normalizeBlank(primaryOrgUnitId);
        Map<String, UserOrgAssignmentItem> byOrgUnitId = new LinkedHashMap<>();
        int primaryCount = 0;
        for (UserOrgAssignmentItem assignment : assignments) {
            String orgUnitId = normalizeBlank(assignment.getOrgUnitId());
            if (orgUnitId == null) {
                continue;
            }
            byOrgUnitId.put(orgUnitId, assignment);
            if (Boolean.TRUE.equals(assignment.getPrimary())) {
                primaryCount++;
            }
        }
        if (normalizedPrimaryOrgUnitId != null) {
            UserOrgAssignmentItem primary = byOrgUnitId.get(normalizedPrimaryOrgUnitId);
            if (primary == null) {
                throw new BizException(40047, "PRIMARY_ORG_UNIT_NOT_ASSIGNED");
            }
            assignments.forEach(item -> item.setPrimary(normalizedPrimaryOrgUnitId.equals(item.getOrgUnitId())));
            return;
        }
        if (primaryCount > 1) {
            throw new BizException(40048, "MULTIPLE_PRIMARY_ORG_UNITS");
        }
    }

    private Short toStatus(Integer status) {
        return status != null && status == 0 ? (short) 0 : (short) 1;
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
