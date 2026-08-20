import { useRef, useState } from 'react'
import { formatBytes } from '../../utils/format'

/**
 * Drag-and-drop file picker. Client-side checks are a convenience only; the
 * backend validates type and size again on every upload.
 */
export function FileDropzone({
  onSelect,
  accept = '.pdf,.png,.jpg,.jpeg,.webp',
  maxBytes = 10 * 1024 * 1024,
  allowedExtensions = ['pdf', 'png', 'jpg', 'jpeg', 'webp'],
  compact = false,
  disabled = false,
  label = 'Choose file',
}) {
  const inputRef = useRef(null)
  const [dragging, setDragging] = useState(false)
  const [error, setError] = useState(null)

  const handleFiles = (files) => {
    setError(null)
    const file = files?.[0]
    if (!file) return
    const extension = file.name.split('.').pop()?.toLowerCase()
    if (!allowedExtensions.includes(extension)) {
      setError(`Allowed formats: ${allowedExtensions.join(', ')}`)
      return
    }
    if (file.size > maxBytes) {
      setError(`Maximum file size is ${formatBytes(maxBytes)}`)
      return
    }
    onSelect(file)
  }

  return (
    <div>
      <div
        role="button"
        tabIndex={disabled ? -1 : 0}
        onClick={() => !disabled && inputRef.current?.click()}
        onKeyDown={(event) => {
          if (!disabled && (event.key === 'Enter' || event.key === ' ')) {
            event.preventDefault()
            inputRef.current?.click()
          }
        }}
        onDragOver={(event) => {
          event.preventDefault()
          if (!disabled) setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(event) => {
          event.preventDefault()
          setDragging(false)
          if (!disabled) handleFiles(event.dataTransfer.files)
        }}
        className={`flex cursor-pointer flex-col items-center justify-center rounded border-2 border-dashed
          text-center transition
          ${compact ? 'gap-1 px-4 py-4' : 'gap-2 px-6 py-8'}
          ${disabled ? 'cursor-not-allowed border-surface-line bg-surface-offwhite/60 opacity-60' : ''}
          ${dragging ? 'border-brand bg-brand-tint' : 'border-surface-line bg-surface-offwhite/60 hover:border-brand/60 hover:bg-brand-tint/50'}`}
      >
        <svg
          viewBox="0 0 24 24"
          className={`${compact ? 'h-4 w-4' : 'h-6 w-6'} text-brand`}
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
        >
          <path d="M12 16V4m0 0L8 8m4-4 4 4M4 16v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" />
        </svg>
        <span className={`font-medium text-ink ${compact ? 'text-[12.5px]' : 'text-[13.5px]'}`}>
          {label}
        </span>
        <span className="text-[11.5px] text-ink-muted">
          Drag and drop, or click to browse &middot; {allowedExtensions.join(', ')} up to{' '}
          {formatBytes(maxBytes)}
        </span>
        <input
          ref={inputRef}
          type="file"
          accept={accept}
          disabled={disabled}
          className="hidden"
          onChange={(event) => {
            handleFiles(event.target.files)
            event.target.value = ''
          }}
        />
      </div>
      {error && <p className="mt-1.5 text-[12px] text-accent-red">{error}</p>}
    </div>
  )
}
