import { useState } from 'react'
import { CustomFieldModal } from '../../components/hr/CustomFieldModal'
import { Button } from '../../components/ui/Button'
import { StatusPill } from '../../components/ui/StatusPill'
import { ErrorState } from '../../components/ui/EmptyState'
import { SkeletonRows } from '../../components/ui/Spinner'
import { useAsync } from '../../hooks/useAsync'
import { useToast } from '../../context/ToastContext'
import { hrService } from '../../services/hrService'

/**
 * Which personal details candidates are asked for.
 *
 * <p>The set of fields is fixed - each is a real column on the candidate record
 * - but whether each is asked for, and whether it is required, is an
 * administrator's call. Turning one off hides it from the candidate's form and
 * stops it being enforced, immediately and for everyone.
 */
export function AdminFieldsPanel() {
  const toast = useToast()
  const [busy, setBusy] = useState(null)

  const fields = useAsync(() => hrService.candidateFields(), [])
  const rows = fields.data || []

  const custom = useAsync(() => hrService.customFields(), [])
  const customRows = custom.data || []
  const liveCustom = customRows.filter((f) => !f.archived)
  const removedCustom = customRows.filter((f) => f.archived)
  const [editing, setEditing] = useState(null)
  const [adding, setAdding] = useState(false)

  /* Archive and restore both return the whole list, so the screen never has to
     guess what the change did to the ordering. */
  const runCustom = async (field, action, failure) => {
    setBusy(field.id)
    try {
      custom.setData(await action())
    } catch (error) {
      toast.apiError(error, failure)
    } finally {
      setBusy(null)
    }
  }

  // Grouped as the candidate's own form groups them, so the two read alike.
  const groups = rows.reduce((acc, field) => {
    (acc[field.groupLabel] ||= []).push(field)
    return acc
  }, {})

  const save = async (field, next) => {
    setBusy(field.code)
    try {
      const updated = await hrService.setCandidateField(field.code, next)
      fields.setData(updated)
    } catch (error) {
      toast.apiError(error, `Could not update ${field.label}`)
    } finally {
      setBusy(null)
    }
  }

  const enabledCount = rows.filter((f) => f.enabled).length
  const requiredCount = rows.filter((f) => f.enabled && f.required).length

  return (
    <>
      <p className="mb-4 max-w-3xl text-[13px] leading-6 text-ink-muted">
        What candidates are asked for when they fill in their details. Changes apply immediately, to new
        submissions and to HR editing a candidate on their behalf.
      </p>

      <section className="c-panel">
        <div className="c-toolbar">
          <span className="c-count">
            {enabledCount} of {rows.length} asked for &middot; {requiredCount} required
          </span>
        </div>

        {fields.loading && !fields.data && <SkeletonRows rows={5} />}

        {fields.error && (
          <ErrorState message={fields.error.message} action={<Button onClick={fields.reload}>Try again</Button>} />
        )}

        {Object.entries(groups).map(([groupLabel, groupFields]) => (
          <div key={groupLabel} className="border-t border-surface-hair first:border-t-0">
            <p className="px-5 pt-4 text-[11px] font-semibold uppercase tracking-[0.12em] text-ink-muted">
              {groupLabel}
            </p>
            <ul className="px-5 pb-4">
              {groupFields.map((field) => (
                <li
                  key={field.code}
                  className="flex flex-wrap items-center justify-between gap-3 border-b
                    border-surface-hair py-3 last:border-b-0"
                >
                  <label className="flex min-w-0 flex-1 cursor-pointer items-center gap-3">
                    <input
                      type="checkbox"
                      checked={field.enabled}
                      disabled={busy === field.code}
                      onChange={() => save(field, { enabled: !field.enabled, required: field.required })}
                      className="h-4 w-4 shrink-0 cursor-pointer accent-brand"
                    />
                    <span className={`text-[13.5px] ${field.enabled ? 'text-ink' : 'text-ink-faint line-through'}`}>
                      {field.label}
                    </span>
                  </label>

                  {/* Required is meaningless for a field nobody is asked, so it
                      disappears rather than sitting there inert. */}
                  {field.enabled ? (
                    <div className="c-segctl shrink-0" role="group" aria-label={`${field.label} requirement`}>
                      <button
                        type="button"
                        disabled={busy === field.code}
                        className={`c-segbtn${field.required ? ' is-on' : ''}`}
                        onClick={() => save(field, { enabled: true, required: true })}
                      >
                        Required
                      </button>
                      <button
                        type="button"
                        disabled={busy === field.code}
                        className={`c-segbtn${!field.required ? ' is-on' : ''}`}
                        onClick={() => save(field, { enabled: true, required: false })}
                      >
                        Optional
                      </button>
                    </div>
                  ) : (
                    <span className="shrink-0 text-[12px] text-ink-faint">Not asked for</span>
                  )}
                </li>
              ))}
            </ul>
          </div>
        ))}
      </section>

      <p className="mt-4 text-[12.5px] leading-6 text-ink-muted">
        Turning a field off does not delete anything already collected — existing candidate records keep
        the values they have. It only stops the field being asked for and enforced from now on.
      </p>

      {/* Fields the company invented, kept apart from the built-in ones above:
          those are fixed and can only be switched on and off, these can be
          created, renamed and removed. */}
      <section className="c-panel mt-6">
        <div className="c-toolbar">
          <span className="c-count">
            {liveCustom.length} extra field{liveCustom.length === 1 ? '' : 's'}
            {removedCustom.length > 0 && ` · ${removedCustom.length} removed`}
          </span>
          <Button size="sm" className="ml-auto" onClick={() => setAdding(true)}>Add a field</Button>
        </div>

        {custom.loading && !custom.data && <SkeletonRows rows={2} />}

        {custom.error && (
          <ErrorState message={custom.error.message} action={<Button onClick={custom.reload}>Try again</Button>} />
        )}

        {custom.data && customRows.length === 0 && (
          <p className="px-5 py-8 text-center text-[13px] text-ink-muted">
            No extra fields yet. Add one to ask candidates something the built-in details do not cover.
          </p>
        )}

        {customRows.length > 0 && (
          <ul className="px-5 py-2">
            {[...liveCustom, ...removedCustom].map((field) => (
              <li
                key={field.id}
                className="flex flex-wrap items-center justify-between gap-3 border-b
                  border-surface-hair py-3 last:border-b-0"
              >
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className={`text-[13.5px] ${
                      field.archived ? 'text-ink-faint line-through' : 'text-ink'
                    }`}>
                      {field.label}
                    </span>
                    <span className="text-[12px] text-ink-muted">{field.typeLabel}</span>
                    {field.archived
                      ? <StatusPill label="Removed" tone="grey" />
                      : field.required
                        ? <StatusPill label="Required" tone="amber" />
                        : !field.enabled
                          ? <StatusPill label="Not asked for" tone="grey" />
                          : <StatusPill label="Optional" tone="slate" />}
                  </div>
                  <p className="mt-0.5 text-[12px] text-ink-muted">
                    {field.groupLabel}
                    {field.options?.length ? ` · ${field.options.join(', ')}` : ''}
                    {field.helpText ? ` · ${field.helpText}` : ''}
                  </p>
                </div>

                <div className="d-actions shrink-0">
                  {field.archived ? (
                    <Button
                      variant="secondary"
                      size="sm"
                      loading={busy === field.id}
                      onClick={() => runCustom(field,
                        () => hrService.restoreCustomField(field.id),
                        `Could not restore ${field.label}`)}
                    >
                      Restore
                    </Button>
                  ) : (
                    <>
                      <Button variant="secondary" size="sm" onClick={() => setEditing(field)}>
                        Edit
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        loading={busy === field.id}
                        onClick={() => runCustom(field,
                          () => hrService.removeCustomField(field.id),
                          `Could not remove ${field.label}`)}
                      >
                        Remove
                      </Button>
                    </>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      <p className="mt-4 text-[12.5px] leading-6 text-ink-muted">
        Removing an extra field takes it off the form but keeps every answer already given, so it can be
        restored later with nothing lost.
      </p>

      <CustomFieldModal
        open={adding || Boolean(editing)}
        field={editing}
        onClose={() => { setAdding(false); setEditing(null) }}
        onSaved={(next) => custom.setData(next)}
      />
    </>
  )
}
