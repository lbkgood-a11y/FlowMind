package com.triobase.service.org.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.R;
import com.triobase.service.org.dto.CreateOrgUnitRequest;
import com.triobase.service.org.dto.OrgUnitUserResponse;
import com.triobase.service.org.dto.OrgTreeNodeResponse;
import com.triobase.service.org.dto.SaveOrgRelationRequest;
import com.triobase.service.org.dto.UpdateOrgUnitRequest;
import com.triobase.service.org.dto.UserOrgAssignmentRequest;
import com.triobase.service.org.dto.UserOrgAssignmentResponse;
import com.triobase.service.org.entity.SysOrgDimension;
import com.triobase.service.org.entity.SysOrgRelation;
import com.triobase.service.org.entity.SysOrgUnit;
import com.triobase.service.org.entity.SysUserOrgUnit;
import com.triobase.service.org.service.OrgUnitService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/org")
@RequiredArgsConstructor
public class OrgUnitController {

    private final OrgUnitService orgUnitService;

    @GetMapping("/dimensions")
    @RequirePermission("/api/v1/org/units:GET")
    public R<List<SysOrgDimension>> listDimensions() {
        return R.ok(orgUnitService.listDimensions());
    }

    @GetMapping("/units")
    @RequirePermission("/api/v1/org/units:GET")
    public R<List<SysOrgUnit>> listOrgUnits(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String unitType,
                                            @RequestParam(required = false) Integer status) {
        return R.ok(orgUnitService.listOrgUnits(keyword, unitType, status));
    }

    @GetMapping("/dimensions/{dimensionCode}/tree")
    @RequirePermission("/api/v1/org/units:GET")
    public R<List<OrgTreeNodeResponse>> listOrgTree(@PathVariable String dimensionCode) {
        return R.ok(orgUnitService.listOrgTree(dimensionCode));
    }

    @GetMapping("/units/legacy")
    @RequirePermission("/api/v1/org/units:GET")
    public R<List<SysOrgUnit>> listOrgUnits() {
        return R.ok(orgUnitService.listOrgUnits());
    }

    @PostMapping("/units")
    @RequirePermission("/api/v1/org/units:POST")
    public R<SysOrgUnit> createOrgUnit(@RequestBody CreateOrgUnitRequest request) {
        return R.ok(orgUnitService.createOrgUnit(request));
    }

    @PutMapping("/units/{id}")
    @RequirePermission("/api/v1/org/units/*:PUT")
    public R<SysOrgUnit> updateOrgUnit(@PathVariable String id, @RequestBody UpdateOrgUnitRequest request) {
        return R.ok(orgUnitService.updateOrgUnit(id, request));
    }

    @DeleteMapping("/units/{id}")
    @RequirePermission("/api/v1/org/units/*:DELETE")
    public R<String> deleteOrgUnit(@PathVariable String id) {
        orgUnitService.deleteOrgUnit(id);
        return R.ok("组织单元已删除");
    }

    @PutMapping("/dimensions/{dimensionCode}/relations/{childUnitId}")
    @RequirePermission("/api/v1/org/units/*:PUT")
    public R<SysOrgRelation> saveRelation(@PathVariable String dimensionCode,
                                          @PathVariable String childUnitId,
                                          @RequestBody SaveOrgRelationRequest request) {
        return R.ok(orgUnitService.saveRelation(dimensionCode, childUnitId, request));
    }

    @DeleteMapping("/dimensions/{dimensionCode}/relations/{childUnitId}")
    @RequirePermission("/api/v1/org/units/*:DELETE")
    public R<String> deleteRelation(@PathVariable String dimensionCode,
                                    @PathVariable String childUnitId) {
        orgUnitService.deleteRelation(dimensionCode, childUnitId);
        return R.ok("组织关系已删除");
    }

    @GetMapping("/user-units")
    @RequirePermission("/api/v1/org/units:GET")
    public R<List<SysUserOrgUnit>> listUserOrgRelations() {
        return R.ok(orgUnitService.listUserOrgRelations());
    }

    @GetMapping("/users/{userId}/units")
    @RequirePermission("/api/v1/org/units:GET")
    public R<List<UserOrgAssignmentResponse>> listUserAssignments(@PathVariable String userId,
                                                                  @RequestParam(required = false) String dimensionCode) {
        return R.ok(orgUnitService.listUserAssignments(userId, dimensionCode));
    }

    @GetMapping("/units/{orgUnitId}/users")
    @RequirePermission("/api/v1/org/units:GET")
    public R<List<OrgUnitUserResponse>> listOrgUnitUsers(@PathVariable String orgUnitId,
                                                         @RequestParam(required = false) String dimensionCode) {
        return R.ok(orgUnitService.listOrgUnitUsers(orgUnitId, dimensionCode));
    }

    @PutMapping("/users/{userId}/units")
    @RequirePermission("/api/v1/org/units/*:PUT")
    public R<String> assignUserOrgUnits(@PathVariable String userId,
                                        @RequestBody UserOrgAssignmentRequest request) {
        orgUnitService.assignUserOrgUnits(userId, request);
        return R.ok("用户组织归属已更新");
    }
}
