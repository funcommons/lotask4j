export default {
    title: 'Worker Integration Guide',
    sections: {
      overview: 'Overview',
      flow: 'Execution Flow',
      code: 'API Examples',
    },
    overview: {
      p1: 'Worker is a distributed task execution node, responsible for polling tasks, executing business logic, reporting progress, and detecting cancellation signals.',
    },
    interfacePrefix: 'Interface prefix: ',
    flow: {
      s1: 'After starting, Worker enters main loop, calling /poll to fetch tasks.',
      s2: 'Once a task is fetched, start executing business logic.',
      s3: 'During execution, periodically call /progress to report progress.',
      s4: 'After completion, call /result to report the result.',
    },
    code: {
      pollTitle: 'Poll Task',
      reportTitle: 'Report Progress',
      resultTitle: 'Report Result',
    },
  }
