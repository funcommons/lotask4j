import { onBeforeUnmount, onMounted, ref } from 'vue'

interface UsePollingOptions {
  interval: number
  predicate?: () => boolean
  /** tab 隐藏时是否暂停轮询（默认 true） */
  pauseWhenHidden?: boolean
  /** tab 重新可见时是否立即触发一次（默认 true） */
  fireOnVisible?: boolean
}

/**
 * setInterval + visibilitychange 暂停的轮询。
 * - tab 隐藏时不触发 fn（节省后端压力）
 * - tab 重新可见时立即 fire 一次（避免等下一个 tick）
 * - predicate 返回 false 时跳过当次（不强制 clearInterval，留给调用方控制条件）
 * - 组件卸载时自动清理 interval + listener
 */
export function usePolling(fn: () => void | Promise<void>, options: UsePollingOptions) {
  const {
    interval,
    predicate,
    pauseWhenHidden = true,
    fireOnVisible = true,
  } = options

  let timer: number | null = null
  const isPaused = ref(false)

  function shouldRun() {
    if (pauseWhenHidden && document.visibilityState !== 'visible') return false
    if (predicate && !predicate()) return false
    return true
  }

  async function tick() {
    if (!shouldRun()) return
    try {
      await fn()
    } catch (err) {
      console.error('[usePolling] tick failed:', err)
    }
  }

  function start() {
    if (timer !== null) return
    timer = window.setInterval(tick, interval)
  }

  function stop() {
    if (timer !== null) {
      clearInterval(timer)
      timer = null
    }
  }

  function onVisibilityChange() {
    const visible = document.visibilityState === 'visible'
    isPaused.value = !visible
    if (visible && fireOnVisible && (!predicate || predicate())) {
      Promise.resolve(fn()).catch((err: unknown) => console.error('[usePolling] fireOnVisible failed:', err))
    }
  }

  onMounted(() => {
    start()
    if (pauseWhenHidden || fireOnVisible) {
      document.addEventListener('visibilitychange', onVisibilityChange)
    }
  })

  onBeforeUnmount(() => {
    stop()
    document.removeEventListener('visibilitychange', onVisibilityChange)
  })

  return { isPaused, start, stop }
}
