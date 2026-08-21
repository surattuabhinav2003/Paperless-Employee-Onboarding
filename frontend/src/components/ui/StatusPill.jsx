import { TONE_CLASSES, TONE_BARS } from '../../utils/status'

/**
 * The app's status shape: a tinted chip with a saturated dot. Both the tint and
 * the dot come from one modifier class in `index.css`, so a state always looks
 * the same wherever it appears - pipeline, candidate record, portal.
 */
export function StatusPill({ label, tone = 'grey', className = '' }) {
  return (
    <span className={`cf-tag ${TONE_CLASSES[tone] || TONE_CLASSES.grey} ${className}`}>
      <i aria-hidden="true" />
      {label}
    </span>
  )
}

export function Dot({ tone = 'grey' }) {
  return (
    <span
      className={TONE_BARS[tone] || TONE_BARS.grey}
      style={{ width: 6, height: 6, borderRadius: 999, display: 'inline-block' }}
    />
  )
}
