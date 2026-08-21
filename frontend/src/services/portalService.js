import { apiClient } from './apiClient'

/**
 * Candidate portal calls. The token in the path is the only credential; the
 * backend re-checks it, its expiry and the candidate stage on every call.
 */
export const portalService = {
  async overview(token) {
    const { data } = await apiClient.get(`/portal/${token}`)
    return data
  },

  async profile(token) {
    const { data } = await apiClient.get(`/portal/${token}/profile`)
    // 204 No Content means the candidate has not filled the form in yet.
    return data || null
  },

  async saveProfile(token, payload) {
    const { data } = await apiClient.put(`/portal/${token}/profile`, payload)
    return data
  },

  async submitForReview(token) {
    const { data } = await apiClient.post(`/portal/${token}/submit`)
    return data
  },

  async documents(token) {
    const { data } = await apiClient.get(`/portal/${token}/documents`)
    return data
  },

  async uploadDocument(token, documentType, file, course, onProgress) {
    const form = new FormData()
    form.append('file', file)
    const { data } = await apiClient.post(`/portal/${token}/documents/${documentType}`, form, {
      params: course ? { course } : undefined,
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (event) => {
        if (onProgress && event.total) {
          onProgress(Math.round((event.loaded * 100) / event.total))
        }
      },
    })
    return data
  },

  async offer(token) {
    const { data } = await apiClient.get(`/portal/${token}/offer`)
    return data
  },

  async markOfferViewed(token) {
    const { data } = await apiClient.post(`/portal/${token}/offer/view`)
    return data
  },

  async acceptOffer(token, acknowledgementName) {
    const { data } = await apiClient.post(`/portal/${token}/offer/accept`, {
      acknowledgementName,
      accepted: true,
    })
    return data
  },

}
