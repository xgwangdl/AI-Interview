/**
 * L2 实时通道的客户端。
 *
 * 只负责"连上、发帧、发指令、收消息"；播放与 UI 状态留给视图。
 * 连不上或握手 401 时抛错，视图据此退回 L1（REST）。
 */
export class LanguageStream {
  constructor({ onMessage, onClose } = {}) {
    this.onMessage = onMessage;
    this.onClose = onClose;
    this.socket = null;
    this.ready = false;
  }

  get connected() {
    return Boolean(this.socket && this.socket.readyState === WebSocket.OPEN && this.ready);
  }

  connect() {
    const token = localStorage.getItem('token') || '';
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const url = `${protocol}://${window.location.host}/ws/language?token=${encodeURIComponent(token)}`;
    return new Promise((resolve, reject) => {
      const socket = new WebSocket(url);
      socket.binaryType = 'arraybuffer';
      this.socket = socket;
      const timer = setTimeout(() => {
        socket.close();
        reject(new Error('实时通道连接超时'));
      }, 8000);
      socket.onmessage = (event) => {
        let message;
        try {
          message = JSON.parse(event.data);
        } catch {
          return;
        }
        if (message.type === 'ready') {
          this.ready = true;
          clearTimeout(timer);
          resolve();
        }
        this.onMessage?.(message);
      };
      socket.onerror = () => {
        clearTimeout(timer);
        reject(new Error('实时通道不可用'));
      };
      socket.onclose = () => {
        this.ready = false;
        this.onClose?.();
      };
    });
  }

  start(sessionId) {
    this.send({ type: 'start', sessionId });
  }

  sendFrame(arrayBuffer) {
    if (this.connected && this.socket.readyState === WebSocket.OPEN) this.socket.send(arrayBuffer);
  }

  commit() {
    this.send({ type: 'commit' });
  }

  cancel() {
    this.send({ type: 'cancel' });
  }

  send(payload) {
    if (this.socket?.readyState === WebSocket.OPEN) this.socket.send(JSON.stringify(payload));
  }

  close() {
    this.ready = false;
    try { this.socket?.close(); } catch { /* 已经关了 */ }
    this.socket = null;
  }
}
