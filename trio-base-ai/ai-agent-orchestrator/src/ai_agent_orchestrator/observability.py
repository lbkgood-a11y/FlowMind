from __future__ import annotations

from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from prometheus_client import Counter, Histogram

from ai_agent_orchestrator.config import Settings

TRACER = trace.get_tracer("triobase.ai.agent")

AGENT_RUNS = Counter(
    "triobase_agent_runs_total",
    "Agent runs by terminal outcome",
    ("graph_id", "status"),
)
AGENT_EVENTS = Counter(
    "triobase_agent_events_total",
    "Agent events by type",
    ("event_type",),
)
AGENT_RUN_DURATION = Histogram(
    "triobase_agent_run_duration_seconds",
    "Agent run execution duration excluding human wait time",
    ("graph_id",),
)
AGENT_NODE_UPDATES = Counter(
    "triobase_agent_node_updates_total",
    "LangGraph node updates",
    ("graph_id", "node"),
)
AGENT_INTERRUPTS = Counter(
    "triobase_agent_interrupts_total",
    "Agent interrupts by kind",
    ("graph_id", "kind"),
)
AGENT_CANCELLATIONS = Counter(
    "triobase_agent_cancellations_total",
    "Agent run cancellations",
    ("graph_id",),
)
AGENT_TOOL_CALLS = Counter(
    "triobase_agent_tool_calls_total",
    "Governed tool calls by outcome",
    ("tool", "kind", "status"),
)
AGENT_TOOL_RETRIES = Counter(
    "triobase_agent_tool_retries_total",
    "Governed tool retry attempts",
    ("tool",),
)
AGENT_TOOL_DURATION = Histogram(
    "triobase_agent_tool_duration_seconds",
    "Governed tool latency",
    ("tool", "kind"),
)
AGENT_MODEL_CALLS = Counter(
    "triobase_agent_model_calls_total",
    "Reserved model calls by graph",
    ("graph_id",),
)
AGENT_MODEL_TOKENS = Counter(
    "triobase_agent_model_tokens_total",
    "Reserved model tokens by direction",
    ("graph_id", "direction"),
)
AGENT_MODEL_COST_USD = Counter(
    "triobase_agent_model_estimated_cost_usd_total",
    "Estimated model cost in USD",
    ("graph_id",),
)
AGENT_CANDIDATE_VALIDATIONS = Counter(
    "triobase_agent_candidate_validations_total",
    "Action candidate validation outcomes",
    ("action_type", "status"),
)
AGENT_ACTION_OUTCOMES = Counter(
    "triobase_agent_action_outcomes_total",
    "Correlated Global Action outcomes",
    ("action_type", "status"),
)


def configure_telemetry(settings: Settings) -> None:
    global TRACER
    if not settings.otlp_endpoint:
        TRACER = trace.get_tracer("triobase.ai.agent")
        return
    provider = TracerProvider(
        resource=Resource.create(
            {
                "service.name": settings.service_name,
                "deployment.environment": settings.environment,
            }
        )
    )
    provider.add_span_processor(
        BatchSpanProcessor(OTLPSpanExporter(endpoint=settings.otlp_endpoint))
    )
    trace.set_tracer_provider(provider)
    TRACER = trace.get_tracer("triobase.ai.agent")
