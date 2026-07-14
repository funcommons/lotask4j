<template>
  <div class="app-page">
    <TitledSection :title="t('embedConfig.title')" icon="ri-stack-line">
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="handleAdd">
          {{ t('embedConfig.addNew') }}
        </el-button>
      </template>
      <WorkSection>
        <el-table v-loading="loading" :data="configs" stripe border>
          <el-table-column :label="t('embedConfig.componentType')" width="140">
            <template #default="{ row }">
              <el-tag :type="componentTagType(row.componentType)" effect="light">
                {{ row.componentType }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('embedConfig.configKey')" prop="configKey" min-width="160" />
          <el-table-column :label="t('embedConfig.configName')" prop="configName" min-width="140" />
          <el-table-column :label="t('embedConfig.userId')" prop="userId" width="120" />
          <el-table-column :label="t('embedConfig.mode')" width="100">
            <template #default="{ row }">
              <el-tag :type="row.isOpen ? 'warning' : 'primary'" size="small">
                {{ row.isOpen ? t('embedConfig.open') : t('embedConfig.auth') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('common.enabled')" width="100">
            <template #default="{ row }">
              <el-switch :model-value="row.isEnabled === 1" @change="(v: boolean) => handleToggle(row, v)" />
            </template>
          </el-table-column>
          <el-table-column :label="t('common.action')" width="220" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" size="small" link @click="handlePreview(row)">
                {{ t('embedConfig.preview') }}
              </el-button>
              <el-button type="primary" size="small" link @click="handleEmbed(row)">
                {{ t('embedConfig.embed') }}
              </el-button>
              <el-button type="primary" size="small" link @click="handleEdit(row)">
                {{ t('embedConfig.edit') }}
              </el-button>
              <el-popconfirm
                :title="t('common.deleteConfirm')"
                :confirm-button-text="t('common.confirm')"
                :cancel-button-text="t('common.cancel')"
                @confirm="handleDelete(row)"
              >
                <template #reference>
                  <el-button type="danger" size="small" link>
                    {{ t('common.delete') }}
                  </el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </WorkSection>
    </TitledSection>

    <!-- 新建/编辑 -->
    <AppDialog :visible="dialogVisible" @update:visible="dialogVisible = $event"
      :title="editing ? t('embedConfig.edit') : t('embedConfig.addNew')"
      width="640px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item :label="t('embedConfig.componentType')" prop="componentType">
          <el-select v-model="form.componentType" :disabled="!!editing" style="width: 100%">
            <el-option label="task-list" value="task-list" />
            <el-option label="task-detail" value="task-detail" />
            <el-option label="task-card" value="task-card" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('embedConfig.configKey')" prop="configKey">
          <el-input v-model="form.configKey" :placeholder="t('embedConfig.configKeyPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('embedConfig.configName')" prop="configName">
          <el-input v-model="form.configName" />
        </el-form-item>
        <el-form-item :label="t('embedConfig.userId')" prop="userId">
          <el-input v-model="form.userId" />
        </el-form-item>
        <el-form-item :label="t('embedConfig.mode')">
          <el-radio-group v-model="form.isOpen">
            <el-radio :value="0">{{ t('embedConfig.auth') }}</el-radio>
            <el-radio :value="1">{{ t('embedConfig.open') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="!form.isOpen" :label="t('embedConfig.callbackUrl')">
          <el-input v-model="form.callbackUrl" placeholder="https://biz.example.com/auth/verify" />
        </el-form-item>
        <el-form-item :label="t('embedConfig.config')">
          <el-input
            v-model="form.configStr"
            type="textarea"
            :rows="4"
            placeholder='{"task-list": {"theme": "light"}}'
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">{{ t('common.confirm') }}</el-button>
      </template>
    </AppDialog>

    <!-- 预览 -->
    <AppDrawer :visible="previewVisible" @update:visible="previewVisible = $event"
      :title="t('embedConfig.preview')"
      width="900px"
    >
      <iframe v-if="previewUrl" :src="previewUrl" class="preview-iframe" />
    </AppDrawer>

    <!-- 嵌入 URL -->
    <AppDrawer :visible="embedVisible" @update:visible="embedVisible = $event"
      :title="t('embedConfig.embed')"
      width="640px"
    >
      <p>{{ t('embedConfig.embedDesc') }}</p>
      <el-input v-model="embedUrl" type="textarea" :rows="3" readonly class="url-input" />
      <el-button type="primary" style="margin-top: 12px" @click="copyEmbedUrl">
        {{ t('embedConfig.copy') }}
      </el-button>
      <h4 style="margin-top: 24px">{{ t('embedConfig.usage') }}</h4>
      <pre class="code-block">&lt;iframe
  src="{{ embedUrl }}"
  width="100%"
  height="800px"
  frameborder="0"
&gt;&lt;/iframe&gt;</pre>
    </AppDrawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  listConfigs, getConfig, createConfig, updateConfig, deleteConfig, toggleEnabled, getPreviewUrl,
  type WebEmbedConfig
} from '@/api/embed'
import TitledSection from '@/components/sdk/common/TitledSection.vue'
import WorkSection from '@/components/sdk/common/WorkSection.vue'
import AppDialog from '@/components/sdk/common/AppDialog.vue'
import AppDrawer from '@/components/sdk/common/AppDrawer.vue'

const { t } = useI18n()
const loading = ref(false)
const submitting = ref(false)
const configs = ref<WebEmbedConfig[]>([])

const dialogVisible = ref(false)
const editing = ref<WebEmbedConfig | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  id: undefined as number | undefined,
  componentType: 'task-list',
  configKey: '',
  configName: '',
  userId: 'guest',
  isOpen: 1,
  callbackUrl: '',
  configStr: '{}',
})

const rules = {
  componentType: [{ required: true, message: () => t('common.required'), trigger: 'change' }],
  configKey: [{ required: true, message: () => t('common.required'), trigger: 'blur' }],
  configName: [{ required: true, message: () => t('common.required'), trigger: 'blur' }],
  userId: [{ required: true, message: () => t('common.required'), trigger: 'blur' }],
}

async function load() {
  loading.value = true
  try {
    const res = await listConfigs({ page: 1, pageSize: 50 })
    configs.value = res.data.items
  } finally {
    loading.value = false
  }
}

function componentTagType(t: string): 'success' | 'primary' | 'warning' {
  const map: Record<string, 'success' | 'primary' | 'warning'> = {
    'task-list': 'success',
    'task-detail': 'primary',
    'task-card': 'warning'
  }
  return map[t] || 'primary'
}

function handleAdd() {
  editing.value = null
  Object.assign(form, {
    id: undefined,
    componentType: 'task-list',
    configKey: '',
    configName: '',
    userId: 'guest',
    isOpen: 1,
    callbackUrl: '',
    configStr: '{}',
  })
  dialogVisible.value = true
}

async function handleEdit(row: WebEmbedConfig) {
  const res = await getConfig(row.id!)
  Object.assign(form, {
    id: res.data.id,
    componentType: res.data.componentType,
    configKey: res.data.configKey,
    configName: res.data.configName,
    userId: res.data.userId,
    isOpen: res.data.isOpen,
    callbackUrl: res.data.callbackUrl || '',
    configStr: JSON.stringify(res.data.config || {}, null, 2),
  })
  editing.value = res.data
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  submitting.value = true
  try {
    let config: any = {}
    try {
      config = form.configStr ? JSON.parse(form.configStr) : {}
    } catch {
      ElMessage.error(t('embedConfig.configJsonError'))
      return
    }

    const data: any = {
      componentType: form.componentType,
      configKey: form.configKey,
      configName: form.configName,
      userId: form.userId,
      isOpen: form.isOpen,
      callbackUrl: form.isOpen ? undefined : form.callbackUrl,
      config
    }

    if (editing.value?.id) {
      data.id = editing.value.id
      await updateConfig(editing.value.id, data)
    } else {
      await createConfig(data)
    }
    ElMessage.success(t('common.saveSuccess'))
    dialogVisible.value = false
    load()
  } catch (err: any) {
    ElMessage.error(err.message || t('common.saveFailed'))
  } finally {
    submitting.value = false
  }
}

async function handleToggle(row: WebEmbedConfig, v: boolean) {
  try {
    await toggleEnabled(row.id!, v ? 1 : 0)
    ElMessage.success(v ? t('common.saveSuccess') : t('common.disabledSuccess'))
    load()
  } catch (err: any) {
    ElMessage.error(err.message || t('common.operationFailed'))
    load()
  }
}

async function handleDelete(row: WebEmbedConfig) {
  try {
    await deleteConfig(row.id!)
    ElMessage.success(t('common.deleteSuccess'))
    load()
  } catch (err: any) {
    ElMessage.error(err.message || t('common.deleteFailed'))
  }
}

const previewVisible = ref(false)
const previewUrl = ref('')
async function handlePreview(row: WebEmbedConfig) {
  previewVisible.value = true
  try {
    const res = await getPreviewUrl(row.id!, row.componentType)
    previewUrl.value = res.data.url
  } catch (err: any) {
    ElMessage.error(err.message || t('embedConfig.previewUrlFailed'))
  }
}

const embedVisible = ref(false)
const embedUrl = ref('')
async function handleEmbed(row: WebEmbedConfig) {
  embedVisible.value = true
  try {
    const res = await getPreviewUrl(row.id!, row.componentType)
    embedUrl.value = res.data.url
  } catch (err: any) {
    ElMessage.error(err.message || t('embedConfig.urlFailed'))
  }
}

async function copyEmbedUrl() {
  try {
    await navigator.clipboard.writeText(embedUrl.value)
    ElMessage.success(t('embedConfig.copySuccess'))
  } catch {
    ElMessage.error(t('embedConfig.copyFailedManual'))
  }
}

onMounted(load)
</script>

<style scoped lang="scss">
.preview-iframe {
  width: 100%;
  height: calc(100vh - 160px);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.url-input {
  font-family: 'SF Mono', Monaco, monospace;
  font-size: 12px;
}

.code-block {
  padding: 12px 0;
  border-top: 1px solid var(--el-border-color-lighter);
  font-size: 12px;
  font-family: 'SF Mono', Monaco, monospace;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 8px 0 0;
}
</style>
