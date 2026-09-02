---
layout: home

hero:
  name: lotask4j
  text: 异步慢任务服务 (ASTS)
  tagline: 分布式异步任务处理平台 — 执行耗时 >10 秒的业务逻辑, 提供实时进度、任务取消、可靠回调、多租户隔离与可视化管理后台
  actions:
    - theme: brand
      text: 快速入门 → 提交第一个任务
      link: /quick-start/first-task
    - theme: alt
      text: 文档目录
      link: /README
    - theme: alt
      text: 错误码
      link: /dev-guide/error-codes

features:
  - icon: ⚡
    title: 异步解耦
    details: 业务接口立即返回任务 ID, 耗时逻辑后台执行; 幂等键防重复提交, 背压水位保护队列
  - icon: 📊
    title: 实时进度
    details: 分步进度 + 权重折算全局进度; Fencing Token + 版本 CAS 双保险, 租约过期自动回收重派
  - icon: 🔔
    title: 可靠回调
    details: Outbox 模式必达投递 (指数退避 8 次) + HMAC 签名三头防伪造, 高敏动作 verify-then-act
  - icon: 🏢
    title: 多租户隔离
    details: 三域守卫 + 数据面 tenant_id 全链路收口 + PG RLS 兜底; 密钥 AES-GCM 落库, 24h 轮换宽限
  - icon: 🧩
    title: 嵌入组件
    details: task-list / task-detail / task-card 三种组件一行 iframe 免密嵌入, 短期 token 自动签发
  - icon: 📈
    title: 可观测
    details: 执行事件审计 append-only, Micrometer 指标 (lotask4j.*) + Grafana 看板, 管理台可视化排障
---
