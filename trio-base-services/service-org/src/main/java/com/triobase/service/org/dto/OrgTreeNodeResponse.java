package com.triobase.service.org.dto;

import com.triobase.service.org.entity.SysOrgRelation;
import com.triobase.service.org.entity.SysOrgUnit;
import lombok.Data;

@Data
public class OrgTreeNodeResponse {
    private String id;
    private String relationId;
    private String dimensionId;
    private String parentUnitId;
    private String unitCode;
    private String unitName;
    private String unitType;
    private String treePath;
    private Integer level;
    private Integer sortOrder;
    private Short status;
    private String description;

    public static OrgTreeNodeResponse from(SysOrgRelation relation, SysOrgUnit unit) {
        OrgTreeNodeResponse response = new OrgTreeNodeResponse();
        response.setId(unit.getId());
        response.setRelationId(relation.getId());
        response.setDimensionId(relation.getDimensionId());
        response.setParentUnitId(relation.getParentUnitId());
        response.setUnitCode(unit.getUnitCode());
        response.setUnitName(unit.getUnitName());
        response.setUnitType(unit.getUnitType());
        response.setTreePath(relation.getTreePath());
        response.setLevel(relation.getLevel());
        response.setSortOrder(relation.getSortOrder());
        response.setStatus(relation.getStatus());
        response.setDescription(unit.getDescription());
        return response;
    }
}
