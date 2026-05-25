export class SseClient {
  constructor(url, options = {}) {
    this.url = url;
    this.method = options.method || 'POST';
    this.headers = options.headers || {};
    this.body = options.body;
    this.onToken = options.onToken || (() => {});
    this.onDone = options.onDone || (() => {});
    this.onError = options.onError || (() => {});
    this.controller = null;
    this.closed = false;
    this.done = false;
  }

  async connect() {
    this.close();

    this.closed = false;
    this.done = false;
    this.controller = new AbortController();

    try {
      const response = await fetch(this.url, {
        method: this.method,
        headers: {
          Accept: 'text/event-stream',
          'Cache-Control': 'no-cache',
          ...this.headers
        },
        body: this.body,
        signal: this.controller.signal
      });

      if (!response.ok) {
        throw new Error(`SSE request failed: ${response.status}`);
      }

      if (!response.body) {
        throw new Error('SSE response body is empty');
      }

      await this.readStream(response.body);
    } catch (error) {
      if (this.closed || error?.name === 'AbortError') {
        return;
      }

      this.onError(error?.message || 'SSE connection failed');
    }
  }

  close() {
    this.closed = true;

    if (this.controller) {
      this.controller.abort();
      this.controller = null;
    }
  }

  async readStream(body) {
    const reader = body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';

    while (!this.closed) {
      const { done, value } = await reader.read();

      if (done) {
        break;
      }

      buffer += decoder.decode(value, { stream: true });
      buffer = this.consumeFrames(buffer);
    }

    buffer += decoder.decode();

    if (buffer.trim()) {
      this.consumeFrame(buffer);
    }

    if (!this.closed && !this.done) {
      this.onError('SSE connection closed');
    }
  }

  consumeFrames(buffer) {
    let normalized = buffer.replace(/\r\n/g, '\n');
    let separatorIndex = normalized.indexOf('\n\n');

    while (separatorIndex !== -1) {
      const frame = normalized.slice(0, separatorIndex);
      this.consumeFrame(frame);
      normalized = normalized.slice(separatorIndex + 2);
      separatorIndex = normalized.indexOf('\n\n');
    }

    return normalized;
  }

  consumeFrame(frame) {
    const lines = frame.split('\n');
    let eventName = 'message';
    const dataLines = [];

    for (const line of lines) {
      if (!line || line.startsWith(':')) {
        continue;
      }

      const colonIndex = line.indexOf(':');
      const field = colonIndex === -1 ? line : line.slice(0, colonIndex);
      const value = colonIndex === -1 ? '' : line.slice(colonIndex + 1).replace(/^ /, '');

      if (field === 'event') {
        eventName = value || 'message';
      }

      if (field === 'data') {
        dataLines.push(value);
      }
    }

    const data = dataLines.join('\n');

    if (eventName === 'token') {
      this.onToken(data);
      return;
    }

    if (eventName === 'done') {
      this.done = true;
      try {
        this.onDone(data ? JSON.parse(data) : []);
      } catch {
        this.onDone([]);
      }
      return;
    }

    if (eventName === 'error') {
      this.done = true;
      this.onError(data || 'SSE connection failed');
    }
  }
}

export default SseClient;
