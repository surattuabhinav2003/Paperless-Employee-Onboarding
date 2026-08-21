import { useState } from 'react'

/** Read-only value with a copy button - used for the candidate portal link. */
export function CopyField({ value, label, className = '' }) {
  const [copied, setCopied] = useState(false)

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      // Clipboard can be blocked; the value stays selectable as a fallback.
    }
  }

  return (
    <div className={className}>
      {label && <span className="cf-label">{label}</span>}
      <div className="flex items-stretch gap-2">
        <input
          readOnly
          value={value || ''}
          onFocus={(event) => event.target.select()}
          className="cf-input flex-1 bg-surface-canvas font-mono text-[12px]"
        />
        <button
          type="button"
          onClick={copy}
          className="shrink-0 rounded border border-brand px-3 text-[13px] font-semibold text-brand
            transition hover:bg-brand-tint"
        >
          {copied ? 'Copied' : 'Copy'}
        </button>
      </div>
    </div>
  )
}
