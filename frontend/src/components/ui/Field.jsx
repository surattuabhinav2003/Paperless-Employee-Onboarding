export function Field({ label, htmlFor, error, hint, required, children, className = '' }) {
  return (
    <div className={className}>
      {label && (
        <label className="cf-label" htmlFor={htmlFor}>
          {label}
          {required && <span className="ml-0.5 text-accent-red">*</span>}
        </label>
      )}
      {children}
      {error ? (
        <p className="mt-1.5 flex items-start gap-1 text-[12px] text-accent-red">
          <svg viewBox="0 0 24 24" className="mt-[3px] h-3 w-3 shrink-0" fill="none" stroke="currentColor" strokeWidth="2.5">
            <path d="M12 8v5m0 3h.01" />
            <circle cx="12" cy="12" r="9" />
          </svg>
          {error}
        </p>
      ) : (
        hint && <p className="mt-1.5 text-[12px] text-ink-muted">{hint}</p>
      )}
    </div>
  )
}

export function TextInput({ error, className = '', ...props }) {
  return <input className={`cf-input ${error ? 'cf-input-error' : ''} ${className}`} {...props} />
}

export function TextArea({ error, className = '', rows = 3, ...props }) {
  return (
    <textarea
      rows={rows}
      className={`cf-input resize-y ${error ? 'cf-input-error' : ''} ${className}`}
      {...props}
    />
  )
}

export function Select({ error, className = '', children, ...props }) {
  return (
    <select className={`cf-input cf-input--select ${error ? 'cf-input-error' : ''} ${className}`} {...props}>
      {children}
    </select>
  )
}

export function Checkbox({ label, description, className = '', ...props }) {
  return (
    <label className={`flex cursor-pointer items-start gap-2.5 ${className}`}>
      <input
        type="checkbox"
        className="mt-0.5 h-4 w-4 shrink-0 cursor-pointer rounded border-surface-line text-brand
          accent-brand focus:ring-brand"
        {...props}
      />
      <span className="min-w-0">
        <span className="block text-[13.5px] text-ink-body">{label}</span>
        {description && <span className="mt-0.5 block text-[12px] text-ink-muted">{description}</span>}
      </span>
    </label>
  )
}
