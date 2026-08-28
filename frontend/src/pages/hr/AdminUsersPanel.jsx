import { useState } from 'react'
import { AddHrUserModal } from '../../components/hr/AddHrUserModal'
import { Button } from '../../components/ui/Button'
import { ErrorState } from '../../components/ui/EmptyState'
import { SkeletonRows } from '../../components/ui/Spinner'
import { StatusPill } from '../../components/ui/StatusPill'
import { useAsync } from '../../hooks/useAsync'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../context/ToastContext'
import { hrService } from '../../services/hrService'
import { hueOf } from '../../utils/avatar'
import { formatDate, initialsOf } from '../../utils/format'

/**
 * Who can sign in, and which of them are administrators.
 *
 * <p>An admin can change the shape of onboarding for everyone - the document
 * catalogue and the details candidates are asked for - so granting it is a
 * deliberate act rather than a side effect of being added to the team.
 */
export function AdminUsersPanel() {
  const { user } = useAuth()
  const toast = useToast()
  const [busyId, setBusyId] = useState(null)
  const [adding, setAdding] = useState(false)

  const users = useAsync(() => hrService.adminUsers(), [])
  const rows = users.data || []
  const adminCount = rows.filter((u) => u.role === 'admin').length

  const setRole = async (target, role) => {
    setBusyId(target.id)
    try {
      await hrService.setUserRole(target.id, role)
      toast.success(
        role === 'admin' ? 'Admin access granted' : 'Admin access removed',
        `${target.fullName || target.email} is now ${role === 'admin' ? 'an administrator' : 'a standard HR user'}.`,
      )
      users.reload().catch(() => {})
    } catch (error) {
      toast.apiError(error, 'Could not change this user’s access')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <>
      <p className="mb-4 max-w-3xl text-[13px] leading-6 text-ink-muted">
        Everyone who can sign in to the console. Administrators can also change what candidates are asked
        for, and who else is an administrator.
      </p>

      <section className="c-panel">
        <div className="c-toolbar">
          <span className="c-count">
            {rows.length} user{rows.length === 1 ? '' : 's'} &middot; {adminCount} admin
            {adminCount === 1 ? '' : 's'}
          </span>
          <Button size="sm" className="ml-auto" onClick={() => setAdding(true)}>Add person</Button>
        </div>

        {users.loading && !users.data && <SkeletonRows rows={3} />}

        {users.error && (
          <ErrorState message={users.error.message} action={<Button onClick={users.reload}>Try again</Button>} />
        )}

        {rows.length > 0 && (
          <ul className="d-list">
            {rows.map((row) => {
              const isAdmin = row.role === 'admin'
              const isSelf = row.id === user?.id
              // Refusing here matches the server, which also blocks it - this
              // just explains why before they click.
              const lastAdmin = isAdmin && adminCount <= 1
              return (
                <li key={row.id}>
                  <div className={`d-row d-row--${isAdmin ? 'verified' : 'submitted'}`}>
                    <span className={`r-av c-ring--h${hueOf(row.fullName || row.email)}`} aria-hidden="true">
                      {initialsOf(row.fullName || row.email)}
                    </span>

                    <div className="d-body">
                      <div className="d-title-row">
                        <span className="d-title">{row.fullName || row.email}</span>
                        <StatusPill label={row.roleLabel} tone={isAdmin ? 'green' : 'slate'} />
                        {isSelf && <span className="d-course">You</span>}
                        {!row.lastLoginAt && (
                          <StatusPill label="Not signed in yet" tone="amber" />
                        )}
                      </div>
                      <p className="d-meta">
                        {row.email}
                        {row.jobTitle ? ` · ${row.jobTitle}` : ''}
                        {row.createdAt ? ` · added ${formatDate(row.createdAt)}` : ''}
                      </p>
                    </div>

                    <div className="d-actions">
                      {isAdmin ? (
                        <Button
                          variant="secondary"
                          size="sm"
                          loading={busyId === row.id}
                          disabled={isSelf || lastAdmin}
                          title={isSelf
                            ? 'You cannot remove your own admin access'
                            : lastAdmin ? 'Make someone else an admin first' : undefined}
                          onClick={() => setRole(row, 'hr')}
                        >
                          Remove admin
                        </Button>
                      ) : (
                        <Button
                          size="sm"
                          loading={busyId === row.id}
                          onClick={() => setRole(row, 'admin')}
                        >
                          Make admin
                        </Button>
                      )}
                    </div>
                  </div>
                </li>
              )
            })}
          </ul>
        )}
      </section>

      <p className="mt-4 text-[12.5px] leading-6 text-ink-muted">
        Anyone added here can sign in with their Microsoft work account straight away — no password to
        set or send. People who sign in without being added first also appear here automatically, as
        standard HR users.
      </p>

      <AddHrUserModal
        open={adding}
        onClose={() => setAdding(false)}
        onAdded={() => users.reload().catch(() => {})}
      />
    </>
  )
}
