import { apiClient } from './apiClient'

function stripApiPrefix(path) {
  return path?.startsWith('/api') ? path.slice(4) : path
}

/** Used by deliberate save-to-disk downloads. */
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
  /* ---- permanent deletion (administrators only) ---- */

  async deletionPreview(candidateId) {
    const { data } = await apiClient.get(`/hr/admin/candidates/${candidateId}/deletion-preview`)
    return data
  },

  async deleteCandidate(candidateId) {
    await apiClient.delete(`/hr/admin/candidates/${candidateId}`)
  },

  async deleteCandidateOffer(candidateId) {
    await apiClient.delete(`/hr/admin/candidates/${candidateId}/offer`)
  },

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

  /**
   * Invites a batch who all need the same documents. Resolves even when some
   * rows fail - the result reports each one, so the caller shows the outcome
   * rather than a single error.
   */
  async createCandidatesBulk(payload) {
    const { data } = await apiClient.post('/hr/candidates/bulk', payload)
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

  /** HR correcting the candidate's personal details on their behalf. */
  async saveCandidateProfile(candidateId, payload) {
    const { data } = await apiClient.put(`/hr/candidates/${candidateId}/profile`, payload)
    return data
  },

  /** HR correcting the name, role or department they entered at creation. */
  async updateCandidate(candidateId, payload) {
    const { data } = await apiClient.put(`/hr/candidates/${candidateId}`, payload)
    return data
  },

  async documents(candidateId) {
    const { data } = await apiClient.get(`/hr/candidates/${candidateId}/documents`)
    return data
  },

  async verifyDocument(documentId) {
    const { data } = await apiClient.post(`/hr/documents/${documentId}/verify`)
    return data
  },

  /** Undoes a verify so HR can take another look before final approval. */
  async reopenDocument(documentId) {
    const { data } = await apiClient.post(`/hr/documents/${documentId}/reopen`)
    return data
  },

  async rejectDocument(documentId, reason) {
    const { data } = await apiClient.post(`/hr/documents/${documentId}/reject`, { reason })
    return data
  },

  /** Adds new document requirements to a candidate who has already been invited. */
  async addRequiredDocuments(candidateId, requiredDocuments) {
    const { data } = await apiClient.post(`/hr/candidates/${candidateId}/required-documents`, {
      requiredDocuments,
    })
    return data
  },

  /** Toggles an existing requirement between mandatory and optional. */
  async setDocumentMandatory(candidateId, type, mandatory) {
    const { data } = await apiClient.patch(`/hr/candidates/${candidateId}/required-documents/${type}`, {
      mandatory,
    })
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

  /** Releases a drafted offer letter to the candidate. */
  async sendOffer(candidateId) {
    const { data } = await apiClient.post(`/hr/candidates/${candidateId}/offer/send`)
    return data
  },

  /** Places (or replaces) the fields HR wants the candidate to complete. */
  async saveOfferFields(candidateId, fields) {
    const { data } = await apiClient.put(`/hr/candidates/${candidateId}/offer/fields`, { fields })
    return data
  },


  async portalLink(candidateId) {
    const { data } = await apiClient.get(`/hr/candidates/${candidateId}/portal-link`)
    return data
  },

  /** HR finished reviewing: send the candidate one summary email of the outcome. */
  async notifyCandidate(candidateId) {
    const { data } = await apiClient.post(`/hr/candidates/${candidateId}/notify`)
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

  /* ---- admin only -------------------------------------------------------
     Everything under /hr/admin requires the admin role; the server refuses
     these outright for an ordinary HR user. */

  async adminUsers() {
    const { data } = await apiClient.get('/hr/admin/users')
    return data
  },

  async addUser(payload) {
    const { data } = await apiClient.post('/hr/admin/users', payload)
    return data
  },

  async setUserRole(userId, role) {
    const { data } = await apiClient.put(`/hr/admin/users/${userId}/role`, { role })
    return data
  },

  /* ---- admin-created document types ---- */

  async documentTypes() {
    const { data } = await apiClient.get('/hr/admin/document-types')
    return data
  },

  async addDocumentType(payload) {
    const { data } = await apiClient.post('/hr/admin/document-types', payload)
    return data
  },

  async updateDocumentType(id, payload) {
    const { data } = await apiClient.put(`/hr/admin/document-types/${id}`, payload)
    return data
  },

  /* Withdraws it from the picker; documents already collected are kept. */
  async removeDocumentType(id) {
    const { data } = await apiClient.delete(`/hr/admin/document-types/${id}`)
    return data
  },

  async restoreDocumentType(id) {
    const { data } = await apiClient.put(`/hr/admin/document-types/${id}/restore`)
    return data
  },

  /* ---- admin-created detail fields ---- */

  async customFields() {
    const { data } = await apiClient.get('/hr/admin/custom-fields')
    return data
  },

  async addCustomField(payload) {
    const { data } = await apiClient.post('/hr/admin/custom-fields', payload)
    return data
  },

  async updateCustomField(id, payload) {
    const { data } = await apiClient.put(`/hr/admin/custom-fields/${id}`, payload)
    return data
  },

  /* Removes it from the form; answers already collected are kept. */
  async removeCustomField(id) {
    const { data } = await apiClient.delete(`/hr/admin/custom-fields/${id}`)
    return data
  },

  async restoreCustomField(id) {
    const { data } = await apiClient.put(`/hr/admin/custom-fields/${id}/restore`)
    return data
  },

  async candidateFields() {
    const { data } = await apiClient.get('/hr/admin/candidate-fields')
    return data
  },

  async setCandidateField(code, { enabled, required }) {
    const { data } = await apiClient.put('/hr/admin/candidate-fields/' + code, { enabled, required })
    return data
  },

  async metadata() {
    const { data } = await apiClient.get('/meta')
    return data
  },

  /* ---- NDA + NOC packets ------------------------------------------------
     Two documents uploaded together, combined server-side into one, then
     signed in a single pass. */

  async nocList({ status = '', page = 0, size = 50 } = {}) {
    const params = { page, size }
    if (status) params.status = status
    const { data } = await apiClient.get('/hr/noc', { params })
    return data
  },

  async nocPacket(id) {
    const { data } = await apiClient.get(`/hr/noc/${id}`)
    return data
  },

  /** Uploads the NDA and the NOC; the server returns the combined document. */
  async createNoc({ nda, noc, recipientName, recipientEmail, title }) {
    const form = new FormData()
    form.append('nda', nda)
    form.append('noc', noc)
    form.append('recipientName', recipientName)
    form.append('recipientEmail', recipientEmail)
    if (title) form.append('title', title)
    const { data } = await apiClient.post('/hr/noc', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data
  },

  /** Field positions are in combined-document coordinates. */
  async saveNocFields(id, fields) {
    const { data } = await apiClient.put(`/hr/noc/${id}/fields`, { fields })
    return data
  },

  async sendNoc(id) {
    const { data } = await apiClient.post(`/hr/noc/${id}/send`)
    return data
  },

  /** The live signing link, fetched only when HR asks to see it. */
  async nocLink(id) {
    const { data } = await apiClient.get(`/hr/noc/${id}/link`)
    return data.signingUrl
  },

  async deleteNoc(id) {
    await apiClient.delete(`/hr/noc/${id}`)
  },
}
