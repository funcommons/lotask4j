<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

// 上报高度给父页面
const sendHeight = () => {
  const height = document.body.scrollHeight
  window.parent.postMessage({ type: 'ASTS:RESIZE', height }, '*')
}

let observer: ResizeObserver | null = null

onMounted(() => {
  // 监听父页面消息
  window.addEventListener('message', (e) => {
    // 可以处理来自父页面的指令
    console.log('[Web Embed] 收到父页面消息:', e.data)
  })

  // 启动高度监听
  observer = new ResizeObserver(sendHeight)
  observer.observe(document.body)

  // 初始上报
  setTimeout(sendHeight, 100)
})

onUnmounted(() => {
  observer?.disconnect()
})
</script>

<template>
  <router-view />
</template>
