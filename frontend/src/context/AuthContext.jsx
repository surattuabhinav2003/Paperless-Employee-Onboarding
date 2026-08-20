import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { authService } from '../services/authService'
import { tokenStore } from '../services/apiClient'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [status, setStatus] = useState(tokenStore.get() ? 'loading' : 'anonymous')

  const loadSession = useCallback(async () => {
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
  }, [])

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
      logout,
    }),
    [user, status, login, logout],
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
