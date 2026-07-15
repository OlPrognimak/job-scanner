<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { listJobHistory } from '../api/jobs'
import LoadingError from '../components/LoadingError.vue'
import StatusBadge from '../components/StatusBadge.vue'
import type { JobOffer } from '../types/job'

const jobs = ref<JobOffer[]>([])
const page = ref(0)
const size = ref(25)
const totalElements = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const error = ref('')

const firstItem = computed(() => (totalElements.value === 0 ? 0 : page.value * size.value + 1))
const lastItem = computed(() => Math.min((page.value + 1) * size.value, totalElements.value))

async function loadHistory() {
  loading.value = true
  error.value = ''
  try {
    const result = await listJobHistory(page.value, size.value)
    jobs.value = result.content
    page.value = result.page
    size.value = result.size
    totalElements.value = result.totalElements
    totalPages.value = result.totalPages
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Job-Historie konnte nicht geladen werden.'
  } finally {
    loading.value = false
  }
}

function previousPage() {
  if (page.value > 0) {
    page.value -= 1
  }
}

function nextPage() {
  if (page.value + 1 < totalPages.value) {
    page.value += 1
  }
}

function formatDateTime(value?: string) {
  if (!value) return '-'
  return new Intl.DateTimeFormat('de-DE', {
    dateStyle: 'short',
    timeStyle: 'short'
  }).format(new Date(value))
}

function publicationDate(job: JobOffer) {
  return job.publishedAt || job.detectedAt
}

watch([page, size], loadHistory)

onMounted(loadHistory)
</script>

<template>
  <section class="page-header">
    <div>
      <h1>Job History</h1>
      <p>Alle gespeicherten Suchergebnisse aus der Datenbank.</p>
    </div>
    <div class="history-controls">
      <label>
        Page size
        <select v-model.number="size" :disabled="loading" @change="page = 0">
          <option :value="10">10</option>
          <option :value="25">25</option>
          <option :value="50">50</option>
          <option :value="100">100</option>
        </select>
      </label>
    </div>
  </section>

  <LoadingError :loading="loading" :error="error" />

  <div v-if="!loading && jobs.length" class="history-summary">
    <span>{{ firstItem }}-{{ lastItem }} von {{ totalElements }}</span>
    <div class="pagination">
      <button :disabled="page === 0 || loading" @click="previousPage">Zurueck</button>
      <span>Seite {{ page + 1 }} von {{ totalPages || 1 }}</span>
      <button :disabled="page + 1 >= totalPages || loading" @click="nextPage">Weiter</button>
    </div>
  </div>

  <div v-if="!loading && jobs.length" class="table-wrap">
    <table>
      <thead>
        <tr>
          <th>#</th>
          <th>Titel</th>
          <th>Firma</th>
          <th>Quelle</th>
          <th>Ort</th>
          <th>Remote</th>
          <th>Score</th>
          <th>Status</th>
          <th>Veröffentlicht am</th>
          <th>Gefunden am</th>
          <th>Aktionen</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(job, index) in jobs" :key="job.id">
          <td class="row-counter">{{ page * size + index + 1 }}</td>
          <td>
            <a class="job-title-link" :href="job.jobUrl" target="_blank" rel="noreferrer">
              {{ job.title }}
            </a>
          </td>
          <td>{{ job.company }}</td>
          <td>{{ job.source }}</td>
          <td>{{ job.location }}</td>
          <td>{{ job.remoteType }}</td>
          <td>{{ job.matchScore ?? '-' }}</td>
          <td><StatusBadge :status="job.status" /></td>
          <td class="date-cell">{{ formatDateTime(publicationDate(job)) }}</td>
          <td class="date-cell">{{ formatDateTime(job.detectedAt) }}</td>
          <td class="actions compact-actions">
            <RouterLink :to="{ path: `/jobs/${job.id}`, query: { back: 'history' } }">Details</RouterLink>
          </td>
        </tr>
      </tbody>
    </table>
  </div>

  <p v-else-if="!loading" class="empty">Noch keine historischen Jobs vorhanden.</p>
</template>
