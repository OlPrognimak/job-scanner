import { apiRequest } from './client'
import type { SearchCriteria } from '../types/job'

function normalize(criteria: SearchCriteria): SearchCriteria {
  return {
    ...criteria,
    remoteType: criteria.remoteType || undefined,
    contractType: criteria.contractType || undefined
  }
}

export function listSearchCriteria() {
  return apiRequest<SearchCriteria[]>('/api/search-criteria')
}

export function createSearchCriteria(criteria: SearchCriteria) {
  return apiRequest<SearchCriteria>('/api/search-criteria', {
    method: 'POST',
    body: JSON.stringify(normalize(criteria))
  })
}

export function updateSearchCriteria(criteria: SearchCriteria) {
  return apiRequest<SearchCriteria>(`/api/search-criteria/${criteria.id}`, {
    method: 'PUT',
    body: JSON.stringify(normalize(criteria))
  })
}
