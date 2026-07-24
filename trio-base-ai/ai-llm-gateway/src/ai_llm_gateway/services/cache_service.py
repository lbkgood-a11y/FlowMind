from __future__ import annotations

import hashlib
import json
import logging
from dataclasses import dataclass
from typing import Optional

import redis.asyncio as redis

from ..config import config

logger = logging.getLogger(__name__)

_redis_client: Optional[redis.Redis] = None


@dataclass(frozen=True, slots=True)
class CacheScope:
    tenant_id: str
    actor_id: str
    authorization_version: str = "current"
    knowledge_version: str = "none"

    @property
    def complete(self) -> bool:
        return bool(self.tenant_id and self.actor_id)


async def _get_redis() -> redis.Redis | None:
    global _redis_client
    if not config.cache_enabled:
        return None
    if _redis_client is None:
        _redis_client = redis.Redis.from_url(config.redis_url, decode_responses=True)
        try:
            await _redis_client.ping()
            logger.info("Cache: Redis connected at %s", config.redis_url)
        except Exception as error:
            logger.warning("Cache unavailable; caching disabled: %s", type(error).__name__)
            await _redis_client.aclose()
            _redis_client = None
    return _redis_client


def _cache_key(model: str, prompt: str, scope: CacheScope) -> str:
    combined = "|".join(
        (
            scope.tenant_id,
            scope.actor_id,
            scope.authorization_version,
            scope.knowledge_version,
            model,
            prompt,
        )
    )
    return f"llm:cache:{hashlib.sha256(combined.encode()).hexdigest()}"


async def cache_lookup(
    model: str,
    prompt: str,
    scope: CacheScope,
    allowed: bool,
) -> Optional[str]:
    if not allowed or not scope.complete:
        return None
    client = await _get_redis()
    if client is None:
        return None
    key = _cache_key(model, prompt, scope)
    value = await client.get(key)
    if value:
        try:
            data = json.loads(value)
            logger.info("Cache HIT for model=%s", model)
            return data.get("response")
        except json.JSONDecodeError:
            pass
    logger.debug("Cache MISS for model=%s", model)
    return None


async def cache_store(
    model: str,
    prompt: str,
    response: str,
    scope: CacheScope,
    allowed: bool,
) -> None:
    if not allowed or not scope.complete:
        return
    client = await _get_redis()
    if client is None:
        return
    key = _cache_key(model, prompt, scope)
    data = json.dumps({"model": model, "prompt_hash": key, "response": response})
    await client.setex(key, config.cache_ttl, data)
    logger.debug("Cache STORE key=%s ttl=%s", key, config.cache_ttl)
