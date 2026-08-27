#!/usr/bin/env node
/**
 * sync-embed — 把 embed 构建产物拷入 backend 静态目录
 *
 * 用法: pnpm sync-embed   (先 pnpm build:embed)
 *
 * 目标: ../lotask4j-backend/src/main/resources/static/web-embed/
 * 后端 Spring Boot 以 /web-embed/** 直接 serve (WebEmbedController 302 到
 * /web-embed/index.html?component=xxx&taskId=xxx)。
 */
import { cpSync, rmSync, existsSync, mkdirSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const src = resolve(here, '../dist-embed')
const dest = resolve(here, '../../lotask4j-backend/src/main/resources/static/web-embed')

if (!existsSync(src)) {
  console.error('[sync-embed] dist-embed/ 不存在 — 先运行 pnpm build:embed')
  process.exit(1)
}

rmSync(dest, { recursive: true, force: true })
mkdirSync(dest, { recursive: true })
cpSync(src, dest, { recursive: true })
console.log(`[sync-embed] ${src} → ${dest}`)
console.log('[sync-embed] 完成 — backend 打包时将随 JAR 发布 (记得 mvn package 前执行)')
