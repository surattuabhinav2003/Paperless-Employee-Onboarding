import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useOutletContext } from 'react-router-dom'
import { LockedPanel } from '../../components/portal/LockedPanel'
import { SignatureReviewDialog } from '../../components/portal/SignatureReviewDialog'
import { SignaturePad } from '../../components/portal/SignaturePad'
import { Button } from '../../components/ui/Button'
import { EmptyState, ErrorState } from '../../components/ui/EmptyState'
import { LoadingState } from '../../components/ui/Spinner'
import { Modal } from '../../components/ui/Modal'
import { StatusPill } from '../../components/ui/StatusPill'
import { useToast } from '../../context/ToastContext'
import { useAsync } from '../../hooks/useAsync'
import { fetchBytes, fileUrl } from '../../services/apiClient'
import { portalService } from '../../services/portalService'
import { formatDateTime } from '../../utils/format'
import { loadPdf, renderPageToCanvas } from '../../utils/pdfRender'
import { fieldTypeMeta, todayIso } from '../../utils/offerFields'
import { offerStatusMeta } from '../../utils/status'

const PAGE_CSS_WIDTH = 680

export function PortalOfferPage() {
  const { overview, reloadOverview, token } = useOutletContext()
  const toast = useToast()
  const [editingIndex, setEditingIndex] = useState(null)
  const [values, setValues] = useState({})
  const [draft, setDraft] = useState('')
  const [reviewing, setReviewing] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [downloading, setDownloading] = useState(false)

  const offerStep = overview.steps.find((step) => step.key === 'offer')
  const locked = !overview.offerAvailable

  const { data: offer, error, loading, setData } = useAsync(
    () => (locked ? Promise.resolve(null) : portalService.offer(token)),
    [token, locked],
  )

  // Opening this page counts as viewing the offer, tracked server-side.
  useEffect(() => {
    if (!locked && offer?.prepared && offer.status === 'sent') {
      portalService.markOfferViewed(token).then(setData).catch(() => {})
    }
  }, [locked, offer, token, setData])

  const fields = useMemo(() => offer?.fields || [], [offer])

  // Seed each field from what HR pre-filled, so the candidate is editing a
  // sensible starting point rather than an empty box. A date with no prefill
  // defaults to today, which is what signing one almost always means.
  useEffect(() => {
    if (!fields.length) return
    setValues((current) => {
      if (Object.keys(current).length) return current
      const seeded = {}
      fields.forEach((field, index) => {
        if (field.type === 'signature') return
        if (field.prefill) seeded[index] = field.prefill
        else if (field.type === 'date') seeded[index] = todayIso()
        else if (field.type === 'name') seeded[index] = overview.candidateName || ''
      })
      return seeded
    })
  }, [fields, overview.candidateName])

  const [pages, setPages] = useState([])
  const [pdfLoading, setPdfLoading] = useState(true)
  const [pdfError, setPdfError] = useState(null)
  const canvasRefs = useRef(new Map())
  const fieldRefs = useRef(new Map())

  useEffect(() => {
    if (!offer?.prepared || !offer.downloadUrl) return undefined
    let cancelled = false
    setPdfLoading(true)
    setPdfError(null)

    // Pages mount up front so canvas refs stay stable, then each fills in its
    // height as it renders - a long letter reveals progressively instead of
    // sitting behind a spinner until the final page is done.
    const render = async () => {
      try {
        const bytes = await fetchBytes(offer.downloadUrl)
        const pdf = await loadPdf(bytes)
        let sizes = Array.from({ length: pdf.numPages }, (_, i) =>
          ({ number: i + 1, width: PAGE_CSS_WIDTH, height: 0 }))
        setPages(sizes)
        await new Promise((resolve) => setTimeout(resolve, 0))
        for (let number = 1; number <= pdf.numPages; number++) {
          if (cancelled) return
          const canvas = canvasRefs.current.get(number)
          if (!canvas) continue
          const size = await renderPageToCanvas(pdf, number, canvas, PAGE_CSS_WIDTH)
          sizes = sizes.map((p) => (p.number === number ? { number, ...size } : p))
          setPages(sizes)
          if (number === 1) setPdfLoading(false)
        }
      } catch (err) {
        if (!cancelled) setPdfError(err)
      } finally {
        if (!cancelled) setPdfLoading(false)
      }
    }
    render()
    return () => { cancelled = true }
    // offer.status matters too: after signing, the same URL serves the signed
    // file, so the pages must re-render to show the stamped result.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [offer?.downloadUrl, offer?.status])

  if (locked) {
    return (
      <LockedPanel
        title="Offer locked"
        reason={offerStep?.lockReason ||
          'Your documents are currently being reviewed by HR. The offer letter will become available once all required documents are approved.'}
      />
    )
  }

  if (loading && !offer) return <LoadingState label="Loading your offer letter" />
  if (error && !offer) {
    return (
      <div className="cf-card">
        <ErrorState title="We could not load your offer letter" message={error.message} />
      </div>
    )
  }

  if (!offer?.prepared) {
    return (
      <div className="cf-card">
        <EmptyState
          icon="document"
          title="Your offer letter is being prepared"
          message={offer?.message || 'HR is finalising your offer letter. You will be able to review it here.'}
        />
        <div className="flex justify-center pb-6">
          <Link to={`/portal/${token}/documents`}>
            <Button variant="secondary">Review my documents</Button>
          </Link>
        </div>
      </div>
    )
  }

  const meta = offerStatusMeta(offer.status)
  const accepted = offer.status === 'accepted'
  const filledCount = fields.filter((_, i) => (values[i] || '').trim()).length
  const remaining = fields.length - filledCount
  const allDone = fields.length > 0 && remaining === 0

  /** Opens a field only because the candidate chose it - never pushed on them. */
  const openField = (index) => {
    setEditingIndex(index)
    setDraft(values[index] || '')
  }

  const applyDraft = () => {
    if (!(draft || '').trim()) {
      toast.error('Nothing to fill in', 'Enter something before saving this field.')
      return
    }
    setValues((current) => ({ ...current, [editingIndex]: draft }))
    setEditingIndex(null)
  }

  const submit = async () => {
    setSubmitting(true)
    try {
      const updated = await portalService.signOffer(token, values)
      setData(updated)
      setReviewing(false)
      await reloadOverview()
      toast.success('Offer signed', 'That was the final step - your onboarding is complete.')
    } catch (err) {
      toast.apiError(err, 'We could not record your signature')
    } finally {
      setSubmitting(false)
    }
  }

  const download = async () => {
    setDownloading(true)
    try {
      await portalService.downloadOffer(offer.downloadUrl, `Signed - ${offer.filename}`)
    } catch (err) {
      toast.apiError(err, 'Could not download the offer letter')
    } finally {
      setDownloading(false)
    }
  }

  const editing = editingIndex == null ? null : fields[editingIndex]

  return (
    <div className="space-y-5">
      {/* The action sits above the letter: a long contract should never bury
          the one thing the candidate is here to do. */}
      {accepted ? (
        <section className="cf-card border-accent-green/40 bg-[#F3FCF7] p-5 sm:p-6">
          <div className="flex items-start gap-3">
            <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded
              bg-accent-green text-white">
              <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor"
                strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
                <path d="M20 6 9 17l-5-5" />
              </svg>
            </span>
            <div className="flex-1">
              <h3 className="text-[15px] font-semibold text-ink">Offer signed</h3>
              <p className="mt-1 text-[13px] leading-6 text-ink-body">
                Signed {offer.acceptedAt ? formatDateTime(offer.acceptedAt) : 'just now'}. That was the
                final step - your onboarding is complete.
              </p>
              <div className="mt-4">
                <Button onClick={download} loading={downloading}>
                  Download signed offer letter
                </Button>
              </div>
            </div>
          </div>
        </section>
      ) : (
        <section className="cf-card p-5 sm:p-6">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h3 className="text-[15px] font-semibold text-ink">Sign your offer</h3>
              <p className="mt-1.5 text-[13px] leading-6 text-ink-muted">
                {allDone
                  ? 'Everything is filled in. Review it, then submit when you are happy.'
                  : 'Read the letter below. The highlighted boxes are yours to fill in - '
                    + 'click any one when you are ready.'}
              </p>
            </div>
            <span className="shrink-0 rounded bg-brand-tint px-2.5 py-1 text-[11.5px] font-medium text-brand">
              {filledCount} of {fields.length} filled in
            </span>
          </div>

          <div className="mt-4 h-1.5 overflow-hidden rounded-full bg-surface-line">
            <div
              className="h-full rounded-full bg-brand transition-all"
              style={{ width: `${fields.length ? (filledCount / fields.length) * 100 : 0}%` }}
            />
          </div>

          {allDone && (
            <div className="mt-5">
              <Button onClick={() => setReviewing(true)}>Review and submit</Button>
            </div>
          )}
        </section>
      )}

      <section className="cf-card overflow-hidden">
        <header className="flex flex-wrap items-start justify-between gap-3 border-b border-surface-line
          px-5 py-4">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-[16px] font-semibold text-ink">Offer letter</h2>
              <StatusPill label={meta.label} tone={meta.tone} />
            </div>
            <p className="mt-1 text-[12.5px] text-ink-muted">
              {offer.role} &middot; {offer.department} &middot; sent {formatDateTime(offer.sentAt)}
            </p>
          </div>
          <a href={fileUrl(offer.downloadUrl)} target="_blank" rel="noreferrer">
            <Button variant="secondary" size="sm">Open in new tab</Button>
          </a>
        </header>

        <div className="bg-surface-canvas p-3">
          {pdfLoading && <LoadingState label="Loading your offer letter" />}
          {pdfError && !pdfLoading && (
            <p className="py-6 text-center text-[13px] text-accent-red">
              We could not display the letter here. Use{' '}
              <span className="font-medium">Open in new tab</span> above.
            </p>
          )}
          {!pdfError && (
            <div className="space-y-3">
              {pages.map((page) => (
                <div
                  key={page.number}
                  className="relative mx-auto overflow-hidden rounded border border-surface-line bg-white"
                  style={{ width: page.width, height: page.height || undefined, maxWidth: '100%' }}
                >
                  <canvas
                    ref={(el) => { if (el) canvasRefs.current.set(page.number, el) }}
                    className="block w-full"
                  />
                  {!accepted && fields.map((field, index) => {
                    if (field.page !== page.number) return null
                    const filled = Boolean((values[index] || '').trim())
                    const isEditing = index === editingIndex
                    return (
                      <button
                        key={index}
                        type="button"
                        ref={(el) => { if (el) fieldRefs.current.set(index, el) }}
                        onClick={() => openField(index)}
                        className={`absolute flex items-center justify-center overflow-hidden rounded
                          border-2 px-1 transition ${
                          isEditing
                            ? 'border-brand bg-brand-tint ring-2 ring-brand/30'
                            : filled
                              ? 'border-accent-green/70 bg-[#F3FCF7]'
                              : 'border-dashed border-brand bg-brand-tint/40 hover:bg-brand-tint/70'
                        }`}
                        style={{
                          left: `${field.xPct}%`, top: `${field.yPct}%`,
                          width: `${field.widthPct}%`, height: `${field.heightPct}%`,
                        }}
                      >
                        {filled ? (
                          field.type === 'signature' ? (
                            <img src={values[index]} alt="Your signature"
                              className="max-h-full max-w-full object-contain" />
                          ) : (
                            <span className="truncate text-[11px] text-ink" style={{
                              color: field.type === 'text' ? (field.textColor || undefined) : undefined,
                            }}>
                              {values[index]}
                            </span>
                          )
                        ) : (
                          <span className="truncate text-[10px] font-semibold uppercase tracking-wide
                            text-brand">
                            {fieldTypeMeta(field.type).label}
                          </span>
                        )}
                      </button>
                    )
                  })}
                </div>
              ))}
              {!pdfLoading && pages.some((p) => p.height === 0) && (
                <p className="py-3 text-center text-[12px] text-ink-muted">
                  Rendering the rest of the letter&hellip;
                </p>
              )}
            </div>
          )}
        </div>
      </section>

      <Modal
        open={editingIndex != null}
        onClose={() => (submitting ? null : setEditingIndex(null))}
        title={editing ? fieldTypeMeta(editing.type).label : ''}
        description={editing?.type === 'signature'
          ? 'This is recorded as your electronic signature, with a timestamp and audit entry.'
          : fieldTypeMeta(editing?.type || 'text').hint}
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setEditingIndex(null)}>Cancel</Button>
            <Button onClick={applyDraft} disabled={!(draft || '').trim()}>Save</Button>
          </>
        }
      >
        {editing?.type === 'signature' ? (
          <SignaturePad
            defaultName={overview.candidateName || ''}
            value={draft}
            onChange={(dataUrl) => setDraft(dataUrl || '')}
          />
        ) : editing ? (
          <label className="block">
            <span className="mb-1.5 block text-[12.5px] font-medium text-ink-body">
              {fieldTypeMeta(editing.type).hint}
            </span>
            {editing.type === 'text' ? (
              <textarea
                rows={4}
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                className="w-full rounded border border-surface-line px-3 py-2 text-[13.5px]
                  outline-none focus:border-brand"
                style={{ color: editing.textColor || undefined }}
              />
            ) : (
              <input
                type={editing.type === 'date' ? 'date' : 'text'}
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                autoFocus
                className="w-full rounded border border-surface-line px-3 py-2 text-[13.5px]
                  outline-none focus:border-brand"
              />
            )}
          </label>
        ) : null}
      </Modal>

      {/* The last look before signing shows the letter itself with their
          answers in place - a list alone cannot tell you the signature landed
          in the right box, and signing cannot be undone. */}
      <SignatureReviewDialog
        open={reviewing}
        onClose={() => setReviewing(false)}
        document={offer}
        confirmLabel="Confirm and sign"
        values={values}
        submitting={submitting}
        onConfirm={submit}
        onChangeField={(index) => { setReviewing(false); openField(index) }}
      />
    </div>
  )
}
