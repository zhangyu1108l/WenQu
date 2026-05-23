/**
 * SSE 与普通 HTTP 不同：SSE 会保持长连接，由服务端主动推送事件，
 * 适合流式生成这类 token 持续返回场景。
 */
export class SseClient {
  /**
   * 统一封装 EventSource，而不是在页面中直接使用：
   * 这样可以集中错误处理，也便于后续全局管理连接。
   *
   * @param {string} url SSE 接口地址。
   * @param {Object} options 回调配置。
   * @param {Function} options.onToken token 回调，每个字符触发一次。
   * @param {Function} options.onDone 完成回调，携带 sourceChunks。
   * @param {Function} options.onError 错误回调。
   */
  constructor(url, options = {}) {
    this.url = url;
    this.onToken = options.onToken || (() => {});
    this.onDone = options.onDone || (() => {});
    this.onError = options.onError || (() => {});
    this.es = null;
  }

  connect() {
    // withCredentials 用于携带 cookie；当前系统主要使用 Header 认证，这里保留以兼容部分部署场景。
    const es = new EventSource(this.url, { withCredentials: true });

    es.addEventListener('token', (e) => {
      this.onToken(e.data);
    });

    es.addEventListener('done', (e) => {
      this.onDone(JSON.parse(e.data));
    });

    es.addEventListener('error', (e) => {
      this.onError(e.data || '连接异常');
    });

    this.es = es;
  }

  close() {
    if (!this.es) {
      return;
    }

    // 组件卸载时必须调用 close()，否则 SSE 长连接不会断开，会造成资源泄漏。
    this.es.close();
    this.es = null;
  }
}

export default SseClient;
