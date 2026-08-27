/**
 * lotask4j 页面文案 — guides 域 (中文)
 * key 空间: lotask.guides.* 与 lotask.demo.*
 *
 * 含 UserGuide / ClientGuide / WorkerGuide / DemoSimulator 四组文案
 * (从 lotask4j-admin-frontend locales 移植, 精简掉 benefit4j 相关描述)
 */
export const guides = {
  // —— UserGuide 使用指南 ——
  userGuide: {
    title: '使用指南',
    subtitle: '查看使用文档和示例',
    handbookTitle: 'Wiki 风格使用手册',
    handbookDesc: '全新设计的 Wiki 风格文档，包含完整的客户端集成、Worker 实现示例、API 参考和最佳实践。',
    openHandbook: '打开使用手册',
    clientTitle: '客户端指南',
    clientDesc: '面向业务系统后端开发人员，介绍如何提交任务、查询状态、处理结果。',
    workerTitle: 'Worker 指南',
    workerDesc: '面向 Worker 节点开发人员，介绍如何实现任务执行器、Poll 任���、上报进度。',
    simulatorTitle: '模拟测试',
    simulatorDesc: '提交测试任务并实时观察任务执行状态。',
  },

  // —— ClientGuide 客户端指南 (4 个 tab) ——
  clientGuide: {
    title: '客户端接入指南',
  },
  clientGuideExt: {
    tabs: {
      overview: '概览',
      api: 'API 列表',
      code: '代码示例',
      bestPractices: '最佳实践',
    },
  },

  // —— WorkerGuide Worker 接入指南 (4 个 tab) ——
  workerGuide: {
    title: 'Worker 接入指南',
  },
  workerGuideExt: {
    tabs: {
      overview: '概览',
      api: 'API 列表',
      implementation: '实现指南',
      bestPractices: '最佳实践',
    },
  },

  // —— DemoSimulator 模拟测试 ——
  demoSimulatorExt: {
    title: '模拟测试',
    subtitle: 'Client + Worker 双向模拟器，实时演示任务流转',
    envWarning: '环境提示',
    envWarningDesc: '注意：开发环境可用，生产环境因为有鉴权网关的原因模拟测试不可用',
    control: '模拟器控制面板',
    client: {
      title: 'Client 模拟器',
      taskType: '任务类型',
      submitInterval: '提交间隔（毫秒）',
      start: '启动 Client',
      stop: '停止 Client',
      started: 'Client 模拟器已���动',
      stopped: 'Client 模拟器已停止',
    },
    worker: {
      title: 'Worker 模拟器',
      pollInterval: '拉取间隔（毫秒）',
      start: '启动 Worker',
      stop: '停止 Worker',
      started: 'Worker 模拟器已启动',
      stopped: 'Worker 模拟器已停止',
      noTask: '暂无可执行任务',
      begin: '开始执行任务',
    },
    stats: {
      title: '统计信息',
      submitted: '已提交',
      completed: '已完成',
      failed: '已失败',
      clearLogs: '清空日志',
      logsCleared: '日志已清空',
    },
    taskTypeName: {
      video_transcode: '视频转码',
      data_export: '数据导出',
      image_process: '图片处理',
      pdf_generate: 'PDF 生成',
    },
    activeTasks: '活跃任务',
    noActiveTasks: '暂无活跃任务',
    logs: '执行日志',
    logsRealtime: '实时更新',
    step: {
      init: '初始化',
      download: '下载原视频',
      transcode: '转码处理',
      upload: '上传结果',
    },
    logStep: '{name}: {progress}%',
    submitSuccess: '提交任务成功',
    submitFailed: '提交任务失败',
    execSuccess: '任务执行成功',
    execFailed: '任务执行失败',
    workerError: 'Worker 执行异常',
    author1: '张三',
    author2: '李四',
    author3: '王五',
    errors: {
      transcode: '转码失败: 不支持的视频编码格式',
      download: '文件下载失败: 连接超时',
      memory: '内存不足: 无法处理大文件',
      format: '格式验证失败: 文件损坏',
      permission: '权限不足: 无法访问 OSS',
      quota: '配额超限: 已达到日处理上限',
      busy: '系统繁忙: 请稍后重试',
    },
  },

  // —— demo shortcut alias (蓝本 demoSimulatorExt 别名, 给页面用 lotask.demo.* 引用) ——
  demo: {
    title: '模拟测试',
  },
}