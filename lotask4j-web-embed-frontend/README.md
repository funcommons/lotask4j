# ASTS Web Embed 前端

基于 Vite + Vue3 + Vue Router 实现的 Web Embed 组件库。

## 组件

- `task-list` - 任务列表
- `task-detail` - 任务详情
- `task-card` - 任务卡片（轮询刷新）

## 开发

```bash
npm install
npm run dev        # 启动开发服务器 (http://localhost:9082)
npm run build      # 构建生产版本
```

## 后端

本项目是 Web Embed 功能的前端部分，需要配合后端 `/web-embed/*` 接口使用。

详见 `documents/前端组件方案2.md`。

## 嵌入方式

```html
<!-- 开放模式 -->
<iframe src="https://your-domain.com/web-embed/task-list"
        width="100%" height="800px" frameborder="0"></iframe>

<!-- 鉴权模式 -->
<iframe src="https://your-domain.com/web-embed/task-list?accessKey=biz-app-001"
        width="100%" height="800px" frameborder="0"></iframe>

<!-- 任务详情 -->
<iframe src="https://your-domain.com/web-embed/task-detail?accessKey=xxx&taskId=YeirYkxHuQ"
        width="100%" height="600px" frameborder="0"></iframe>
```

## Nginx 部署

```nginx
location /web-embed/ {
    alias /opt/asts/web-embed-frontend/dist/;
    try_files $uri $uri/ /index.html;  # SPA history fallback
}
```

## 高度自适应

组件通过 `postMessage` 实时向父页面发送高度：

```javascript
window.parent.postMessage({ type: 'ASTS:RESIZE', height: 800 }, '*')
```

父页面监听：

```javascript
window.addEventListener('message', (e) => {
  if (e.data?.type === 'ASTS:RESIZE') {
    iframe.style.height = e.data.height + 'px'
  }
})
```
