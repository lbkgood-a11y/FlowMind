from __future__ import annotations

import re
from typing import Any

_PATTERNS = (
    (re.compile(r"(?<!\d)\d{17}[\dXx](?!\d)"), "[REDACTED_ID_CARD]"),
    (re.compile(r"(?<!\d)\d{16,19}(?!\d)"), "[REDACTED_BANK_CARD]"),
    (re.compile(r"(?<!\d)1[3-9]\d{9}(?!\d)"), "[REDACTED_PHONE]"),
    (re.compile(r"(?i)(bearer\s+)[A-Za-z0-9._~+/=-]+"), r"\1[REDACTED_TOKEN]"),
    (re.compile(r"(?i)(api[_-]?key\s*[:=]\s*)[^\s,;]+"), r"\1[REDACTED_SECRET]"),
)

_SENSITIVE_KEYS = {
    "authorization",
    "password",
    "secret",
    "token",
    "apiKey",
    "api_key",
    "bankAccount",
    "bank_account",
    "idCard",
    "id_card",
}


def redact_text(value: str) -> str:
    redacted = value
    for pattern, replacement in _PATTERNS:
        redacted = pattern.sub(replacement, redacted)
    return redacted


def minimize_state_data(value: Any) -> Any:
    if isinstance(value, dict):
        minimized: dict[str, Any] = {}
        for key, item in value.items():
            if key in _SENSITIVE_KEYS:
                minimized[key] = "[REDACTED]"
            else:
                minimized[key] = minimize_state_data(item)
        return minimized
    if isinstance(value, list):
        return [minimize_state_data(item) for item in value]
    if isinstance(value, str):
        return redact_text(value)
    return value
