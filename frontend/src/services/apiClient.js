import axios from 'axios'
import { deviceStore } from './deviceStore'

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
  const url = config.url || ''
  const isPortalCall = url.startsWith('/portal/')
  // NDA + NOC recipients are not signed in either, so these carry no bearer.
  const isNocCall = url.startsWith('/noc/')
  if (token && !isPortalCall && !isNocCall) {
    config.headers.Authorization = `Bearer ${token}`
  }
  /*
   * Portal calls also carry proof that this browser already confirmed an
   * emailed code. Attached here rather than in each service call so no new
   * endpoint can forget it.
   */
  if (isPortalCall) {
    const marker = deviceStore.get(url.split('/')[2])
    if (marker) {
      config.headers['X-Portal-Device'] = marker
    }
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

/**
 * Fetches a download path's raw bytes through apiClient, so the request carries
 * the same auth (HR bearer token or portal device header) a normal API call
 * would - unlike handing the path straight to an <iframe> or <a>.
 */
export async function fetchBytes(apiPath) {
  const path = apiPath.startsWith('/api') ? apiPath.slice(4) : apiPath
  const { data } = await apiClient.get(path, { responseType: 'arraybuffer' })
  return data
}
