import { useState } from 'react'
import { BrowserRouter, Routes, Route, useNavigate } from 'react-router-dom'
import { Layout, Menu } from 'antd'
import {
  DashboardOutlined,
  FileTextOutlined,
  SettingOutlined,
  ApiOutlined,
  AppstoreOutlined,
  BookOutlined,
  ExperimentOutlined,
  ThunderboltOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined
} from '@ant-design/icons'
import Dashboard from '@pages/Dashboard'
import TaskList from '@pages/TaskList'
import TaskDetail from '@pages/TaskDetail'
import ActiveTasks from '@pages/ActiveTasks'
import WorkerNodes from '@pages/WorkerNodes'
import TaskTypeConfig from '@pages/TaskTypeConfig'
import SystemSettings from '@pages/SystemSettings'
import UserGuide from '@pages/UserGuide'
import ClientGuide from '@pages/ClientGuide'
import WorkerGuide from '@pages/WorkerGuide'
import DemoSimulator from '@pages/DemoSimulator'
import WebEmbedConfigManage from '@pages/WebEmbedConfigManage'
import './App.css'

const { Sider, Content, Footer } = Layout

function AppContent() {
  const [collapsed, setCollapsed] = useState(false)
  const navigate = useNavigate()

  return (
    <Layout style={{ height: '100vh', overflow: 'hidden' }}>
      <Sider
        collapsible
        collapsed={collapsed}
        onCollapse={(value) => setCollapsed(value)}
        theme="dark"
        trigger={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
      >
        <div className="logo">
          <span className="logo-icon">⚡</span>
          <span className="logo-text">ASTS 管理后台</span>
        </div>
        <Menu
          theme="dark"
          mode="inline"
          defaultSelectedKeys={['1']}
          items={[
            {
              key: '1',
              icon: <DashboardOutlined />,
              label: '系统概览',
              onClick: () => navigate('/'),
            },
            {
              key: '2',
              icon: <ThunderboltOutlined />,
              label: '活跃任务',
              onClick: () => navigate('/active'),
            },
            {
              key: '3',
              icon: <FileTextOutlined />,
              label: '任务管理',
              onClick: () => navigate('/tasks'),
            },
            {
              key: '4',
              icon: <ApiOutlined />,
              label: '工作节点',
              onClick: () => navigate('/workers'),
            },
            {
              key: '5',
              icon: <AppstoreOutlined />,
              label: '任务类型',
              onClick: () => navigate('/types'),
            },
            {
              key: '6',
              icon: <BookOutlined />,
              label: '使用指南',
              onClick: () => navigate('/guide'),
            },
            {
              key: '7',
              icon: <ExperimentOutlined />,
              label: '模拟测试',
              onClick: () => navigate('/demo'),
            },
            {
              key: '8',
              icon: <AppstoreOutlined />,
              label: '前端接入',
              onClick: () => navigate('/embed-config'),
            },
            {
              key: '9',
              icon: <SettingOutlined />,
              label: '系统设置',
              onClick: () => navigate('/settings'),
            },
          ]}
        />
      </Sider>
      <Layout style={{ display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden' }}>
        <Content style={{ margin: '16px', flex: 1, overflow: 'auto' }}>
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/tasks" element={<TaskList />} />
            <Route path="/tasks/:id" element={<TaskDetail />} />
            <Route path="/active" element={<ActiveTasks />} />
            <Route path="/workers" element={<WorkerNodes />} />
            <Route path="/types" element={<TaskTypeConfig />} />
            <Route path="/settings" element={<SystemSettings />} />
            <Route path="/guide" element={<UserGuide />} />
            <Route path="/guide/client" element={<ClientGuide />} />
            <Route path="/guide/worker" element={<WorkerGuide />} />
            <Route path="/demo" element={<DemoSimulator />} />
            <Route path="/embed-config" element={<WebEmbedConfigManage />} />
          </Routes>
        </Content>
        <Footer style={{ textAlign: 'center', flexShrink: 0 }}>
          lotask4j ASTS ©2026 | 异步慢任务服务管理平台
        </Footer>
      </Layout>
    </Layout>
  )
}

function App() {
  return (
    <BrowserRouter>
      <AppContent />
    </BrowserRouter>
  )
}

export default App
