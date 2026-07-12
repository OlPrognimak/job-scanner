<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { createSearchCriteria, listSearchCriteria, updateSearchCriteria } from '../api/searchCriteria'
import LoadingError from '../components/LoadingError.vue'
import type { SearchCriteria } from '../types/job'

const criteria = ref<SearchCriteria[]>([])
const loading = ref(false)
const error = ref('')
const sourceOptions = [
  { value: 'glassdoor', label: 'Glassdoor' },
  { value: 'freelancermap', label: 'freelancermap' },
  { value: 'adzuna', label: 'Adzuna' },
  { value: 'stepstone', label: 'StepStone' },
  { value: 'mock', label: 'Mock' }
]
const selectedSources = ref<string[]>(['freelancermap'])
const form = reactive<SearchCriteria>({
  name: '',
  keyword: '',
  location: '',
  remoteType: '',
  contractType: '',
  minRate: undefined,
  language: 'Deutsch',
  sourceWebsite: 'freelancermap',
  active: true
})

function parseSources(value?: string) {
  const rawSources = (value || '')
    .split(/[,;]/)
    .map((source) => source.trim())
    .filter(Boolean)
  return rawSources
    .map((source) => {
      const lowerSource = source.toLowerCase()
      return sourceOptions.find((option) =>
        lowerSource === option.value || lowerSource.includes(option.value) || option.value.includes(lowerSource)
      )?.value || source
    })
}

function serializeSources() {
  return selectedSources.value.join(',')
}

function formatSources(value?: string) {
  const sources = parseSources(value)
  if (sources.length === 0) {
    return 'All sources'
  }
  return sources
    .map((source) => sourceOptions.find((option) => option.value === source)?.label || source)
    .join(', ')
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    criteria.value = await listSearchCriteria()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Suchkriterien konnten nicht geladen werden.'
  } finally {
    loading.value = false
  }
}

function edit(item: SearchCriteria) {
  Object.assign(form, item)
  selectedSources.value = parseSources(item.sourceWebsite)
}

function reset() {
  Object.assign(form, {
    id: undefined,
    name: '',
    keyword: '',
    location: '',
    remoteType: '',
    contractType: '',
    minRate: undefined,
    language: 'Deutsch',
    sourceWebsite: 'freelancermap',
    active: true
  })
  selectedSources.value = ['freelancermap']
}

async function save() {
  form.sourceWebsite = serializeSources()
  if (form.id) {
    await updateSearchCriteria(form)
  } else {
    await createSearchCriteria(form)
  }
  reset()
  await load()
}

onMounted(load)
</script>

<template>
  <section class="page-header">
    <div>
      <h1>Search Criteria</h1>
      <p>Aktive Profile steuern manuelle Scans.</p>
    </div>
  </section>

  <LoadingError :loading="loading" :error="error" />

  <section class="criteria-layout">
    <form class="form-panel" @submit.prevent="save">
      <label>Name <input v-model="form.name" /></label>
      <label>Keyword <input v-model="form.keyword" placeholder="Java, Spring Boot, Kafka" /></label>
      <label>Location <input v-model="form.location" /></label>
      <label>
        Remote type
        <select v-model="form.remoteType">
          <option value="">Any</option>
          <option value="REMOTE">Remote</option>
          <option value="HYBRID">Hybrid</option>
          <option value="ON_SITE">On-site</option>
        </select>
      </label>
      <label>
        Contract type
        <select v-model="form.contractType">
          <option value="">Any</option>
          <option value="FREELANCE">Freelance</option>
          <option value="PERMANENT">Permanent</option>
        </select>
      </label>
      <label>Minimum rate/salary <input v-model.number="form.minRate" type="number" /></label>
      <label>Language <input v-model="form.language" /></label>
      <label>
        Source website
        <select v-model="selectedSources" multiple size="4">
          <option v-for="source in sourceOptions" :key="source.value" :value="source.value">
            {{ source.label }}
          </option>
        </select>
      </label>
      <label class="checkbox"><input v-model="form.active" type="checkbox" /> Active</label>
      <div class="button-row">
        <button class="primary" type="submit">{{ form.id ? 'Update' : 'Create' }}</button>
        <button type="button" @click="reset">Reset</button>
      </div>
    </form>

    <div class="criteria-list">
      <button v-for="item in criteria" :key="item.id" class="criteria-item" @click="edit(item)">
        <strong>{{ item.name || item.keyword || 'Unnamed criteria' }}</strong>
        <span>{{ item.keyword }} · {{ item.location }} · {{ formatSources(item.sourceWebsite) }}</span>
      </button>
    </div>
  </section>
</template>
