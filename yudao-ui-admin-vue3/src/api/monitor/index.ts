import request from '@/config/axios'

export type FilterType = 'LMS' | 'KALMAN'

export interface MonitorUploadResp {
  jobId: string
  websocketPath: string
  playbackSeconds: number
  channel: {
    name: string
    unit: string
    sampleRate: number
    startTimestamp: number
    endTimestamp: number
    sampleCount: number
  }
}

export interface HistoryAnalysisPoint {
  timestamp: number
  rawValue: number
  filteredValue: number
  residualValue: number
  isAnomaly: boolean
  anomalyType?: string
  channelName: string
}

export interface HistoryAnalysisResult {
  channel: {
    name: string
    unit: string
  }
  points: HistoryAnalysisPoint[]
  anomalyCount: number
}

export type KalmanParams = {
  kalmanQ?: number
  kalmanR?: number
  kalmanP0?: number
  kalmanX0N?: number
}

export type FilterParams = {
  filterType?: FilterType
} & KalmanParams

const appendFilterParams = (formData: FormData, params?: FilterParams) => {
  if (!params) return
  if (params.filterType) formData.append('filterType', params.filterType)
  if (params.kalmanQ != null) formData.append('kalmanQ', String(params.kalmanQ))
  if (params.kalmanR != null) formData.append('kalmanR', String(params.kalmanR))
  if (params.kalmanP0 != null) formData.append('kalmanP0', String(params.kalmanP0))
  if (params.kalmanX0N != null) formData.append('kalmanX0N', String(params.kalmanX0N))
}

export const uploadTdms = (formData: FormData, filterParams?: FilterParams) => {
  // 必须用 multipart/form-data，否则后端收不到 file
  appendFilterParams(formData, filterParams)
  return request.post<MonitorUploadResp>({
    url: '/api/monitor/upload',
    data: formData,
    headersType: 'multipart/form-data'
  })
}

export const analyzeTdmsHistory = (formData: FormData, filterParams?: FilterParams) => {
  appendFilterParams(formData, filterParams)
  return request.post<HistoryAnalysisResult>({
    url: '/api/monitor/history/analyze',
    data: formData,
    headersType: 'multipart/form-data'
  })
}

export const updateAnomalyConfig = (jobId: string, threshold: number, enabled: boolean) => {
  return request.post({ url: `/api/monitor/${jobId}/anomaly`, params: { threshold, enabled } })
}

export const updateFilterConfig = (jobId: string, filterParams: FilterParams) => {
  return request.post({ url: `/api/monitor/${jobId}/filter`, params: filterParams })
}

export const stopMonitorJob = (jobId: string) => {
  return request.delete({ url: `/api/monitor/${jobId}` })
}
