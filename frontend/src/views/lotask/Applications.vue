<template>
  <div class="lotask-page lotask-applications">
    <FcSectionHeader :title="t('lotask.system.apps.title')" :subtitle="t('lotask.system.apps.subtitle')" :back="true" @back="router.back()">
      <template #actions>
        <div class="search-wrapper">
          <i class="ri-search-line search-icon" />
          <input
            v-model="searchQuery"
            type="text"
            :placeholder="t('lotask.system.apps.searchPlaceholder')"
            class="search-input"
          />
        </div>
        <FcButton variant="primary" :icon="Plus" @click="openCreate">
          {{ t('lotask.system.apps.create') }}
        </FcButton>
      </template>
    </FcSectionHeader>

    <FcFilterBar>
      <FcFilterButton :active="filterStatus === undefined" @click="filterStatus = undefined">
        {{ t('common.filter-all') }}
      </FcFilterButton>
      <FcFilterButton :active="filterStatus === 'ACTIVE'" @click="filterStatus = 'ACTIVE'">
        {{ t('lotask.system.apps.active') }}
      </FcFilterButton>
      <FcFilterButton :active="filterStatus === 'INACTIVE'" @click="filterStatus = 'INACTIVE'">
        {{ t('lotask.system.apps.inactive') }}
      </FcFilterButton>
    </FcFilterBar>

    <FcSection padding="md" shadow="sm">
      <el-table
        v-loading="loading"
        :data="filteredApps"
        stripe
        border
        row-key="id"
        class="fc-table"
        :max-height="600"
      >
        <el-table-column :label="t('lotask.system.apps.column.name')" min-width="200">
          <template #default="{ row }">
            <div class="app-name">
              <strong>{{ row.name }}</strong>
              <span class="app-id-inline">{{ row.id }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column :label="t('lotask.system.apps.column.description')" min-width="200">
          <template #default="{ row }">
            <span class="app-desc">{{ row.description || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column :label="t('lotask.system.apps.column.status')" width="100">
          <template #default="{ row }">
            <FcTag :color="row.status === 'ACTIVE' ? 'success' : 'gray'" size="sm">
              {{ row.status === 'ACTIVE' ? t('lotask.system.apps.active') : t('lotask.system.apps.inactive') }}
            </FcTag>
          </template>
        </el-table-column>

        <el-table-column :label="t('lotask.system.apps.column.createdAt')" width="160">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>

        <el-table-column :label="t('common.actions')" width="240" fixed="right">
          <template #default="{ row }">
            <div class="actions-cell">
              <FcTooltip :content="row.status === 'ACTIVE'
                ? t('lotask.system.apps.deactivate')
                : t('lotask.system.apps.activate')">
                <button class="action-icon-btn" @click="toggleStatus(row)">
                  <i :class="row.status === 'ACTIVE' ? 'ri-forbid-line' : 'ri-check-line'" />
                </button>
              </FcTooltip>
              <FcTooltip :content="t('lotask.system.apps.resetSecret')">
                <button class="action-icon-btn" @click="onResetSecret(row)">
                  <i class="ri-refresh-key-line" />
                </button>
              </FcTooltip>
              <FcTooltip :content="t('common.delete')">
                <button class="action-icon-btn danger" @click="onDelete(row)">
                  <i class="ri-delete-bin-line" />
                </button>
              </FcTooltip>
            </div>
          </template>
        </el-table-column>

        <template #empty><FcEmpty /></template>
      </el-table>
    </FcSection>

    <!-- 创建应用 -->
    <FcDialog
      v-model:open="createVisible"
      :title="t('lotask.system.apps.create')"
      width="480px"
      append-to-body
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item class="fc-form-item" :label="t('lotask.system.apps.column.name')" prop="name">
          <el-input class="fc-input" v-model="form.name" :placeholder="t('lotask.system.apps.namePlaceholder')" />
        </el-form-item>
        <el-form-item class="fc-form-item" :label="t('lotask.system.apps.column.description')">
          <el-input
            class="fc-input"
            v-model="form.description"
            type="textarea"
            :rows="2"
            :placeholder="t('lotask.system.apps.descPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer-right">
          <FcButton @click="createVisible = false">{{ t('common.cancel') }}</FcButton>
          <FcButton variant="primary" :loading="submitting" @click="submitCreate">
            {{ t('common.confirm') }}
          </FcButton>
        </div>
      </template>
    </FcDialog>

    <!-- 创建/重置后一次性展示 secret -->
    <FcDialog
      v-model:open="secretVisible"
      :title="t('lotask.system.apps.secretOnceTitle')"
      width="520px"
      append-to-body
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
    >
      <div class="new-secret-warning">
        <i class="ri-alert-line" />
        <span>{{ t('lotask.system.apps.secretOnceHint') }}</span>
      </div>
      <div class="new-secret-box">
        <code>{{ newSecret }}</code>
        <FcButton :icon="DocumentCopy" @click="onCopySecret">{{ t('lotask.system.apps.copy') }}</FcButton>
      </div>
      <template #footer>
        <FcButton variant="primary" @click="secretVisible = false">{{ t('common.close') }}</FcButton>
      </template>
    </FcDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { DocumentCopy, Plus } from '@element-plus/icons-vue'
import {
  createApplication,
  deleteApplication,
  listApplications,
  resetApplicationSecret,
  setApplicationStatus,
  type ApplicationItem,
} from '@/api/applications'
import {
  FcButton, FcDialog, FcSection, FcSectionHeader,
  FcFilterBar, FcFilterButton, FcTag, FcTooltip, FcEmpty,
} from '@/components/sdk'
import { copySilent } from '@/composables'

defineOptions({ name: 'LotaskApplications' })

const { t } = useI18n()
const router = useRouter()

const loading = ref(false)
const apps = ref<ApplicationItem[]>([])
const searchQuery = ref('')
const filterStatus = ref<'ACTIVE' | 'INACTIVE' | undefined>()

const filteredApps = computed(() => {
  let list = apps.value
  if (filterStatus.value) list = list.filter(a => a.status === filterStatus.value)
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    list = list.filter(a =>
      (a.name || '').toLowerCase().includes(q)
      || String(a.id || '').toLowerCase().includes(q)
      || (a.description || '').toLowerCase().includes(q),
    )
  }
  return list
})

const fetchApps = async () => {
  loading.value = true
  try {
    const res = await listApplications({ page: 1, pageSize: 100 })
    apps.value = res.items || []
  } catch (err: any) {
    ElMessage.error(err.message || t('lotask.system.apps.actionFailed'))
  } finally {
    loading.value = false
  }
}

// —— 创建 ——
const createVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = ref({ name: '', description: '' })

const rules: FormRules = {
  name: [{ required: true, message: t('lotask.system.apps.nameRequired'), trigger: 'blur' }],
}

const openCreate = () => {
  form.value = { name: '', description: '' }
  createVisible.value = true
}

const submitCreate = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const secret = await createApplication(form.value)
    createVisible.value = false
    // secret 明文仅此一次
    newSecret.value = secret.appSecret
    secretVisible.value = true
    ElMessage.success(t('lotask.system.apps.createSuccess'))
    fetchApps()
  } catch (err: any) {
    ElMessage.error(err.message || t('lotask.system.apps.actionFailed'))
  } finally {
    submitting.value = false
  }
}

// —— 一次性 secret 展示 ——
const secretVisible = ref(false)
const newSecret = ref('')

const onCopySecret = async () => {
  const ok = await copySilent(newSecret.value)
  if (ok) ElMessage.success(t('lotask.system.apps.copied'))
  else ElMessage.error(t('lotask.system.apps.actionFailed'))
}

// —— 状态切换 / 重置 / 删除 ——
const toggleStatus = async (row: ApplicationItem) => {
  const next = row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  try {
    await setApplicationStatus(row.id, next)
    ElMessage.success(t('lotask.system.apps.statusUpdated'))
    fetchApps()
  } catch (err: any) {
    ElMessage.error(err.message || t('lotask.system.apps.actionFailed'))
  }
}

const onResetSecret = async (row: ApplicationItem) => {
  try {
    await ElMessageBox.confirm(
      t('lotask.system.apps.resetConfirm'),
      `${row.name} (${row.id})`,
      {
        confirmButtonText: t('lotask.system.apps.resetSecret'),
        cancelButtonText: t('common.cancel'),
        type: 'warning',
      },
    )
  } catch {
    return
  }
  try {
    const secret = await resetApplicationSecret(row.id)
    newSecret.value = secret.appSecret
    secretVisible.value = true
    ElMessage.success(t('lotask.system.apps.resetSuccess'))
  } catch (err: any) {
    ElMessage.error(err.message || t('lotask.system.apps.actionFailed'))
  }
}

const onDelete = async (row: ApplicationItem) => {
  try {
    await ElMessageBox.confirm(
      t('lotask.system.apps.deleteConfirm'),
      `${row.name} (${row.id})`,
      {
        confirmButtonText: t('common.delete'),
        cancelButtonText: t('common.cancel'),
        type: 'warning',
      },
    )
  } catch {
    return
  }
  try {
    await deleteApplication(row.id)
    ElMessage.success(t('lotask.system.apps.deleteSuccess'))
    fetchApps()
  } catch (err: any) {
    ElMessage.error(err.message || t('lotask.system.apps.actionFailed'))
  }
}

const formatDate = (raw?: string) => {
  if (!raw) return '—'
  const d = new Date(raw)
  if (isNaN(d.getTime())) return raw
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(() => {
  fetchApps()
})
</script>

<style scoped lang="scss">
@use '@/styles/benefit-shared' as *;

.lotask-applications {
  display: flex;
  flex-direction: column;
  gap: var(--app-block-mb, 16px);
  min-width: 0;
}

@include search-input(260px);

.app-name {
  display: flex;
  flex-direction: column;
  gap: 2px;

  strong {
    font-size: 14px;
    font-weight: 600;
    color: var(--app-text);
  }
}

.app-id-inline {
  font-family: ui-monospace, monospace;
  font-size: 11px;
  color: var(--app-text-tertiary);
}

.app-desc {
  color: var(--app-text-secondary);
  font-size: 13px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.actions-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.dialog-footer-right {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.new-secret-warning {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: color-mix(in srgb, var(--el-color-warning) 12%, transparent);
  border-left: 3px solid var(--el-color-warning);
  border-radius: var(--app-radius-sm, 6px);
  color: var(--el-text-color-regular);
  font-size: 13px;

  i {
    font-size: 18px;
    color: var(--el-color-warning);
  }
}

.new-secret-box {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  padding: 12px 16px;
  background: var(--app-bg-muted, #f5f5f7);
  border: 1px dashed var(--el-color-warning-light-5);
  border-radius: var(--app-radius-md, 12px);

  code {
    flex: 1;
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    font-size: 13px;
    color: var(--el-color-warning-dark-2);
    word-break: break-all;
  }
}
</style>
