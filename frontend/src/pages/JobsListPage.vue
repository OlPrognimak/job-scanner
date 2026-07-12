<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { generateAnschreiben, listJobs, scanJobs } from '../api/jobs'
import { listSearchCriteria } from '../api/searchCriteria'
import StatusBadge from '../components/StatusBadge.vue'
import LoadingError from '../components/LoadingError.vue'
import type { JobOffer, SearchCriteria } from '../types/job'

const jobs = ref<JobOffer[]>([])
const searchCriteria = ref<SearchCriteria[]>([])
const selectedCriteriaId = ref<string>('adhoc')
const portalOptions = [
  { value: 'adzuna', label: 'Adzuna' },
  { value: 'freelancermap', label: 'freelancermap' },
  { value: 'glassdoor', label: 'Glassdoor' },
  { value: 'stepstone', label: 'StepStone' },
  { value: 'mock', label: 'Mock' }
]
const selectedPortals = ref<string[]>(['adzuna', 'freelancermap'])
const quickScan = reactive({
  keyword: 'Java',
  country: 'de',
  location: ''
})
const loading = ref(false)
const criteriaLoading = ref(false)
const scanning = ref(false)
const error = ref('')
const scanMessage = ref('')
const scanWarnings = ref<string[]>([])
const generatingJobId = ref<number | null>(null)
const router = useRouter()
const filters = reactive({
  title: '',
  company: '',
  source: '',
  location: '',
  remoteType: '',
  score: '',
  status: ''
})
const sortKey = ref<SortableColumn>('detectedAt')
const sortDirection = ref<'asc' | 'desc'>('desc')

type SortableColumn = 'title' | 'company' | 'source' | 'location' | 'remoteType' | 'matchScore' | 'status' | 'detectedAt'

const filteredJobs = computed(() => {
  return [...jobs.value
    .filter((job) => contains(job.title, filters.title))
    .filter((job) => contains(job.company, filters.company))
    .filter((job) => contains(job.source, filters.source))
    .filter((job) => contains(job.location, filters.location))
    .filter((job) => !filters.remoteType || job.remoteType === filters.remoteType)
    .filter((job) => scoreMatches(job.matchScore, filters.score))
    .filter((job) => !filters.status || job.status === filters.status)]
    .sort((left: JobOffer, right: JobOffer) => compareJobs(left, right))
})

const remoteTypeOptions = computed(() => uniqueValues(jobs.value.map((job) => job.remoteType)))
const statusOptions = computed(() => uniqueValues(jobs.value.map((job) => job.status)))
const sourceOptions = computed(() => uniqueValues(jobs.value.map((job) => job.source)))

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

async function loadSearchCriteria() {
  criteriaLoading.value = true
  try {
    searchCriteria.value = await listSearchCriteria()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Suchkriterien konnten nicht geladen werden.'
  } finally {
    criteriaLoading.value = false
  }
}

async function runScan() {
  scanning.value = true
  error.value = ''
  scanMessage.value = ''
  scanWarnings.value = []
  try {
    const result = await scanJobs(scanRequest())
    jobs.value = result.jobs
    scanWarnings.value = result.messages || []
    scanMessage.value = `${result.importedCount} Jobs fuer die ausgewaehlten Suchkriterien gefunden.`
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Scan fehlgeschlagen.'
  } finally {
    scanning.value = false
  }
}

function scanRequest() {
  if (selectedCriteriaId.value === 'active') {
    return { criteriaId: null }
  }
  if (selectedCriteriaId.value !== 'adhoc') {
    return { criteriaId: Number(selectedCriteriaId.value) }
  }
  return {
    criteriaId: null,
    keyword: quickScan.keyword,
    country: quickScan.country,
    location: quickScan.location,
    sourceWebsite: selectedPortals.value.join(',')
  }
}

async function generate(job: JobOffer) {
  generatingJobId.value = job.id
  error.value = ''
  try {
    await generateAnschreiben(job.id)
    await router.push(`/jobs/${job.id}`)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Anschreiben konnte nicht generiert werden.'
  } finally {
    generatingJobId.value = null
  }
}

function sortBy(key: SortableColumn) {
  if (sortKey.value === key) {
    sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc'
    return
  }
  sortKey.value = key
  sortDirection.value = key === 'matchScore' || key === 'detectedAt' ? 'desc' : 'asc'
}

function sortLabel(key: SortableColumn) {
  if (sortKey.value !== key) return ''
  return sortDirection.value === 'asc' ? '▲' : '▼'
}

function contains(value: string | undefined, filter: string) {
  return !filter || (value ?? '').toLowerCase().includes(filter.toLowerCase())
}

function scoreMatches(score: number | undefined, filter: string) {
  if (!filter.trim()) return true
  const normalized = filter.trim()
  const numericScore = score ?? -1
  const match = normalized.match(/^(>=|<=|>|<|=)?\s*(\d{1,3})$/)
  if (!match) {
    return String(score ?? '').includes(normalized)
  }
  const operator = match[1] ?? '='
  const value = Number(match[2])
  if (operator === '>=') return numericScore >= value
  if (operator === '<=') return numericScore <= value
  if (operator === '>') return numericScore > value
  if (operator === '<') return numericScore < value
  return numericScore === value
}

function compareJobs(left: JobOffer, right: JobOffer) {
  const direction = sortDirection.value === 'asc' ? 1 : -1
  const leftValue = sortableValue(left, sortKey.value)
  const rightValue = sortableValue(right, sortKey.value)
  if (typeof leftValue === 'number' && typeof rightValue === 'number') {
    return (leftValue - rightValue) * direction
  }
  return String(leftValue).localeCompare(String(rightValue), 'de', { sensitivity: 'base' }) * direction
}

function sortableValue(job: JobOffer, key: SortableColumn) {
  if (key === 'matchScore') return job.matchScore ?? -1
  if (key === 'detectedAt') return new Date(job.detectedAt).getTime()
  return job[key] ?? ''
}

function uniqueValues(values: Array<string | undefined>) {
  return [...new Set(values.filter(Boolean) as string[])].sort((left, right) => left.localeCompare(right, 'de'))
}

function formatPortalList(values: string[]) {
  if (values.length === 0) {
    return 'Alle Portale'
  }
  return values
    .map((value) => portalOptions.find((option) => option.value === value)?.label || value)
    .join(', ')
}

onMounted(async () => {
  await Promise.all([load(), loadSearchCriteria()])
})
</script>

<template>
  <section class="page-header">
    <div>
      <h1>Gefundene Jobs</h1>
      <p>Review, Matching und Anschreiben-Entwuerfe.</p>
    </div>
    <div class="scan-controls">
      <label>
        Scan mode
        <select v-model="selectedCriteriaId" :disabled="criteriaLoading || scanning">
          <option value="adhoc">Freie Suche</option>
          <option value="active">Alle aktiven Suchkriterien</option>
          <option v-for="criteria in searchCriteria" :key="criteria.id" :value="String(criteria.id)">
            {{ criteria.name || criteria.keyword || `Criteria #${criteria.id}` }}
            {{ criteria.active ? '' : ' (inactive)' }}
          </option>
        </select>
      </label>
      <button class="primary" :disabled="scanning || criteriaLoading" @click="runScan">
        {{ scanning ? 'Scan laeuft...' : 'Scan starten' }}
      </button>
    </div>
  </section>

  <section v-if="selectedCriteriaId === 'adhoc'" class="quick-scan-panel">
    <label>
      Skills
      <input v-model="quickScan.keyword" placeholder="Java, Spring Boot, Kafka" :disabled="scanning" />
    </label>
    <label>
      Country
      <select v-model="quickScan.country" :disabled="scanning">
        <option value="de">Germany</option>
        <option value="gb">United Kingdom</option>
        <option value="us">United States</option>
        <option value="at">Austria</option>
        <option value="ch">Switzerland</option>
      </select>
    </label>
    <label>
      Location
      <input v-model="quickScan.location" placeholder="Koeln, Berlin, Remote" :disabled="scanning" />
    </label>
    <label>
      Search portals
      <select v-model="selectedPortals" multiple size="5" :disabled="scanning">
        <option v-for="portal in portalOptions" :key="portal.value" :value="portal.value">
          {{ portal.label }}
        </option>
      </select>
      <span class="muted">{{ formatPortalList(selectedPortals) }}</span>
    </label>
  </section>

  <LoadingError :loading="loading" :error="error" />
  <p v-if="scanMessage && !error" class="success">{{ scanMessage }}</p>
  <div v-if="scanWarnings.length && !error" class="warning-list">
    <p v-for="message in scanWarnings" :key="message">{{ message }}</p>
  </div>

  <div v-if="!loading && jobs.length" class="table-wrap">
    <table>
      <thead>
        <tr>
          <th>#</th>
          <th>
            <button class="sort-button" @click="sortBy('title')">Titel {{ sortLabel('title') }}</button>
          </th>
          <th>
            <button class="sort-button" @click="sortBy('company')">Firma {{ sortLabel('company') }}</button>
          </th>
          <th>
            <button class="sort-button" @click="sortBy('source')">Quelle {{ sortLabel('source') }}</button>
          </th>
          <th>
            <button class="sort-button" @click="sortBy('location')">Ort {{ sortLabel('location') }}</button>
          </th>
          <th>
            <button class="sort-button" @click="sortBy('remoteType')">Remote {{ sortLabel('remoteType') }}</button>
          </th>
          <th>
            <button class="sort-button" @click="sortBy('matchScore')">Score {{ sortLabel('matchScore') }}</button>
          </th>
          <th>
            <button class="sort-button" @click="sortBy('status')">Status {{ sortLabel('status') }}</button>
          </th>
          <th>Aktionen</th>
        </tr>
        <tr class="filter-row">
          <th></th>
          <th><input v-model="filters.title" placeholder="Titel" /></th>
          <th><input v-model="filters.company" placeholder="Firma" /></th>
          <th>
            <select v-model="filters.source">
              <option value="">Alle</option>
              <option v-for="source in sourceOptions" :key="source" :value="source">{{ source }}</option>
            </select>
          </th>
          <th><input v-model="filters.location" placeholder="Ort" /></th>
          <th>
            <select v-model="filters.remoteType">
              <option value="">Alle</option>
              <option v-for="remoteType in remoteTypeOptions" :key="remoteType" :value="remoteType">{{ remoteType }}</option>
            </select>
          </th>
          <th><input v-model="filters.score" placeholder=">=75" /></th>
          <th>
            <select v-model="filters.status">
              <option value="">Alle</option>
              <option v-for="status in statusOptions" :key="status" :value="status">{{ status }}</option>
            </select>
          </th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(job, index) in filteredJobs" :key="job.id">
          <td class="row-counter">{{ index + 1 }}</td>
          <td>{{ job.title }}</td>
          <td>{{ job.company }}</td>
          <td>{{ job.source }}</td>
          <td>{{ job.location }}</td>
          <td>{{ job.remoteType }}</td>
          <td>{{ job.matchScore ?? '-' }}</td>
          <td><StatusBadge :status="job.status" /></td>
          <td class="actions">
            <a :href="job.jobUrl" target="_blank" rel="noreferrer">Original</a>
            <button :disabled="generatingJobId === job.id" @click="generate(job)">
              {{ generatingJobId === job.id ? 'Generiert...' : 'Anschreiben' }}
            </button>
            <RouterLink :to="`/jobs/${job.id}`">Details</RouterLink>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
  <p v-else-if="!loading" class="empty">Noch keine Jobs vorhanden. Starte den ersten Scan.</p>
</template>
