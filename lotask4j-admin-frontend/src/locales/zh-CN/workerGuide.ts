export default {
    title: 'Worker 接入指南',
    sections: {
      overview: '概述',
      flow: '执行流程',
      code: 'API 示例',
    },
    overview: {
      p1: 'Worker 是分布式任务执行节点，负责 Poll 任务、执行业务逻辑、上报进度、检测取消信号。',
    },
    interfacePrefix: '接口前缀：',
    flow: {
      s1: 'Worker 启动后进入主循环，调用 /poll 接口拉取任务。',
      s2: '拉取到任务后开始执行业务逻辑。',
      s3: '执行过程中定期调用 /progress 上报进度。',
      s4: '执行完成后调用 /result 上报结果。',
    },
    code: {
      pollTitle: 'Poll 任务',
      reportTitle: '上报进度',
      resultTitle: '上报结果',
    },
  }
