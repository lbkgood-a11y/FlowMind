INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('P124', '/api/v1/openapi/management/operations', 'GET', 'View OpenAPI lifecycle catalog and readiness')
ON CONFLICT (id) DO UPDATE SET resource=EXCLUDED.resource, action=EXCLUDED.action,
    description=EXCLUDED.description, updated_by='SYSTEM', updated_at=CURRENT_TIMESTAMP;

UPDATE sys_menu SET menu_key='OpenApiLifecycleOverview', menu_name='生命周期总览',
    path='/openapi-operations/overview', component='/openapi/lifecycle/overview',
    icon='mdi:timeline-check-outline', permission_id='P124',
    permission_code='/api/v1/openapi/management/operations:GET', description='API 生命周期依赖与开放就绪状态',
    updated_by='SYSTEM', updated_at=CURRENT_TIMESTAMP
WHERE id='OA_MGMT_WORKBENCH';

INSERT INTO sys_menu (id,parent_id,menu_key,menu_name,path,component,icon,menu_type,menu_group,sort_order,
 visible,status,hide_in_menu,hide_children_in_menu,hide_in_breadcrumb,hide_in_tab,permission_id,permission_code,description)
VALUES
 ('OA_GROUP_DESIGN','OA_MGMT_100','OpenApiDesign','API 设计','/openapi-operations/design',NULL,'mdi:file-document-edit-outline','catalog','integration',20,1,1,0,0,0,0,NULL,NULL,'结构与映射设计'),
 ('OA_STRUCTURES','OA_GROUP_DESIGN','OpenApiStructures','标准与外部结构','/openapi-operations/structures','/openapi/lifecycle/resource','mdi:code-json','menu','integration',10,1,1,0,0,0,0,'P100','/api/v1/openapi/management/structures:GET','结构注册与版本治理'),
 ('OA_MAPPINGS','OA_GROUP_DESIGN','OpenApiMappings','字段映射','/openapi-operations/mappings','/openapi/lifecycle/resource','mdi:transit-connection-variant','menu','integration',20,1,1,0,0,0,0,'P104','/api/v1/openapi/management/mappings:GET','字段映射与契约测试'),
 ('OA_VALUE_MAPS','OA_GROUP_DESIGN','OpenApiValueMaps','值映射','/openapi-operations/value-maps','/openapi/lifecycle/resource','mdi:swap-horizontal-bold','menu','integration',30,1,1,0,0,0,0,'P104','/api/v1/openapi/management/mappings:GET','枚举和值映射'),
 ('OA_GROUP_IMPL','OA_MGMT_100','OpenApiImplementation','API 实现','/openapi-operations/implementation',NULL,'mdi:tools','catalog','integration',30,1,1,0,0,0,0,NULL,NULL,'连接、路由与编排'),
 ('OA_CONNECTORS','OA_GROUP_IMPL','OpenApiConnectors','连接器','/openapi-operations/connectors','/openapi/lifecycle/resource','mdi:connection','menu','integration',10,1,1,0,0,0,0,'P112','/api/v1/openapi/management/connectors:GET','受控外部端点与凭证引用'),
 ('OA_ROUTES','OA_GROUP_IMPL','OpenApiRoutes','路由与发布','/openapi-operations/routes','/openapi/lifecycle/resource','mdi:source-branch','menu','integration',20,1,1,0,0,0,0,'P114','/api/v1/openapi/management/routes:GET','路由、Release、激活与回滚'),
 ('OA_ORCHESTRATIONS','OA_GROUP_IMPL','OpenApiOrchestrations','流程编排','/openapi-operations/orchestrations','/openapi/lifecycle/resource','mdi:graph-outline','menu','integration',30,1,1,0,0,0,0,'P116','/api/v1/openapi/management/orchestrations:GET','Temporal 编排与补偿'),
 ('OA_CALLBACKS','OA_GROUP_IMPL','OpenApiCallbacks','回调配置','/openapi-operations/callbacks','/openapi/lifecycle/resource','mdi:call-received','menu','integration',40,1,1,0,0,0,0,'P118','/api/v1/openapi/management/callback-profiles:GET','回调验签、关联与 Signal'),
 ('OA_GROUP_EXPOSURE','OA_MGMT_100','OpenApiExposure','API 开放','/openapi-operations/exposure',NULL,'mdi:api','catalog','integration',40,1,1,0,0,0,0,NULL,NULL,'产品、应用、订阅和策略'),
 ('OA_PRODUCTS','OA_GROUP_EXPOSURE','OpenApiProducts','API 产品','/openapi-operations/products','/openapi/lifecycle/resource','mdi:package-variant-closed','menu','integration',10,1,1,0,0,0,0,'P106','/api/v1/openapi/management/products:GET','API 产品与语义版本'),
 ('OA_APPLICATIONS','OA_GROUP_EXPOSURE','OpenApiApplications','接入应用','/openapi-operations/applications','/openapi/lifecycle/resource','mdi:application-cog-outline','menu','integration',20,1,1,0,0,0,0,'P108','/api/v1/openapi/management/applications:GET','应用、客户端与凭证'),
 ('OA_SUBSCRIPTIONS','OA_GROUP_EXPOSURE','OpenApiSubscriptions','订阅与审批','/openapi-operations/subscriptions','/openapi/lifecycle/resource','mdi:clipboard-check-outline','menu','integration',30,1,1,0,0,0,0,'P108','/api/v1/openapi/management/applications:GET','产品订阅、升级和双重审批'),
 ('OA_POLICIES','OA_GROUP_EXPOSURE','OpenApiPolicies','流控与安全策略','/openapi-operations/policies','/openapi/lifecycle/resource','mdi:shield-lock-outline','menu','integration',40,1,1,0,0,0,0,'P106','/api/v1/openapi/management/products:GET','配额、并发与策略快照'),
 ('OA_GROUP_OPS','OA_MGMT_100','OpenApiRuntimeOperations','API 运营','/openapi-operations/runtime',NULL,'mdi:monitor-dashboard','catalog','integration',50,1,1,0,0,0,0,NULL,NULL,'执行、诊断与异常处理'),
 ('OA_EXECUTIONS','OA_GROUP_OPS','OpenApiExecutions','执行中心','/openapi-operations/executions','/openapi/workbench/index','mdi:history','menu','integration',10,1,1,0,0,0,0,'P111','/api/v1/openapi/management/executions:GET','执行与 Trace 查询'),
 ('OA_QUARANTINE','OA_GROUP_OPS','OpenApiCallbackQuarantine','回调隔离区','/openapi-operations/quarantine','/openapi/workbench/index','mdi:alert-octagon-outline','menu','integration',20,1,1,0,0,0,0,'P120','/api/v1/openapi/management/callback-quarantine:GET','回调隔离与人工处置'),
 ('OA_MGMT_125','OA_MGMT_100','OpenApiOperationsRead','生命周期目录权限',NULL,NULL,NULL,'button','integration',250,0,1,1,0,0,0,'P124','/api/v1/openapi/management/operations:GET','生命周期目录读取权限')
ON CONFLICT(id) DO UPDATE SET parent_id=EXCLUDED.parent_id,menu_key=EXCLUDED.menu_key,menu_name=EXCLUDED.menu_name,
 path=EXCLUDED.path,component=EXCLUDED.component,icon=EXCLUDED.icon,menu_type=EXCLUDED.menu_type,
 menu_group=EXCLUDED.menu_group,sort_order=EXCLUDED.sort_order,visible=EXCLUDED.visible,status=EXCLUDED.status,
 hide_in_menu=EXCLUDED.hide_in_menu,permission_id=EXCLUDED.permission_id,permission_code=EXCLUDED.permission_code,
 description=EXCLUDED.description,updated_by='SYSTEM',updated_at=CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu(id,role_id,menu_id,created_by,updated_by)
SELECT 'OA'||upper(substr(md5('R001'||':'||m.id),1,24)),'R001',m.id,'SYSTEM','SYSTEM'
FROM sys_menu m WHERE m.id LIKE 'OA_%'
ON CONFLICT(role_id,menu_id) DO NOTHING;
