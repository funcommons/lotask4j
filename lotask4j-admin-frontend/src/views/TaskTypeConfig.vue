<template>
  <div class="app-page">
    <TitledSection :title="t('taskTypeConfig.title')" icon="ri-apps-2-line">
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">
          {{ t('taskList.action.refresh') }}
        </el-button>
        <el-button type="primary" :icon="Plus" @click="handleAdd">
          {{ t('taskTypeConfig.addNew') }}
        </el-button>
      </template>
      <WorkSection>
        <el-table v-loading="loading" :data="types" stripe border>
          <el-table-column :label="t('taskTypeConfig.typeKey')" prop="typeKey" min-width="140">
            <template #default="{ row }">
              <code>{{ row.typeKey }}</code>
            </template>
          </el-table-column>
          <el-table-column :label="t('taskTypeConfig.typeName')" prop="name" min-width="120" />
          <el-table-column :label="t('taskTypeConfig.maxConcurrency')" prop="concurrencyLimit" width="100" align="center" />
          <el-table-column :label="t('taskTypeConfig.execTimeout')" prop="timeoutSeconds" width="120" align="center">
            <template #default="{ row }">{{ row.timeoutSeconds }}s</template>
          </el-table-column>
          <el-table-column :label="t('taskTypeConfig.maxRetries')" prop="maxRetries" width="100" align="center" />
          <el-table-column :label="t('taskTypeConfigExt.column.steps')" min-width="220">
            <template #default="{ row }">
              <template v-if="row.stepsConfig && row.stepsConfig.length">
                <el-tag
                  v-for="(step, idx) in row.stepsConfig"
                  :key="idx"
                  type="primary"
                  size="small"
                  effect="light"
                  style="margin: 2px"
                >
                  {{ step.name }} ({{ step.weight }}%)
                </el-tag>
              </template>
              <el-tag v-else type="info" size="small">{{ t('taskTypeConfigExt.column.noSteps') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('taskTypeConfig.enabled')" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.isEnabled ? 'success' : 'danger'" size="small" effect="light">
                {{ row.isEnabled ? t('common.enabled') : t('common.disabled') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('taskTypeConfigExt.column.createdAt')" width="170" align="center">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column :label="t('taskTypeConfigExt.column.updatedAt')" width="170" align="center">
            <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column :label="t('common.action')" width="140" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" size="small" link :icon="Edit" @click="handleEdit(row)">
                {{ t('taskTypeConfig.edit') }}
              </el-button>
              <el-popconfirm
                :title="t('taskTypeConfigExt.deleteConfirm', { name: row.name })"
                :confirm-button-text="t('common.confirm')"
                :cancel-button-text="t('common.cancel')"
                @confirm="handleDelete(row)"
              >
                <template #reference>
                  <el-button type="danger" size="small" link :icon="Delete">
                    {{ t('common.delete') }}
                  </el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </WorkSection>
    </TitledSection>

    <AppDialog
      :visible="dialogVisible"
      @update:visible="dialogVisible = $event"
      :title="editing ? t('taskTypeConfig.edit') : t('taskTypeConfig.addNew')"
      width="720px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="140px">
        <el-form-item :label="t('taskTypeConfig.typeKey')" prop="typeKey">
          <el-input v-model="form.typeKey" :disabled="!!editing" :placeholder="t('taskTypeConfigExt.stepsEditor.stepKeyPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('taskTypeConfig.typeName')" prop="typeName">
          <el-input v-model="form.typeName" />
        </el-form-item>
        <el-form-item :label="t('taskTypeConfig.description')">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="t('taskTypeConfig.maxConcurrency')">
          <el-input-number v-model="form.maxConcurrency" :min="1" :max="100" />
        </el-form-item>
        <el-form-item :label="t('taskTypeConfig.execTimeout')">
          <el-input-number v-model="form.execTimeoutSec" :min="60" :max="86400" />
        </el-form-item>
        <el-form-item :label="t('taskTypeConfig.maxRetries')">
          <el-input-number v-model="form.maxRetryCount" :min="0" :max="10" />
        </el-form-item>
        <el-form-item :label="t('taskTypeConfig.enabled')">
          <el-switch v-model="form.isEnabled" />
        </el-form-item>

        <el-divider content-position="left">{{ t('taskTypeConfigExt.stepsEditor.title') }}</el-divider>

        <el-form-item :label="t('taskTypeConfigExt.stepsEditor.title')" :error="stepsError">
          <div class="steps-table">
            <div class="steps-row steps-header">
              <div class="col-key">{{ t('taskTypeConfigExt.stepsEditor.stepKey') }}</div>
              <div class="col-name">{{ t('taskTypeConfigExt.stepsEditor.stepName') }}</div>
              <div class="col-weight">{{ t('taskTypeConfigExt.stepsEditor.stepWeight') }}</div>
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
                  :placeholder="t('taskTypeConfigExt.stepsEditor.stepKeyPlaceholder')"
                />
              </div>
              <div class="col-name">
                <el-input
                  v-model="step.name"
                  size="small"
                  :placeholder="t('taskTypeConfigExt.stepsEditor.stepNamePlaceholder')"
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
                <el-button
                  type="danger"
                  size="small"
                  link
                  :icon="Delete"
                  @click="removeStep(idx)"
                />
              </div>
            </div>
            <el-button
              type="primary"
              plain
              :icon="Plus"
              size="small"
              style="margin-top: 8px"
              @click="addStep"
            >
              {{ t('taskTypeConfigExt.stepsEditor.addStep') }}
            </el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">{{ t('common.confirm') }}</el-button>
      </template>
    </AppDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance } from 'element-plus'
import { Plus, Refresh, Edit, Delete } from '@element-plus/icons-vue'
import {
  getAllTaskTypeConfigs,
  saveTaskTypeConfig,
  deleteTaskTypeConfig,
  type TaskTypeConfigVO,
} from '@/api/admin'
import { formatTime } from '@/utils/time'
import TitledSection from '@/components/sdk/common/TitledSection.vue'
import WorkSection from '@/components/sdk/common/WorkSection.vue'
import AppDialog from '@/components/sdk/common/AppDialog.vue'

interface StepItem {
  key: string
  name: string
  weight: number
}

const { t } = useI18n()
const loading = ref(false)
const submitting = ref(false)
const types = ref<TaskTypeConfigVO[]>([])
const dialogVisible = ref(false)
const editing = ref<TaskTypeConfigVO | null>(null)
const formRef = ref<FormInstance>()
const stepsError = ref('')

const form = reactive({
  id: undefined as number | undefined,
  typeKey: '',
  typeName: '',
  description: '',
  maxConcurrency: 10,
  execTimeoutSec: 600,
  maxRetryCount: 3,
  isEnabled: true,
  steps: [] as StepItem[],
})

const rules = {
  typeKey: [
    { required: true, message: () => t('common.required'), trigger: 'blur' },
    { pattern: /^[a-z_]+$/, message: () => t('taskTypeConfigExt.stepsEditor.keyPatternError'), trigger: 'blur' },
  ],
  typeName: [{ required: true, message: () => t('common.required'), trigger: 'blur' }],
}

async function load() {
  loading.value = true
  try {
    const res = await getAllTaskTypeConfigs()
    types.value = res.data || []
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

function handleAdd() {
  editing.value = null
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row: TaskTypeConfigVO) {
  editing.value = row
  const numericId = row.id ? Number(row.id) : undefined
  resetForm()
  Object.assign(form, {
    id: numericId,
    typeKey: row.typeKey,
    typeName: row.name,
    description: row.description || '',
    maxConcurrency: row.concurrencyLimit,
    execTimeoutSec: row.timeoutSeconds,
    maxRetryCount: row.maxRetries,
    isEnabled: !!row.isEnabled,
    steps: (row.stepsConfig || []).map((s: any) => ({
      key: s.key || '',
      name: s.name || '',
      weight: Number(s.weight ?? 0),
    })),
  })
  dialogVisible.value = true
}

async function handleDelete(row: TaskTypeConfigVO) {
  try {
    await deleteTaskTypeConfig(row.typeKey)
    ElMessage.success(t('taskTypeConfigExt.deleteSuccess'))
    load()
  } catch (err: any) {
    ElMessage.error(err.message || t('taskTypeConfigExt.deleteFailed'))
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
      stepsError.value = t('taskTypeConfigExt.stepsEditor.keyRequired')
      return false
    }
    if (!/^[a-z_]+$/.test(step.key)) {
      stepsError.value = t('taskTypeConfigExt.stepsEditor.keyPatternError')
      return false
    }
    if (!step.name) {
      stepsError.value = t('taskTypeConfigExt.stepsEditor.nameRequired')
      return false
    }
    if (!step.weight || step.weight < 1 || step.weight > 100) {
      stepsError.value = t('taskTypeConfigExt.stepsEditor.weightRange')
      return false
    }
  }
  return true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  if (!validateSteps()) return

  submitting.value = true
  try {
    const payload: any = {
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
      payload.id = Number(editing.value.id)
    }

    await saveTaskTypeConfig(payload)
    ElMessage.success(editing.value ? t('settings.saveSuccess') : t('settings.saveSuccess'))
    dialogVisible.value = false
    load()
  } catch (err: any) {
    ElMessage.error(err.message || t('settings.saveFailed'))
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped lang="scss">
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
    border-bottom: 1px solid var(--el-border-color-lighter);
    margin-bottom: 8px;
  }
}

.col-key {
  flex: 0 0 130px;
}

.col-name {
  flex: 0 0 130px;
}

.col-weight {
  flex: 1 1 120px;
}

.col-action {
  flex: 0 0 32px;
  text-align: center;
}

.steps-header .col-action {
  flex: 0 0 32px;
}
</style>
