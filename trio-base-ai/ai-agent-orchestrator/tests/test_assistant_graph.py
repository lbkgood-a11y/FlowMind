from __future__ import annotations

from datetime import datetime, timedelta
from typing import Any
from zoneinfo import ZoneInfo

import pytest
from langgraph.checkpoint.memory import InMemorySaver
from langgraph.types import Command

from ai_agent_orchestrator.clients.action import ActionValidationResult
from ai_agent_orchestrator.clients.lowcode import RuntimeApplicationDescriptor
from ai_agent_orchestrator.config import Settings
from ai_agent_orchestrator.contracts.models import AgentActor, AgentState, TrustedRequestContext
from ai_agent_orchestrator.graphs import build_assistant_graph
from ai_agent_orchestrator.runtime.governed_tools import create_governed_tool_registry
from ai_agent_orchestrator.security.context import ExecutionCredentials, use_execution_credentials


class FakeLlm:
    async def complete_json(self, **_: Any) -> dict[str, Any]:
        return {"data": {}}

    async def complete(self, **_: Any) -> str:
        return "answer"


class FakeRag:
    async def search(self, *_: Any, **__: Any) -> list[Any]:
        return []


class FakeLowcode:
    async def get_runtime_application(
        self,
        app_key: str,
        version: int | None = None,
    ) -> RuntimeApplicationDescriptor:
        assert app_key == "leave"
        assert version is None
        return RuntimeApplicationDescriptor.model_validate(
            {
                "appKey": "leave",
                "name": "请假申请",
                "formKey": "leave",
                "version": 1,
                "schemaJson": """
                {
                  "type":"object",
                  "required":["date"],
                  "properties":{
                    "name":{"type":"string","title":"姓名"},
                    "date":{"type":"string","format":"date","title":"日期"},
                    "desc":{"type":"string","title":"理由"}
                  }
                }
                """,
                "actions": [
                    {
                        "id": "action-1",
                        "actionCode": "CREATE",
                        "actionType": "CREATE",
                        "label": "新建",
                        "allowed": True,
                    }
                ],
            }
        )


class FakeAction:
    async def validate_candidate(self, candidate: Any) -> ActionValidationResult:
        assert candidate.action_type == "lowcode.form.create"
        assert candidate.payload["appKey"] == "leave"
        assert candidate.actor.id == "U001"
        return ActionValidationResult(
            candidateId=candidate.candidate_id,
            actionType=candidate.action_type,
            valid=True,
            definitionExists=True,
            schemaValid=True,
            visible=True,
            enabled=True,
            dispatchable=False,
            requiresConfirmation=True,
            confirmationSatisfied=False,
            confirmation=candidate.confirmation.model_dump(mode="json", by_alias=True),
        )


def context() -> TrustedRequestContext:
    return TrustedRequestContext(
        tenantId="default",
        actor=AgentActor(id="U001", displayName="测试员工", tenantId="default"),
        traceId="trace-1",
        correlationId="correlation-1",
    )


def initial(message: str) -> dict[str, Any]:
    state = AgentState(
        graphId="triobase-assistant",
        graphVersion="1.0.0",
        threadId="thread-1",
        runId="run-1",
        tenantId="default",
        actor=context().actor,
        traceId="trace-1",
        correlationId="correlation-1",
        message=message,
    )
    return {"agent": state.model_dump(mode="json"), "model": "mock/model", "events": []}


@pytest.mark.asyncio
async def test_leave_graph_builds_candidate_then_waits_for_external_action_result() -> None:
    settings = Settings(environment="test", llm_mode="mock")
    tools = create_governed_tool_registry(
        max_result_bytes=settings.max_tool_result_bytes,
        lowcode=FakeLowcode(),
        rag=FakeRag(),
        action=FakeAction(),
    )
    graph = build_assistant_graph(
        settings=settings,
        llm=FakeLlm(),
        tools=tools,
        checkpointer=InMemorySaver(),
    )
    config = {"configurable": {"thread_id": "default:assistant:1:thread-1"}}
    credentials = ExecutionCredentials(context())
    with use_execution_credentials(credentials):
        waiting = await graph.ainvoke(
            initial("帮我申请明天一天事假，因为家中有事。"),
            config,
        )
    assert waiting["__interrupt__"][0].value["kind"] == "confirmation"
    candidate = waiting["__interrupt__"][0].value["candidate"]
    assert candidate["idempotencyKey"] == "agent:default:run-1:CREATE"
    assert candidate["target"]["attributes"]["authorizationResourceCode"] == (
        "LOWCODE_FORM:LEAVE"
    )
    tomorrow = (datetime.now(ZoneInfo("Asia/Shanghai")) + timedelta(days=1)).date().isoformat()
    assert candidate["payload"]["data"] == {
        "name": "测试员工",
        "date": tomorrow,
        "desc": "家中有事",
    }

    with use_execution_credentials(credentials):
        finished = await graph.ainvoke(
            Command(
                resume={
                    "kind": "action_result",
                    "values": {
                        "actionId": "action-1",
                        "status": "SUCCEEDED",
                        "ownerExecutionRef": "process-1",
                    },
                }
            ),
            config,
        )
    state = AgentState.model_validate(finished["agent"])
    assert state.status == "COMPLETED"
    assert state.action_refs == ["action-1", "process-1"]


@pytest.mark.asyncio
async def test_failed_action_result_marks_agent_run_failed() -> None:
    settings = Settings(environment="test", llm_mode="mock")
    tools = create_governed_tool_registry(
        max_result_bytes=settings.max_tool_result_bytes,
        lowcode=FakeLowcode(),
        rag=FakeRag(),
        action=FakeAction(),
    )
    graph = build_assistant_graph(
        settings=settings,
        llm=FakeLlm(),
        tools=tools,
        checkpointer=InMemorySaver(),
    )
    config = {"configurable": {"thread_id": "default:assistant:1:failed-action"}}
    credentials = ExecutionCredentials(context())
    with use_execution_credentials(credentials):
        waiting = await graph.ainvoke(
            initial("帮我申请明天一天事假，因为家中有事。"), config
        )
        finished = await graph.ainvoke(
            Command(
                resume={
                    "kind": "action_result",
                    "values": {
                        "actionId": "action-failed",
                        "status": "FAILED",
                        "retryable": True,
                        "errors": [
                            {
                                "code": "ACTION_OWNER_DISPATCH_FAILED",
                                "category": "DISPATCH",
                            }
                        ],
                    },
                }
            ),
            config,
        )
    state = AgentState.model_validate(finished["agent"])
    assert state.status == "FAILED"
    assert state.error is not None
    assert state.error.code == "ACTION_OWNER_DISPATCH_FAILED"
    assert state.error.retryable
    assert any(item["eventType"] == "run.failed" for item in finished["events"])


@pytest.mark.asyncio
async def test_leave_graph_interrupts_for_missing_fields_without_candidate() -> None:
    settings = Settings(environment="test", llm_mode="mock")
    tools = create_governed_tool_registry(
        max_result_bytes=settings.max_tool_result_bytes,
        lowcode=FakeLowcode(),
        rag=FakeRag(),
        action=FakeAction(),
    )
    graph = build_assistant_graph(
        settings=settings,
        llm=FakeLlm(),
        tools=tools,
        checkpointer=InMemorySaver(),
    )
    config = {"configurable": {"thread_id": "default:assistant:1:thread-missing"}}
    with use_execution_credentials(ExecutionCredentials(context())):
        waiting = await graph.ainvoke(initial("帮我请事假"), config)
    value = waiting["__interrupt__"][0].value
    assert value["kind"] == "missing_input"
    assert set(value["missingSlots"]) == {"date"}


@pytest.mark.asyncio
async def test_checkpoint_survives_graph_rebuild_and_cancel_resume() -> None:
    settings = Settings(environment="test", llm_mode="mock")
    saver = InMemorySaver()
    tools = create_governed_tool_registry(
        max_result_bytes=settings.max_tool_result_bytes,
        lowcode=FakeLowcode(),
        rag=FakeRag(),
        action=FakeAction(),
    )
    config = {"configurable": {"thread_id": "default:assistant:1:restart"}}
    credentials = ExecutionCredentials(context())
    first_process = build_assistant_graph(
        settings=settings,
        llm=FakeLlm(),
        tools=tools,
        checkpointer=saver,
    )
    with use_execution_credentials(credentials):
        waiting = await first_process.ainvoke(initial("帮我请事假"), config)
    assert waiting["__interrupt__"][0].value["kind"] == "missing_input"

    restarted_process = build_assistant_graph(
        settings=settings,
        llm=FakeLlm(),
        tools=tools,
        checkpointer=saver,
    )
    with use_execution_credentials(credentials):
        cancelled = await restarted_process.ainvoke(
            Command(resume={"kind": "cancel", "values": {}}),
            config,
        )
    assert AgentState.model_validate(cancelled["agent"]).status == "CANCELLED"


@pytest.mark.asyncio
async def test_knowledge_branch_refuses_to_guess_without_authorized_evidence() -> None:
    settings = Settings(
        environment="test",
        llm_mode="mock",
        feature_knowledge_enabled=True,
    )
    tools = create_governed_tool_registry(
        max_result_bytes=settings.max_tool_result_bytes,
        lowcode=FakeLowcode(),
        rag=FakeRag(),
        action=FakeAction(),
    )
    graph = build_assistant_graph(
        settings=settings,
        llm=FakeLlm(),
        tools=tools,
        checkpointer=InMemorySaver(),
    )
    config = {"configurable": {"thread_id": "default:assistant:1:knowledge"}}
    with use_execution_credentials(ExecutionCredentials(context())):
        result = await graph.ainvoke(initial("公司的请假制度是什么？"), config)
    assert AgentState.model_validate(result["agent"]).status == "COMPLETED"
    text_events = [
        item["data"]["text"]
        for item in result["events"]
        if item["eventType"] == "message.delta"
    ]
    assert any("不基于通用知识猜测" in text for text in text_events)
