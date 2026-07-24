from __future__ import annotations

import logging

from fastapi import FastAPI
from fastapi.testclient import TestClient

from ai_llm_gateway.middleware.data_masking import scan_and_redact
from ai_llm_gateway.middleware.prompt_logging import PromptLoggingMiddleware
from ai_llm_gateway.services.cache_service import CacheScope, _cache_key


def test_sensitive_prompt_is_redacted_before_use() -> None:
    value, findings = scan_and_redact("手机号13800138000，身份证110101199001011234")
    assert "13800138000" not in value
    assert "110101199001011234" not in value
    assert set(findings) == {"id_card", "phone_number"}


def test_prompt_logging_never_records_raw_prompt(caplog) -> None:
    app = FastAPI()
    app.add_middleware(PromptLoggingMiddleware)

    @app.post("/api/v1/ai/chat")
    async def chat() -> dict[str, bool]:
        return {"ok": True}

    with caplog.at_level(logging.INFO, logger="prompt_logging"):
        response = TestClient(app).post(
            "/api/v1/ai/chat",
            json={
                "model": "test/model",
                "messages": [{"role": "user", "content": "联系13800138000"}],
            },
        )
    assert response.status_code == 200
    joined = "\n".join(caplog.messages)
    assert "13800138000" not in joined
    assert "prompt_hash" in joined
    assert "prompt_preview" not in joined


def test_cache_key_isolated_by_tenant_actor_and_policy_version() -> None:
    default = CacheScope("default", "U001", "v1", "kb1")
    other_actor = CacheScope("default", "U002", "v1", "kb1")
    other_policy = CacheScope("default", "U001", "v2", "kb1")
    assert _cache_key("model", "prompt", default) != _cache_key(
        "model", "prompt", other_actor
    )
    assert _cache_key("model", "prompt", default) != _cache_key(
        "model", "prompt", other_policy
    )
