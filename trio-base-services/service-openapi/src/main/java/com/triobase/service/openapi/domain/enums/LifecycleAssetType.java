package com.triobase.service.openapi.domain.enums;

import com.triobase.common.core.exception.BizException;

import java.util.Arrays;

public enum LifecycleAssetType {
    STRUCTURES("structures", "structure_key", "display_name", "lifecycle_state", "created_at"),
    MAPPINGS("mappings", "mapping_key", "display_name", "lifecycle_state", "created_at"),
    VALUE_MAPS("value-maps", "value_map_key", "display_name", "lifecycle_state", "created_at"),
    CONNECTORS("connectors", "connector_key", "display_name", "lifecycle_state", "created_at"),
    ROUTES("routes", "route_key", "display_name", "lifecycle_state", "created_at"),
    RELEASES("releases", "id", "route_definition_id", "lifecycle_state", "published_at"),
    ORCHESTRATIONS("orchestrations", "orchestration_key", "display_name", "lifecycle_state", "created_at"),
    CALLBACKS("callbacks", "callback_key", "display_name", "lifecycle_state", "created_at"),
    PRODUCTS("products", "product_key", "display_name", "lifecycle_state", "created_at"),
    APPLICATIONS("applications", "application_key", "display_name", "lifecycle_state", "created_at"),
    SUBSCRIPTIONS("subscriptions", "id", "id", "lifecycle_state", "created_at"),
    APPROVALS("approvals", "asset_id", "asset_type", "decision", "created_at"),
    POLICIES("policies", "id", "scope_type", "lifecycle_state", "created_at"),
    POLICY_SNAPSHOTS("policy-snapshots", "id", "environment", null, "published_at");

    private final String path;
    private final String keyColumn;
    private final String nameColumn;
    private final String stateColumn;
    private final String orderColumn;

    LifecycleAssetType(String path, String keyColumn, String nameColumn, String stateColumn, String orderColumn) {
        this.path = path;
        this.keyColumn = keyColumn;
        this.nameColumn = nameColumn;
        this.stateColumn = stateColumn;
        this.orderColumn = orderColumn;
    }

    public String path() { return path; }
    public String keyColumn() { return keyColumn; }
    public String nameColumn() { return nameColumn; }
    public String stateColumn() { return stateColumn; }
    public String orderColumn() { return orderColumn; }

    public static LifecycleAssetType fromPath(String value) {
        return Arrays.stream(values()).filter(type -> type.path.equals(value)).findFirst()
                .orElseThrow(() -> new BizException(40060, "OPENAPI_LIFECYCLE_ASSET_TYPE_UNSUPPORTED"));
    }
}
