import { apiClient, tokenStore } from './apiClient'

export const authService = {
  async login(email, password) {
    const { data } = await apiClient.post('/auth/login', { email, password })
    tokenStore.set(data.token)
    return data
  },

  /** Exchanges a Microsoft ID token (from MSAL) for an app session. */
  async microsoftLogin(idToken) {
    const { data } = await apiClient.post('/auth/microsoft', { idToken })
    tokenStore.set(data.token)
    return data
  },

  async me() {
    const { data } = await apiClient.get('/auth/me')
    return data
  },

  logout() {
    tokenStore.clear()
  },

  isAuthenticated() {
    return Boolean(tokenStore.get())
  },
}
