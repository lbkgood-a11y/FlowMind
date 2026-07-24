from __future__ import annotations

import ast
from pathlib import Path

SOURCE = Path(__file__).parents[1] / "src" / "ai_agent_orchestrator"


def test_orchestrator_has_no_provider_or_temporal_worker_imports() -> None:
    forbidden_roots = {"anthropic", "google.generativeai", "litellm", "openai", "temporalio"}
    violations: list[str] = []
    for path in SOURCE.rglob("*.py"):
        tree = ast.parse(path.read_text(encoding="utf-8"))
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                names = [alias.name for alias in node.names]
            elif isinstance(node, ast.ImportFrom) and node.module:
                names = [node.module]
            else:
                continue
            for name in names:
                if any(name == root or name.startswith(f"{root}.") for root in forbidden_roots):
                    violations.append(f"{path.relative_to(SOURCE)}:{node.lineno}:{name}")
    assert violations == []


def test_network_access_is_confined_to_governed_clients() -> None:
    violations: list[str] = []
    for path in SOURCE.rglob("*.py"):
        relative = path.relative_to(SOURCE)
        if relative.parts[0] == "clients":
            continue
        text = path.read_text(encoding="utf-8")
        if "import httpx" in text or "import requests" in text:
            violations.append(str(relative))
    assert violations == []


def test_no_shell_sql_or_dynamic_execution_tools_are_registered() -> None:
    source_text = "\n".join(path.read_text(encoding="utf-8") for path in SOURCE.rglob("*.py"))
    assert "subprocess." not in source_text
    assert "os.system(" not in source_text
    assert "eval(" not in source_text
    assert "exec(" not in source_text
    assert 'ToolKind("SHELL")' not in source_text
