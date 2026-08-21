import { Spinner } from './Spinner'

/**
 * Styled by `.c-btn` in index.css - core CSS, so the primary variant can carry a
 * real gradient and an inner highlight rather than a flat fill.
 */
const VARIANTS = {
  primary: 'c-btn--primary',
  secondary: 'c-btn--secondary',
  subtle: 'c-btn--subtle',
  ghost: 'c-btn--ghost',
  danger: 'c-btn--danger',
  success: 'c-btn--success',
}

const SIZES = {
  sm: 'c-btn--sm',
  md: '',
  lg: 'c-btn--lg',
}

export function Button({
  variant = 'primary',
  size = 'md',
  type = 'button',
  loading = false,
  disabled = false,
  icon = null,
  className = '',
  children,
  ...rest
}) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      className={`c-btn ${VARIANTS[variant] || VARIANTS.primary} ${SIZES[size] || ''} ${className}`}
      {...rest}
    >
      {loading ? <Spinner size={size === 'sm' ? 12 : 14} /> : icon}
      {children}
    </button>
  )
}
