import os
from dataclasses import dataclass


@dataclass
class Config:
    database_url: str = os.getenv("DATABASE_URL", "postgresql://triobase:triobase123@localhost:5432/triobase")
    embedding_model: str = os.getenv("EMBEDDING_MODEL", "all-MiniLM-L6-v2")
    embedding_dim: int = 384


config = Config()
