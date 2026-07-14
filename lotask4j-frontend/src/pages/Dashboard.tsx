import { useEffect, useState } from 'react'
import { Card, Row, Col, Statistic, Spin, Divider, Progress } from 'antd'
import {
  CheckOutlined,
  SyncOutlined,
  ExclamationOutlined,
  StopOutlined,
  ClockCircleOutlined,
  ApiOutlined,
  TeamOutlined,
  BookOutlined
} from '@ant-design/icons'
import { getStatsOverview } from '@services/adminService'

interface TodayStats {
  success: number
  failed: number
  cancelled: number
}

interface WorkerCount {
  online: number
  offline: number
}

interface StatsOverview {
  totalPending: number
  totalRunning: number
  todayStats: TodayStats
  workerCount: WorkerCount
}

export default function Dashboard() {
  const [stats, setStats] = useState<StatsOverview | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const fetchStats = async () => {
      try {
        const data = await getStatsOverview()
        setStats(data)
      } catch (error) {
        console.error('Failed to fetch stats:', error)
      } finally {
        setLoading(false)
      }
    }

    fetchStats()
    const interval = setInterval(fetchStats, 5000) // 刷新间隔 5 秒

    return () => clearInterval(interval)
  }, [])

  if (loading) {
    return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', marginTop: '50px' }} />
  }

  const totalToday = (stats?.todayStats.success || 0) +
                     (stats?.todayStats.failed || 0) +
                     (stats?.todayStats.cancelled || 0)

  const successRate = totalToday > 0
    ? ((stats?.todayStats.success || 0) / totalToday * 100).toFixed(1)
    : '0.0'

  return (
    <div style={{ maxWidth: '100%', overflow: 'hidden' }}>
      <h2>系统概览</h2>

      {/* 实时任务状态 */}
      <Divider orientation="left">实时任务状态</Divider>
      <Row gutter={16}>
        <Col span={8}>
          <Card>
            <Statistic
              title="待处理任务"
              value={stats?.totalPending || 0}
              prefix={<ClockCircleOutlined />}
              valueStyle={{ color: 'var(--primary-color)' }}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic
              title="运行中任务"
              value={stats?.totalRunning || 0}
              prefix={<SyncOutlined spin />}
              valueStyle={{ color: '#D97706' }}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic
              title="在线 Worker 节点"
              value={stats?.workerCount.online || 0}
              suffix={`/ ${(stats?.workerCount.online || 0) + (stats?.workerCount.offline || 0)}`}
              prefix={<ApiOutlined />}
              valueStyle={{ color: '#10B981' }}
            />
          </Card>
        </Col>
      </Row>

      {/* 今日统计 */}
      <Divider orientation="left" style={{ marginTop: 32 }}>今日任务统计</Divider>
      <Row gutter={16}>
        <Col span={6}>
          <Card>
            <Statistic
              title="今日完成"
              value={stats?.todayStats.success || 0}
              prefix={<CheckOutlined />}
              valueStyle={{ color: '#10B981' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="今日失败"
              value={stats?.todayStats.failed || 0}
              prefix={<ExclamationOutlined />}
              valueStyle={{ color: '#DC2626' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="今日取消"
              value={stats?.todayStats.cancelled || 0}
              prefix={<StopOutlined />}
              valueStyle={{ color: 'var(--text-secondary)' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="今日总计"
              value={totalToday}
              prefix={<TeamOutlined />}
            />
            <div style={{ marginTop: 16 }}>
              <div style={{ marginBottom: 8 }}>成功率: {successRate}%</div>
              <Progress
                percent={parseFloat(successRate)}
                strokeColor={{
                  '0%': 'var(--primary-color)',
                  '100%': '#10B981',
                }}
                showInfo={false}
              />
            </div>
          </Card>
        </Col>
      </Row>

      {/* Worker 节点状态 */}
      <Divider orientation="left" style={{ marginTop: 32 }}>Worker 节点状态</Divider>
      <Row gutter={16}>
        <Col span={12}>
          <Card title="节点分布">
            <Row>
              <Col span={12}>
                <Statistic
                  title="在线节点"
                  value={stats?.workerCount.online || 0}
                  valueStyle={{ color: '#10B981' }}
                />
              </Col>
              <Col span={12}>
                <Statistic
                  title="离线节点"
                  value={stats?.workerCount.offline || 0}
                  valueStyle={{ color: '#DC2626' }}
                />
              </Col>
            </Row>
          </Card>
        </Col>
        <Col span={12}>
          <Card title="节点在线率">
            <Progress
              type="dashboard"
              percent={
                (stats?.workerCount.online || 0) + (stats?.workerCount.offline || 0) > 0
                  ? Math.round((stats?.workerCount.online || 0) /
                      ((stats?.workerCount.online || 0) + (stats?.workerCount.offline || 0)) * 100)
                  : 0
              }
              strokeColor={{
                '0%': 'var(--primary-color)',
                '100%': '#10B981',
              }}
            />
          </Card>
        </Col>
      </Row>

      {/* API 文档入口 */}
      <Divider orientation="left" style={{ marginTop: 32 }}>开发资源</Divider>
      <Row gutter={16}>
        <Col span={8}>
          <Card
            hoverable
            onClick={() => window.open('/swagger-ui.html', '_blank')}
            style={{ cursor: 'pointer' }}
          >
            <Statistic
              title="OPENAPI 接口文档"
              value="Swagger UI"
              prefix={<BookOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
      </Row>
    </div>
  )
}
