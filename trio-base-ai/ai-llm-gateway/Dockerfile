FROM python:3.12-slim

WORKDIR /app

RUN pip install poetry==2.4.1

COPY pyproject.toml poetry.lock* ./
RUN poetry config virtualenvs.create false \
    && poetry install --only main --no-interaction --no-ansi --no-root

COPY src/ ./src/

ENV PYTHONPATH=/app/src
EXPOSE 8002

HEALTHCHECK --interval=15s --timeout=3s --retries=5 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8002/health', timeout=2)"

CMD ["python", "-m", "uvicorn", "ai_llm_gateway.main:app", "--host", "0.0.0.0", "--port", "8002"]
