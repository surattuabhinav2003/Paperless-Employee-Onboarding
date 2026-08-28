import { apiClient } from './apiClient'

/**
 * Candidate portal calls. The token in the path is the only credential; the
 * backend re-checks it, its expiry and the candidate stage on every call.
 */
export const portalService = {
  /**
   * Confirms the candidate's own email and asks for a code. The backend only
   * sends one if the email matches the person this link belongs to.
   */
  async requestCode(token, email) {
    const { data } = await apiClient.post(`/portal/${token}/verify/request`, { email })
    return data
  },

  /** Exchanges a correct code for a device marker. */
  async verifyCode(token, code) {
    const { data } = await apiClient.post(`/portal/${token}/verify`, { code })
    return data
  },

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

  /** The candidate's answers for every placed field, stamped into the PDF. */
  async signOffer(token, fieldValues) {
    const { data } = await apiClient.post(`/portal/${token}/offer/sign`, { fieldValues })
    return data
  },

  /**
   * Saves the offer letter to disk. Goes through apiClient rather than a plain
   * link so the request carries the portal device header the backend requires.
   */
  async downloadOffer(apiPath, filename) {
    const path = apiPath.startsWith('/api') ? apiPath.slice(4) : apiPath
    const { data } = await apiClient.get(path, { responseType: 'blob' })
    const url = URL.createObjectURL(data)
    const link = document.createElement('a')
    link.href = url
    link.download = filename || 'offer-letter.pdf'
    document.body.appendChild(link)
    link.click()
    link.remove()
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  },

}
