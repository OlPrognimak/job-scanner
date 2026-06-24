<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { createSearchCriteria, listSearchCriteria, updateSearchCriteria } from '../api/searchCriteria'
import LoadingError from '../components/LoadingError.vue'
import type { SearchCriteria } from '../types/job'

const criteria = ref<SearchCriteria[]>([])
const loading = ref(false)
const error = ref('')
const form = reactive<SearchCriteria>({
  name: '',
  keyword: '',
  location: '',
  remoteType: '',
  contractType: '',
  minRate: undefined,
  language: 'Deutsch',
  sourceWebsite: 'mock',
  active: true
})

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
    sourceWebsite: 'mock',
    active: true
  })
}

async function save() {
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
      <label>Source website <input v-model="form.sourceWebsite" placeholder="mock, freelancermap, stepstone" /></label>
      <label class="checkbox"><input v-model="form.active" type="checkbox" /> Active</label>
      <div class="button-row">
        <button class="primary" type="submit">{{ form.id ? 'Update' : 'Create' }}</button>
        <button type="button" @click="reset">Reset</button>
      </div>
    </form>

    <div class="criteria-list">
      <button v-for="item in criteria" :key="item.id" class="criteria-item" @click="edit(item)">
        <strong>{{ item.name || item.keyword || 'Unnamed criteria' }}</strong>
        <span>{{ item.keyword }} · {{ item.location }} · {{ item.sourceWebsite }}</span>
      </button>
    </div>
  </section>
</template>
