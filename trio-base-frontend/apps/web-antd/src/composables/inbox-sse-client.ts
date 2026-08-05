export type InboxStreamState =
  | 'connected'
  | 'connecting'
  | 'polling'
  | 'stopped';

export interface InboxSseClientOptions {
  fetch?: typeof globalThis.fetch;
  getAccessToken: () => null | string;
  onStateChange?: (state: InboxStreamState) => void;
  pollIntervalMs?: number;
  reconcile: () => Promise<void>;
  reconnectBaseMs?: number;
  reconnectMaxMs?: number;
  streamUrl: string;
}

/**
 * 消费用户级 SSE 失效信号，并通过权威接口对账。
 *
 * 流载荷不得直接写入未读数或最近列表；断线期间可能丢事件，因此首次连接、
 * 每次业务事件和每次重连都会执行 reconcile。连续失败时轮询只作为对账降级。
 */
export class InboxSseClient {
  private abortController?: AbortController;
  private failures = 0;
  private pollTimer?: ReturnType<typeof setInterval>;
  private readonly recentEventIds = new Set<string>();
  private reconnectTimer?: ReturnType<typeof setTimeout>;
  private running = false;

  constructor(private readonly options: InboxSseClientOptions) {}

  start() {
    if (this.running) return;
    this.running = true;
    void this.connect();
  }

  stop() {
    this.running = false;
    this.abortController?.abort();
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    if (this.pollTimer) clearInterval(this.pollTimer);
    this.reconnectTimer = undefined;
    this.pollTimer = undefined;
    this.options.onStateChange?.('stopped');
  }

  private async connect() {
    if (!this.running) return;
    this.options.onStateChange?.('connecting');
    await this.safeReconcile();

    const token = this.options.getAccessToken();
    if (!token) {
      this.scheduleReconnect();
      return;
    }

    this.abortController = new AbortController();
    try {
      const fetchImpl = this.options.fetch ?? globalThis.fetch;
      const response = await fetchImpl(this.options.streamUrl, {
        headers: {
          Accept: 'text/event-stream',
          Authorization: `Bearer ${token}`,
        },
        signal: this.abortController.signal,
      });
      if (!response.ok || !response.body) {
        throw new Error(`Inbox SSE rejected with status ${response.status}`);
      }

      this.failures = 0;
      this.stopPolling();
      this.options.onStateChange?.('connected');
      await this.consume(response.body);
    } catch (error) {
      if (!this.running || this.abortController.signal.aborted) return;
      // 连接失败只触发重连/轮询；不得用不完整的流数据修补本地状态。
      console.warn('Inbox SSE disconnected; scheduling reconciliation.', error);
    }
    this.scheduleReconnect();
  }

  private async consume(stream: ReadableStream<Uint8Array>) {
    const reader = stream.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    while (this.running) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer = (buffer + decoder.decode(value, { stream: true })).replaceAll(
        '\r\n',
        '\n',
      );
      let boundary = buffer.indexOf('\n\n');
      while (boundary >= 0) {
        const frame = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        if (this.shouldReconcile(frame)) await this.safeReconcile();
        boundary = buffer.indexOf('\n\n');
      }
    }
  }

  private async safeReconcile() {
    try {
      await this.options.reconcile();
    } catch (error) {
      // 对账失败保留上一次权威快照，等待下个事件、轮询或重连再次查询。
      console.warn('Inbox reconciliation failed.', error);
    }
  }

  private scheduleReconnect() {
    if (!this.running) return;
    this.failures += 1;
    if (this.failures >= 3) this.startPolling();
    const base = this.options.reconnectBaseMs ?? 1_000;
    const maximum = this.options.reconnectMaxMs ?? 30_000;
    const delay = Math.min(maximum, base * 2 ** (this.failures - 1));
    this.reconnectTimer = setTimeout(() => void this.connect(), delay);
  }

  private shouldReconcile(frame: string) {
    const lines = frame.split('\n');
    if (!lines.some((line) => line.trim() === 'event:inbox-change')) {
      return false;
    }
    const eventId = lines
      .find((line) => line.startsWith('id:'))
      ?.slice(3)
      .trim();
    if (!eventId || this.recentEventIds.has(eventId)) return !eventId;

    // SSE 允许至少一次投递；有限去重只抑制重复刷新，权威状态仍由查询接口决定。
    this.recentEventIds.add(eventId);
    if (this.recentEventIds.size > 256) {
      this.recentEventIds.delete(this.recentEventIds.values().next().value!);
    }
    return true;
  }

  private startPolling() {
    if (this.pollTimer) return;
    this.options.onStateChange?.('polling');
    this.pollTimer = setInterval(
      () => void this.safeReconcile(),
      this.options.pollIntervalMs ?? 30_000,
    );
  }

  private stopPolling() {
    if (this.pollTimer) clearInterval(this.pollTimer);
    this.pollTimer = undefined;
  }
}
