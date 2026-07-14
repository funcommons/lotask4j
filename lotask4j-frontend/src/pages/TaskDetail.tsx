import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Card,
  Descriptions,
  Progress,
  Tag,
  Spin,
  Button,
  Space,
  Timeline,
  Statistic,
  Row,
  Col,
  Modal,
  message,
  Alert,
  Typography
} from 'antd'
import {
  ArrowLeftOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SyncOutlined,
  StopOutlined,
  PlayCircleOutlined,
  InfoCircleOutlined
} from '@ant-design/icons'
import JsonView from '@uiw/react-json-view'
import { getTaskDetail, cancelTask } from '@services/taskService'
import { formatTime, formatDuration } from '@utils/timeUtils'

const { Text } = Typography

interface TaskDetail {
  id: string // OpenID 格式的任务 ID
  type: string
  typeName?: string
  status: string
  progress: number
  priority?: number
  retryCount?: number
  currentStep?: string
  stepsDetail?: Array<{
    key: string
    name: string
    status: string
    detail?: string
    progress?: number
    start_time?: string
    end_time?: string
    cost_ms?: number
  }>
  payload?: any
  result?: any
  errorMsg?: string
  workerIp?: string
  callbackUrl?: string
  callbackStatus?: number
  createdAt: string
  updatedAt?: string
  startedAt?: string
  finishedAt?: string
  expiredAt?: string
}

const statusConfig: Record<string, { color: string; icon: any; text: string }> = {
  PENDING: { color: 'default', icon: <ClockCircleOutlined />, text: '待处理' },
  RUNNING: { color: 'processing', icon: <SyncOutlined spin />, text: '运行中' },
  SUCCESS: { color: 'success', icon: <CheckCircleOutlined />, text: '成功' },
  FAILED: { color: 'error', icon: <CloseCircleOutlined />, text: '失败' },
  CANCELLED: { color: 'default', icon: <StopOutlined />, text: '已取消' },
  CANCELLING: { color: 'warning', icon: <StopOutlined />, text: '取消中' },
}

const stepStatusConfig: Record<string, { color: string; text: string }> = {
  pending: { color: 'default', text: '待执行' },
  processing: { color: 'processing', text: '执行中' },
  finished: { color: 'success', text: '已完成' },
  failed: { color: 'error', text: '失败' },
}

const callbackStatusConfig: Record<number, { color: string; text: string }> = {
  0: { color: 'default', text: '未回调' },
  1: { color: 'success', text: '回调成功' },
  2: { color: 'error', text: '回调失败' },
}

export default function TaskDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [task, setTask] = useState<TaskDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [, setRefreshTrigger] = useState(0)

  useEffect(() => {
    if (id) {
      fetchTaskDetail()
      const interval = setInterval(fetchTaskDetail, 2000) // 2秒刷新一次
      return () => clearInterval(interval)
    }
  }, [id])

  // 每秒刷新时间显示
  useEffect(() => {
    const timer = setInterval(() => {
      setRefreshTrigger(prev => prev + 1)
    }, 1000)
    return () => clearInterval(timer)
  }, [])

  const fetchTaskDetail = async () => {
    if (!id) return
    try {
      const data = await getTaskDetail(id)
      setTask(data)
    } catch (error) {
      console.error('Failed to fetch task detail:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleCancel = () => {
    if (!id) return
    Modal.confirm({
      title: '确认取消任务？',
      content: '任务将被标记为取消中，Worker 会在检测到后停止执行',
      onOk: async () => {
        try {
          await cancelTask(id)
          message.success('任务已标记为取消中')
          fetchTaskDetail()
        } catch (error) {
          message.error('取消失败')
        }
      },
    })
  }

  const calculateDuration = (startTime?: string, endTime?: string): string => {
    if (!startTime) return '-'
    const start = new Date(startTime).getTime()
    const end = endTime ? new Date(endTime).getTime() : Date.now()
    return formatDuration(end - start)
  }

  if (loading) {
    return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', marginTop: '50px' }} />
  }

  if (!task) {
    return (
      <div style={{ padding: '24px' }}>
        <Alert
          message="任务不存在"
          description="未找到该任务，可能已被删除或任务 ID 不正确"
          type="warning"
          showIcon
        />
        <Button
          type="primary"
          icon={<ArrowLeftOutlined />}
          onClick={() => navigate('/tasks')}
          style={{ marginTop: 16 }}
        >
          返回任务列表
        </Button>
      </div>
    )
  }

  const statusInfo = statusConfig[task.status] || statusConfig.PENDING
  const isRunning = task.status === 'RUNNING'
  const isFinished = ['SUCCESS', 'FAILED', 'CANCELLED'].includes(task.status)

  return (
    <div style={{ padding: '24px', maxWidth: '100%', overflow: 'hidden' }}>
      {/* 顶部操作栏 */}
      <div style={{ marginBottom: '16px' }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/tasks')}>
            返回列表
          </Button>
          {(task.status === 'PENDING' || task.status === 'RUNNING') && (
            <Button type="primary" danger icon={<StopOutlined />} onClick={handleCancel}>
              取消任务
            </Button>
          )}
        </Space>
      </div>

      {/* 状态警告 */}
      {task.status === 'FAILED' && task.errorMsg && (
        <Alert
          message="任务执行失败"
          description={task.errorMsg}
          type="error"
          showIcon
          closable
          style={{ marginBottom: 16 }}
        />
      )}

      {task.status === 'CANCELLING' && (
        <Alert
          message="任务取消中"
          description="任务已标记为取消中，等待 Worker 响应"
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
        />
      )}

      {/* 核心统计卡片 */}
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}>
          <Card style={{ textAlign: 'left' }}>
            <Statistic
              title="任务状态"
              value={statusInfo.text}
              prefix={statusInfo.icon}
              valueStyle={{ color: statusInfo.color === 'success' ? '#10B981' : statusInfo.color === 'error' ? '#DC2626' : undefined }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card style={{ textAlign: 'left' }}>
            <Statistic
              title="执行进度"
              value={task.progress}
              suffix="%"
              prefix={isRunning ? <SyncOutlined spin /> : <InfoCircleOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card style={{ textAlign: 'left' }}>
            <Statistic
              title="优先级"
              value={task.priority ?? 0}
              prefix={<PlayCircleOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card style={{ textAlign: 'left' }}>
            <Statistic
              title="重试次数"
              value={task.retryCount ?? 0}
              prefix={<SyncOutlined />}
            />
          </Card>
        </Col>
      </Row>

      {/* 进度条 */}
      {isRunning && (
        <Card style={{ marginBottom: 16, textAlign: 'left' }}>
          <div style={{ marginBottom: 8 }}>
            <Text strong>当前步骤: </Text>
            <Text>{task.currentStep || '准备中...'}</Text>
          </div>
          <Progress
            percent={task.progress}
            status="active"
            strokeColor={{
              '0%': 'var(--primary-color)',
              '100%': '#10B981',
            }}
          />
        </Card>
      )}

      {/* 基本信息 */}
      <Card title="基本信息" headStyle={{ textAlign: 'left' }} style={{ marginBottom: 16 }}>
        <Descriptions bordered column={2} contentStyle={{ textAlign: 'left' }}>
          <Descriptions.Item label="任务 ID" span={2}>
            <Text copyable>{task.id}</Text>
          </Descriptions.Item>
          <Descriptions.Item label="任务类型">
            <div>
              <div style={{ fontSize: '14px', fontWeight: 500, marginBottom: '2px' }}>
                {task.typeName || task.type}
              </div>
              <Text type="secondary" style={{ fontSize: '11px' }}>{task.type}</Text>
            </div>
          </Descriptions.Item>
          <Descriptions.Item label="任务状态">
            <Tag color={statusInfo.color} icon={statusInfo.icon}>
              {statusInfo.text}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="优先级">{task.priority ?? 0}</Descriptions.Item>
          <Descriptions.Item label="重试次数">{task.retryCount ?? 0}</Descriptions.Item>
          <Descriptions.Item label="当前进度">{task.progress}%</Descriptions.Item>
          <Descriptions.Item label="当前步骤">{task.currentStep || '-'}</Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 执行信息 */}
      <Card title="执行信息" headStyle={{ textAlign: 'left' }} style={{ marginBottom: 16 }}>
        <Descriptions bordered column={2} contentStyle={{ textAlign: 'left' }}>
          <Descriptions.Item label="Worker IP">
            {task.workerIp ? <Tag color="blue">{task.workerIp}</Tag> : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="回调状态">
            {task.callbackStatus !== undefined ? (
              <Tag color={callbackStatusConfig[task.callbackStatus]?.color}>
                {callbackStatusConfig[task.callbackStatus]?.text}
              </Tag>
            ) : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="回调地址" span={2}>
            {task.callbackUrl ? <Text copyable>{task.callbackUrl}</Text> : '-'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 时间信息 */}
      <Card title="时间信息" headStyle={{ textAlign: 'left' }} style={{ marginBottom: 16 }}>
        <Descriptions bordered column={2} contentStyle={{ textAlign: 'left' }}>
          <Descriptions.Item label="创建时间">
            {formatTime(task.createdAt)}
          </Descriptions.Item>
          <Descriptions.Item label="最后更新">
            {formatTime(task.updatedAt)}
          </Descriptions.Item>
          <Descriptions.Item label="开始时间">
            {formatTime(task.startedAt)}
          </Descriptions.Item>
          <Descriptions.Item label="完成时间">
            {formatTime(task.finishedAt)}
          </Descriptions.Item>
          <Descriptions.Item label="过期时间" span={2}>
            {task.expiredAt ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span>过期: {formatTime(task.expiredAt)}</span>
                {(() => {
                  const now = Date.now()
                  const expireTime = new Date(task.expiredAt).getTime()
                  const remaining = Math.floor((expireTime - now) / 1000)

                  if (remaining <= 0) {
                    return <Tag color="error">已过期</Tag>
                  } else if (remaining < 600) {
                    const mins = Math.floor(remaining / 60)
                    return <Tag color="warning">剩余 {mins}分钟</Tag>
                  } else if (remaining < 3600) {
                    const mins = Math.floor(remaining / 60)
                    return <Tag color="default">剩余 {mins}分钟</Tag>
                  } else if (remaining < 86400) {
                    const hours = Math.floor(remaining / 3600)
                    return <Tag color="default">剩余 {hours}小时</Tag>
                  } else {
                    const days = Math.floor(remaining / 86400)
                    return <Tag color="default">剩余 {days}天</Tag>
                  }
                })()}
              </div>
            ) : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="等待时长">
            {task.startedAt ? calculateDuration(task.createdAt, task.startedAt) : calculateDuration(task.createdAt)}
          </Descriptions.Item>
          <Descriptions.Item label="执行时长">
            {isRunning ? (
              <Text type="success">{calculateDuration(task.startedAt)}</Text>
            ) : isFinished ? (
              calculateDuration(task.startedAt, task.finishedAt)
            ) : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="总耗时" span={2}>
            {isFinished ? (
              <Text strong>{calculateDuration(task.createdAt, task.finishedAt)}</Text>
            ) : (
              calculateDuration(task.createdAt)
            )}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 步骤详情 */}
      {task.stepsDetail && task.stepsDetail.length > 0 && (
        <Card title="步骤详情" headStyle={{ textAlign: 'left' }} style={{ marginBottom: 16 }}>
          <Timeline>
            {task.stepsDetail.map((step, index) => {
              const stepStatus = stepStatusConfig[step.status] || stepStatusConfig.pending
              const isActive = step.status === 'processing'

              return (
                <Timeline.Item
                  key={step.key}
                  color={stepStatus.color}
                  dot={isActive ? <SyncOutlined spin style={{ fontSize: '16px' }} /> : undefined}
                >
                  <div style={{ padding: '8px 0', textAlign: 'left' }}>
                    <div style={{ marginBottom: 8 }}>
                      <Text strong style={{ fontSize: '16px' }}>
                        {index + 1}. {step.name}
                      </Text>
                      <Tag color={stepStatus.color} style={{ marginLeft: 8 }}>
                        {stepStatus.text}
                      </Tag>
                    </div>

                    {step.detail && (
                      <div style={{ marginBottom: 4 }}>
                        <Text type="secondary">{step.detail}</Text>
                      </div>
                    )}

                    {step.progress !== undefined && step.progress > 0 && (
                      <div style={{ marginBottom: 8, maxWidth: 400 }}>
                        <Progress percent={step.progress} size="small" status={isActive ? 'active' : 'normal'} />
                      </div>
                    )}

                    <Row gutter={16}>
                      {step.start_time && (
                        <Col>
                          <Text type="secondary">开始: {formatTime(step.start_time)}</Text>
                        </Col>
                      )}
                      {step.end_time && (
                        <Col>
                          <Text type="secondary">结束: {formatTime(step.end_time)}</Text>
                        </Col>
                      )}
                      {step.cost_ms !== undefined && (
                        <Col>
                          <Text type="secondary">耗时: {step.cost_ms}ms</Text>
                        </Col>
                      )}
                    </Row>
                  </div>
                </Timeline.Item>
              )
            })}
          </Timeline>
        </Card>
      )}

      {/* 任务入参 */}
      {task.payload && Object.keys(task.payload || {}).length > 0 && (
        <Card title="任务入参" headStyle={{ textAlign: 'left' }} style={{ marginBottom: 16 }}>
          <JsonView
            value={task.payload}
            collapsed={1}
            displayDataTypes={false}
            style={{
              background: 'var(--bg-page)',
              padding: 16,
              borderRadius: 4,
              overflow: 'auto',
              maxHeight: 400,
              fontSize: '13px',
              textAlign: 'left'
            }}
          />
        </Card>
      )}

      {/* 执行结果 */}
      {task.result && Object.keys(task.result || {}).length > 0 && (
        <Card title="执行结果" headStyle={{ textAlign: 'left' }} style={{ marginBottom: 16 }}>
          <JsonView
            value={task.result}
            collapsed={1}
            displayDataTypes={false}
            style={{
              background: 'var(--bg-page)',
              padding: 16,
              borderRadius: 4,
              overflow: 'auto',
              maxHeight: 400,
              fontSize: '13px',
              textAlign: 'left'
            }}
          />
        </Card>
      )}

      {/* 错误信息 */}
      {task.errorMsg && (
        <Card title="错误信息" headStyle={{ textAlign: 'left' }} style={{ marginBottom: 16 }}>
          <Alert
            message="执行失败"
            description={
              <pre style={{ whiteSpace: 'pre-wrap', wordWrap: 'break-word' }}>
                {task.errorMsg}
              </pre>
            }
            type="error"
            showIcon
          />
        </Card>
      )}
    </div>
  )
}
