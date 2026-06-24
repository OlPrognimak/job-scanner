import { apiRequest } from './client'
import type { ApplicationDraft, JobOffer } from '../types/job'

export interface ScanResult {
  importedCount: number
  jobs: JobOffer[]
}

export function listJobs() {
  return apiRequest<JobOffer[]>('/api/jobs')
}

export function getJob(id: number) {
  return apiRequest<JobOffer>(`/api/jobs/${id}`)
}

export function scanJobs(criteriaId?: number) {
  return apiRequest<ScanResult>('/api/jobs/scan', {
    method: 'POST',
    body: JSON.stringify({ criteriaId: criteriaId ?? null })
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
