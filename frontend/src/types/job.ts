export type RemoteType = 'REMOTE' | 'HYBRID' | 'ON_SITE'
export type ContractType = 'FREELANCE' | 'PERMANENT'
export type JobStatus = 'NEW' | 'REVIEWED' | 'APPLIED' | 'REJECTED'
export type DraftStatus = 'DRAFT' | 'APPROVED' | 'SENT' | 'FAILED'

export interface JobOffer {
  id: number
  source: string
  title: string
  company?: string
  location?: string
  remoteType?: RemoteType
  contractType?: ContractType
  description: string
  jobUrl: string
  detectedAt: string
  status: JobStatus
  matchScore?: number
  matchExplanation?: string
  rateOrSalary?: number
}

export interface ApplicationDraft {
  id: number
  jobOfferId: number
  anschreibenText: string
  cvFilePath?: string
  cvDocumentId?: string
  createdAt: string
  updatedAt: string
  sentAt?: string
  status: DraftStatus
}

export interface SearchCriteria {
  id?: number
  name?: string
  keyword?: string
  location?: string
  remoteType?: RemoteType | ''
  contractType?: ContractType | ''
  minRate?: number
  language?: string
  sourceWebsite?: string
  active: boolean
}
