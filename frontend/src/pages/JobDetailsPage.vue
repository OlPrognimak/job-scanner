<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
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
const generating = ref(false)

const formattedDescription = computed(() => formatDescription(job.value?.description ?? ''))
const jobFacts = computed(() => {
  if (!job.value) return []
  return [
    { label: 'Firma', value: job.value.company },
    { label: 'Ort', value: job.value.location },
    { label: 'Remote', value: job.value.remoteType },
    { label: 'Vertrag', value: job.value.contractType },
    { label: 'Quelle', value: job.value.source },
    { label: 'Verguetung', value: job.value.rateOrSalary ? String(job.value.rateOrSalary) : undefined }
  ].filter((item) => item.value)
})

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
  generating.value = true
  error.value = ''
  try {
    draft.value = await generateAnschreiben(Number(props.id))
    job.value = await getJob(Number(props.id))
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Anschreiben konnte nicht generiert werden.'
  } finally {
    generating.value = false
  }
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

function formatDescription(description: string) {
  const normalized = description
    .replace(/\r/g, '')
    .replace(/\t/g, ' ')
    .replace(/([.!?])\s+(?=[A-ZÄÖÜ])/g, '$1\n')
    .replace(/\s{2,}/g, ' ')
    .trim()

  if (!normalized) return []

  return normalized
    .split(/\n{1,}|\s(?=(?:Aufgaben|Anforderungen|Profil|Rahmenbedingungen|Skills|Beschreibung|Projekt|Start|Dauer|Ort|Remote|Kontakt):)/gi)
    .map((part) => part.trim())
    .filter(Boolean)
    .map((text) => ({
      text: text.replace(/^[-•]\s*/, ''),
      bullet: /^[-•]/.test(text) || /^[A-ZÄÖÜ][A-Za-zÄÖÜäöüß\s]+:/.test(text)
    }))
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
          <p>{{ job.company || 'Unbekannte Firma' }} · {{ job.location || 'Ort nicht angegeben' }}</p>
        </div>
        <StatusBadge :status="job.status" />
      </div>

      <div class="details-actions">
        <a :href="job.jobUrl" target="_blank" rel="noreferrer">Original job oeffnen</a>
      </div>

      <div class="facts-grid">
        <div v-for="fact in jobFacts" :key="fact.label" class="fact-item">
          <span>{{ fact.label }}</span>
          <strong>{{ fact.value }}</strong>
        </div>
        <div class="fact-item">
          <span>Match</span>
          <strong>{{ job.matchScore ?? '-' }}/100</strong>
        </div>
      </div>

      <div class="match-panel">
        <div>
          <span>Matching</span>
          <strong>{{ job.matchScore ?? '-' }}/100</strong>
        </div>
        <p>{{ job.matchExplanation ?? 'Noch keine Matching-Erklaerung vorhanden.' }}</p>
      </div>

      <div class="section-title description-title">
        <h2>Beschreibung</h2>
        <a :href="job.jobUrl" target="_blank" rel="noreferrer">Original job</a>
      </div>

      <div class="job-description formatted">
        <p v-if="!formattedDescription.length" class="muted">Keine Beschreibung vorhanden.</p>
        <template v-for="(block, index) in formattedDescription" :key="`${index}-${block.text}`">
          <div v-if="block.bullet" class="description-bullet">{{ block.text }}</div>
          <p v-else>{{ block.text }}</p>
        </template>
      </div>
    </section>

    <section class="draft-panel">
      <div class="section-title">
        <h2>Anschreiben</h2>
        <StatusBadge v-if="draft" :status="draft.status" />
      </div>

      <button v-if="!draft" class="primary full" :disabled="generating" @click="generate">
        {{ generating ? 'Anschreiben wird generiert...' : 'Anschreiben generieren' }}
      </button>

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
