#!/usr/bin/env node
/**
 * sync-console — 把控制台构建产物拷入 starter 静态目录 (issue #4)
 *
 * 用法: pnpm sync-console   (先 pnpm build)
 *
 * 目标: ../lotask4j-server-spring-boot-starter/src/main/resources/static/
 * (index.html + assets/) — starter JAR 以 / 直接 serve 控制台 (Spring Boot
 * 静态资源默认路径)。该目录 gitignore, 提交前/打包前需先构建同步。
 */
import { cpSync, rmSync, existsSync, mkdirSync } from 'node:fs'
import { resolve, dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const src = resolve(here, '../dist')
const dest = resolve(here, '../../lotask4j-server-spring-boot-starter/src/main/resources/static')

if (!existsSync(src)) {
  console.error('[sync-console] dist/ 不存在 — 先运行 pnpm build')
  process.exit(1)
}

// 只清控制台自身产物 (index.html + assets/), 保留同目录的 web-embed/ (sync-embed 负责)
for (const entry of ['index.html', 'assets']) {
  rmSync(join(dest, entry), { recursive: true, force: true })
}
mkdirSync(dest, { recursive: true })
cpSync(src, dest, { recursive: true })
console.log(`[sync-console] ${src} → ${dest}`)
console.log('[sync-console] 完成 — starter 打包时将随 JAR 发布 (记得 mvn package 前执行)')
