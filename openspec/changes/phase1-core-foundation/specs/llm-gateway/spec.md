## ADDED Requirements

### Requirement: Multi-Provider Model Routing
The LLM Gateway SHALL route chat completion requests to the configured LLM provider based on a `model` parameter, using LiteLLM as the unified abstraction layer. Supported providers MUST include OpenAI and at least one domestic Chinese LLM vendor.

#### Scenario: Route to OpenAI
- **WHEN** a request specifies `model: "openai/gpt-4o"`
- **THEN** the gateway routes the request to OpenAI via LiteLLM and streams the response back as SSE

#### Scenario: Route to domestic provider
- **WHEN** a request specifies `model: "deepseek/deepseek-chat"`
- **THEN** the gateway routes to DeepSeek via LiteLLM (OpenAI-compatible API) with the configured API key

#### Scenario: Unknown model
- **WHEN** a request specifies a model name not in the configured provider list
- **THEN** the gateway returns HTTP 400 with `{"error": "UNSUPPORTED_MODEL", "available": [...]}`

### Requirement: Semantic Cache
The LLM Gateway SHALL check GPTCache for semantically similar previous responses before calling the LLM provider. A cache hit MUST return the cached response immediately without consuming LLM tokens.

#### Scenario: Cache hit
- **WHEN** a user sends a prompt semantically similar (similarity >= 0.85) to a previously cached prompt within TTL
- **THEN** the gateway returns the cached response immediately, logs the cache hit, and does NOT call the LLM provider

#### Scenario: Cache miss
- **WHEN** a user sends a prompt with no similar match in cache
- **THEN** the gateway calls the LLM provider, stores the prompt-response pair in GPTCache, and logs the cache miss

### Requirement: Prompt Logging and Cost Tracking
The LLM Gateway SHALL log every LLM request with: timestamp, traceId, model, prompt (first 500 chars), response (first 500 chars), token usage, and estimated cost. Logs MUST be queryable.

#### Scenario: Successful request logging
- **WHEN** an LLM request completes (cache hit or provider call)
- **THEN** the gateway writes a log entry containing `trace_id`, `model`, `prompt_preview`, `response_preview`, `tokens_used`, `cost_estimate`, `cache_hit` boolean, and `latency_ms`

#### Scenario: TraceId propagation
- **WHEN** the API gateway forwards a request with header `X-B3-TraceId`
- **THEN** the LLM gateway extracts the TraceId and includes it in the log entry, ensuring end-to-end traceability per 铁律 8

### Requirement: Data Masking Second Pass
The LLM Gateway SHALL perform a secondary scan of outgoing prompt content for sensitive data patterns (phone numbers, ID cards, financial keys) as the second line of defense per 铁律 2. Detected sensitive data MUST be redacted before the prompt reaches the LLM provider.

#### Scenario: Phone number detected
- **WHEN** an outgoing prompt contains a Chinese mobile number pattern (1[3-9]\d{9})
- **THEN** the gateway replaces it with `[REDACTED_PHONE]` before sending to the LLM provider and logs the redaction event

#### Scenario: ID card number detected
- **WHEN** an outgoing prompt contains an 18-digit Chinese ID card pattern
- **THEN** the gateway replaces it with `[REDACTED_ID_CARD]` and logs the redaction event

#### Scenario: Clean prompt passes through
- **WHEN** an outgoing prompt contains no sensitive data patterns
- **THEN** the prompt passes through unmodified to the LLM provider

### Requirement: SSE Streaming Response
The LLM Gateway SHALL stream LLM responses using Server-Sent Events (SSE) with `text/event-stream` content type, per 铁律 9. No blocking wait-then-JSON pattern is permitted.

#### Scenario: Streaming response
- **WHEN** a chat completion request is received
- **THEN** the gateway returns `Content-Type: text/event-stream` and streams `data: {...}` chunks as they arrive from the LLM provider

#### Scenario: Client disconnect
- **WHEN** the client disconnects mid-stream
- **THEN** the gateway cancels the upstream LLM call and logs a partial completion event

### Requirement: Health and Model List
The LLM Gateway SHALL expose a health check endpoint and a model list endpoint for operational visibility.

#### Scenario: Health check
- **WHEN** `GET /health` is called
- **THEN** the gateway returns `{"status": "healthy", "providers": {"openai": "ok", "qwen": "ok"}}`

#### Scenario: List available models
- **WHEN** `GET /api/v1/ai/models` is called
- **THEN** the gateway returns the list of configured models with their provider, max tokens, and cost per 1K tokens
