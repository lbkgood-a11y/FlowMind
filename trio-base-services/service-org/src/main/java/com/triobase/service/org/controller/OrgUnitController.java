package com.triobase.service.org.controller;

import com.triobase.common.core.result.R;
import com.triobase.service.org.dto.CreateOrgUnitRequest;
import com.triobase.service.org.dto.UserOrgAssignmentRequest;
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

    @GetMapping("/units")
    public R<List<SysOrgUnit>> listOrgUnits() {
        return R.ok(orgUnitService.listOrgUnits());
    }

    @PostMapping("/units")
    public R<SysOrgUnit> createOrgUnit(@RequestBody CreateOrgUnitRequest request) {
        return R.ok(orgUnitService.createOrgUnit(request));
    }

    @DeleteMapping("/units/{id}")
    public R<String> deleteOrgUnit(@PathVariable String id) {
        orgUnitService.deleteOrgUnit(id);
        return R.ok("组织单元已删除");
    }

    @GetMapping("/user-units")
    public R<List<SysUserOrgUnit>> listUserOrgRelations() {
        return R.ok(orgUnitService.listUserOrgRelations());
    }

    @PutMapping("/users/{userId}/units")
    public R<String> assignUserOrgUnits(@PathVariable String userId,
                                        @RequestBody UserOrgAssignmentRequest request) {
        orgUnitService.assignUserOrgUnits(userId, request);
        return R.ok("用户组织归属已更新");
    }
}
