<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { generateAnschreiben, getDraft, getJob, sendDraft, updateDraft } from '../api/jobs'
import LoadingError from '../components/LoadingError.vue'
import StatusBadge from '../components/StatusBadge.vue'
import type { ApplicationDraft, JobOffer } from '../types/job'

const props = defineProps<{ id: string }>()

const job = ref<JobOffer | null>(null)
const draft = ref<ApplicationDraft | null>(null)
const loading = ref(false)
const error = ref('')
const saving = ref(false)
const sending = ref(false)

async function load() {
  loading.value = true
  error.value = ''
  try {
    job.value = await getJob(Number(props.id))
    try {
      draft.value = await getDraft(Number(props.id))
    } catch {
      draft.value = null
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Job konnte nicht geladen werden.'
  } finally {
    loading.value = false
  }
}

async function generate() {
  draft.value = await generateAnschreiben(Number(props.id))
  job.value = await getJob(Number(props.id))
}

async function save() {
  if (!draft.value) return
  saving.value = true
  try {
    draft.value = await updateDraft(draft.value)
  } finally {
    saving.value = false
  }
}

async function send() {
  if (!draft.value) return
  sending.value = true
  try {
    await save()
    draft.value = await sendDraft(draft.value.id)
    job.value = await getJob(Number(props.id))
  } finally {
    sending.value = false
  }
}

onMounted(load)
</script>

<template>
  <LoadingError :loading="loading" :error="error" />

  <article v-if="job" class="details-grid">
    <section class="job-main">
      <div class="page-header compact">
        <div>
          <h1>{{ job.title }}</h1>
          <p>{{ job.company }} · {{ job.location }} · {{ job.source }}</p>
        </div>
        <StatusBadge :status="job.status" />
      </div>

      <div class="meta-row">
        <span>{{ job.remoteType }}</span>
        <span>{{ job.contractType }}</span>
        <span>Match {{ job.matchScore ?? '-' }}/100</span>
        <a :href="job.jobUrl" target="_blank" rel="noreferrer">Original job</a>
      </div>

      <h2>Beschreibung</h2>
      <p class="job-description">{{ job.description }}</p>

      <h2>Matching</h2>
      <p>{{ job.matchExplanation ?? 'Noch keine Matching-Erklaerung vorhanden.' }}</p>
    </section>

    <section class="draft-panel">
      <div class="section-title">
        <h2>Anschreiben</h2>
        <StatusBadge v-if="draft" :status="draft.status" />
      </div>

      <button v-if="!draft" class="primary full" @click="generate">Anschreiben generieren</button>

      <template v-else>
        <textarea v-model="draft.anschreibenText" rows="20" />
        <label>
          CV file path
          <input v-model="draft.cvFilePath" placeholder="/documents/cv.pdf" />
        </label>
        <div class="button-row">
          <button :disabled="saving" @click="save">{{ saving ? 'Speichert...' : 'Speichern' }}</button>
          <button class="primary" :disabled="sending || draft.status === 'SENT'" @click="send">
            {{ sending ? 'Sendet...' : 'Send application with CV' }}
          </button>
        </div>
        <p class="safety-note">Versand erfolgt nur nach diesem Klick. Es gibt keinen automatischen Versand.</p>
      </template>
    </section>
  </article>
</template>
