import { useState } from 'react'
import { Button } from '../ui/Button'
import { Field, TextInput } from '../ui/Field'
import { Modal } from '../ui/Modal'
import { useToast } from '../../context/ToastContext'
import { hrService } from '../../services/hrService'
import {
  DEFAULT_DOCUMENT_SELECTION,
  DocumentChecklistPicker,
  toRequiredDocuments,
} from './DocumentChecklistPicker'

const emptyRow = () => ({ key: Math.random().toString(36).slice(2), name: '', email: '' })

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/**
 * Invites a batch of candidates who all need the same documents.
 *
 * <p>Role, department and the document checklist are entered once and applied
 * to everyone - that repetition is the whole reason this screen exists. Anyone
 * needing a different checklist is added through "New candidate" instead.
 *
 * <p>Rows succeed or fail individually on the server, so the result is shown as
 * a per-row report rather than a single success or error toast.
 */
export function BulkInviteModal({ open, onClose, documentTypes = [], onCreated }) {
  const toast = useToast()
  const [shared, setShared] = useState({ role: '', department: '' })
  const [rows, setRows] = useState(() => [emptyRow(), emptyRow(), emptyRow()])
  const [selected, setSelected] = useState(() => new Set(DEFAULT_DOCUMENT_SELECTION))
  const [optional, setOptional] = useState(() => new Set())
  const [errors, setErrors] = useState({})
  const [rowErrors, setRowErrors] = useState({})
  const [saving, setSaving] = useState(false)
  const [result, setResult] = useState(null)

  const reset = () => {
    setShared({ role: '', department: '' })
    setRows([emptyRow(), emptyRow(), emptyRow()])
    setSelected(new Set(DEFAULT_DOCUMENT_SELECTION))
    setOptional(new Set())
    setErrors({})
    setRowErrors({})
    setResult(null)
  }

  const close = () => {
    if (saving) return
    reset()
    onClose()
  }

  const setRow = (key, patch) => {
    setRows((current) => current.map((row) => (row.key === key ? { ...row, ...patch } : row)))
    setRowErrors((current) => ({ ...current, [key]: undefined }))
  }

  const addRow = () => setRows((current) => [...current, emptyRow()])
  const removeRow = (key) => setRows((current) => current.filter((row) => row.key !== key))

  /** Rows the user actually typed into - blank ones are simply ignored. */
  const filledRows = rows.filter((row) => row.name.trim() || row.email.trim())

  const validate = () => {
    const next = {}
    const perRow = {}
    if (!shared.role.trim()) next.role = 'Role is required'
    if (!shared.department.trim()) next.department = 'Department is required'
    if (selected.size === 0) next.requiredDocuments = 'Select at least one document'
    if (filledRows.length === 0) next.rows = 'Add at least one candidate'

    const seen = new Map()
    filledRows.forEach((row) => {
      const email = row.email.trim().toLowerCase()
      if (!row.name.trim()) perRow[row.key] = 'Name is required'
      else if (!row.email.trim()) perRow[row.key] = 'Email is required'
      else if (!EMAIL_RE.test(email)) perRow[row.key] = 'Enter a valid email address'
      else if (seen.has(email)) perRow[row.key] = 'This email is already in the list'
      else seen.set(email, row.key)
    })

    setErrors(next)
    setRowErrors(perRow)
    return Object.keys(next).length === 0 && Object.keys(perRow).length === 0
  }

  const submit = async () => {
    if (!validate()) return
    setSaving(true)
    try {
      const outcome = await hrService.createCandidatesBulk({
        candidates: filledRows.map((row) => ({
          name: row.name.trim(),
          email: row.email.trim(),
          role: shared.role.trim(),
          department: shared.department.trim(),
        })),
        requiredDocuments: toRequiredDocuments(selected, optional),
      })

      if (outcome.created > 0) {
        toast.success(
          `${outcome.created} candidate${outcome.created === 1 ? '' : 's'} invited`,
          outcome.failed > 0
            ? `${outcome.failed} could not be added - see the list.`
            : 'Each of them was emailed their own portal link.',
        )
        onCreated?.(outcome)
      } else {
        toast.error('Nobody was invited', 'Every row failed - see the list.')
      }

      // Kept open on any failure so HR can see exactly which rows to fix.
      if (outcome.failed > 0) setResult(outcome)
      else { reset(); onClose() }
    } catch (error) {
      if (error.fieldErrors && Object.keys(error.fieldErrors).length) setErrors(error.fieldErrors)
      toast.apiError(error, 'Could not invite these candidates')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={close}
      title={result ? 'Invite results' : 'Invite several candidates'}
      description={result
        ? 'Everyone who went through has been emailed. The rest are listed with the reason.'
        : 'Everyone here gets the same role, department and document checklist, and their own portal link.'}
      size="xl"
      footer={result ? (
        <Button onClick={close}>Done</Button>
      ) : (
        <>
          <Button variant="ghost" onClick={close} disabled={saving}>Cancel</Button>
          <Button onClick={submit} loading={saving}>
            {filledRows.length > 0
              ? `Invite ${filledRows.length} candidate${filledRows.length === 1 ? '' : 's'}`
              : 'Invite candidates'}
          </Button>
        </>
      )}
    >
      {result ? (
        <ul className="space-y-2">
          {result.rows.map((row) => (
            <li
              key={row.email}
              className={`flex flex-wrap items-center justify-between gap-3 rounded border px-3.5 py-2.5 ${
                row.success ? 'border-surface-line bg-surface-canvas' : 'border-accent-red/30 bg-[#FFF5F5]'
              }`}
            >
              <span className="min-w-0">
                <span className="block text-[13.5px] font-medium text-ink">{row.name}</span>
                <span className="block text-[12px] text-ink-muted">{row.email}</span>
              </span>
              <span className={`text-[12.5px] ${row.success ? 'text-ink-muted' : 'text-accent-red'}`}>
                {row.success
                  ? (row.invitationSent ? 'Invited' : 'Created - email not sent')
                  : row.error}
              </span>
            </li>
          ))}
        </ul>
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Role" htmlFor="bulk-role" required error={errors.role}
              hint="Applied to everyone in this batch.">
              <TextInput
                id="bulk-role"
                value={shared.role}
                error={errors.role}
                placeholder="Software Engineer"
                onChange={(event) => setShared({ ...shared, role: event.target.value })}
              />
            </Field>
            <Field label="Department" htmlFor="bulk-department" required error={errors.department}
              hint="Applied to everyone in this batch.">
              <TextInput
                id="bulk-department"
                value={shared.department}
                error={errors.department}
                placeholder="Engineering"
                onChange={(event) => setShared({ ...shared, department: event.target.value })}
              />
            </Field>
          </div>

          <div className="mt-6">
            <div className="flex items-end justify-between gap-3">
              <div>
                <h3 className="text-[14px] font-semibold text-ink">Candidates</h3>
                <p className="mt-1 text-[12.5px] text-ink-muted">
                  Each one gets their own secure link. Blank rows are ignored.
                </p>
              </div>
              <span className="shrink-0 rounded bg-brand-tint px-2.5 py-1 text-[11.5px] font-medium text-brand">
                {filledRows.length} candidate{filledRows.length === 1 ? '' : 's'}
              </span>
            </div>

            {errors.rows && <p className="mt-2 text-[12px] text-accent-red">{errors.rows}</p>}

            <div className="mt-3 space-y-2">
              {rows.map((row, index) => (
                <div key={row.key}>
                  <div className="flex items-start gap-2">
                    <span className="mt-2.5 w-5 shrink-0 text-right text-[12px] tabular-nums text-ink-faint">
                      {index + 1}
                    </span>
                    <TextInput
                      value={row.name}
                      error={rowErrors[row.key]}
                      placeholder="Full name"
                      aria-label={`Candidate ${index + 1} name`}
                      onChange={(event) => setRow(row.key, { name: event.target.value })}
                    />
                    <TextInput
                      type="email"
                      value={row.email}
                      error={rowErrors[row.key]}
                      placeholder="name@example.com"
                      aria-label={`Candidate ${index + 1} email`}
                      onChange={(event) => setRow(row.key, { email: event.target.value })}
                    />
                    <button
                      type="button"
                      onClick={() => removeRow(row.key)}
                      disabled={rows.length === 1}
                      aria-label={`Remove candidate ${index + 1}`}
                      className="mt-1 shrink-0 rounded p-1.5 text-ink-faint transition hover:bg-surface-canvas
                        hover:text-accent-red disabled:cursor-not-allowed disabled:opacity-40"
                    >
                      <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor"
                        strokeWidth="1.9" strokeLinecap="round">
                        <path d="M18 6 6 18M6 6l12 12" />
                      </svg>
                    </button>
                  </div>
                  {rowErrors[row.key] && (
                    <p className="ml-7 mt-1 text-[12px] text-accent-red">{rowErrors[row.key]}</p>
                  )}
                </div>
              ))}
            </div>

            <Button variant="secondary" size="sm" onClick={addRow} className="mt-3">
              Add another
            </Button>
          </div>

          <div className="mt-6">
            <DocumentChecklistPicker
              documentTypes={documentTypes}
              selected={selected}
              optional={optional}
              setSelected={setSelected}
              setOptional={setOptional}
              error={errors.requiredDocuments}
              description="Everyone in this batch is asked for exactly these. Mandatory documents gate the offer stage; optional ones do not."
            />
          </div>
        </>
      )}
    </Modal>
  )
}
