import { Spinner } from './Spinner'

const VARIANTS = {
  // CloudFuze buttons: 4px radius, 600 weight, 10px/20px padding.
  primary:
    'bg-brand text-white hover:bg-brand-bright active:bg-brand-ink disabled:bg-brand/40 shadow-[0_1px_2px_rgba(1,19,63,0.16)]',
  secondary:
    'bg-white text-brand border border-brand hover:bg-brand-tint active:bg-brand-tint/80 disabled:text-brand/40 disabled:border-brand/30',
  subtle:
    'bg-surface-offwhite text-ink-body border border-surface-line hover:bg-white hover:border-brand/40 hover:text-brand',
  ghost: 'text-ink-body hover:bg-surface-offwhite hover:text-brand',
  danger: 'bg-accent-red text-white hover:bg-[#DB1515] disabled:bg-accent-red/40',
  success: 'bg-accent-green text-white hover:bg-[#17B172] disabled:bg-accent-green/40',
}

const SIZES = {
  sm: 'text-[13px] px-3 py-1.5 gap-1.5',
  md: 'text-[14px] px-5 py-2.5 gap-2',
  lg: 'text-[16px] px-6 py-3 gap-2',
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
      className={`inline-flex items-center justify-center rounded font-semibold transition-colors
        disabled:cursor-not-allowed ${VARIANTS[variant]} ${SIZES[size]} ${className}`}
      {...rest}
    >
      {loading ? <Spinner size={size === 'sm' ? 12 : 14} /> : icon}
      {children}
    </button>
  )
}
