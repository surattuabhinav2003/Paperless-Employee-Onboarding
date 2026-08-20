import axios from 'axios'

const TOKEN_KEY = 'cf_onboarding_hr_token'

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
})

/** HR requests carry the JWT; candidate portal requests are authenticated by their path token. */
apiClient.interceptors.request.use((config) => {
  const token = tokenStore.get()
  const isPortalCall = (config.url || '').startsWith('/portal/')
  if (token && !isPortalCall) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/**
 * Normalises every backend failure into a single shape so components never dig
 * through axios internals: { code, message, status, fieldErrors, details }.
 */
export class ApiError extends Error {
  constructor({ message, code, status, fieldErrors, details }) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.fieldErrors = fieldErrors || {}
    this.details = details || {}
  }

  get isStageForbidden() {
    return this.code === 'STAGE_FORBIDDEN'
  }

  get isTokenProblem() {
    return this.code === 'INVALID_PORTAL_TOKEN' || this.code === 'PORTAL_TOKEN_EXPIRED'
  }
}

const NETWORK_MESSAGE =
  'We could not reach the onboarding service. Check your connection and try again.'

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response) {
      const { status, data } = error.response
      const isHrCall = !(error.config?.url || '').startsWith('/portal/')
      if (status === 401 && isHrCall && tokenStore.get()) {
        tokenStore.clear()
        window.dispatchEvent(new CustomEvent('cf:session-expired'))
      }
      return Promise.reject(
        new ApiError({
          status,
          code: data?.code || 'REQUEST_FAILED',
          message: data?.message || 'Something went wrong. Please try again.',
          fieldErrors: data?.fieldErrors,
          details: data?.details,
        }),
      )
    }
    return Promise.reject(
      new ApiError({
        status: 0,
        code: 'NETWORK_ERROR',
        message: error.code === 'ECONNABORTED' ? 'The request timed out.' : NETWORK_MESSAGE,
      }),
    )
  },
)

/** Absolute URL for a download path returned by the API. */
export function fileUrl(path) {
  if (!path) return null
  const base = import.meta.env.VITE_API_BASE_URL || '/api'
  return path.startsWith('/api') ? path.replace('/api', base) : path
}
