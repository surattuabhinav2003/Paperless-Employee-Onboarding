import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { authService } from '../services/authService'
import {
  completeMicrosoftRedirect,
  microsoftLoginEnabled,
  startMicrosoftRedirect,
} from '../services/msal'
import { tokenStore } from '../services/apiClient'
import { useToast } from './ToastContext'

const AuthContext = createContext(null)

// A response coming back from Microsoft arrives in the URL hash; if it is there,
// start in "loading" so the app does not flash the login screen first.
const returningFromMicrosoft =
  typeof window !== 'undefined' && /[#&](code|id_token|error)=/.test(window.location.hash)

// One exchange per redirect, shared across renders. React StrictMode mounts the
// provider twice in development; without this, both would POST the same token and
// race to provision the same HR user.
let microsoftExchange = null

export function AuthProvider({ children }) {
  const toast = useToast()
  const [user, setUser] = useState(null)
  const [status, setStatus] = useState(
    tokenStore.get() || returningFromMicrosoft ? 'loading' : 'anonymous',
  )

  const loadSession = useCallback(async () => {
    // First: are we landing back from a Microsoft sign-in redirect?
    if (microsoftLoginEnabled) {
      try {
        const idToken = await completeMicrosoftRedirect()
        if (idToken) {
          if (!microsoftExchange) {
            microsoftExchange = authService.microsoftLogin(idToken)
          }
          const session = await microsoftExchange
          setUser(session.user)
          setStatus('authenticated')
          return
        }
      } catch (error) {
        // The account was authenticated by Microsoft but rejected by us (e.g.
        // not on the HR list). Say so, then fall back to the sign-in screen.
        toast.error('Microsoft sign in failed', error.message || 'Please try again.')
        setUser(null)
        setStatus('anonymous')
        return
      }
    }

    if (!tokenStore.get()) {
      setUser(null)
      setStatus('anonymous')
      return
    }
    try {
      const profile = await authService.me()
      setUser(profile)
      setStatus('authenticated')
    } catch {
      authService.logout()
      setUser(null)
      setStatus('anonymous')
    }
  }, [toast])

  useEffect(() => {
    loadSession()
  }, [loadSession])

  // The API client raises this when a JWT is rejected mid-session.
  useEffect(() => {
    const onExpired = () => {
      setUser(null)
      setStatus('anonymous')
    }
    window.addEventListener('cf:session-expired', onExpired)
    return () => window.removeEventListener('cf:session-expired', onExpired)
  }, [])

  const login = useCallback(async (email, password) => {
    const session = await authService.login(email, password)
    setUser(session.user)
    setStatus('authenticated')
    return session
  }, [])

  // Navigates the page to Microsoft; the session is established on the way back
  // by loadSession() above.
  const loginWithMicrosoft = useCallback(() => startMicrosoftRedirect(), [])

  const logout = useCallback(() => {
    authService.logout()
    setUser(null)
    setStatus('anonymous')
  }, [])

  const value = useMemo(
    () => ({
      user,
      status,
      isAuthenticated: status === 'authenticated',
      isLoading: status === 'loading',
      login,
      loginWithMicrosoft,
      logout,
    }),
    [user, status, login, loginWithMicrosoft, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider')
  }
  return context
}
