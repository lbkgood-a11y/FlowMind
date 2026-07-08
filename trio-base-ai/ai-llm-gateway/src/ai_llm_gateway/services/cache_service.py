from __future__ import annotations

import hashlib
import json
import logging
from typing import Optional

import redis

from ..config import config

logger = logging.getLogger(__name__)

_redis_client: Optional[redis.Redis] = None


def _get_redis() -> redis.Redis:
    global _redis_client
    if _redis_client is None:
        _redis_client = redis.Redis.from_url(config.redis_url, decode_responses=True)
        try:
            _redis_client.ping()
            logger.info("Cache: Redis connected at %s", config.redis_url)
        except Exception as e:
            logger.warning("Cache: Redis unavailable (%s), caching disabled", e)
            _redis_client = None
    return _redis_client


def _cache_key(model: str, prompt: str) -> str:
    combined = f"{model}|{prompt}"
    return f"llm:cache:{hashlib.sha256(combined.encode()).hexdigest()[:16]}"


def cache_lookup(model: str, prompt: str) -> Optional[str]:
    r = _get_redis()
    if r is None:
        return None
    key = _cache_key(model, prompt)
    value = r.get(key)
    if value:
        try:
            data = json.loads(value)
            logger.info("Cache HIT for model=%s", model)
            return data.get("response")
        except json.JSONDecodeError:
            pass
    logger.debug("Cache MISS for model=%s", model)
    return None


def cache_store(model: str, prompt: str, response: str) -> None:
    r = _get_redis()
    if r is None:
        return
    key = _cache_key(model, prompt)
    data = json.dumps({"model": model, "prompt_hash": key, "response": response})
    r.setex(key, config.cache_ttl, data)
    logger.debug("Cache STORE key=%s ttl=%s", key, config.cache_ttl)
