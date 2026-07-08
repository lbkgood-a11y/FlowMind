from __future__ import annotations

from typing import List, Optional

from pydantic import BaseModel, Field


class ChatMessage(BaseModel):
    role: str
    content: str


class ChatRequest(BaseModel):
    model: str = Field(description="Provider/model name, e.g. openai/gpt-4o, deepseek/deepseek-chat")
    messages: List[ChatMessage]
    temperature: float = Field(default=0.7, ge=0.0, le=2.0)
    max_tokens: int = Field(default=2048, ge=1, le=32768)
    stream: bool = Field(default=True)


class ChatResponse(BaseModel):
    id: str
    model: str
    content: str
    cached: bool = False


class ModelInfo(BaseModel):
    id: str
    provider: str
    max_tokens: int
    cost_per_1k_tokens: float
