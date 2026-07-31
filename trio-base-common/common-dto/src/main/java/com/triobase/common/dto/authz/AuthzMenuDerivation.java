package com.triobase.common.dto.authz;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AuthzMenuDerivation {
    private String menuId;
    private String menuName;
    private String derivation;
    private String permissionCode;
    private String resourceCode;
    private String actionCode;
    private List<String> derivedFromMenuIds = new ArrayList<>();
}
