from __future__ import annotations

from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.responses import JSONResponse
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest
from starlette.responses import Response

from ai_agent_orchestrator.api import health_router, runs_router
from ai_agent_orchestrator.clients import ActionClient, LlmGatewayClient, LowcodeClient, RagClient
from ai_agent_orchestrator.config import Settings, get_settings
from ai_agent_orchestrator.graphs import build_assistant_graph
from ai_agent_orchestrator.observability import configure_telemetry
from ai_agent_orchestrator.persistence import (
    CheckpointManager,
    InMemoryRunRepository,
    PostgresRunRepository,
)
from ai_agent_orchestrator.runtime.governed_tools import create_governed_tool_registry
from ai_agent_orchestrator.runtime.graph_registry import GraphRegistration, GraphRegistry
from ai_agent_orchestrator.runtime.run_service import AgentRunService


def create_app(settings: Settings | None = None) -> FastAPI:
    config = settings or get_settings()
    configure_telemetry(config)

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        repository = (
            PostgresRunRepository(config.database_url, config.event_poll_interval_seconds)
            if config.checkpoint_backend == "postgres"
            else InMemoryRunRepository()
        )
        checkpoints = CheckpointManager(config)
        await repository.setup()
        checkpointer = await checkpoints.open()

        llm = LlmGatewayClient(config.llm_gateway_url, config.request_timeout_seconds)
        rag = RagClient(config.rag_service_url, config.request_timeout_seconds)
        lowcode = LowcodeClient(config.platform_gateway_url, config.request_timeout_seconds)
        action = ActionClient(config.platform_gateway_url, config.request_timeout_seconds)
        clients = (llm, rag, lowcode, action)
        tools = create_governed_tool_registry(
            max_result_bytes=config.max_tool_result_bytes,
            lowcode=lowcode,
            rag=rag,
            action=action,
        )

        graphs = GraphRegistry()
        graphs.register(
            GraphRegistration(
                graph_id="triobase-assistant",
                graph_version="1.0.0",
                state_schema_version="1.0",
                graph=build_assistant_graph(
                    settings=config,
                    llm=llm,
                    tools=tools,
                    checkpointer=checkpointer,
                ),
            )
        )
        service = AgentRunService(
            settings=config,
            repository=repository,
            checkpoints=checkpoints,
            graphs=graphs,
        )
        app.state.settings = config
        app.state.repository = repository
        app.state.checkpoints = checkpoints
        app.state.graphs = graphs
        app.state.run_service = service
        try:
            yield
        finally:
            await service.close()
            for client in clients:
                await client.close()
            await checkpoints.close()
            await repository.close()

    app = FastAPI(
        title="TrioBase AI Agent Orchestrator",
        version="0.1.0",
        lifespan=lifespan,
    )
    app.include_router(health_router)
    app.include_router(runs_router)

    @app.get("/metrics", include_in_schema=False)
    async def metrics() -> Response:
        return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)

    @app.exception_handler(KeyError)
    async def key_error_handler(_, exception: KeyError) -> JSONResponse:
        code = str(exception).strip("'")
        return JSONResponse(status_code=404, content={"error": code[:128]})

    @app.exception_handler(ValueError)
    async def value_error_handler(_, exception: ValueError) -> JSONResponse:
        code = str(exception)
        if not code or len(code) > 128:
            code = "AGENT_REQUEST_INVALID"
        return JSONResponse(status_code=400, content={"error": code})

    return app
