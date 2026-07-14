import { Card, Button, Space, Typography } from 'antd'
import { BookOutlined, LinkOutlined, FileTextOutlined } from '@ant-design/icons'
import './UserGuide.css'

const { Title, Paragraph, Text } = Typography

export default function UserGuide() {
  const handbookUrl = '/handbook.html'

  return (
    <div style={{ padding: '24px' }}>
      <Card>
        <div style={{ textAlign: 'center', marginBottom: '40px' }}>
          <BookOutlined style={{ fontSize: '64px', color: '#1890ff', marginBottom: '16px' }} />
          <Title level={2} style={{ marginBottom: '8px' }}>
            ASTS 使用手册
          </Title>
          <Paragraph style={{ fontSize: '16px', color: '#666' }}>
            异步慢任务服务 (Asynchronous Slow Task Service) - 完整开发文档
          </Paragraph>
        </div>

        <Card
          style={{
            background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
            border: 'none',
            marginBottom: '24px'
          }}
          styles={{ body: { padding: '32px', color: 'white' } }}
        >
          <div style={{ textAlign: 'center', color: 'white' }}>
            <Title level={3} style={{ color: 'white', marginBottom: '12px' }}>
              全新 Wiki 风格使用手册
            </Title>
            <Paragraph style={{ color: 'rgba(255,255,255,0.9)', marginBottom: '20px' }}>
              包含完整的客户端集成指南、Worker 实现示例、API 参考和最佳实践
            </Paragraph>
            <Button
              type="primary"
              size="large"
              icon={<LinkOutlined />}
              href={handbookUrl}
              target="_blank"
              style={{
                background: 'white',
                color: '#667eea',
                border: 'none',
                fontWeight: 600
              }}
            >
              打开使用手册
            </Button>
          </div>
        </Card>

        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))',
          gap: '24px',
          marginTop: '32px'
        }}>
          <Card hoverable className="guide-card">
            <div style={{ fontSize: '48px', marginBottom: '16px', textAlign: 'center' }}>
              <FileTextOutlined style={{ color: '#1890ff' }} />
            </div>
            <Title level={4} style={{ textAlign: 'center', marginBottom: '12px' }}>
              HTML 使用手册
            </Title>
            <Paragraph style={{ color: '#666', textAlign: 'center' }}>
              全新设计的 Wiki 风格文档，支持深色模式响应式布局，适合开源展示
            </Paragraph>
            <Button
              type="link"
              icon={<LinkOutlined />}
              href={handbookUrl}
              target="_blank"
              style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto' }}
            >
              查看手册
            </Button>
          </Card>

          <Card hoverable className="guide-card">
            <div style={{ fontSize: '48px', marginBottom: '16px', textAlign: 'center' }}>
              <BookOutlined style={{ color: '#52c41a' }} />
            </div>
            <Title level={4} style={{ textAlign: 'center', marginBottom: '12px' }}>
              客户端使用指南
            </Title>
            <Paragraph style={{ color: '#666', textAlign: 'center' }}>
              面向业务系统后端开发人员，介绍如何提交任务、查询状态、处理结果
            </Paragraph>
          </Card>

          <Card hoverable className="guide-card">
            <div style={{ fontSize: '48px', marginBottom: '16px', textAlign: 'center' }}>
              <BookOutlined style={{ color: '#faad14' }} />
            </div>
            <Title level={4} style={{ textAlign: 'center', marginBottom: '12px' }}>
              Worker 使用指南
            </Title>
            <Paragraph style={{ color: '#666', textAlign: 'center' }}>
              面向 Worker 节点开发人员，介绍如何实现任务执行器、Poll 任务、上报进度
            </Paragraph>
          </Card>
        </div>

        <div style={{
          marginTop: '40px',
          padding: '24px',
          background: '#f5f5f5',
          borderRadius: '8px'
        }}>
          <Title level={4} style={{ marginBottom: '16px' }}>📖 文档导航</Title>
          <Space direction="vertical" style={{ width: '100%' }}>
            <Text>
              <strong>HTML 使用手册</strong>：全新 Wiki 风格，可直接在浏览器打开或用于开源项目展示
            </Text>
            <Text>
              <strong>客户端指南</strong>：API 接口、代码集成、轮询策略、Webhook 回调
            </Text>
            <Text>
              <strong>Worker 指南</strong>：Poll 机制、任务执行、进度上报、取消处理
            </Text>
            <Text>
              <strong>接口前缀</strong>：Client API (/api/v1/client), Worker API (/api/v1/worker)
            </Text>
            <Text>
              <strong>统一响应</strong>：所有接口返回统一的 JSON 响应格式
            </Text>
            <Text>
              <strong>任务状态</strong>：PENDING → RUNNING → SUCCESS/FAILED/CANCELLED
            </Text>
          </Space>
        </div>
      </Card>
    </div>
  )
}