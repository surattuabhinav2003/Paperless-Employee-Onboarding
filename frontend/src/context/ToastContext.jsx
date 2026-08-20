import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react'
import { Toaster } from '../components/ui/Toaster'

const ToastContext = createContext(null)

const DEFAULT_DURATION = 5000

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([])
  const counter = useRef(0)

  const dismiss = useCallback((id) => {
    setToasts((current) => current.filter((toast) => toast.id !== id))
  }, [])

  const push = useCallback(
    (toast) => {
      counter.current += 1
      const id = `toast-${counter.current}`
      const entry = { id, variant: 'info', ...toast }
      setToasts((current) => [...current, entry])
      if (entry.duration !== 0) {
        setTimeout(() => dismiss(id), entry.duration || DEFAULT_DURATION)
      }
      return id
    },
    [dismiss],
  )

  const api = useMemo(
    () => ({
      push,
      dismiss,
      success: (title, description) => push({ variant: 'success', title, description }),
      error: (title, description) => push({ variant: 'error', title, description }),
      info: (title, description) => push({ variant: 'info', title, description }),
      warning: (title, description) => push({ variant: 'warning', title, description }),
      /** Convenience for ApiError instances. */
      apiError: (error, fallback = 'Something went wrong') =>
        push({
          variant: 'error',
          title: fallback,
          description: error?.message || undefined,
        }),
    }),
    [push, dismiss],
  )

  return (
    <ToastContext.Provider value={api}>
      {children}
      <Toaster toasts={toasts} onDismiss={dismiss} />
    </ToastContext.Provider>
  )
}

export function useToast() {
  const context = useContext(ToastContext)
  if (!context) {
    throw new Error('useToast must be used inside a ToastProvider')
  }
  return context
}
