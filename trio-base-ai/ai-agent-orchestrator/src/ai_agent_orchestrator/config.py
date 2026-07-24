from __future__ import annotations

from functools import lru_cache
from typing import Literal

from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_prefix="AGENT_",
        extra="ignore",
    )

    service_name: str = "ai-agent-orchestrator"
    environment: Literal["development", "test", "production"] = "development"
    enabled: bool = True
    host: str = "0.0.0.0"  # noqa: S104
    port: int = 8004

    default_tenant_id: str = "default"
    allow_development_identity: bool = False
    llm_mode: Literal["gateway", "mock", "auto"] = "auto"
    default_model: str = "deepseek/deepseek-chat"

    checkpoint_backend: Literal["memory", "postgres"] = "memory"
    database_url: str = "postgresql://triobase:triobase123@localhost:5432/triobase"
    checkpoint_retention_days: int = Field(default=30, ge=1, le=3650)
    event_poll_interval_seconds: float = Field(default=0.25, gt=0, le=5)

    llm_gateway_url: str = "http://localhost:8080"
    rag_service_url: str = "http://localhost:8080"
    platform_gateway_url: str = "http://localhost:8080/api/v1"
    request_timeout_seconds: float = Field(default=20, gt=0, le=300)

    max_steps: int = Field(default=20, ge=1, le=200)
    max_model_calls: int = Field(default=6, ge=1, le=50)
    max_tokens: int = Field(default=8_000, ge=128, le=1_000_000)
    max_cost_usd: float = Field(default=2.0, gt=0, le=1000)
    estimated_model_cost_per_1k_tokens_usd: float = Field(default=0.02, ge=0, le=100)
    max_run_seconds: int = Field(default=180, ge=5, le=3600)
    max_tool_result_bytes: int = Field(default=128_000, ge=1024, le=5_000_000)
    max_concurrent_invocations: int = Field(default=16, ge=1, le=512)
    max_queued_invocations: int = Field(default=64, ge=1, le=4096)

    otlp_endpoint: str | None = None
    feature_leave_enabled: bool = True
    feature_expense_enabled: bool = False
    feature_knowledge_enabled: bool = False

    @model_validator(mode="after")
    def production_requires_postgres(self) -> Settings:
        if self.environment == "production" and self.checkpoint_backend != "postgres":
            raise ValueError("production requires AGENT_CHECKPOINT_BACKEND=postgres")
        if self.default_tenant_id != "default":
            raise ValueError("the current release supports only tenant 'default'")
        if self.max_queued_invocations < self.max_concurrent_invocations:
            raise ValueError(
                "AGENT_MAX_QUEUED_INVOCATIONS must be greater than or equal to "
                "AGENT_MAX_CONCURRENT_INVOCATIONS"
            )
        return self


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
