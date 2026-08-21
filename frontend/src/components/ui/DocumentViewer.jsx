import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { apiClient } from '../../services/apiClient'
import { Button } from './Button'
import { LoadingState } from './Spinner'
import { formatBytes } from '../../utils/format'

/**
 * Views a stored document inside the app rather than downloading it.
 * <p>
 * The file is fetched through the API client - so HR requests carry the JWT and
 * candidate requests carry their portal token - and rendered from an object URL:
 * images as an image, everything else (PDFs) in a frame. Download stays available
 * as a secondary action.
 */
export function DocumentViewer({ open, onClose, apiPath, title, filename, contentType }) {
  const [state, setState] = useState({ status: 'idle', url: null, type: null, size: 0, error: null })

  useEffect(() => {
    if (!open || !apiPath) return undefined

    let objectUrl = null
    let cancelled = false
    setState({ status: 'loading', url: null, type: null, size: 0, error: null })

    const path = apiPath.startsWith('/api') ? apiPath.slice(4) : apiPath
    apiClient
      .get(path, { responseType: 'blob' })
      .then(({ data }) => {
        if (cancelled) return
        objectUrl = URL.createObjectURL(data)
        setState({
          status: 'ready',
          url: objectUrl,
          type: data.type || contentType || '',
          size: data.size,
          error: null,
        })
      })
      .catch((error) => {
        if (!cancelled) {
          setState({ status: 'error', url: null, type: null, size: 0, error })
        }
      })

    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [open, apiPath, contentType])

  useEffect(() => {
    if (!open) return undefined
    const onKeyDown = (event) => {
      if (event.key === 'Escape') onClose?.()
    }
    document.addEventListener('keydown', onKeyDown)
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      document.body.style.overflow = previousOverflow
    }
  }, [open, onClose])

  if (!open) return null

  const isImage = (state.type || '').startsWith('image/')

  const download = () => {
    if (!state.url) return
    const link = document.createElement('a')
    link.href = state.url
    link.download = filename || 'document'
    document.body.appendChild(link)
    link.click()
    link.remove()
  }

  return createPortal(
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-3 sm:p-6">
      <div className="absolute inset-0 animate-fade-in bg-brand-ink/60 backdrop-blur-[2px]" onClick={onClose} />

      <div
        role="dialog"
        aria-modal="true"
        aria-label={title || 'Document'}
        className="relative flex h-full max-h-[92vh] w-full max-w-4xl animate-slide-up flex-col
          overflow-hidden rounded bg-white shadow-pop"
      >
        <header className="flex flex-wrap items-center justify-between gap-3 border-b border-surface-line
          px-5 py-3.5">
          <div className="min-w-0">
            <h2 className="truncate text-[15px] font-semibold text-ink">{title || 'Document'}</h2>
            <p className="truncate text-[12px] text-ink-muted">
              {filename}
              {state.size ? ` · ${formatBytes(state.size)}` : ''}
            </p>
          </div>
          <div className="flex shrink-0 items-center gap-2">
            <Button variant="subtle" size="sm" onClick={download} disabled={state.status !== 'ready'}>
              Download
            </Button>
            <button
              type="button"
              onClick={onClose}
              className="rounded p-1.5 text-ink-muted transition hover:bg-surface-canvas hover:text-ink"
              aria-label="Close document"
            >
              <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M18 6 6 18M6 6l12 12" />
              </svg>
            </button>
          </div>
        </header>

        <div className="flex-1 overflow-auto bg-surface-canvas p-3">
          {state.status === 'loading' && <LoadingState label="Opening document" />}

          {state.status === 'error' && (
            <div className="flex h-full flex-col items-center justify-center gap-3 text-center">
              <p className="text-[14px] font-medium text-ink">This document could not be opened</p>
              <p className="max-w-sm text-[13px] text-ink-muted">
                {state.error?.message || 'Please try again in a moment.'}
              </p>
            </div>
          )}

          {state.status === 'ready' &&
            (isImage ? (
              <div className="flex h-full items-center justify-center">
                <img
                  src={state.url}
                  alt={title || filename || 'Document'}
                  className="max-h-full max-w-full rounded border border-surface-line bg-white object-contain"
                />
              </div>
            ) : (
              <iframe
                title={title || filename || 'Document'}
                src={state.url}
                className="h-full min-h-[60vh] w-full rounded border border-surface-line bg-white"
              />
            ))}
        </div>
      </div>
    </div>,
    document.body,
  )
}
