import { TONE_CLASSES } from '../../utils/status'

export function StatusPill({ label, tone = 'grey', icon = null, className = '' }) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 py-1
        text-[11.5px] font-medium leading-none ${TONE_CLASSES[tone] || TONE_CLASSES.grey} ${className}`}
    >
      {icon}
      {label}
    </span>
  )
}

export function Dot({ tone = 'grey' }) {
  const colors = {
    grey: 'bg-ink-muted/50',
    blue: 'bg-brand',
    amber: 'bg-accent-orange',
    green: 'bg-accent-green',
    teal: 'bg-accent-teal',
    red: 'bg-accent-red',
  }
  return <span className={`h-1.5 w-1.5 rounded-full ${colors[tone] || colors.grey}`} />
}
