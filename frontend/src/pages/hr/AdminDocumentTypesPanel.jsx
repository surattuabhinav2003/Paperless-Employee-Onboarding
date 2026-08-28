import { useState } from 'react'
import { DocumentTypeModal } from '../../components/hr/DocumentTypeModal'
import { Button } from '../../components/ui/Button'
import { ErrorState } from '../../components/ui/EmptyState'
import { SkeletonRows } from '../../components/ui/Spinner'
import { StatusPill } from '../../components/ui/StatusPill'
import { useAsync } from '../../hooks/useAsync'
import { useToast } from '../../context/ToastContext'
import { hrService } from '../../services/hrService'

/**
 * Document types the company added to the built-in catalogue.
 *
 * <p>Only the custom ones are listed. The twenty-five built-in types are fixed
 * - each carries behaviour, like which education course applies - so there is
 * nothing here for an admin to change about them, and listing them read-only
 * would just be noise.
 */
export function AdminDocumentTypesPanel() {
  const toast = useToast()
  const [busy, setBusy] = useState(null)
  const [editing, setEditing] = useState(null)
  const [adding, setAdding] = useState(false)

  const types = useAsync(() => hrService.documentTypes(), [])
  const rows = types.data || []
  const live = rows.filter((t) => !t.archived)
  const withdrawn = rows.filter((t) => t.archived)

  /* Both actions return the whole list, so the screen never has to guess what
     the change did to the ordering. */
  const run = async (type, action, failure) => {
    setBusy(type.id)
    try {
      types.setData(await action())
    } catch (error) {
      toast.apiError(error, failure)
    } finally {
      setBusy(null)
    }
  }

  return (
    <>
      <p className="mb-4 max-w-3xl text-[13px] leading-6 text-ink-muted">
        Documents of your own, requested alongside the built-in ones. A type added here appears in the
        list HR picks from when inviting a candidate, or when asking an existing candidate for something
        extra.
      </p>

      <section className="c-panel">
        <div className="c-toolbar">
          <span className="c-count">
            {live.length} added type{live.length === 1 ? '' : 's'}
            {withdrawn.length > 0 && ` · ${withdrawn.length} withdrawn`}
          </span>
          <Button size="sm" className="ml-auto" onClick={() => setAdding(true)}>Add document type</Button>
        </div>

        {types.loading && !types.data && <SkeletonRows rows={2} />}

        {types.error && (
          <ErrorState message={types.error.message} action={<Button onClick={types.reload}>Try again</Button>} />
        )}

        {types.data && rows.length === 0 && (
          <p className="px-5 py-8 text-center text-[13px] text-ink-muted">
            No added document types yet. The built-in catalogue already covers education, identity and
            previous employment — add one here for anything it does not.
          </p>
        )}

        {rows.length > 0 && (
          <ul className="px-5 py-2">
            {[...live, ...withdrawn].map((type) => (
              <li
                key={type.id}
                className="flex flex-wrap items-center justify-between gap-3 border-b
                  border-surface-hair py-3 last:border-b-0"
              >
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className={`text-[13.5px] ${
                      type.archived ? 'text-ink-faint line-through' : 'text-ink'
                    }`}>
                      {type.label}
                    </span>
                    {type.archived
                      ? <StatusPill label="Withdrawn" tone="grey" />
                      : type.enabled
                        ? <StatusPill label="Offered to HR" tone="green" />
                        : <StatusPill label="Not offered" tone="slate" />}
                  </div>
                  <p className="mt-0.5 text-[12px] text-ink-muted">
                    {type.groupLabel}
                    {type.description ? ` · ${type.description}` : ''}
                  </p>
                </div>

                <div className="d-actions shrink-0">
                  {type.archived ? (
                    <Button
                      variant="secondary"
                      size="sm"
                      loading={busy === type.id}
                      onClick={() => run(type,
                        () => hrService.restoreDocumentType(type.id),
                        `Could not restore ${type.label}`)}
                    >
                      Restore
                    </Button>
                  ) : (
                    <>
                      <Button variant="secondary" size="sm" onClick={() => setEditing(type)}>
                        Edit
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        loading={busy === type.id}
                        onClick={() => run(type,
                          () => hrService.removeDocumentType(type.id),
                          `Could not withdraw ${type.label}`)}
                      >
                        Withdraw
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
        Withdrawing a type stops it being offered for new candidates, but candidates already asked for it
        keep the requirement and anything they uploaded stays on their record. It can be restored later
        with nothing lost.
      </p>

      <DocumentTypeModal
        open={adding || Boolean(editing)}
        documentType={editing}
        onClose={() => { setAdding(false); setEditing(null) }}
        onSaved={(next) => types.setData(next)}
      />
    </>
  )
}
