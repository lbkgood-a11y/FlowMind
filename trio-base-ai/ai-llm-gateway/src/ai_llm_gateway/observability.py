from prometheus_client import Counter, Histogram

LLM_CACHE_REQUESTS = Counter(
    "triobase_llm_cache_requests_total",
    "LLM cache decisions",
    ("outcome",),
)
LLM_STREAMS = Counter(
    "triobase_llm_streams_total",
    "LLM stream outcomes",
    ("model", "status"),
)
LLM_FIRST_TOKEN_SECONDS = Histogram(
    "triobase_llm_first_token_seconds",
    "Time from provider stream start to first content token",
    ("model",),
)
LLM_STREAM_DURATION_SECONDS = Histogram(
    "triobase_llm_stream_duration_seconds",
    "LLM stream duration",
    ("model",),
)
LLM_OUTPUT_CHARACTERS = Counter(
    "triobase_llm_output_characters_total",
    "Streamed output characters (token estimation input)",
    ("model",),
)
