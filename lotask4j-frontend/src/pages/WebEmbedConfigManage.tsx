import { useEffect, useState } from 'react'
import {
  Card,
  Table,
  Button,
  Space,
  Modal,
  Form,
  Input,
  Select,
  Switch,
  message,
  Popconfirm,
  Tag,
  Tabs,
  Drawer
} from 'antd'
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  EyeOutlined,
  ReloadOutlined,
  LinkOutlined
} from '@ant-design/icons'
import {
  listConfigs,
  getConfig,
  createConfig,
  updateConfig,
  deleteConfig,
  toggleEnabled,
  previewUrl,
  type WebEmbedConfig
} from '@services/webEmbedService'

const { TextArea } = Input

export default function WebEmbedConfigManage() {
  const [loading, setLoading] = useState(false)
  const [data, setData] = useState<WebEmbedConfig[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [keyword, setKeyword] = useState('')
  const [isEnabledFilter, setIsEnabledFilter] = useState<number | undefined>()

  // 表单
  const [formModalOpen, setFormModalOpen] = useState(false)
  const [editing, setEditing] = useState<WebEmbedConfig | null>(null)
  const [form] = Form.useForm()

  // 预览 Drawer
  const [previewDrawerOpen, setPreviewDrawerOpen] = useState(false)
  const [previewUrlValue, setPreviewUrlValue] = useState('')

  // 嵌入 URL Drawer
  const [embedUrlDrawerOpen, setEmbedUrlDrawerOpen] = useState(false)
  const [embedUrlValue, setEmbedUrlValue] = useState('')

  async function load() {
    setLoading(true)
    try {
      const res = await listConfigs({ keyword, isEnabled: isEnabledFilter, page, pageSize })
      setData(res.data.items)
      setTotal(res.data.total)
    } catch (err: any) {
      message.error('加载失败: ' + err.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [page, pageSize, keyword, isEnabledFilter])

  function handleAdd() {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ isOpen: 0, componentType: 'task-list', config: { 'task-list': { theme: 'light' } } })
    setFormModalOpen(true)
  }

  async function handleEdit(record: WebEmbedConfig) {
    try {
      const res = await getConfig(record.id!)
      setEditing(res.data)
      form.setFieldsValue(res.data)
      setFormModalOpen(true)
    } catch (err: any) {
      message.error('加载详情失败: ' + err.message)
    }
  }

  async function handleSubmit() {
    try {
      const values = await form.validateFields()
      if (editing && editing.id) {
        await updateConfig(editing.id, values)
        message.success('更新成功')
      } else {
        await createConfig(values)
        message.success('创建成功')
      }
      setFormModalOpen(false)
      load()
    } catch (err: any) {
      if (err.errorFields) return
      message.error('操作失败: ' + err.message)
    }
  }

  async function handleDelete(id: number) {
    try {
      await deleteConfig(id)
      message.success('删除成功')
      load()
    } catch (err: any) {
      message.error('删除失败: ' + err.message)
    }
  }

  async function handleToggle(id: number, isEnabled: number) {
    try {
      await toggleEnabled(id, isEnabled)
      message.success(isEnabled === 1 ? '已启用' : '已禁用')
      load()
    } catch (err: any) {
      message.error('操作失败: ' + err.message)
    }
  }

  async function handlePreview(record: WebEmbedConfig) {
    setPreviewDrawerOpen(true)
    try {
      // 预览使用配置的 componentType
      const res = await previewUrl(record.id!, record.componentType)
      setPreviewUrlValue(res.data.url)
    } catch (err: any) {
      message.error('生成预览 URL 失败: ' + err.message)
    }
  }

  async function handleCopyEmbedUrl(record: WebEmbedConfig) {
    try {
      const res = await previewUrl(record.id!, record.componentType)
      setEmbedUrlValue(res.data.url)
      setEmbedUrlDrawerOpen(true)
    } catch (err: any) {
      message.error('生成 URL 失败: ' + err.message)
    }
  }

  return (
    <div style={{ padding: 24 }}>
      <Card>
        <Tabs
          defaultActiveKey="list"
          items={[
            {
              key: 'list',
              label: '配置列表',
              children: (
                <>
                  <Space style={{ marginBottom: 16 }} wrap>
                    <Input.Search
                      placeholder="搜索 configKey / 名称"
                      allowClear
                      style={{ width: 240 }}
                      onSearch={setKeyword}
                    />
                    <Select
                      placeholder="状态筛选"
                      allowClear
                      style={{ width: 120 }}
                      value={isEnabledFilter}
                      onChange={setIsEnabledFilter}
                      options={[
                        { label: '启用', value: 1 },
                        { label: '禁用', value: 0 }
                      ]}
                    />
                    <Button icon={<ReloadOutlined />} onClick={load}>刷新</Button>
                    <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
                      新建配置
                    </Button>
                  </Space>

                  <Table
                    rowKey="id"
                    loading={loading}
                    dataSource={data}
                    pagination={{
                      current: page,
                      pageSize,
                      total,
                      showSizeChanger: true,
                      onChange: (p, ps) => { setPage(p); setPageSize(ps) }
                    }}
                    columns={[
                      { title: 'ID', dataIndex: 'id', width: 80 },
                      {
                        title: '前端组件',
                        dataIndex: 'componentType',
                        width: 140,
                        render: (v: string) => {
                          const colors: Record<string, string> = {
                            'task-list': 'green',
                            'task-detail': 'blue',
                            'task-card': 'purple'
                          }
                          return <Tag color={colors[v] || 'default'}>{v}</Tag>
                        }
                      },
                      { title: 'configKey', dataIndex: 'configKey', width: 160 },
                      { title: '名称', dataIndex: 'configName', width: 160 },
                      { title: 'userId', dataIndex: 'userId', width: 140 },
                      {
                        title: '模式',
                        dataIndex: 'isOpen',
                        width: 100,
                        render: (v: number) => v === 1
                          ? <Tag color="orange">开放</Tag>
                          : <Tag color="blue">鉴权</Tag>
                      },
                      {
                        title: '回调地址',
                        dataIndex: 'callbackUrl',
                        ellipsis: true,
                        render: (v: string) => v || <span style={{ color: '#999' }}>-</span>
                      },
                      {
                        title: '状态',
                        dataIndex: 'isEnabled',
                        width: 100,
                        render: (v: number, r: WebEmbedConfig) => (
                          <Switch
                            checked={v === 1}
                            onChange={(c) => handleToggle(r.id!, c ? 1 : 0)}
                          />
                        )
                      },
                      { title: '创建时间', dataIndex: 'createdAt', width: 170 },
                      {
                        title: '操作',
                        width: 240,
                        fixed: 'right',
                        render: (_: any, r: WebEmbedConfig) => (
                          <Space size="small">
                            <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => handlePreview(r)}>
                              预览
                            </Button>
                            <Button type="link" size="small" icon={<LinkOutlined />} onClick={() => handleCopyEmbedUrl(r)}>
                              嵌入
                            </Button>
                            <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(r)}>
                              编辑
                            </Button>
                            <Popconfirm
                              title="确认删除？"
                              onConfirm={() => handleDelete(r.id!)}
                            >
                              <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                                删除
                              </Button>
                            </Popconfirm>
                          </Space>
                        )
                      }
                    ]}
                  />
                </>
              )
            }
          ]}
        />
      </Card>

      {/* 新建/编辑表单 */}
      <Modal
        title={editing ? '编辑配置' : '新建配置'}
        open={formModalOpen}
        onCancel={() => setFormModalOpen(false)}
        onOk={handleSubmit}
        width={720}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="configKey" label="configKey" rules={[{ required: true, max: 64 }]}>
            <Input placeholder="唯一标识" />
          </Form.Item>
          <Form.Item name="configName" label="配置名称" rules={[{ required: true, max: 128 }]}>
            <Input placeholder="业务系统 A" />
          </Form.Item>
          <Form.Item name="userId" label="默认 userId" rules={[{ required: true, max: 64 }]}>
            <Input placeholder="userId" />
          </Form.Item>
          <Form.Item name="isOpen" label="模式">
            <Select
              options={[
                { label: '鉴权模式（需要回调验证）', value: 0 },
                { label: '开放模式（无需鉴权）', value: 1 }
              ]}
            />
          </Form.Item>
          <Form.Item name="componentType" label="限定组件" rules={[{ required: true, message: '必填' }]}>
            <Select
              placeholder="选择该配置用于哪个组件"
              options={[
                { label: '任务列表 (task-list)', value: 'task-list' },
                { label: '任务详情 (task-detail)', value: 'task-detail' },
                { label: '任务卡片 (task-card)', value: 'task-card' }
              ]}
            />
          </Form.Item>
          <Form.Item name="callbackUrl" label="回调地址（鉴权模式必填）">
            <Input placeholder="https://biz.example.com/auth/verify" />
          </Form.Item>
          <Form.Item name="allowedDomains" label="允许的域名">
            <Input placeholder="https://example.com,https://app.example.com" />
          </Form.Item>
          <Form.Item name="config" label="组件配置 (JSON)">
            <TextArea
              rows={6}
              placeholder='{"task-list": {"theme": "light"}}'
              style={{ fontFamily: 'monospace' }}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 预览 Drawer */}
      <Drawer
        title="嵌入预览"
        open={previewDrawerOpen}
        onClose={() => setPreviewDrawerOpen(false)}
        width={900}
      >
        {previewUrlValue && (
          <iframe
            src={previewUrlValue}
            style={{ width: '100%', height: 'calc(100vh - 130px)', border: '1px solid #d9d9d9' }}
          />
        )}
      </Drawer>

      {/* 嵌入 URL Drawer */}
      <Drawer
        title="嵌入 URL"
        open={embedUrlDrawerOpen}
        onClose={() => setEmbedUrlDrawerOpen(false)}
        width={720}
      >
        <p style={{ marginBottom: 12, color: '#666' }}>
          复制以下 URL 到业务方系统中即可嵌入：
        </p>
        <Input.TextArea
          value={embedUrlValue}
          rows={4}
          readOnly
          style={{ fontFamily: 'monospace' }}
        />
        <Button
          type="primary"
          style={{ marginTop: 12 }}
          onClick={() => {
            navigator.clipboard.writeText(embedUrlValue)
            message.success('已复制')
          }}
        >
          复制 URL
        </Button>
        <h4 style={{ marginTop: 24 }}>使用示例：</h4>
        <pre style={{ background: '#f5f5f5', padding: 12, borderRadius: 8 }}>
{`<iframe
  src="${embedUrlValue}"
  width="100%"
  height="800px"
  frameborder="0"
></iframe>`}
        </pre>
      </Drawer>
    </div>
  )
}
