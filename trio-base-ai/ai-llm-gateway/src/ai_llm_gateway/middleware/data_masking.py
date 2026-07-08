from __future__ import annotations

import re
from typing import List, Tuple


PHONE_PATTERN = re.compile(r"1[3-9]\d{9}")
ID_CARD_PATTERN = re.compile(r"\d{17}[\dXx]")
BANK_CARD_PATTERN = re.compile(r"\d{16,19}")
FINANCE_KEY_PATTERN = re.compile(
    r"(secret|SECRET|api_key|API_KEY|private_key|PRIVATE_KEY|access_token|ACCESS_TOKEN)\s*[:=]\s*[\"']?[a-zA-Z0-9_-]{20,}"
)


def scan_and_redact(content: str) -> Tuple[str, List[str]]:
    redactions: List[str] = []
    result = content

    if PHONE_PATTERN.search(result):
        redactions.append("phone_number")
        result = PHONE_PATTERN.sub("[REDACTED_PHONE]", result)

    if ID_CARD_PATTERN.search(result):
        redactions.append("id_card")
        result = ID_CARD_PATTERN.sub("[REDACTED_ID_CARD]", result)

    if BANK_CARD_PATTERN.search(result):
        redactions.append("bank_card")
        result = BANK_CARD_PATTERN.sub("[REDACTED_BANK_CARD]", result)

    if FINANCE_KEY_PATTERN.search(result):
        redactions.append("financial_key")
        result = FINANCE_KEY_PATTERN.sub("[REDACTED_KEY]", result)

    return result, redactions
