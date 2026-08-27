/**
 * postMessage 工具 — 与父页面（业务方）通信
 * 移植自 lotask4j-web-embed-frontend/src/utils/postMessage.ts
 *
 * 主要场景:
 *  - embed 页 ResizeObserver 监听 body 高度, 上报 ASTS:RESIZE
 *  - 业务方 (parent) 调整 iframe 高度以避免内部滚动条
 */

export function sendToParent(data: unknown): void {
  if (window.parent !== window) {
    window.parent.postMessage(data as unknown as Record<string, unknown>, '*')
  }
}

export function onParentMessage(handler: (data: unknown) => void): () => void {
  const listener = (e: MessageEvent) => handler(e.data)
  window.addEventListener('message', listener)
  return () => window.removeEventListener('message', listener)
}

/**
 * 监听 body 高度变化并向 parent 上报 (embed 模式)
 * - 使用 ResizeObserver 监听 document.body
 * - 初始 100ms 后先发一帧, 避免 iframe 高度为 0
 * - 返回 disconnect 函数 (在 onUnmounted 调用)
 */
export function reportHeightToParent(): () => void {
  if (typeof window === 'undefined' || window.parent === window) {
    return () => { /* noop */ }
  }
  const send = () => {
    const height = document.body.scrollHeight
    window.parent.postMessage({ type: 'ASTS:RESIZE', height }, '*')
  }
  const observer = new ResizeObserver(send)
  observer.observe(document.body)
  const t = window.setTimeout(send, 100)
  return () => {
    observer.disconnect()
    window.clearTimeout(t)
  }
}