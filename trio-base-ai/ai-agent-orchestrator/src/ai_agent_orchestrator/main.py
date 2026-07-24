from __future__ import annotations

import asyncio
import sys

import uvicorn

from .app import create_app
from .config import get_settings


def configure_event_loop_policy() -> None:
    if sys.platform == "win32":
        policy_factory = getattr(asyncio, "WindowsSelectorEventLoopPolicy", None)
        if policy_factory is not None:
            asyncio.set_event_loop_policy(policy_factory())


configure_event_loop_policy()

app = create_app()


def main() -> None:
    settings = get_settings()
    target = "ai_agent_orchestrator.main:app" if settings.environment == "development" else app
    uvicorn.run(
        target,
        host=settings.host,
        port=settings.port,
        reload=settings.environment == "development",
    )


if __name__ == "__main__":
    main()
