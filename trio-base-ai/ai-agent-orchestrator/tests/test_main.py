from __future__ import annotations

import asyncio

from ai_agent_orchestrator import main


def test_windows_entrypoint_uses_selector_event_loop_policy(monkeypatch) -> None:
    marker = object()
    applied: list[object] = []
    monkeypatch.setattr(main.sys, "platform", "win32")
    monkeypatch.setattr(
        asyncio,
        "WindowsSelectorEventLoopPolicy",
        lambda: marker,
        raising=False,
    )
    monkeypatch.setattr(asyncio, "set_event_loop_policy", applied.append)

    main.configure_event_loop_policy()

    assert applied == [marker]
