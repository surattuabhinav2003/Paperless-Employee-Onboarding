export function Spinner({ size = 16, className = '' }) {
  return (
    <svg
      className={`animate-spin ${className}`}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeOpacity="0.25" strokeWidth="3" />
      <path
        d="M21 12a9 9 0 0 0-9-9"
        stroke="currentColor"
        strokeWidth="3"
        strokeLinecap="round"
      />
    </svg>
  )
}

export function LoadingState({ label = 'Loading', className = '' }) {
  return (
    <div className={`flex items-center justify-center gap-2 py-12 text-ink-muted ${className}`}>
      <Spinner size={18} className="text-brand" />
      <span className="text-[13.5px]">{label}...</span>
    </div>
  )
}

export function SkeletonRows({ rows = 5, className = '' }) {
  return (
    <div className={`space-y-2.5 ${className}`}>
      {Array.from({ length: rows }).map((_, index) => (
        <div key={index} className="cf-skeleton h-11 w-full" />
      ))}
    </div>
  )
}
