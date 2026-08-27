<template>
  <div class="lotask-page lotask-web-embed-config">
    <FcSectionHeader :title="t('lotask.system.webEmbed.title')" :back="true" @back="router.back()">
      <template #actions>
        <FcButton variant="primary" :icon="Plus" @click="handleAdd">
          {{ t('lotask.system.webEmbed.addNew') }}
        </FcButton>
      </template>
    </FcSectionHeader>

    <FcSection padding="md" shadow="sm">
      <el-table
        v-loading="loading"
        :data="pageData.items"
        stripe
        border
        class="fc-table"
      >
        <el-table-column :label="t('lotask.system.webEmbed.column.componentType')" width="160">
          <template #default="{ row }">
            <FcTag :color="componentTagColor(row.componentType)" size="sm">
              {{ row.componentType }}
            </FcTag>
          </template>
        </el-table-column>
        <el-table-column :label="t('lotask.system.webEmbed.column.configKey')" prop="configKey" min-width="160" />
        <el-table-column :label="t('lotask.system.webEmbed.column.configName')" prop="configName" min-width="160" />
        <el-table-column :label="t('lotask.system.webEmbed.column.userId')" prop="userId" width="120" />
        <el-table-column :label="t('lotask.system.webEmbed.column.mode')" width="100">
          <template #default="{ row }">
            <FcTag :color="row.isOpen ? 'warning' : 'primary'" size="sm">
              {{ row.isOpen ? t('lotask.system.webEmbed.open') : t('lotask.system.webEmbed.auth') }}
            </FcTag>
          </template>
        </el-table-column>
        <el-table-column :label="t('lotask.system.webEmbed.column.enabled')" width="100" align="center">
          <template #default="{ row }">
            <FcSwitch
              :model-value="!!row.isEnabled"
              :loading="togglingId === row.id"
              @change="(v) => handleToggle(row, Boolean(v))"
            />
          </template>
        </el-table-column>
        <el-table-column :label="t('lotask.system.webEmbed.column.action')" width="280" fixed="right">
          <template #default="{ row }">
            <FcButton variant="text" size="sm" @click="handlePreview(row)">
              {{ t('lotask.system.webEmbed.actions.preview') }}
            </FcButton>
            <FcButton variant="text" size="sm" @click="handleEmbed(row)">
              {{ t('lotask.system.webEmbed.actions.embed') }}
            </FcButton>
            <FcButton variant="text" size="sm" :icon="Edit" @click="handleEdit(row)">
              {{ t('lotask.system.webEmbed.actions.edit') }}
            </FcButton>
            <FcButton variant="text" size="sm" :icon="Delete" @click="handleDelete(row)">
              {{ t('lotask.system.webEmbed.actions.delete') }}
            </FcButton>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <FcPagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="pageData.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
        />
      </div>
    </FcSection>

    <!-- 新建 / 编辑 -->
    <FcDialog
      v-model:open="dialogVisible"
      :title="editing ? t('lotask.system.webEmbed.edit') : t('lotask.system.webEmbed.addNew')"
      width="640px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item :label="t('lotask.system.webEmbed.componentType')" prop="componentType" class="fc-form-item">
          <FcSelect v-model="form.componentType" :disabled="!!editing">
            <FcSelect.Option label="task-list" value="task-list" />
            <FcSelect.Option label="task-detail" value="task-detail" />
            <FcSelect.Option label="task-card" value="task-card" />
          </FcSelect>
        </el-form-item>
        <el-form-item :label="t('lotask.system.webEmbed.configKey')" prop="configKey" class="fc-form-item">
          <el-input
            v-model="form.configKey"
            :placeholder="t('lotask.system.webEmbed.configKeyPlaceholder')"
            :maxlength="64"
            class="fc-input"
          />
        </el-form-item>
        <el-form-item :label="t('lotask.system.webEmbed.configName')" prop="configName" class="fc-form-item">
          <el-input v-model="form.configName" class="fc-input" />
        </el-form-item>
        <el-form-item :label="t('lotask.system.webEmbed.userId')" prop="userId" class="fc-form-item">
          <el-input v-model="form.userId" class="fc-input" />
        </el-form-item>
        <el-form-item :label="t('lotask.system.webEmbed.mode')" class="fc-form-item">
          <el-radio-group v-model="form.isOpen">
            <el-radio :value="0">{{ t('lotask.system.webEmbed.auth') }}</el-radio>
            <el-radio :value="1">{{ t('lotask.system.webEmbed.open') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          v-if="!form.isOpen"
          :label="t('lotask.system.webEmbed.callbackUrl')"
          class="fc-form-item"
        >
          <el-input
            v-model="form.callbackUrl"
            :placeholder="t('lotask.system.webEmbed.callbackUrlPlaceholder')"
            class="fc-input"
          />
        </el-form-item>
        <el-form-item :label="t('lotask.system.webEmbed.allowedDomains')" class="fc-form-item">
          <el-input v-model="form.allowedDomains" class="fc-input" />
        </el-form-item>
        <el-form-item :label="t('lotask.system.webEmbed.config')" class="fc-form-item">
          <el-input
            v-model="form.configStr"
            type="textarea"
            :rows="4"
            placeholder='{"task-list": {"theme": "light"}}'
            class="fc-input config-input"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <FcButton variant="secondary" @click="dialogVisible = false">
          {{ t('lotask.system.common.cancel') }}
        </FcButton>
        <FcButton variant="primary" :loading="submitting" @click="handleSubmit">
          {{ t('lotask.system.common.confirm') }}
        </FcButton>
      </template>
    </FcDialog>

    <!-- 预览抽屉 -->
    <FcDrawer
      v-model:open="previewVisible"
      :title="t('lotask.system.webEmbed.preview.title')"
      direction="rtl"
      size="900px"
    >
      <iframe v-if="previewUrl" :src="previewUrl" class="preview-iframe" />
    </FcDrawer>

    <!-- 嵌入代码抽屉 -->
    <FcDrawer
      v-model:open="embedVisible"
      :title="t('lotask.system.webEmbed.embedDrawer.title')"
      direction="rtl"
      size="640px"
    >
      <p class="embed-desc">{{ t('lotask.system.webEmbed.embedDrawer.desc') }}</p>
      <el-input
        v-model="embedUrl"
        type="textarea"
        :rows="3"
        readonly
        class="fc-input url-input"
      />
      <FcButton variant="primary" class="copy-btn" @click="copyEmbedUrl">
        {{ t('lotask.system.webEmbed.embedDrawer.copy') }}
      </FcButton>
      <h4 class="usage-title">{{ t('lotask.system.webEmbed.embedDrawer.usage') }}</h4>
      <pre class="code-block">&lt;iframe
  src="{{ embedUrl }}"
  width="100%"
  height="800px"
  frameborder="0"
&gt;&lt;/iframe&gt;</pre>
    </FcDrawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import {
  listConfigs,
  getConfig,
  createConfig,
  updateConfig,
  deleteConfig,
  toggleEnabled,
  getPreviewUrl,
  type EmbedConfigQuery,
} from '@/api/embedConfig'
import type { WebEmbedConfig } from '@/api/types'
import { useClipboard } from '@/composables'
import { toast } from '@/components/sdk'
import FcSection from '@/components/sdk/section/FcSection.vue'
import FcSectionHeader from '@/components/sdk/section/FcSectionHeader.vue'
import FcTag from '@/components/sdk/display/FcTag.vue'
import FcDialog from '@/components/sdk/overlay/FcDialog.vue'
import FcDrawer from '@/components/sdk/overlay/FcDrawer.vue'
import FcSwitch from '@/components/sdk/form/FcSwitch.vue'
import FcSelect from '@/components/sdk/form/FcSelect.vue'
import FcButton from '@/components/sdk/form/FcButton.vue'
import FcPagination from '@/components/sdk/navigation/FcPagination.vue'

defineOptions({ name: 'LotaskWebEmbedConfigPage' })

const { t } = useI18n()
const router = useRouter()
const { copy } = useClipboard()

interface EmbedConfigPageView {
  items: WebEmbedConfig[]
  total: number
  page: number
  pageSize: number
}

const loading = ref(false)
const submitting = ref(false)
const page = ref(1)
const pageSize = ref(10)
const pageData = ref<EmbedConfigPageView>({ items: [], total: 0, page: 1, pageSize: 10 })
const togglingId = ref<number | null>(null)

const dialogVisible = ref(false)
const editing = ref<WebEmbedConfig | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  id: undefined as number | undefined,
  componentType: 'task-list' as WebEmbedConfig['componentType'],
  configKey: '',
  configName: '',
  userId: 'guest',
  isOpen: 1,
  callbackUrl: '',
  allowedDomains: '',
  configStr: '{}',
})

const rules = computed<FormRules>(() => ({
  componentType: [
    { required: true, message: () => t('lotask.system.common.required'), trigger: 'change' },
  ],
  configKey: [
    { required: true, message: () => t('lotask.system.common.required'), trigger: 'blur' },
    { max: 64, message: () => t('lotask.system.webEmbed.configKeyPlaceholder'), trigger: 'blur' },
  ],
  configName: [
    { required: true, message: () => t('lotask.system.common.required'), trigger: 'blur' },
  ],
  userId: [
    { required: true, message: () => t('lotask.system.common.required'), trigger: 'blur' },
  ],
}))

async function load() {
  loading.value = true
  try {
    const params: EmbedConfigQuery = { page: page.value, pageSize: pageSize.value }
    const res = await listConfigs(params)
    const data = res as unknown as EmbedConfigPageView
    pageData.value = {
      items: Array.isArray(data.items) ? data.items : [],
      total: Number(data.total) || 0,
      page: Number(data.page) || page.value,
      pageSize: Number(data.pageSize) || pageSize.value,
    }
  } catch (err) {
    console.error('load embed configs failed:', err)
  } finally {
    loading.value = false
  }
}

function componentTagColor(t: string): 'success' | 'primary' | 'warning' {
  const map: Record<string, 'success' | 'primary' | 'warning'> = {
    'task-list': 'success',
    'task-detail': 'primary',
    'task-card': 'warning',
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
    allowedDomains: '',
    configStr: '{}',
  })
  dialogVisible.value = true
}

async function handleEdit(row: WebEmbedConfig) {
  try {
    const res = await getConfig(row.id!)
    const data = res as WebEmbedConfig
    Object.assign(form, {
      id: data.id,
      componentType: data.componentType,
      configKey: data.configKey,
      configName: data.configName,
      userId: data.userId,
      isOpen: data.isOpen,
      callbackUrl: data.callbackUrl || '',
      allowedDomains: data.allowedDomains || '',
      configStr: JSON.stringify(data.config || {}, null, 2),
    })
    editing.value = data
    dialogVisible.value = true
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : ''
    ElMessage.error(msg || t('lotask.system.webEmbed.saveFailed'))
  }
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (!form.isOpen) {
    if (!form.callbackUrl || !/^https?:\/\//.test(form.callbackUrl)) {
      ElMessage.error(t('lotask.system.webEmbed.callbackUrlPlaceholder'))
      return
    }
  }

  let configObj: Record<string, unknown>
  try {
    configObj = form.configStr ? JSON.parse(form.configStr) : {}
  } catch {
    ElMessage.error(t('lotask.system.webEmbed.configJsonError'))
    return
  }

  submitting.value = true
  try {
    const data: Record<string, unknown> = {
      componentType: form.componentType,
      configKey: form.configKey,
      configName: form.configName,
      userId: form.userId,
      isOpen: form.isOpen,
      callbackUrl: form.isOpen ? undefined : form.callbackUrl,
      allowedDomains: form.allowedDomains || undefined,
      config: configObj,
    }
    if (editing.value?.id) {
      await updateConfig(editing.value.id, data as unknown as WebEmbedConfig)
    } else {
      await createConfig(data as unknown as WebEmbedConfig)
    }
    ElMessage.success(t('lotask.system.webEmbed.saveSuccess'))
    dialogVisible.value = false
    await load()
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : ''
    ElMessage.error(msg || t('lotask.system.webEmbed.saveFailed'))
  } finally {
    submitting.value = false
  }
}

async function handleToggle(row: WebEmbedConfig, v: boolean) {
  if (row.id == null) return
  togglingId.value = row.id
  try {
    await toggleEnabled(row.id, v)
    ElMessage.success(t('lotask.system.webEmbed.toggleSuccess'))
    await load()
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : ''
    ElMessage.error(msg || t('lotask.system.webEmbed.operationFailed'))
    await load()
  } finally {
    togglingId.value = null
  }
}

async function handleDelete(row: WebEmbedConfig) {
  try {
    await toast.confirm({
      title: t('lotask.system.common.confirm'),
      message: t('lotask.system.webEmbed.deleteConfirm'),
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    if (row.id == null) return
    await deleteConfig(row.id)
    ElMessage.success(t('lotask.system.webEmbed.deleteSuccess'))
    await load()
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : ''
    ElMessage.error(msg || t('lotask.system.webEmbed.deleteFailed'))
  }
}

const previewVisible = ref(false)
const previewUrl = ref('')

async function handlePreview(row: WebEmbedConfig) {
  previewVisible.value = true
  previewUrl.value = ''
  try {
    if (row.id == null) return
    const res = await getPreviewUrl(row.id, row.componentType)
    const data = res as { url?: string }
    previewUrl.value = data.url || ''
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : ''
    ElMessage.error(msg || t('lotask.system.webEmbed.preview.loadFailed'))
  }
}

const embedVisible = ref(false)
const embedUrl = ref('')

async function handleEmbed(row: WebEmbedConfig) {
  embedVisible.value = true
  embedUrl.value = ''
  try {
    if (row.id == null) return
    const res = await getPreviewUrl(row.id, row.componentType)
    const data = res as { url?: string }
    embedUrl.value = data.url || ''
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : ''
    ElMessage.error(msg || t('lotask.system.webEmbed.embedDrawer.copyFailed'))
  }
}

async function copyEmbedUrl() {
  if (!embedUrl.value) return
  await copy(embedUrl.value)
}

watch([page, pageSize], () => {
  load()
})

onMounted(load)
</script>

<style scoped lang="scss">
.lotask-web-embed-config {
  display: flex;
  flex-direction: column;
  gap: var(--app-block-mb, 16px);
}

.pagination-row {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.config-input {
  :deep(textarea) {
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    font-size: 12px;
  }
}

.preview-iframe {
  width: 100%;
  height: calc(100vh - 160px);
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 8px;
}

.embed-desc {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin: 0 0 12px;
}

.url-input {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}

.copy-btn {
  margin-top: 12px;
}

.usage-title {
  margin: 24px 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.code-block {
  padding: 12px;
  background: var(--app-bg-muted, #f5f5f7);
  border-radius: 6px;
  font-size: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}
</style>
