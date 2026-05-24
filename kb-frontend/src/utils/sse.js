export class SseClient {
  constructor(url, options = {}) {
    this.url = url;
    this.onToken = options.onToken || (() => {});
    this.onDone = options.onDone || (() => {});
    this.onError = options.onError || (() => {});
    this.es = null;
  }

  connect() {
    this.close();

    const es = new EventSource(this.url);

    es.addEventListener('token', (event) => {
      this.onToken(event.data || '');
    });

    es.addEventListener('done', (event) => {
      try {
        this.onDone(event.data ? JSON.parse(event.data) : []);
      } catch {
        this.onDone([]);
      }
    });

    es.addEventListener('error', () => {
      this.onError('SSE 连接异常');
    });

    this.es = es;
  }

  close() {
    if (this.es) {
      this.es.close();
      this.es = null;
    }
  }
}

export default SseClient;
