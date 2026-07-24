from __future__ import annotations

import json
from pathlib import Path

from ai_agent_orchestrator.config import Settings
from ai_agent_orchestrator.graphs.domains import BusinessSlotExtractor, classify_intent

EVALS = Path(__file__).parents[1] / "evals"


def test_versioned_intent_and_slot_golden_set_meets_thresholds() -> None:
    settings = Settings(environment="test", llm_mode="mock", feature_expense_enabled=True)
    extractor = BusinessSlotExtractor(llm=None, settings=settings)  # type: ignore[arg-type]
    cases = [
        json.loads(line)
        for line in (EVALS / "leave-expense-v1.jsonl").read_text(encoding="utf-8").splitlines()
    ]
    thresholds = json.loads((EVALS / "thresholds.json").read_text(encoding="utf-8"))["minimum"]

    intent_matches = 0
    domain_matches = 0
    slot_matches = 0
    slot_total = 0
    for case in cases:
        intent, domain, _ = classify_intent(case["message"], settings)
        intent_matches += intent == case["expectedIntent"]
        domain_matches += domain == case["expectedDomain"]
        if domain == "leave":
            actual = extractor._leave_values(case["message"])
        elif domain == "expense":
            actual = extractor._expense_values(case["message"])
        else:
            actual = {}
        for key, expected in case["expectedSlots"].items():
            slot_total += 1
            slot_matches += actual.get(key) == expected

    assert intent_matches / len(cases) >= thresholds["intentAccuracy"]
    assert domain_matches / len(cases) >= thresholds["domainAccuracy"]
    assert slot_matches / max(1, slot_total) >= thresholds["expectedSlotAccuracy"]


def test_safety_invariants_are_zero_tolerance() -> None:
    thresholds = json.loads((EVALS / "thresholds.json").read_text(encoding="utf-8"))
    assert set(thresholds["zeroTolerance"]) == {
        "CROSS_TENANT_ACCESS",
        "DIRECT_MODEL_PROVIDER",
        "PYTHON_TEMPORAL_WORKER",
        "UNAUTHORIZED_READ",
        "UNCONFIRMED_STATE_CHANGE",
        "UNREGISTERED_SIDE_EFFECT",
    }


def test_expense_department_is_extracted_for_published_schema() -> None:
    settings = Settings(environment="test", llm_mode="mock", feature_expense_enabled=True)
    extractor = BusinessSlotExtractor(llm=None, settings=settings)  # type: ignore[arg-type]
    values = extractor._expense_values("帮我报销交通费128元，用途是灰度测试，所属部门研发部。")
    assert values["reason"] == "灰度测试"
    assert values["department"] == "研发部"
