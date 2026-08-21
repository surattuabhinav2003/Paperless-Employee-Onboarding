import { useCallback, useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Logo } from '../components/ui/Logo'
import { useAuth } from '../context/AuthContext'
import { initialsOf } from '../utils/format'
import '../styles/console.css'

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
    label: 'Candidate Records',
    hint: 'Every candidate and status',
    icon: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Zm9 14v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75',
  },
  {
    to: '/offers',
    label: 'Offer Letters',
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
    <div className="c-rail">
      <div className={rail ? 'c-rail-top c-rail-top--narrow' : 'c-rail-top'}>
        <Logo onDark subtitle={rail ? null : 'HR Onboarding'} compact={rail} />
        <button
          type="button"
          onClick={toggleCollapsed}
          className="c-rail-toggle"
          style={rail ? undefined : { marginLeft: 'auto' }}
          aria-label={rail ? 'Expand navigation' : 'Collapse navigation'}
          aria-expanded={!rail}
          title={rail ? 'Expand navigation' : 'Collapse navigation'}
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
            strokeLinecap="round" strokeLinejoin="round">
            <path d={rail ? 'm9 18 6-6-6-6' : 'm15 18-6-6 6-6'} />
          </svg>
        </button>
      </div>

      <nav className="c-nav">
        {!rail && <p className="c-rail-heading">Onboarding</p>}
        {NAV_ITEMS.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            title={rail ? item.label : undefined}
            className={({ isActive }) =>
              `c-nav-item${rail ? ' c-nav-item--narrow' : ''}${isActive ? ' is-active' : ''}`
            }
          >
            <svg
              viewBox="0 0 24 24"
              className="c-nav-icon"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.7"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d={item.icon} />
            </svg>
            {!rail && (
              <span className="c-nav-text">
                <span className="c-nav-label">{item.label}</span>
                <span className="c-nav-hint">{item.hint}</span>
              </span>
            )}
          </NavLink>
        ))}
      </nav>

      <div className="c-rail-foot">
        {rail ? (
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 10 }}>
            <span className="c-avatar" title={`${user?.fullName || 'HR user'} (${user?.email || ''})`}>
              {initialsOf(user?.fullName)}
            </span>
            <button
              type="button"
              onClick={signOut}
              className="c-signout c-signout--icon"
              aria-label="Log out"
              title="Log out"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
                strokeLinecap="round">
                <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9" />
              </svg>
            </button>
          </div>
        ) : (
          <div className="c-user">
            <div className="c-user-row">
              <span className="c-avatar">{initialsOf(user?.fullName)}</span>
              <span className="c-user-who">
                <span className="c-user-name">{user?.fullName || 'HR user'}</span>
                <span className="c-user-mail">{user?.email}</span>
              </span>
            </div>
            <button type="button" onClick={signOut} className="c-signout">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
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
    <div className="min-h-screen bg-surface-canvas">
      <aside
        className={`fixed inset-y-0 left-0 z-30 hidden transition-[width] duration-200 ease-out lg:block
          ${collapsed ? 'w-[76px]' : 'w-[268px]'}`}
      >
        {sidebarBody(collapsed)}
      </aside>

      {mobileOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-ink/35 backdrop-blur-sm" onClick={() => setMobileOpen(false)} />
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
          bg-white/95 px-4 backdrop-blur sm:px-6 lg:hidden">
          <button
            type="button"
            onClick={() => setMobileOpen(true)}
            className="rounded p-2 text-ink-body transition hover:bg-surface-canvas"
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
