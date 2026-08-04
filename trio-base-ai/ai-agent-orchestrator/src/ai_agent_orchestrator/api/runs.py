"""Expose authenticated agent-run lifecycle and resumable SSE contracts."""

from __future__ import annotations

from typing import Annotated
from uuid import uuid4

from fastapi import APIRouter, Depends, Header, HTTPException, Request, Response, status
from fastapi.responses import StreamingResponse

from ai_agent_orchestrator.contracts.models import TrustedRequestContext
from ai_agent_orchestrator.contracts.runs import AgentRunCreate, AgentRunResponse, AgentRunResume

from .dependencies import authorization_header, run_service, trusted_context

router = APIRouter(prefix="/api/v1/agent", tags=["agent-runs"])

Context = Annotated[TrustedRequestContext, Depends(trusted_context)]


@router.post("/runs", response_model=AgentRunResponse, status_code=status.HTTP_202_ACCEPTED)
async def create_run(
    body: AgentRunCreate,
    request: Request,
    context: Context,
    idempotency_key: str | None = Header(default=None, alias="Idempotency-Key"),
) -> AgentRunResponse:
    """Create an asynchronous run, deduplicated when the caller supplies a stable key."""
    if not request.app.state.settings.enabled:
        raise HTTPException(status_code=503, detail="AGENT_RUNTIME_DISABLED")
    service = run_service(request)
    try:
        return await service.create(
            body,
            context,
            authorization_header(request),
            idempotency_key or f"agent-run:{uuid4()}",
        )
    except RuntimeError as exception:
        if str(exception) == "AGENT_RUN_CREATE_FAILED":
            raise HTTPException(status_code=503, detail="AGENT_RUN_CREATE_FAILED") from exception
        raise


@router.get("/runs/{run_id}", response_model=AgentRunResponse)
async def get_run(run_id: str, request: Request, context: Context) -> AgentRunResponse:
    """Return a run visible to the trusted tenant and actor, or HTTP 404."""
    result = await run_service(request).get(run_id, context)
    if result is None:
        raise HTTPException(status_code=404, detail="AGENT_RUN_NOT_FOUND")
    return result


@router.get("/runs/{run_id}/events")
async def stream_run_events(
    run_id: str,
    request: Request,
    context: Context,
    cursor: int = 0,
    last_event_id: str | None = Header(default=None, alias="Last-Event-ID"),
) -> StreamingResponse:
    """Stream ordered run events after the greatest cursor supplied by the client.

    Event IDs make reconnects resumable. Proxy buffering and caching are disabled so
    partial model and tool states reach the UI without waiting for run completion.
    """
    after_sequence = _event_sequence(last_event_id, cursor)

    async def generate():
        async for event in run_service(request).stream(run_id, context, after_sequence):
            if await request.is_disconnected():
                break
            yield event.to_sse()

    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@router.post("/runs/{run_id}/resume", response_model=AgentRunResponse, status_code=202)
async def resume_run(
    run_id: str,
    body: AgentRunResume,
    request: Request,
    context: Context,
) -> AgentRunResponse:
    """Resume a suspended run with an authorization-bearing human response."""
    try:
        return await run_service(request).resume(
            run_id,
            body,
            context,
            authorization_header(request),
        )
    except KeyError as exception:
        raise HTTPException(status_code=404, detail="AGENT_RUN_NOT_FOUND") from exception
    except ValueError as exception:
        raise HTTPException(status_code=409, detail=str(exception)) from exception


@router.post("/runs/{run_id}/cancel", response_model=AgentRunResponse)
async def cancel_run(run_id: str, request: Request, context: Context) -> AgentRunResponse:
    """Request cancellation without exposing runs outside the trusted context."""
    try:
        return await run_service(request).cancel(run_id, context)
    except KeyError as exception:
        raise HTTPException(status_code=404, detail="AGENT_RUN_NOT_FOUND") from exception


@router.delete("/threads/{thread_id}", status_code=204)
async def delete_thread(thread_id: str, request: Request, context: Context) -> Response:
    """Delete persisted conversation state owned by the trusted tenant and actor."""
    await run_service(request).delete_thread(thread_id, context)
    return Response(status_code=204)


def _event_sequence(last_event_id: str | None, cursor: int) -> int:
    if not last_event_id:
        return max(0, cursor)
    try:
        return max(cursor, int(last_event_id.rsplit(":", 1)[-1]))
    except ValueError:
        return max(0, cursor)
