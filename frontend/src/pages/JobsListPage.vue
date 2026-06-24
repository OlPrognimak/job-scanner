<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { generateAnschreiben, listJobs, scanJobs } from '../api/jobs'
import StatusBadge from '../components/StatusBadge.vue'
import LoadingError from '../components/LoadingError.vue'
import type { JobOffer } from '../types/job'

const jobs = ref<JobOffer[]>([])
const loading = ref(false)
const scanning = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    jobs.value = await listJobs()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Jobs konnten nicht geladen werden.'
  } finally {
    loading.value = false
  }
}

async function runScan() {
  scanning.value = true
  error.value = ''
  try {
    await scanJobs()
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Scan fehlgeschlagen.'
  } finally {
    scanning.value = false
  }
}

async function generate(job: JobOffer) {
  await generateAnschreiben(job.id)
  await load()
}

onMounted(load)
</script>

<template>
  <section class="page-header">
    <div>
      <h1>Gefundene Jobs</h1>
      <p>Review, Matching und Anschreiben-Entwuerfe.</p>
    </div>
    <button class="primary" :disabled="scanning" @click="runScan">
      {{ scanning ? 'Scan laeuft...' : 'Scan starten' }}
    </button>
  </section>

  <LoadingError :loading="loading" :error="error" />

  <div v-if="!loading && jobs.length" class="table-wrap">
    <table>
      <thead>
        <tr>
          <th>Titel</th>
          <th>Firma</th>
          <th>Quelle</th>
          <th>Ort</th>
          <th>Remote</th>
          <th>Score</th>
          <th>Status</th>
          <th>Aktionen</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="job in jobs" :key="job.id">
          <td>{{ job.title }}</td>
          <td>{{ job.company }}</td>
          <td>{{ job.source }}</td>
          <td>{{ job.location }}</td>
          <td>{{ job.remoteType }}</td>
          <td>{{ job.matchScore ?? '-' }}</td>
          <td><StatusBadge :status="job.status" /></td>
          <td class="actions">
            <a :href="job.jobUrl" target="_blank" rel="noreferrer">Original</a>
            <button @click="generate(job)">Anschreiben</button>
            <RouterLink :to="`/jobs/${job.id}`">Details</RouterLink>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
  <p v-else-if="!loading" class="empty">Noch keine Jobs vorhanden. Starte den ersten Scan.</p>
</template>
