<template>
  <div class="lotask-page lotask-task-type-config">
    <FcSectionHeader :title="t('lotask.system.taskType.title')" :back="true" @back="router.back()">
      <template #actions>
        <FcButton variant="secondary" :loading="loading" :icon="Refresh" @click="load">
          {{ t('lotask.system.common.refresh') }}
        </FcButton>
        <FcButton variant="primary" :icon="Plus" @click="handleAdd">
          {{ t('lotask.system.taskType.addNew') }}
        </FcButton>
      </template>
    </FcSectionHeader>

    <FcSection padding="md" shadow="sm">
      <el-table
        v-loading="loading"
        :data="types"
        stripe
        border
        class="fc-table"
      >
        <el-table-column :label="t('lotask.system.taskType.typeKey')" prop="typeKey" min-width="140">
          <template #default="{ row }">
            <code>{{ row.typeKey }}</code>
          </template>
        </el-table-column>
        <el-table-column :label="t('lotask.system.taskType.typeName')" prop="name" min-width="140" />
        <el-table-column :label="t('lotask.system.taskType.maxConcurrency')" prop="concurrencyLimit" width="110" align="center" />
        <el-table-column :label="t('lotask.system.taskType.execTimeout')" prop="timeoutSeconds" width="120" align="center">
          <template #default="{ row }">
            <span>{{ row.timeoutSeconds }} {{ t('lotask.system.taskType.execTimeoutUnit') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('lotask.system.taskType.maxRetries')" prop="maxRetries" width="100" align="center" />
        <el-table-column :label="t('lotask.system.taskType.column.steps')" min-width="240">
          <template #default="{ row }">
            <template v-if="row.stepsConfig && row.stepsConfig.length">
              <FcTag
                v-for="(step, idx) in row.stepsConfig"
                :key="idx"
                color="primary"
                size="sm"
                class="step-tag"
              >
                {{ step.name }} ({{ step.weight || 0 }}%)
              </FcTag>
            </template>
            <FcTag v-else color="gray" size="sm">
              {{ t('lotask.system.taskType.column.noSteps') }}
            </FcTag>
          </template>
        </el-table-column>
        <el-table-column :label="t('lotask.system.taskType.enabled')" width="100" align="center">
          <template #default="{ row }">
            <FcTag :color="row.isEnabled ? 'success' : 'danger'" size="sm">
              {{ row.isEnabled ? t('lotask.system.common.enabled') : t('lotask.system.common.disabled') }}
            </FcTag>
          </template>
        </el-table-column>
        <el-table-column :label="t('lotask.system.taskType.createdAt')" width="170" align="center">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('lotask.system.taskType.updatedAt')" width="170" align="center">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('lotask.system.common.action')" width="160" fixed="right">
          <template #default="{ row }">
            <FcButton variant="text" size="sm" :icon="Edit" @click="handleEdit(row)">
              {{ t('lotask.system.common.edit') }}
            </FcButton>
            <FcButton variant="text" size="sm" :icon="Delete" @click="handleDelete(row)">
              {{ t('lotask.system.common.delete') }}
            </FcButton>
          </template>
        </el-table-column>
      </el-table>
    </FcSection>

    <!-- 新建 / 编辑对话框 -->
    <FcDialog
      v-model:open="dialogVisible"
      :title="editing ? t('lotask.system.taskType.edit') : t('lotask.system.taskType.addNew')"
      width="720px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="140px">
        <el-form-item :label="t('lotask.system.taskType.tenant')" prop="tenantId" class="fc-form-item">
          <FcSelect v-model="form.tenantId" :placeholder="t('lotask.system.taskType.tenantPlaceholder')" filterable>
            <el-option
              v-for="tenant in tenantOptions"
              :key="tenant.id"
              :label="tenant.name"
              :value="tenant.id"
            />
          </FcSelect>
        </el-form-item>
        <el-form-item :label="t('lotask.system.taskType.typeKey')" prop="typeKey" class="fc-form-item">
          <el-input
            v-model="form.typeKey"
            :disabled="!!editing"
            :placeholder="t('lotask.system.taskType.typeKeyPlaceholder')"
            class="fc-input"
          />
        </el-form-item>
        <el-form-item :label="t('lotask.system.taskType.typeName')" prop="typeName" class="fc-form-item">
          <el-input v-model="form.typeName" class="fc-input" />
        </el-form-item>
        <el-form-item :label="t('lotask.system.taskType.description')" class="fc-form-item">
          <el-input v-model="form.description" type="textarea" :rows="2" class="fc-input" />
        </el-form-item>
        <el-form-item :label="t('lotask.system.taskType.maxConcurrency')" class="fc-form-item">
          <el-input-number v-model="form.maxConcurrency" :min="1" :max="100" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('lotask.system.taskType.execTimeout')" class="fc-form-item">
          <el-input-number v-model="form.execTimeoutSec" :min="60" :max="86400" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('lotask.system.taskType.maxRetries')" class="fc-form-item">
          <el-input-number v-model="form.maxRetryCount" :min="0" :max="10" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('lotask.system.taskType.enabled')" class="fc-form-item">
          <FcSwitch v-model="form.isEnabled" />
        </el-form-item>

        <div class="steps-section">
          <h4 class="steps-title">{{ t('lotask.system.taskType.stepsEditor.title') }}</h4>
          <div v-if="stepsError" class="steps-error">{{ stepsError }}</div>
          <div class="steps-table">
            <div class="steps-row steps-header">
              <div class="col-key">{{ t('lotask.system.taskType.stepsEditor.stepKey') }}</div>
              <div class="col-name">{{ t('lotask.system.taskType.stepsEditor.stepName') }}</div>
              <div class="col-weight">{{ t('lotask.system.taskType.stepsEditor.stepWeight') }}</div>
              <div class="col-action"></div>
            </div>
            <div
              v-for="(step, idx) in form.steps"
              :key="idx"
              class="steps-row"
            >
              <div class="col-key">
                <el-input
                  v-model="step.key"
                  size="small"
                  :placeholder="t('lotask.system.taskType.stepsEditor.stepKeyPlaceholder')"
                  class="fc-input"
                />
              </div>
              <div class="col-name">
                <el-input
                  v-model="step.name"
                  size="small"
                  :placeholder="t('lotask.system.taskType.stepsEditor.stepNamePlaceholder')"
                  class="fc-input"
                />
              </div>
              <div class="col-weight">
                <el-input-number
                  v-model="step.weight"
                  size="small"
                  :min="1"
                  :max="100"
                  controls-position="right"
                  style="width: 100%"
                />
              </div>
              <div class="col-action">
                <FcButton
                  variant="text"
                  size="sm"
                  :icon="Delete"
                  @click="removeStep(idx)"
                />
              </div>
            </div>
            <FcButton
              variant="secondary"
              :icon="Plus"
              size="sm"
              class="add-step-btn"
              @click="addStep"
            >
              {{ t('lotask.system.taskType.stepsEditor.addStep') }}
            </FcButton>
          </div>
        </div>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Edit, Delete } from '@element-plus/icons-vue'
import {
  getAllTaskTypeConfigs,
  saveTaskTypeConfig,
  deleteTaskTypeConfig,
} from '@/api/admin'
import type { TaskTypeConfig } from '@/api/types'
import { listTenants, type TenantItem } from '@/api/tenants'
import { formatDateTime } from '@/utils/taskStatus'
import { toast } from '@/components/sdk'
import FcSection from '@/components/sdk/section/FcSection.vue'
import FcSectionHeader from '@/components/sdk/section/FcSectionHeader.vue'
import FcTag from '@/components/sdk/display/FcTag.vue'
import FcDialog from '@/components/sdk/overlay/FcDialog.vue'
import FcSwitch from '@/components/sdk/form/FcSwitch.vue'
import FcButton from '@/components/sdk/form/FcButton.vue'

defineOptions({ name: 'LotaskTaskTypeConfigPage' })

interface StepItem {
  key: string
  name: string
  weight: number
}

const { t } = useI18n()
const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const types = ref<TaskTypeConfig[]>([])
const dialogVisible = ref(false)
const editing = ref<TaskTypeConfig | null>(null)
const formRef = ref<FormInstance>()
const stepsError = ref('')

const tenantOptions = ref<TenantItem[]>([])

const form = reactive({
  id: undefined as number | undefined,
  tenantId: undefined as number | undefined,
  typeKey: '',
  typeName: '',
  description: '',
  maxConcurrency: 10,
  execTimeoutSec: 600,
  maxRetryCount: 3,
  isEnabled: true,
  steps: [] as StepItem[],
})

const rules = computed<FormRules>(() => ({
  tenantId: [
    { required: true, message: () => t('lotask.system.common.required'), trigger: 'change' },
  ],
  typeKey: [
    { required: true, message: () => t('lotask.system.common.required'), trigger: 'blur' },
    {
      pattern: /^[a-z_]+$/,
      message: () => t('lotask.system.taskType.stepsEditor.keyPatternError'),
      trigger: 'blur',
    },
  ],
  typeName: [
    { required: true, message: () => t('lotask.system.common.required'), trigger: 'blur' },
  ],
}))

async function load() {
  loading.value = true
  try {
    const res = await getAllTaskTypeConfigs()
    types.value = Array.isArray(res) ? res : []
  } catch (err) {
    console.error('load task types failed:', err)
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    id: undefined,
    typeKey: '',
    typeName: '',
    description: '',
    maxConcurrency: 10,
    execTimeoutSec: 600,
    maxRetryCount: 3,
    isEnabled: true,
    steps: [],
  })
  stepsError.value = ''
}

async function loadTenantOptions() {
  try {
    const res = await listTenants({ page: 1, pageSize: 200 })
    tenantOptions.value = (res.items || []).filter((t) => t.status === 'ACTIVE')
  } catch (err) {
    console.error('load tenants failed:', err)
  }
}

function handleAdd() {
  editing.value = null
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row: TaskTypeConfig) {
  editing.value = row
  resetForm()
  Object.assign(form, {
    id: row.id,
    tenantId: row.tenantId,
    typeKey: row.typeKey,
    typeName: row.name,
    description: row.description || '',
    maxConcurrency: row.concurrencyLimit,
    execTimeoutSec: row.timeoutSeconds,
    maxRetryCount: row.maxRetries,
    isEnabled: !!row.isEnabled,
    steps: (row.stepsConfig || []).map((s: { key?: string; name?: string; weight?: number }) => ({
      key: s.key || '',
      name: s.name || '',
      weight: Number(s.weight ?? 0),
    })),
  })
  dialogVisible.value = true
}

async function handleDelete(row: TaskTypeConfig) {
  try {
    await toast.confirm({
      title: t('lotask.system.common.confirm'),
      message: t('lotask.system.taskType.deleteConfirm', { name: row.name }),
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteTaskTypeConfig(row.typeKey)
    ElMessage.success(t('lotask.system.taskType.deleteSuccess'))
    await load()
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : ''
    ElMessage.error(msg || t('lotask.system.taskType.deleteFailed'))
  }
}

function addStep() {
  form.steps.push({ key: '', name: '', weight: 10 })
}

function removeStep(idx: number) {
  form.steps.splice(idx, 1)
}

function validateSteps(): boolean {
  stepsError.value = ''
  for (const step of form.steps) {
    if (!step.key) {
      stepsError.value = t('lotask.system.taskType.stepsEditor.keyRequired')
      return false
    }
    if (!/^[a-z_]+$/.test(step.key)) {
      stepsError.value = t('lotask.system.taskType.stepsEditor.keyPatternError')
      return false
    }
    if (!step.name) {
      stepsError.value = t('lotask.system.taskType.stepsEditor.nameRequired')
      return false
    }
    if (!step.weight || step.weight < 1 || step.weight > 100) {
      stepsError.value = t('lotask.system.taskType.stepsEditor.weightRange')
      return false
    }
  }
  return true
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (!validateSteps()) return

  submitting.value = true
  try {
    const payload: Record<string, unknown> = {
      tenantId: form.tenantId,
      typeKey: form.typeKey,
      name: form.typeName,
      description: form.description,
      concurrencyLimit: form.maxConcurrency,
      timeoutSeconds: form.execTimeoutSec,
      maxRetries: form.maxRetryCount,
      isEnabled: form.isEnabled ? 1 : 0,
      stepsDefinition: form.steps.map((s) => ({
        key: s.key,
        name: s.name,
        weight: s.weight,
      })),
    }
    if (editing.value?.id) {
      payload.id = editing.value.id
    }

    await saveTaskTypeConfig(payload as unknown as TaskTypeConfig)
    ElMessage.success(t('lotask.system.taskType.saveSuccess'))
    dialogVisible.value = false
    await load()
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : ''
    ElMessage.error(msg || t('lotask.system.taskType.saveFailed'))
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  load()
  loadTenantOptions()
})
</script>

<style scoped lang="scss">
.lotask-task-type-config {
  display: flex;
  flex-direction: column;
  gap: var(--app-block-mb, 16px);
}

.step-tag {
  margin: 2px;
}

.steps-section {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter, #ebeef5);

  .steps-title {
    margin: 0 0 12px 0;
    font-size: 14px;
    font-weight: 600;
    color: var(--el-text-color-regular);
  }

  .steps-error {
    color: var(--el-color-danger);
    font-size: 12px;
    margin-bottom: 8px;
  }
}

.steps-table {
  width: 100%;
}

.steps-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;

  &.steps-header {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    padding: 0 4px 4px;
    border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
    margin-bottom: 8px;
  }
}

.col-key { flex: 0 0 160px; }
.col-name { flex: 0 0 160px; }
.col-weight { flex: 1 1 120px; }
.col-action { flex: 0 0 40px; text-align: center; }

.add-step-btn {
  margin-top: 8px;
}
</style>
