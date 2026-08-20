import { apiClient } from './apiClient'

function stripApiPrefix(path) {
  return path?.startsWith('/api') ? path.slice(4) : path
}

/** Used by the signed-bond download, which is a deliberate save-to-disk. */
function triggerDownload(objectUrl, filename) {
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
}

/** Every HR API call in one place. Components never build URLs themselves. */
export const hrService = {
  async dashboardStats() {
    const { data } = await apiClient.get('/hr/dashboard/stats')
    return data
  },

  async candidates({ query = '', stage = '', page = 0, size = 50 } = {}) {
    const params = { page, size }
    if (query) params.q = query
    if (stage) params.stage = stage
    const { data } = await apiClient.get('/hr/candidates', { params })
    return data
  },

  async createCandidate(payload) {
    const { data } = await apiClient.post('/hr/candidates', payload)
    return data
  },

  async candidate(id) {
    const { data } = await apiClient.get(`/hr/candidates/${id}`)
    return data
  },

  async profile(candidateId) {
    const { data } = await apiClient.get(`/hr/candidates/${candidateId}/profile`)
    return data || null
  },

  async documents(candidateId) {
    const { data } = await apiClient.get(`/hr/candidates/${candidateId}/documents`)
    return data
  },

  async verifyDocument(documentId) {
    const { data } = await apiClient.post(`/hr/documents/${documentId}/verify`)
    return data
  },

  async rejectDocument(documentId, reason) {
    const { data } = await apiClient.post(`/hr/documents/${documentId}/reject`, { reason })
    return data
  },

  async auditTrail(candidateId) {
    const { data } = await apiClient.get(`/hr/candidates/${candidateId}/audit`)
    return data
  },

  async uploadOffer(candidateId, file, notes) {
    const form = new FormData()
    form.append('file', file)
    const { data } = await apiClient.post(`/hr/candidates/${candidateId}/offer`, form, {
      params: notes ? { notes } : undefined,
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data
  },

  async uploadBond(candidateId, file, documentVersion) {
    const form = new FormData()
    form.append('file', file)
    const { data } = await apiClient.post(`/hr/candidates/${candidateId}/bond`, form, {
      params: documentVersion ? { documentVersion } : undefined,
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data
  },

  async portalLink(candidateId) {
    const { data } = await apiClient.get(`/hr/candidates/${candidateId}/portal-link`)
    return data
  },

  async resendInvite(candidateId) {
    const { data } = await apiClient.post(`/hr/candidates/${candidateId}/resend-invite`)
    return data
  },

  async regenerateToken(candidateId) {
    const { data } = await apiClient.post(`/hr/candidates/${candidateId}/regenerate-token`)
    return data
  },

  async downloadSecureFile(apiPath, filename) {
    const { data } = await apiClient.get(stripApiPrefix(apiPath), { responseType: 'blob' })
    const url = URL.createObjectURL(data)
    triggerDownload(url, filename || 'document')
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  },

  async metadata() {
    const { data } = await apiClient.get('/meta')
    return data
  },
}
