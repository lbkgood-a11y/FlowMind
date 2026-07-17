$ErrorActionPreference = 'Stop'

mvn -pl trio-base-platform/platform-gateway,trio-base-services/service-openapi -am `
  '-Dtest=OpenApiApplicationAdmissionFilterTest,SensitiveDataRedactorTest,IntegrationAdmissionServiceTest,SynchronousInvocationServiceTest,JdkOutboundIntegrationClientTest,OpenApiOutboundLoadAcceptanceTest,OutboundTargetPolicyTest,ConnectorRegistryServiceTest,RuntimeBudgetServiceTest,IntegrationOrchestrationActivitiesTest,IntegrationOrchestrationWorkflowTest,CallbackRuntimeServiceTest,CallbackSignalDispatcherTest,PolicySnapshotServiceTest,ApplicationCredentialServiceTest,CompiledReleaseCacheTest,ReleaseManagementServiceTest,OpenApiMigrationIntegrationTest,RouteReleasePersistenceIntegrationTest,ManagedApplicationPilotContractTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' test
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

docker compose --profile openapi -f docker/docker-compose.yml config --quiet
exit $LASTEXITCODE
