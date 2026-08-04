"""LLM 网关的 Prompt 二次脱敏。

API 网关负责拒绝明显敏感请求；本模块在模型调用前再次清洗 Prompt，覆盖服务内拼装、
历史消息和工具结果带入的敏感内容。调用方必须使用返回后的文本，不能继续发送原始内容。
"""

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
    """替换已识别的敏感片段并返回命中的类别。

    返回的类别仅用于审计和指标，禁止记录被替换的原始值。规则按长标识符优先执行，
    防止手机号模式截取身份证或银行卡号的一部分而降低脱敏强度。
    """
    redactions: List[str] = []
    result = content

    # 先处理最长数字标识；否则手机号规则可能吞掉身份证片段，使更强规则无法命中。
    if ID_CARD_PATTERN.search(result):
        redactions.append("id_card")
        result = ID_CARD_PATTERN.sub("[REDACTED_ID_CARD]", result)

    if BANK_CARD_PATTERN.search(result):
        redactions.append("bank_card")
        result = BANK_CARD_PATTERN.sub("[REDACTED_BANK_CARD]", result)

    if PHONE_PATTERN.search(result):
        redactions.append("phone_number")
        result = PHONE_PATTERN.sub("[REDACTED_PHONE]", result)

    if FINANCE_KEY_PATTERN.search(result):
        redactions.append("financial_key")
        result = FINANCE_KEY_PATTERN.sub("[REDACTED_KEY]", result)

    return result, redactions
