import { apiRequest } from './client'
import type { ApplicationDraft, JobOffer } from '../types/job'

export interface PageResult<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface ScanResult {
  importedCount: number
  jobs: JobOffer[]
  messages: string[]
}

export interface ScanRequest {
  criteriaId?: number | null
  keyword?: string
  country?: string
  location?: string
  sourceWebsite?: string
}

export function listJobs() {
  return apiRequest<JobOffer[]>('/api/jobs')
}

export function listJobHistory(page = 0, size = 25) {
  return apiRequest<PageResult<JobOffer>>(`/api/jobs/history?page=${page}&size=${size}`)
}

export function getJob(id: number) {
  return apiRequest<JobOffer>(`/api/jobs/${id}`)
}

export function scanJobs(request?: ScanRequest) {
  return apiRequest<ScanResult>('/api/jobs/scan', {
    method: 'POST',
    body: JSON.stringify(request ?? { criteriaId: null })
  })
}

export function generateAnschreiben(jobId: number) {
  return apiRequest<ApplicationDraft>(`/api/jobs/${jobId}/generate-anschreiben`, { method: 'POST' })
}

export function getDraft(jobId: number) {
  return apiRequest<ApplicationDraft>(`/api/jobs/${jobId}/draft`)
}

export function updateDraft(draft: ApplicationDraft) {
  return apiRequest<ApplicationDraft>(`/api/drafts/${draft.id}`, {
    method: 'PUT',
    body: JSON.stringify({
      anschreibenText: draft.anschreibenText,
      cvFilePath: draft.cvFilePath,
      cvDocumentId: draft.cvDocumentId
    })
  })
}

export function sendDraft(id: number) {
  return apiRequest<ApplicationDraft>(`/api/drafts/${id}/send`, { method: 'POST' })
}
