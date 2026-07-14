/**
 * postMessage 工具
 * 与父页面（业务方）通信
 */

export function sendToParent(data: any) {
  if (window.parent !== window) {
    window.parent.postMessage(data, '*')
  }
}

export function onParentMessage(handler: (data: any) => void) {
  window.addEventListener('message', (e) => {
    handler(e.data)
  })
}
