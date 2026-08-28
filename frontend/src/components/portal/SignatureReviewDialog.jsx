import { useEffect, useRef, useState } from 'react'
import { Button } from '../ui/Button'
import { Modal } from '../ui/Modal'
import { LoadingState } from '../ui/Spinner'
import { fetchBytes } from '../../services/apiClient'
import { loadPdf, renderPageToCanvas } from '../../utils/pdfRender'
import { fieldTypeMeta } from '../../utils/offerFields'

const PAGE_CSS_WIDTH = 620

/**
 * The last look before signing: the document itself with the answers in place.
 *
 * Takes any { downloadUrl, fields } pair, so the offer letter and the combined
 * NDA + NOC share one review step - a list of answers alone cannot show that a
 * signature landed in the right box, and signing cannot be undone.
 */
export function SignatureReviewDialog({
  open, onClose, document: doc, values, onChangeField, onConfirm, submitting,
  title = 'Review before you submit',
  confirmLabel = 'Sign and submit',
}) {
  const [pages, setPages] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const canvasRefs = useRef(new Map())
  const fieldRefs = useRef(new Map())

  const fields = doc?.fields || []

  useEffect(() => {
    if (!open || !doc?.downloadUrl) return undefined
    let cancelled = false
    setLoading(true)
    setError(null)

    const render = async () => {
      try {
        const bytes = await fetchBytes(doc.downloadUrl)
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
          if (number === 1) setLoading(false)
        }
      } catch (err) {
        if (!cancelled) setError(err)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    render()
    return () => { cancelled = true }
  }, [open, doc?.downloadUrl])

  const jumpTo = (index) =>
    fieldRefs.current.get(index)?.scrollIntoView({ behavior: 'smooth', block: 'center' })

  return (
    <Modal
      open={open}
      onClose={() => (submitting ? null : onClose())}
      title={title}
      description="This is exactly how your document will look. Once submitted it is signed and cannot be changed."
      size="2xl"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={submitting}>Back</Button>
          <Button onClick={onConfirm} loading={submitting}>{confirmLabel}</Button>
        </>
      }
    >
      {error && (
        <p className="py-6 text-center text-[13px] text-accent-red">
          We could not display the document here. Go back and try again.
        </p>
      )}

      {!error && (
        <div className="flex items-start gap-4">
          <div className="min-w-0 flex-1">
            {loading && <LoadingState label="Preparing your document" />}
            <div className="space-y-4">
              {pages.map((page) => (
                <div key={page.number}>
                  <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-[0.1em] text-ink-muted">
                    Page {page.number}
                  </p>
                  <div
                    className="relative mx-auto overflow-hidden rounded border border-surface-line bg-white"
                    style={{ width: page.width, height: page.height || undefined }}
                  >
                    <canvas
                      ref={(el) => { if (el) canvasRefs.current.set(page.number, el) }}
                      className="block"
                    />
                    {fields.map((field, index) => {
                      if (field.page !== page.number) return null
                      const value = values[index] || ''
                      return (
                        <div
                          key={index}
                          ref={(el) => { if (el) fieldRefs.current.set(index, el) }}
                          className="absolute flex items-center justify-center overflow-hidden"
                          style={{
                            left: `${field.xPct}%`, top: `${field.yPct}%`,
                            width: `${field.widthPct}%`, height: `${field.heightPct}%`,
                          }}
                        >
                          {field.type === 'signature' ? (
                            <img
                              src={value}
                              alt="Your signature"
                              className="max-h-full max-w-full object-contain"
                            />
                          ) : (
                            <span
                              className="w-full truncate px-0.5 text-[11px] leading-tight text-ink"
                              style={{
                                color: field.type === 'text' ? (field.textColor || undefined) : undefined,
                              }}
                            >
                              {value}
                            </span>
                          )}
                        </div>
                      )
                    })}
                  </div>
                </div>
              ))}
              {!loading && pages.some((p) => p.height === 0) && (
                <p className="py-2 text-center text-[12px] text-ink-muted">
                  Rendering the rest of the letter&hellip;
                </p>
              )}
            </div>
          </div>

          {/* Sticky so a change is always one click away, however far down they scroll. */}
          <div className="sticky top-0 w-56 shrink-0 self-start">
            <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.1em] text-ink-muted">
              Your answers
            </p>
            <ul className="space-y-2">
              {fields.map((field, index) => (
                <li
                  key={index}
                  className="rounded border border-surface-line bg-surface-canvas px-2.5 py-2"
                >
                  <div className="flex items-center justify-between gap-2">
                    <button
                      type="button"
                      onClick={() => jumpTo(index)}
                      className="truncate text-[12px] font-medium text-ink-body transition hover:text-brand"
                    >
                      {fieldTypeMeta(field.type).label}
                      <span className="ml-1 text-[11px] text-ink-muted">p{field.page}</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => onChangeField(index)}
                      disabled={submitting}
                      className="shrink-0 text-[11.5px] font-medium text-brand transition
                        hover:underline disabled:opacity-50"
                    >
                      Change
                    </button>
                  </div>
                  {field.type === 'signature' ? (
                    <img
                      src={values[index]}
                      alt="Your signature"
                      className="mt-1 max-h-10 object-contain"
                    />
                  ) : (
                    <p
                      className="mt-0.5 break-words text-[12.5px] text-ink"
                      style={{ color: field.type === 'text' ? (field.textColor || undefined) : undefined }}
                    >
                      {values[index]}
                    </p>
                  )}
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}
    </Modal>
  )
}
