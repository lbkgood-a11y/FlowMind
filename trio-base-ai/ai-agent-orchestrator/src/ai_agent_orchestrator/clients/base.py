from __future__ import annotations

from typing import Any

import httpx

from ai_agent_orchestrator import observability
from ai_agent_orchestrator.security.context import get_execution_credentials


class GovernedClientError(RuntimeError):
    def __init__(self, code: str, *, status_code: int = 502, retryable: bool = False) -> None:
        super().__init__(code)
        self.code = code
        self.status_code = status_code
        self.retryable = retryable


class GovernedHttpClient:
    def __init__(self, base_url: str, timeout_seconds: float) -> None:
        self._client = httpx.AsyncClient(
            base_url=base_url.rstrip("/"),
            timeout=httpx.Timeout(timeout_seconds),
            follow_redirects=False,
        )

    async def close(self) -> None:
        await self._client.aclose()

    def headers(self) -> dict[str, str]:
        return get_execution_credentials().downstream_headers()

    async def request_json(
        self,
        method: str,
        path: str,
        *,
        params: dict[str, Any] | None = None,
        json_body: Any = None,
    ) -> Any:
        try:
            with observability.TRACER.start_as_current_span(
                "agent.downstream.request",
                attributes={
                    "http.request.method": method,
                    "url.path": path,
                    "server.address": str(self._client.base_url.host or "downstream"),
                },
            ) as span:
                response = await self._client.request(
                    method,
                    path,
                    params=params,
                    json=json_body,
                    headers=self.headers(),
                )
                span.set_attribute("http.response.status_code", response.status_code)
        except httpx.TimeoutException as exception:
            raise GovernedClientError(
                "DOWNSTREAM_TIMEOUT", status_code=504, retryable=True
            ) from exception
        except httpx.HTTPError as exception:
            raise GovernedClientError("DOWNSTREAM_UNAVAILABLE", retryable=True) from exception
        if response.status_code >= 400:
            code = _bounded_error_code(response)
            raise GovernedClientError(
                code, status_code=response.status_code, retryable=response.status_code >= 500
            )
        payload = response.json()
        if isinstance(payload, dict) and ("code" in payload or "success" in payload):
            if not _wrapped_response_success(payload):
                raise GovernedClientError(
                    _bounded_payload_error_code(payload),
                    status_code=response.status_code,
                    retryable=False,
                )
            return payload.get("data")
        return payload


def _wrapped_response_success(payload: dict[str, Any]) -> bool:
    success = payload.get("success")
    if success is False:
        return False
    code = payload.get("code")
    if code is None:
        return success is not False
    return str(code) == "0"


def _bounded_payload_error_code(payload: dict[str, Any]) -> str:
    for key in ("error", "message", "code"):
        value = payload.get(key)
        if isinstance(value, str) and value:
            normalized = _normalize_error_code(value)
            if normalized:
                return normalized
        if isinstance(value, int):
            return f"DOWNSTREAM_CODE_{value}"
    return "DOWNSTREAM_BUSINESS_ERROR"


def _bounded_error_code(response: httpx.Response) -> str:
    try:
        payload = response.json()
    except ValueError:
        return f"DOWNSTREAM_HTTP_{response.status_code}"
    if isinstance(payload, dict):
        for key in ("error", "code", "message"):
            value = payload.get(key)
            if isinstance(value, str) and value and len(value) <= 128:
                normalized = _normalize_error_code(value)
                if normalized:
                    return normalized[:128]
    return f"DOWNSTREAM_HTTP_{response.status_code}"


def _normalize_error_code(value: str) -> str:
    return "".join(character for character in value if character.isalnum() or character in "_-.")[:128]
