import { useEffect, useMemo, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { SignaturePad } from '../components/portal/SignaturePad'
import { useContainerWidth } from '../hooks/useContainerWidth'
import { useSignatureStyle } from '../hooks/useSignatureStyle'
import { SignatureReviewDialog } from '../components/portal/SignatureReviewDialog'
import { Logo } from '../components/ui/Logo'
import { Button } from '../components/ui/Button'
import { ErrorState } from '../components/ui/EmptyState'
import { Modal } from '../components/ui/Modal'
import { LoadingState } from '../components/ui/Spinner'
import { StatusPill } from '../components/ui/StatusPill'
import { useToast } from '../context/ToastContext'
import { useAsync } from '../hooks/useAsync'
import { fetchBytes } from '../services/apiClient'
import { nocService } from '../services/nocService'
import { formatDateTime } from '../utils/format'
import { fieldTypeMeta, todayIso } from '../utils/offerFields'
import { loadPdf, renderPageToCanvas } from '../utils/pdfRender'

/* A cap, not the render width: pages render at whatever the column
   actually offers, so a phone gets a page that fits it. */
const MAX_PAGE_WIDTH = 680

/**
 * Signing the combined NDA + NOC.
 *
 * <p>Standalone: the recipient is not a candidate and never signs in, so this
 * page sits outside the portal layout and is authenticated purely by the token
 * in the URL. What they see is one document, because the two files were merged
 * before it was sent.
 */
export function NocSignPage() {
  const { token } = useParams()
  const toast = useToast()
  const [values, setValues] = useState({})
  const [editingIndex, setEditingIndex] = useState(null)
  const [draft, setDraft] = useState('')
  const [reviewing, setReviewing] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [downloading, setDownloading] = useState(false)
  const { data: packet, error, loading, setData } = useAsync(() => nocService.view(token), [token])

  const fields = useMemo(() => packet?.fields || [], [packet])

  /* One hand across the whole document, however many places it is signed in. */
  const signature = useSignatureStyle(packet?.recipientName || '')
  const signed = Boolean(packet?.signed)

  // Seed from what HR pre-filled, so the recipient edits a sensible starting
  // point. A date with no prefill means today, which is what signing one is.
  useEffect(() => {
    if (!fields.length) return
    setValues((current) => {
      if (Object.keys(current).length) return current
      const seeded = {}
      fields.forEach((field, index) => {
        if (field.type === 'signature') return
        if (field.prefill) seeded[index] = field.prefill
        else if (field.type === 'date') seeded[index] = todayIso()
        else if (field.type === 'name') seeded[index] = packet?.recipientName || ''
      })
      return seeded
    })
  }, [fields, packet?.recipientName])

  const [pages, setPages] = useState([])
  const [pdfLoading, setPdfLoading] = useState(true)
  const [pdfError, setPdfError] = useState(null)
  const canvasRefs = useRef(new Map())
  const [pageWidth, pageAreaRef] = useContainerWidth(MAX_PAGE_WIDTH)

  const documentUrl = packet?.documentUrl

  useEffect(() => {
    if (!documentUrl) return undefined
    let cancelled = false
    setPdfLoading(true)
    setPdfError(null)

    // Pages mount up front so canvas refs stay stable, then each fills in its
    // own height as it renders - a long combined document reveals progressively
    // instead of sitting behind a spinner until the last page is done.
    const render = async () => {
      try {
        const bytes = await fetchBytes(documentUrl)
        const pdf = await loadPdf(bytes)
        let sizes = Array.from({ length: pdf.numPages }, (_, i) =>
          ({ number: i + 1, width: pageWidth, height: 0 }))
        setPages(sizes)
        await new Promise((resolve) => setTimeout(resolve, 0))
        for (let number = 1; number <= pdf.numPages; number++) {
          if (cancelled) return
          const canvas = canvasRefs.current.get(number)
          if (!canvas) continue
          const size = await renderPageToCanvas(pdf, number, canvas, pageWidth)
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
    // Re-renders after signing: the same URL then serves the stamped copy.
    // Redrawn when the width changes, so rotating a phone keeps the signature
    // fields on the part of the page they belong to.
  }, [documentUrl, signed, pageWidth])

  const editing = editingIndex == null ? null : fields[editingIndex]

  const openField = (index) => {
    setEditingIndex(index)
    setDraft(values[index] || '')
  }

  const applyDraft = () => {
    if (fields[editingIndex]?.type === 'signature') {
      signature.rememberSource(editingIndex, signature.style.tab === 'type')
    }
    setValues((current) => ({ ...current, [editingIndex]: draft }))
    setEditingIndex(null)
  }

  /* A typed signature is the same mark wherever it appears, so restyling it
     reaches the fields already signed that way, not only the open one. */
  const onSignatureChange = (dataUrl) => {
    setDraft(dataUrl || '')
    if (signature.style.tab !== 'type' || !dataUrl) return
    setValues((current) => {
      let changed = false
      const next = { ...current }
      signature.typedFields.forEach((key) => {
        if (key !== editingIndex && next[key] !== dataUrl) {
          next[key] = dataUrl
          changed = true
        }
      })
      return changed ? next : current
    })
  }

  const allDone = fields.length > 0 && fields.every((_, i) => (values[i] || '').trim())

  const submit = async () => {
    setSubmitting(true)
    try {
      const result = await nocService.sign(token, values[nameFieldIndex(fields)] || packet.recipientName, values)
      setData(result)
      setReviewing(false)
      toast.success('Signed', 'Your signed copy is ready to download.')
    } catch (err) {
      toast.apiError(err, 'Could not submit the document')
    } finally {
      setSubmitting(false)
    }
  }

  const download = async () => {
    setDownloading(true)
    try {
      await nocService.download(token, 'NDA-NOC-signed.pdf')
    } catch (err) {
      toast.apiError(err, 'Could not download the document')
    } finally {
      setDownloading(false)
    }
  }

  if (loading && !packet) return <LoadingState label="Opening your document" />

  if (error && !packet) {
    return (
      <div className="mx-auto max-w-lg p-6">
        <div className="cf-card p-6">
          <ErrorState
            title="This link cannot be opened"
            message={error.message || 'The link may have expired. Ask HR to send you a new one.'}
          />
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-surface-canvas">
      <header className="border-b border-surface-line bg-white px-5 py-3.5">
        <div className="mx-auto flex max-w-4xl items-center justify-between gap-3">
          <Logo subtitle="Documents" />
          <StatusPill label={signed ? 'Signed' : 'Awaiting your signature'} tone={signed ? 'green' : 'amber'} />
        </div>
      </header>

      <main className="mx-auto max-w-4xl px-5 py-6">
        <section className="cf-card cf-spine p-5">
          <h1 className="text-[19px] font-semibold text-ink">
            {packet.title || 'Your NDA and NOC'}
          </h1>
          <p className="mt-1.5 text-[13.5px] text-ink-body">
            {signed ? (
              <>
                Signed by <b>{packet.signedByName}</b> on {formatDateTime(packet.signedAt)}. Your copy is
                below and can be downloaded at any time before the link expires.
              </>
            ) : (
              <>
                Hi {packet.recipientName} - your NDA and NOC have been combined into one document of{' '}
                <b>{packet.pageCount} page{packet.pageCount === 1 ? '' : 's'}</b>. Read through it, complete
                every highlighted field, then review and submit once.
              </>
            )}
          </p>

          {!signed && (
            <p className="mt-3 text-[12.5px] text-ink-muted">
              {fields.filter((_, i) => (values[i] || '').trim()).length} of {fields.length} field
              {fields.length === 1 ? '' : 's'} completed.
            </p>
          )}

          <div className="mt-4 flex flex-wrap gap-2.5">
            {signed ? (
              <Button loading={downloading} onClick={download}>Download signed document</Button>
            ) : (
              <Button disabled={!allDone} onClick={() => setReviewing(true)}
                title={allDone ? undefined : 'Complete every field first'}>
                Review and submit
              </Button>
            )}
          </div>
        </section>

        <section
          ref={pageAreaRef}
          className="mt-5 overflow-hidden rounded-[14px] border border-surface-line
            bg-surface-canvas p-2 sm:p-3"
        >
          {pdfLoading && <LoadingState label="Loading your document" />}
          {pdfError && !pdfLoading && (
            <p className="py-6 text-center text-[13px] text-accent-red">
              We could not display the document here. Please try reloading the page.
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
                  {/* Nothing is pushed at the recipient: the boxes sit on the
                      page and open only when clicked. */}
                  {!signed && fields.map((field, index) => {
                    if (field.page !== page.number) return null
                    const filled = Boolean((values[index] || '').trim())
                    const isEditing = index === editingIndex
                    return (
                      <button
                        key={index}
                        type="button"
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
                          <span className="truncate text-[10px] font-semibold uppercase tracking-wide text-brand">
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
                  Rendering the rest of the document&hellip;
                </p>
              )}
            </div>
          )}
        </section>
      </main>

      <Modal
        open={editingIndex != null}
        onClose={() => (submitting ? null : setEditingIndex(null))}
        title={editing ? fieldTypeMeta(editing.type).label : ''}
        description={editing?.type === 'signature'
          ? 'This is recorded as your electronic signature, with a timestamp.'
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
            value={draft}
            onChange={onSignatureChange}
            style={signature.style}
            onStyleChange={signature.changeStyle}
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

      <SignatureReviewDialog
        open={reviewing}
        onClose={() => setReviewing(false)}
        document={{ downloadUrl: documentUrl, fields }}
        values={values}
        submitting={submitting}
        onConfirm={submit}
        onChangeField={(index) => { setReviewing(false); openField(index) }}
      />
    </div>
  )
}

/** The name field, if HR placed one - it is what we record as the signatory. */
function nameFieldIndex(fields) {
  return fields.findIndex((field) => field.type === 'name')
}
