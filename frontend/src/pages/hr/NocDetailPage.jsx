import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams, useLocation } from 'react-router-dom'
import { DocumentFieldEditor } from '../../components/hr/DocumentFieldEditor'
import { DocumentFieldPreview } from '../../components/hr/DocumentFieldPreview'
import { Button } from '../../components/ui/Button'
import { DocumentViewer } from '../../components/ui/DocumentViewer'
import { ErrorState } from '../../components/ui/EmptyState'
import { LoadingState } from '../../components/ui/Spinner'
import { StatusPill } from '../../components/ui/StatusPill'
import { useToast } from '../../context/ToastContext'
import { hrService } from '../../services/hrService'
import { hueOf } from '../../utils/avatar'
import { formatBytes, formatDate, formatDateTime, initialsOf } from '../../utils/format'

const STATUS_TONE = { draft: 'amber', sent: 'blue', viewed: 'teal', signed: 'green' }

/**
 * One NDA + NOC packet, laid out like the candidate and offer records so the
 * console reads the same way throughout: who it is up top, then the work.
 */
export function NocDetailPage() {
  const { nocId } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const toast = useToast()
  const backTo = location.state?.from || '/noc'

  const [packet, setPacket] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [preparing, setPreparing] = useState(false)
  const [viewing, setViewing] = useState(false)
  const [sending, setSending] = useState(false)
  /* Kept off the record until asked for: it is the credential that opens the
     document, so it should not sit on screen by default. */
  const [signingUrl, setSigningUrl] = useState(null)
  const [loadingLink, setLoadingLink] = useState(false)
  const [copied, setCopied] = useState(false)

  const revealLink = async () => {
    setLoadingLink(true)
    try {
      setSigningUrl(await hrService.nocLink(nocId))
    } catch (caught) {
      toast.apiError(caught, 'Could not fetch the signing link')
    } finally {
      setLoadingLink(false)
    }
  }

  const copyLink = async () => {
    try {
      await navigator.clipboard.writeText(signingUrl)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      // Clipboard can be blocked; the link stays selectable on screen.
    }
  }

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setPacket(await hrService.nocPacket(nocId))
    } catch (caught) {
      setError(caught)
    } finally {
      setLoading(false)
    }
  }, [nocId])

  useEffect(() => { load() }, [load])

  const send = async () => {
    setSending(true)
    try {
      const sent = await hrService.sendNoc(nocId)
      toast.success('Sent for signature', `${sent.recipientEmail} has been emailed the document.`)
      load()
    } catch (caught) {
      toast.apiError(caught, 'Could not send the document')
    } finally {
      setSending(false)
    }
  }

  if (loading && !packet) return <LoadingState label="Loading document" />

  if (error && !packet) {
    return (
      <div className="cf-card">
        <ErrorState
          message={error.message}
          action={
            <div className="flex flex-wrap justify-center gap-2.5">
              <Button onClick={load}>Try again</Button>
              <Button variant="secondary" onClick={() => navigate(backTo)}>Back to NDA &amp; NOC</Button>
            </div>
          }
        />
      </div>
    )
  }

  const isDraft = packet.status === 'draft'
  const signed = packet.status === 'signed'
  const fieldCount = packet.fields?.length || 0

  // Before signing, the field positions live only as metadata - the raw PDF
  // would look empty, so HR needs the overlay to see what they placed. Once
  // signed the answers are baked in, and the plain viewer shows the real thing.
  const showFieldPreview = viewing && !signed && fieldCount > 0

  return (
    <>
      <Link
        to={backTo}
        className="mb-4 inline-flex items-center gap-1.5 text-[13px] font-medium text-ink-muted
          transition hover:text-brand"
      >
        <svg viewBox="0 0 24 24" className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth="2"
          strokeLinecap="round" strokeLinejoin="round">
          <path d="M19 12H5m7-7-7 7 7 7" />
        </svg>
        Back to NDA &amp; NOC
      </Link>

      <section className="c-panel r-head">
        <div className="r-id">
          <span className={`r-av c-ring--h${hueOf(packet.recipientName)}`}>
            {initialsOf(packet.recipientName)}
          </span>

          <div className="r-id-text">
            <div className="r-name-row">
              <h1>{packet.recipientName}</h1>
              <StatusPill label={packet.statusLabel} tone={STATUS_TONE[packet.status] || 'grey'} />
            </div>
            <p className="r-contact">
              {packet.recipientEmail}
              {packet.title ? ` · ${packet.title}` : ''}
            </p>

            <div className="r-track">
              <div className="r-step">
                <span className="r-step-label">Created</span>
                <span className="r-step-value">{formatDate(packet.createdAt)}</span>
              </div>
              <div className="r-step">
                <span className="r-step-label">Sent</span>
                <span className={`r-step-value${packet.sentAt ? '' : ' is-waiting'}`}>
                  {packet.sentAt ? formatDate(packet.sentAt) : 'Not yet'}
                </span>
              </div>
              <div className="r-step">
                <span className="r-step-label">Signed</span>
                <span className={`r-step-value${packet.signedAt ? '' : ' is-waiting'}`}>
                  {packet.signedAt ? formatDate(packet.signedAt) : 'Not yet'}
                </span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <div className="mt-5 grid items-start gap-5 xl:grid-cols-[1.4fr_1fr]">
        <section className={`cf-card p-5 ${signed ? 'cf-spine cf-spine-green' : 'cf-spine'}`}>
          <header className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <h3 className="text-[15px] font-semibold text-ink">The combined document</h3>
              <p className="mt-0.5 text-[12px] text-ink-muted">
                Two documents joined into one, so it is read and signed in a single pass.
              </p>
            </div>
          </header>

          <div className="mt-4 rounded border border-surface-line bg-surface-canvas px-3.5 py-3">
            <p className="text-[13px] text-ink">
              <span className="font-medium">{packet.ndaFilename}</span>
              <span className="text-ink-muted"> + </span>
              <span className="font-medium">{packet.nocFilename}</span>
            </p>
            <p className="mt-1 text-[12px] text-ink-muted">
              {packet.pageCount} page{packet.pageCount === 1 ? '' : 's'} &middot;{' '}
              {formatBytes(packet.sizeBytes)} &middot; added by {packet.createdBy}
            </p>
          </div>

          <dl className="mt-3 grid gap-2 text-[12.5px] sm:grid-cols-2 sm:gap-x-8">
            <Row label="Fields placed" value={fieldCount > 0 ? `${fieldCount} placed` : 'None placed yet'} />
            <Row label="Opened by recipient"
              value={packet.viewedAt ? formatDateTime(packet.viewedAt) : 'Not yet'} />
            <Row label="Signed"
              value={packet.signedAt
                ? `${formatDateTime(packet.signedAt)} · ${packet.signedByName}`
                : 'Not yet'} />
            <Row label="Link expires"
              value={packet.tokenExpiresAt ? formatDate(packet.tokenExpiresAt) : 'Not sent yet'} />
          </dl>

          <div className="mt-4 flex flex-wrap items-center gap-2">
            <Button variant="subtle" size="sm" onClick={() => setViewing(true)}>
              {signed ? 'View signed document' : 'View document'}
            </Button>
            {isDraft && (
              <>
                <Button variant="secondary" size="sm" onClick={() => setPreparing(true)}>
                  {fieldCount > 0 ? 'Edit fields' : 'Add fields'}
                </Button>
                <Button size="sm" onClick={send} loading={sending} disabled={fieldCount === 0}
                  title={fieldCount === 0 ? 'Add at least one field first' : undefined}>
                  Send for signature
                </Button>
              </>
            )}
          </div>

          {isDraft && (
            <p className="mt-3 rounded border border-[#EBC88A] bg-[#FFF8EC] px-3.5 py-2.5
              text-[12.5px] leading-5 text-[#8A5A12]">
              {fieldCount === 0
                ? 'Add at least one signature field before this can be sent. Click "Add fields" above.'
                : 'Not sent yet. Review the document and the fields you placed, then send it - '
                  + 'the recipient cannot see anything until you do.'}
            </p>
          )}
        </section>

        <section className="cf-card p-5">
          <div className="flex items-start justify-between gap-3">
            <div>
              <h3 className="text-[15px] font-semibold text-ink">Recipient</h3>
              <p className="mt-0.5 text-[12px] text-ink-muted">
                They sign through their own private link - no sign-in needed.
              </p>
            </div>
            {packet.sentAt && (
              <StatusPill label={packet.linkActive ? 'Link active' : 'Link expired'}
                tone={packet.linkActive ? 'green' : 'red'} />
            )}
          </div>

          <dl className="mt-4 grid gap-2 text-[12.5px]">
            <Row label="Name" value={packet.recipientName} />
            <Row label="Email" value={packet.recipientEmail} />
            <Row label="Emailed" value={packet.sentAt ? formatDateTime(packet.sentAt) : 'Not sent yet'} />
            <Row label="Expires"
              value={packet.tokenExpiresAt ? formatDate(packet.tokenExpiresAt) : 'Not issued'} />
          </dl>

          {packet.sentAt && (
            <div className="mt-4">
              {signingUrl ? (
                <>
                  <p className="cf-micro text-ink-muted">Signing link</p>
                  <p className="mt-1.5 break-all rounded border border-surface-line bg-surface-canvas
                    px-3 py-2 font-mono text-[11.5px] text-ink-body">
                    {signingUrl}
                  </p>
                  <div className="mt-2 flex flex-wrap gap-2">
                    <Button variant="secondary" size="sm" onClick={copyLink}>
                      {copied ? 'Copied' : 'Copy link'}
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => setSigningUrl(null)}>Hide</Button>
                  </div>
                  <p className="mt-2 text-[11.5px] text-ink-muted">
                    This link opens the document. The recipient still has to confirm a code emailed to
                    them before they can read or sign it.
                  </p>
                </>
              ) : (
                <Button variant="secondary" size="sm" loading={loadingLink} onClick={revealLink}>
                  Show signing link
                </Button>
              )}
            </div>
          )}
        </section>
      </div>

      <DocumentFieldPreview
        open={showFieldPreview}
        onClose={() => setViewing(false)}
        document={{ ...packet, filename: `NDA-NOC-${packet.recipientName}.pdf` }}
        title="NDA + NOC"
        onEdit={isDraft ? () => { setViewing(false); setPreparing(true) } : null}
      />

      <DocumentViewer
        open={viewing && !showFieldPreview}
        onClose={() => setViewing(false)}
        apiPath={packet.downloadUrl}
        title={`${packet.recipientName} · NDA + NOC`}
        filename={`NDA-NOC-${packet.recipientName}.pdf`}
      />

      {isDraft && (
        <DocumentFieldEditor
          open={preparing}
          onClose={() => setPreparing(false)}
          documentUrl={packet.downloadUrl}
          initialFields={packet.fields}
          onSave={(fields) => hrService.saveNocFields(packet.id, fields)}
          title="Add fields to the combined document"
          description="Drag a field onto the document. Both documents are one here, so scroll through all of it."
          onSaved={() => { setPreparing(false); load() }}
        />
      )}
    </>
  )
}

function Row({ label, value }) {
  return (
    <div className="flex items-baseline justify-between gap-4 border-b border-surface-hair pb-1.5">
      <dt className="shrink-0 text-ink-muted">{label}</dt>
      <dd className="text-right font-medium text-ink">{value}</dd>
    </div>
  )
}
