/**
 * The page header. Not a dark slab and not bare canvas: a soft white-to-blue
 * wash with a brand gradient hairline along the top edge, so every page opens
 * with colour without a heavy block of it.
 */
export function PageHeader({ title, subtitle, actions, breadcrumb, className = '' }) {
  return (
    <header className={`c-head ${className}`}>
      <div style={{ minWidth: 0 }}>
        {breadcrumb && (
          <p className="c-head-eyebrow">
            <i aria-hidden="true" />
            {breadcrumb}
          </p>
        )}
        <h1>{title}</h1>
        {subtitle && <p className="c-head-sub">{subtitle}</p>}
      </div>
      {actions && <div className="c-head-actions">{actions}</div>}
    </header>
  )
}
