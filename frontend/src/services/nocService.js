import { apiClient } from './apiClient'

/**
 * The recipient's side of an NDA + NOC packet.
 *
 * <p>No sign-in: the token in the link is the credential. Recipients are on a
 * restricted company domain, so there is no separate code step.
 */
export const nocService = {
  async view(token) {
    const { data } = await apiClient.get(`/noc/${token}`)
    return data
  },

  async sign(token, signedByName, fieldValues) {
    const { data } = await apiClient.post(`/noc/${token}/sign`, { signedByName, fieldValues })
    return data
  },

  /**
   * Saves the signed copy to disk. Fetched through the API client rather than a
   * plain link so it goes through the same base URL and error handling.
   */
  async download(token, filename = 'NDA-NOC-signed.pdf') {
    const { data } = await apiClient.get(`/noc/${token}/file`, { responseType: 'blob' })
    const url = URL.createObjectURL(data)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    document.body.appendChild(link)
    link.click()
    link.remove()
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  },
}
