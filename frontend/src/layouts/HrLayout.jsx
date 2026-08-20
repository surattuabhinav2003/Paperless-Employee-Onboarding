import { useCallback, useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Logo } from '../components/ui/Logo'
import { useAuth } from '../context/AuthContext'
import { initialsOf } from '../utils/format'

const COLLAPSED_KEY = 'cf_hr_sidebar_collapsed'

/** Storage is a nicety here - a browser that blocks it just loses the preference. */
function readCollapsed() {
  try {
    return window.localStorage?.getItem(COLLAPSED_KEY) === '1'
  } catch {
    return false
  }
}

function writeCollapsed(collapsed) {
  try {
    window.localStorage?.setItem(COLLAPSED_KEY, collapsed ? '1' : '0')
  } catch {
    // Preference simply is not remembered.
  }
}

const NAV_ITEMS = [
  {
    to: '/dashboard',
    label: 'Dashboard',
    hint: 'Live onboarding overview',
    icon: 'M4 13h6V4H4v9Zm10 7h6v-9h-6v9ZM4 20h6v-5H4v5Zm10-11h6V4h-6v5Z',
  },
  {
    to: '/candidates',
    label: 'Pipeline',
    hint: 'All candidates and stages',
    icon: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Zm9 14v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75',
  },
  {
    to: '/documents',
    label: 'Document Requests',
    hint: 'Verify and reject uploads',
    icon: 'M14 3v5h5M6 3h9l5 5v13H6V3Zm3 9h6M9 16h6',
  },
  {
    to: '/offers-bonds',
    label: 'Offers & Bonds',
    hint: 'Publish offers, track signatures',
    icon: 'M9 12l2 2 4-4M7.8 4.6a3 3 0 0 0-3.2 3.2 3 3 0 0 1-.8 2.4 3 3 0 0 0 0 4 3 3 0 0 1 .8 2.4 3 3 0 0 0 3.2 3.2 3 3 0 0 1 2.3 1 3 3 0 0 0 3.8 0 3 3 0 0 1 2.3-1 3 3 0 0 0 3.2-3.2 3 3 0 0 1 .8-2.4 3 3 0 0 0 0-4 3 3 0 0 1-.8-2.4 3 3 0 0 0-3.2-3.2 3 3 0 0 1-2.3-1 3 3 0 0 0-3.8 0 3 3 0 0 1-2.3 1Z',
  },
]

export function HrLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [mobileOpen, setMobileOpen] = useState(false)
  // Remembered per browser, so HR keeps the width they prefer.
  const [collapsed, setCollapsed] = useState(readCollapsed)

  useEffect(() => {
    setMobileOpen(false)
  }, [location.pathname])

  useEffect(() => {
    writeCollapsed(collapsed)
  }, [collapsed])

  const toggleCollapsed = useCallback(() => setCollapsed((current) => !current), [])

  const signOut = () => {
    logout()
    navigate('/login', { replace: true })
  }


  /** @param {boolean} rail icon-only rail (desktop collapsed); the mobile drawer is always full width */
  const sidebarBody = (rail) => (
    <div className="flex h-full flex-col bg-gradient-to-b from-brand-ink via-[#021A63] to-brand pb-4 pt-5">
      <div className={`pb-5 ${rail ? 'flex flex-col items-center gap-3 px-3' : 'flex items-center gap-2 px-5'}`}>
        <Logo onDark subtitle={rail ? null : 'HR Onboarding'} compact={rail} />
        <button
          type="button"
          onClick={toggleCollapsed}
          className={`hidden shrink-0 rounded p-1.5 text-white/60 transition hover:bg-white/10
            hover:text-white lg:block ${rail ? '' : 'ml-auto'}`}
          aria-label={rail ? 'Expand navigation' : 'Collapse navigation'}
          aria-expanded={!rail}
          title={rail ? 'Expand navigation' : 'Collapse navigation'}
        >
          <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2"
            strokeLinecap="round" strokeLinejoin="round">
            <path d={rail ? 'm9 18 6-6-6-6' : 'm15 18-6-6 6-6'} />
          </svg>
        </button>
      </div>

      <nav className={`flex flex-1 flex-col gap-1 ${rail ? 'px-2' : 'px-3'}`}>
        {!rail && (
          <p className="px-3 pb-2 pt-1 text-[10.5px] font-semibold uppercase tracking-[0.16em] text-white/40">
            Onboarding
          </p>
        )}
        {NAV_ITEMS.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            title={rail ? item.label : undefined}
            className={({ isActive }) =>
              `group flex rounded transition-colors ${
                rail ? 'items-center justify-center p-2.5' : 'items-start gap-3 px-3 py-2.5'
              } ${
                isActive
                  ? 'bg-white/12 text-white ring-1 ring-inset ring-white/15'
                  : 'text-white/70 hover:bg-white/8 hover:text-white'
              }`
            }
          >
            <svg
              viewBox="0 0 24 24"
              className={`h-4 w-4 shrink-0 ${rail ? '' : 'mt-0.5'}`}
              fill="none"
              stroke="currentColor"
              strokeWidth="1.7"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d={item.icon} />
            </svg>
            {!rail && (
              <span className="min-w-0">
                <span className="block text-[13.5px] font-medium leading-5">{item.label}</span>
                <span className="block truncate text-[11px] text-white/45">{item.hint}</span>
              </span>
            )}
          </NavLink>
        ))}
      </nav>

      <div className={`mt-4 ${rail ? 'px-2' : 'px-3'}`}>
        {rail ? (
          <div className="flex flex-col items-center gap-2">
            <span
              className="flex h-9 w-9 items-center justify-center rounded-full bg-white/15 text-[12px]
                font-semibold text-white"
              title={`${user?.fullName || 'HR user'} (${user?.email || ''})`}
            >
              {initialsOf(user?.fullName)}
            </span>
            <button
              type="button"
              onClick={signOut}
              className="rounded p-2 text-white/70 transition hover:bg-white/10 hover:text-white"
              aria-label="Log out"
              title="Log out"
            >
              <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2"
                strokeLinecap="round">
                <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9" />
              </svg>
            </button>
          </div>
        ) : (
          <div className="rounded-card bg-white/8 p-3 ring-1 ring-inset ring-white/12">
            <p className="text-[11px] font-medium uppercase tracking-[0.12em] text-white/50">Signed in as</p>
            <div className="mt-2 flex items-center gap-2.5">
              <span className="flex h-8 w-8 items-center justify-center rounded-full bg-white/15 text-[12px]
                font-semibold text-white">
                {initialsOf(user?.fullName)}
              </span>
              <span className="min-w-0 flex-1">
                <span className="block truncate text-[13px] font-medium text-white">
                  {user?.fullName || 'HR user'}
                </span>
                <span className="block truncate text-[11px] text-white/50">{user?.email}</span>
              </span>
            </div>
            <button
              type="button"
              onClick={signOut}
              className="mt-3 flex w-full items-center justify-center gap-1.5 rounded border border-white/25
                px-3 py-2 text-[12.5px] font-semibold text-white transition hover:bg-white hover:text-brand"
            >
              <svg viewBox="0 0 24 24" className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth="2"
                strokeLinecap="round">
                <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9" />
              </svg>
              Log out
            </button>
          </div>
        )}
      </div>
    </div>
  )

  return (
    <div className="min-h-screen bg-surface-offwhite">
      <aside
        className={`fixed inset-y-0 left-0 z-30 hidden transition-[width] duration-200 ease-out lg:block
          ${collapsed ? 'w-[76px]' : 'w-[268px]'}`}
      >
        {sidebarBody(collapsed)}
      </aside>

      {mobileOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-brand-ink/50" onClick={() => setMobileOpen(false)} />
          <aside className="absolute inset-y-0 left-0 w-[280px] animate-slide-up shadow-pop">
            {sidebarBody(false)}
          </aside>
        </div>
      )}

      <div
        className={`transition-[padding] duration-200 ease-out ${
          collapsed ? 'lg:pl-[76px]' : 'lg:pl-[268px]'
        }`}
      >
        {/* Only needed below lg, where the sidebar is collapsed behind this menu
            button. On desktop the sidebar carries the branding and the signed-in
            user, so no top bar is rendered at all. */}
        <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-surface-line
          bg-white/90 px-4 backdrop-blur sm:px-6 lg:hidden">
          <button
            type="button"
            onClick={() => setMobileOpen(true)}
            className="rounded p-2 text-ink-body transition hover:bg-surface-offwhite"
            aria-label="Open navigation"
          >
            <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2"
              strokeLinecap="round">
              <path d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>
          <Logo subtitle={null} compact />
        </header>

        <main className="mx-auto w-full max-w-[1400px] px-4 py-6 sm:px-6 lg:py-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
