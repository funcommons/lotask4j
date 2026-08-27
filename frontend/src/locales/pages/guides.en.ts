/**
 * lotask4j page copy — guides domain (English)
 * Mirror of guides.zh.ts (UserGuide / ClientGuide / WorkerGuide / DemoSimulator)
 */
export const guides = {
  userGuide: {
    title: 'User Guide',
    subtitle: 'View usage documentation and examples',
    handbookTitle: 'Wiki-style Handbook',
    handbookDesc: 'Brand-new Wiki-style documentation, including complete client integration, Worker implementation examples, API reference, and best practices.',
    openHandbook: 'Open Handbook',
    clientTitle: 'Client Guide',
    clientDesc: 'For backend developers of business systems, how to submit tasks, query status, handle results.',
    workerTitle: 'Worker Guide',
    workerDesc: 'For Worker node developers, how to implement task executors, poll tasks, report progress.',
    simulatorTitle: 'Demo Simulator',
    simulatorDesc: 'Submit test tasks and observe task execution in real-time.',
  },

  clientGuide: {
    title: 'Client Integration Guide',
  },
  clientGuideExt: {
    tabs: {
      overview: 'Overview',
      api: 'API List',
      code: 'Code Examples',
      bestPractices: 'Best Practices',
    },
  },

  workerGuide: {
    title: 'Worker Integration Guide',
  },
  workerGuideExt: {
    tabs: {
      overview: 'Overview',
      api: 'API List',
      implementation: 'Implementation',
      bestPractices: 'Best Practices',
    },
  },

  demoSimulatorExt: {
    title: 'Demo Simulator',
    subtitle: 'Client + Worker dual simulator for real-time task flow',
    envWarning: 'Environment Notice',
    envWarningDesc: 'Note: works in dev only — production has auth gateway that blocks simulator',
    control: 'Simulator Control Panel',
    client: {
      title: 'Client Simulator',
      taskType: 'Task Type',
      submitInterval: 'Submit Interval (ms)',
      start: 'Start Client',
      stop: 'Stop Client',
      started: 'Client simulator started',
      stopped: 'Client simulator stopped',
    },
    worker: {
      title: 'Worker Simulator',
      pollInterval: 'Poll Interval (ms)',
      start: 'Start Worker',
      stop: 'Stop Worker',
      started: 'Worker simulator started',
      stopped: 'Worker simulator stopped',
      noTask: 'No task to execute',
      begin: 'Begin executing task',
    },
    stats: {
      title: 'Stats',
      submitted: 'Submitted',
      completed: 'Completed',
      failed: 'Failed',
      clearLogs: 'Clear Logs',
      logsCleared: 'Logs cleared',
    },
    taskTypeName: {
      video_transcode: 'Video Transcode',
      data_export: 'Data Export',
      image_process: 'Image Process',
      pdf_generate: 'PDF Generate',
    },
    activeTasks: 'Active Tasks',
    noActiveTasks: 'No active tasks',
    logs: 'Execution Logs',
    logsRealtime: 'Live',
    step: {
      init: 'Init',
      download: 'Download',
      transcode: 'Transcode',
      upload: 'Upload',
    },
    logStep: '{name}: {progress}%',
    submitSuccess: 'Submit task success',
    submitFailed: 'Submit task failed',
    execSuccess: 'Task execution success',
    execFailed: 'Task execution failed',
    workerError: 'Worker error',
    author1: 'Alice',
    author2: 'Bob',
    author3: 'Carol',
    errors: {
      transcode: 'Transcode failed: unsupported video codec',
      download: 'Download failed: connection timeout',
      memory: 'Out of memory: file too large',
      format: 'Format invalid: file corrupted',
      permission: 'Permission denied: cannot access OSS',
      quota: 'Quota exceeded: daily limit reached',
      busy: 'System busy: please retry later',
    },
  },

  demo: {
    title: 'Demo Simulator',
  },
}