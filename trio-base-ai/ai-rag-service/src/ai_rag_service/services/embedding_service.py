from __future__ import annotations

import logging
from typing import List

from sentence_transformers import SentenceTransformer

from ..config import config

logger = logging.getLogger(__name__)

_model: SentenceTransformer | None = None


def get_model() -> SentenceTransformer:
    global _model
    if _model is None:
        logger.info("Loading embedding model: %s ...", config.embedding_model)
        _model = SentenceTransformer(config.embedding_model)
        logger.info("Embedding model loaded: %s (%dd)", config.embedding_model, config.embedding_dim)
    return _model


def embed(text: str) -> List[float]:
    model = get_model()
    vector = model.encode(text, normalize_embeddings=True)
    return vector.tolist()


def embed_batch(texts: List[str]) -> List[List[float]]:
    model = get_model()
    vectors = model.encode(texts, normalize_embeddings=True)
    return vectors.tolist()
