import os
from dataclasses import dataclass, field
from typing import Dict, List


@dataclass
class LLMProvider:
    name: str
    api_key: str
    base_url: str = ""
    models: List[str] = field(default_factory=list)


@dataclass
class Config:
    redis_url: str = os.getenv("REDIS_URL", "redis://localhost:6379/0")
    jwt_secret: str = os.getenv("JWT_SECRET", "triobase-dev-secret-key-min-32-chars!!")

    cache_ttl: int = int(os.getenv("CACHE_TTL", "3600"))
    cache_similarity_threshold: float = float(os.getenv("CACHE_SIMILARITY_THRESHOLD", "0.85"))
    cache_enabled: bool = os.getenv("LLM_CACHE_ENABLED", "false").lower() == "true"

    llm_providers: Dict[str, LLMProvider] = field(default_factory=dict)

    def __post_init__(self):
        openai_key = os.getenv("OPENAI_API_KEY", "")
        if openai_key:
            self.llm_providers["openai"] = LLMProvider(
                name="openai",
                api_key=openai_key,
                models=["gpt-4o", "gpt-4o-mini"],
            )

        deepseek_key = os.getenv("DEEPSEEK_API_KEY", "")
        if deepseek_key:
            self.llm_providers["deepseek"] = LLMProvider(
                name="deepseek",
                api_key=deepseek_key,
                base_url="https://api.deepseek.com/v1",
                models=["deepseek-chat"],
            )


config = Config()
