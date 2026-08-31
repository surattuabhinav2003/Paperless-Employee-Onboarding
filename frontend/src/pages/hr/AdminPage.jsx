import { useState } from 'react'
import { PageHeader } from '../../components/PageHeader'
import { AdminDeletePanel } from './AdminDeletePanel'
import { AdminDocumentTypesPanel } from './AdminDocumentTypesPanel'
import { AdminFieldsPanel } from './AdminFieldsPanel'
import { AdminUsersPanel } from './AdminUsersPanel'

/**
 * Everything an administrator configures, in one place.
 *
 * <p>These were separate sidebar entries at first, which pushed the settings
 * out into the day-to-day navigation and would only have got worse as more were
 * added. They are all "change how onboarding works for everyone", so they
 * belong behind one door with tabs.
 */
const TABS = [
  { key: 'users', label: 'People & access' },
  { key: 'fields', label: 'Candidate detail fields' },
  { key: 'documents', label: 'Document types' },
  // Last, and named for what it does. Destructive work belongs somewhere you
  // have to go on purpose, not beside the records it destroys.
  { key: 'delete', label: 'Delete records' },
]

export function AdminPage() {
  const [tab, setTab] = useState('users')

  return (
    <>
      <PageHeader
        breadcrumb="HR console"
        title="Admin settings"
        subtitle="Who can sign in, and how onboarding works for everyone. Changes here apply across the whole console."
      />

      <nav className="mb-5 flex flex-wrap gap-1 border-b border-surface-line">
        {TABS.map((item) => (
          <button
            key={item.key}
            type="button"
            onClick={() => setTab(item.key)}
            className={`-mb-px border-b-2 px-4 py-2.5 text-[13.5px] font-medium transition ${
              tab === item.key
                ? 'border-brand text-brand'
                : 'border-transparent text-ink-muted hover:border-surface-line hover:text-ink'
            }`}
          >
            {item.label}
          </button>
        ))}
      </nav>

      {tab === 'users' && <AdminUsersPanel />}
      {tab === 'fields' && <AdminFieldsPanel />}
      {tab === 'documents' && <AdminDocumentTypesPanel />}
      {tab === 'delete' && <AdminDeletePanel />}
    </>
  )
}
