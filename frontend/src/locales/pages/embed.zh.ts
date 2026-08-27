/**
 * lotask4j 页面文案 — embed 域 (中文)
 * key 空间: lotask.embed.*
 *
 * 注：embed 三个页面 (TaskList / TaskDetail / TaskCard) 按蓝本传统硬编码中文,
 * 本文件仅作为扩展点保留, 方便后续接入 i18n.
 */
export const embed = {
  list: {
    title: '任务列表',
    filterAll: '全部状态',
    refresh: '刷新',
    loading: '加载中...',
    empty: '暂无任务',
    colId: 'ID',
    colType: '类型',
    colStatus: '状态',
    colProgress: '进度',
    colCreatedAt: '创建时间',
    prev: '上一页',
    next: '下一页',
    pageOf: '{page} / {total}',
  },
  detail: {
    title: '任务详情',
    loading: '加载中...',
    empty: '未指定任务 ID',
    colTaskId: '任务 ID',
    colType: '任务类型',
    colCurrentStep: '当前步骤',
    colCreatedAt: '创建时间',
    colFinishedAt: '完成时间',
    progressLabel: '进度',
    errorTitle: '错误信息',
    resultTitle: '执行结果',
    successText: '✓ 执行成功',
  },
  card: {
    loading: '加载中...',
    empty: '未指定任务',
    currentStepLabel: '当前步骤',
    successText: '✓ 执行成功',
  },
}