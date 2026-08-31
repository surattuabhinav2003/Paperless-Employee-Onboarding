import { useMemo, useState } from 'react'
import { Button } from '../../components/ui/Button'
import { ErrorState } from '../../components/ui/EmptyState'
import { Modal } from '../../components/ui/Modal'
import { useToast } from '../../context/ToastContext'
import { useAsync } from '../../hooks/useAsync'
import { hrService } from '../../services/hrService'
import { searchCandidates } from '../../utils/search'

/**
 * Permanent deletion, kept behind Admin settings.
 *
 * <p>Deliberately not a delete button on every row. Onboarding holds Aadhaar
 * and PAN numbers, bank details and scans of the documents behind them, and a
 * control that sits next to a candidate all day is a control that eventually
 * gets pressed by mistake. Someone who means to destroy a record can come here.
 *
 * <p>Nothing goes without the name being typed back. Not a flourish: it is the
 * difference between confirming a thing and confirming whichever thing happened
 * to be selected, and it is the only step that makes a wrong row obvious before
 * rather than after.
 */
export function AdminDeletePanel() {
  const toast = useToast()
  const [query, setQuery] = useState('')
  const [target, setTarget] = useState(null)   // { kind, id, name, subtitle, preview }
  const [typed, setTyped] = useState('')
  const [busy, setBusy] = useState(false)

  const candidates = useAsync(() => hrService.candidates({ size: 100 }), [])
  const packets = useAsync(() => hrService.nocList({}), [])

  const rows = candidates.data?.content || []
  const search = useMemo(() => searchCandidates(rows, query), [rows, query])

  const nocRows = useMemo(() => {
    const all = packets.data?.content || []
    const q = query.trim().toLowerCase()
    if (!q) return all
    return all.filter((p) => `${p.recipientName} ${p.recipientEmail} ${p.title || ''}`
      .toLowerCase().includes(q))
  }, [packets.data, query])

  const reloadAll = () => {
    candidates.reload().catch(() => {})
    packets.reload().catch(() => {})
  }

  const openCandidate = async (candidate) => {
    setTyped('')
    try {
      const preview = await hrService.deletionPreview(candidate.id)
      setTarget({
        kind: 'candidate',
        id: candidate.id,
        name: candidate.name,
        subtitle: candidate.email,
        preview,
      })
    } catch (error) {
      toast.error('Could not open that record', error.message)
    }
  }

  const openOffer = (candidate) => {
    setTyped('')
    setTarget({
      kind: 'offer',
      id: candidate.id,
      name: candidate.name,
      subtitle: `Offer letter only - ${candidate.email} keeps their documents`,
      preview: { offerSigned: candidate.offerStatus === 'accepted' },
    })
  }

  const openPacket = (packet) => {
    setTyped('')
    setTarget({
      kind: 'noc',
      id: packet.id,
      name: packet.recipientName,
      subtitle: `${packet.recipientEmail} · ${packet.statusLabel}`,
      preview: { offerSigned: packet.status === 'signed' },
    })
  }

  const confirmed = typed.trim().toLowerCase() === (target?.name || '').trim().toLowerCase()

  const destroy = async () => {
    if (!confirmed || !target) return
    setBusy(true)
    try {
      if (target.kind === 'candidate') await hrService.deleteCandidate(target.id)
      else if (target.kind === 'offer') await hrService.deleteCandidateOffer(target.id)
      else await hrService.deleteNoc(target.id)

      toast.success('Deleted', `${target.name} has been permanently removed.`)
      setTarget(null)
      reloadAll()
    } catch (error) {
      toast.error('Could not delete', error.message)
    } finally {
      setBusy(false)
    }
  }

  if (candidates.error && !candidates.data) {
    return <ErrorState message={candidates.error.message}
      action={<Button onClick={() => candidates.reload()}>Try again</Button>} />
  }

  return (
    <>
      <p className="mb-4 text-[13px] leading-6 text-ink-muted">
        Removing a record here is permanent and takes the uploaded documents with it. The audit
        trail is kept on purpose &mdash; what was deleted, by whom, and when stays on the record
        even though the record itself is gone.
      </p>

      <div className="mb-4">
        <input
          className="cf-input w-full max-w-md"
          type="text"
          value={query}
          placeholder="Search candidates and NDA + NOC documents"
          aria-label="Search records to delete"
          onChange={(event) => setQuery(event.target.value)}
        />
      </div>

      <section className="cf-card mb-5 overflow-hidden">
        <header className="flex items-baseline justify-between border-b border-surface-line px-5 py-3">
          <h3 className="text-[13.5px] font-semibold text-ink">Candidates</h3>
          <span className="text-[12px] text-ink-muted">{search.results.length} shown</span>
        </header>
        {search.results.length === 0 ? (
          <p className="px-5 py-6 text-center text-[13px] text-ink-muted">No candidate matches that.</p>
        ) : (
          <ul>
            {search.results.map((candidate) => (
              <li key={candidate.id}
                className="flex flex-wrap items-center gap-3 border-b border-surface-line px-5 py-3 last:border-0">
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-[13.5px] font-medium text-ink">{candidate.name}</span>
                  <span className="block truncate text-[12px] text-ink-muted">
                    {candidate.email} &middot; {candidate.stageLabel}
                  </span>
                </span>
                {candidate.offerPrepared && (
                  <Button variant="secondary" size="sm" onClick={() => openOffer(candidate)}>
                    Delete offer letter
                  </Button>
                )}
                <Button variant="danger" size="sm" onClick={() => openCandidate(candidate)}>
                  Delete candidate
                </Button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="cf-card overflow-hidden">
        <header className="flex items-baseline justify-between border-b border-surface-line px-5 py-3">
          <h3 className="text-[13.5px] font-semibold text-ink">NDA &amp; NOC documents</h3>
          <span className="text-[12px] text-ink-muted">{nocRows.length} shown</span>
        </header>
        {nocRows.length === 0 ? (
          <p className="px-5 py-6 text-center text-[13px] text-ink-muted">Nothing matches that.</p>
        ) : (
          <ul>
            {nocRows.map((packet) => (
              <li key={packet.id}
                className="flex flex-wrap items-center gap-3 border-b border-surface-line px-5 py-3 last:border-0">
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-[13.5px] font-medium text-ink">
                    {packet.recipientName}
                  </span>
                  <span className="block truncate text-[12px] text-ink-muted">
                    {packet.recipientEmail} &middot; {packet.statusLabel}
                  </span>
                </span>
                <Button variant="danger" size="sm" onClick={() => openPacket(packet)}>Delete</Button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <Modal
        open={Boolean(target)}
        onClose={() => (busy ? null : setTarget(null))}
        title="Delete permanently"
        description="This cannot be undone."
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setTarget(null)} disabled={busy}>Cancel</Button>
            <Button variant="danger" onClick={destroy} loading={busy} disabled={!confirmed}>
              Delete permanently
            </Button>
          </>
        }
      >
        {target && (
          <>
            <p className="text-[13.5px] leading-6 text-ink-body">
              <span className="font-semibold text-ink">{target.name}</span>
              <span className="block text-[12.5px] text-ink-muted">{target.subtitle}</span>
            </p>

            <ul className="mt-3 space-y-1 text-[12.5px] leading-5 text-ink-muted">
              {target.kind === 'candidate' && (
                <>
                  <li>&bull; {target.preview.documents} uploaded document
                    {target.preview.documents === 1 ? '' : 's'}, including any identity scans</li>
                  {target.preview.hasProfile && <li>&bull; Their personal and bank details</li>}
                  {target.preview.hasOffer && (
                    <li>&bull; Their offer letter{target.preview.offerSigned ? ', which they have signed' : ''}</li>
                  )}
                </>
              )}
              {target.kind === 'offer' && (
                <li>&bull; The offer letter{target.preview.offerSigned ? ', which they have signed' : ''}.
                  Their documents and details stay.</li>
              )}
              {target.kind === 'noc' && (
                <li>&bull; The combined NDA + NOC{target.preview.offerSigned ? ', which has been signed' : ''}.</li>
              )}
            </ul>

            {target.preview.offerSigned && (
              <p className="mt-3 rounded border border-accent-red/30 bg-accent-red/5 px-3 py-2
                text-[12.5px] leading-5 text-accent-red">
                This has been signed. Removing it destroys the signed copy, and the signature
                cannot be recovered.
              </p>
            )}

            {/* Typing the name is what makes the wrong row obvious before, not after. */}
            <label className="mt-4 block">
              <span className="mb-1.5 block text-[12.5px] text-ink-body">
                Type <b className="text-ink">{target.name}</b> to confirm
              </span>
              <input
                className="cf-input w-full"
                type="text"
                value={typed}
                autoFocus
                autoComplete="off"
                onChange={(event) => setTyped(event.target.value)}
              />
            </label>
          </>
        )}
      </Modal>
    </>
  )
}
