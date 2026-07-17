<script setup lang="ts">
import type { OpenApiOperationsApi } from '#/api';
import type { TableProps } from 'ant-design-vue';
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { Button, Input, message, Modal, Select, Space, Table, Tag } from 'ant-design-vue';
import { getLifecycleAssets, invokeOpenApiLifecycleAction } from '#/api';
import { lifecycleResources } from './resource-config';

const route = useRoute(); const { hasAccessByCodes } = useAccess();
const resourceKey = computed(() => String(route.path.split('/').filter(Boolean).at(-1)));
const config = computed(() => lifecycleResources[resourceKey.value] ?? lifecycleResources.structures!);
const canWrite = computed(() => hasAccessByCodes([config.value.writePermission]));
const rows = ref<OpenApiOperationsApi.LifecycleAsset[]>([]); const loading = ref(false);
const detail = ref<OpenApiOperationsApi.LifecycleAsset>(); const detailOpen = ref(false); const createOpen = ref(false); const saving = ref(false);
const payload = ref('{}'); const query = reactive({ keyword: '', state: '' });
const actionOpen=ref(false); const actionTarget=ref(''); const actionPath=ref(''); const actionPayload=ref('{}');
const pagination = reactive({ current: 1, pageSize: 20, total: 0, showSizeChanger: true });
const columns: TableProps['columns'] = [
 { title:'标识',dataIndex:'assetKey',key:'key' },{ title:'名称',dataIndex:'displayName',key:'name' },
 { title:'状态',dataIndex:'lifecycleState',key:'state',width:140 },{ title:'更新时间',dataIndex:'updatedAt',key:'updated',width:180 },
 { title:'操作',key:'actions',width:100,fixed:'right' },
];
async function load(){loading.value=true;try{const result=await getLifecycleAssets(config.value.assetType,{keyword:query.keyword||undefined,state:query.state||undefined,page:pagination.current,size:pagination.pageSize});rows.value=result.records;pagination.total=result.total;}finally{loading.value=false;}}
function inspect(record:OpenApiOperationsApi.LifecycleAsset){detail.value=record;detailOpen.value=true;}
function asAsset(record:Record<string,any>){return record as OpenApiOperationsApi.LifecycleAsset;}
async function create(){let data:Record<string,any>;try{data=JSON.parse(payload.value);}catch{message.error('请输入合法 JSON');return;}saving.value=true;try{const result=await invokeOpenApiLifecycleAction('POST',config.value.createEndpoint,data);message.success('创建成功');createOpen.value=false;payload.value=JSON.stringify(result,null,2);await load();}finally{saving.value=false;}}
async function executeAction(){let data:Record<string,any>;try{data=JSON.parse(actionPayload.value);}catch{message.error('请输入合法 JSON');return;}if(!actionTarget.value||!actionPath.value){message.warning('请选择动作并填写目标 ID');return;}saving.value=true;try{await invokeOpenApiLifecycleAction(config.value.actions.find(item=>item.path===actionPath.value)?.method??'POST',actionPath.value.replace('{id}',encodeURIComponent(actionTarget.value)),data);message.success('生命周期动作已执行');actionOpen.value=false;await load();}finally{saving.value=false;}}
function tableChange(next:TableProps['pagination']){if(next&&typeof next==='object'){pagination.current=next.current??1;pagination.pageSize=next.pageSize??20;load();}}
watch(resourceKey,()=>{pagination.current=1;load();}); onMounted(load);
</script>
<template><Page :title="config.title" :description="config.description">
 <Space class="toolbar" wrap><Input v-model:value="query.keyword" allow-clear placeholder="名称或标识"/><Input v-model:value="query.state" allow-clear placeholder="生命周期状态"/><Button type="primary" @click="load">查询</Button><Button v-if="canWrite" @click="createOpen=true">新建</Button><Button v-if="canWrite&&config.actions.length" @click="actionOpen=true">生命周期动作</Button></Space>
 <Table row-key="id" size="small" :columns="columns" :data-source="rows" :loading="loading" :pagination="pagination" @change="tableChange"><template #bodyCell="{column,record}"><Tag v-if="column.key==='state'" color="blue">{{record.lifecycleState||'-'}}</Tag><Button v-if="column.key==='actions'" type="link" @click="inspect(asAsset(record))">详情</Button></template></Table>
 <Modal v-model:open="detailOpen" title="资产详情" :footer="null" width="760px"><pre>{{ JSON.stringify(detail?.detail,null,2) }}</pre></Modal>
 <Modal v-model:open="createOpen" :title="`新建${config.title}`" :confirm-loading="saving" width="760px" @ok="create"><Input.TextArea v-model:value="payload" :rows="18" /></Modal>
 <Modal v-model:open="actionOpen" title="执行生命周期动作" :confirm-loading="saving" width="760px" @ok="executeAction"><Space direction="vertical" style="width:100%"><Select v-model:value="actionPath" placeholder="选择动作" :options="config.actions.map(item=>({label:item.label,value:item.path}))"/><Input v-model:value="actionTarget" placeholder="目标 ID（结构版本、Release、应用或订阅 ID）"/><Input.TextArea v-model:value="actionPayload" :rows="12" placeholder="动作请求 JSON；无参数可填写 {}"/></Space></Modal>
</Page></template>
<style scoped>.toolbar{margin-bottom:12px}.toolbar :deep(.ant-input){width:220px}pre{max-height:520px;overflow:auto;background:#0f172a;color:#e2e8f0;padding:12px;border-radius:6px}</style>
