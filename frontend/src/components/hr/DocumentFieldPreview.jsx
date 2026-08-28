import { useEffect, useRef, useState } from 'react'
import { Button } from '../ui/Button'
import { Modal } from '../ui/Modal'
import { LoadingState } from '../ui/Spinner'
import { useToast } from '../../context/ToastContext'
import { fetchBytes } from '../../services/apiClient'
import { hrService } from '../../services/hrService'
import { loadPdf, renderPageToCanvas } from '../../utils/pdfRender'
import { fieldTypeMeta } from '../../utils/offerFields'

const PAGE_CSS_WIDTH = 620

/**
 * Read-only view of a document with the placed fields drawn on it.
 *
 * Takes any { downloadUrl, filename, fields } shape so the offer letter and the
 * combined NDA + NOC share it. A plain PDF viewer cannot show this: the field
 * positions live in the database and are only baked into the file once signed,
 * so without the overlay HR has no way to see what they placed.
 */
export function DocumentFieldPreview({ open, onClose, document: doc, onEdit, title }) {
  const toast = useToast()
  const [pages, setPages] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [downloading, setDownloading] = useState(false)
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

  const download = async () => {
    setDownloading(true)
    try {
      await hrService.downloadSecureFile(doc.downloadUrl, doc.filename)
    } catch (err) {
      toast.apiError(err, 'Could not download the document')
    } finally {
      setDownloading(false)
    }
  }

  const jumpTo = (index) => {
    fieldRefs.current.get(index)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={title || 'Document'}
      description={fields.length
        ? `${fields.length} field${fields.length === 1 ? '' : 's'} placed for the candidate to complete.`
        : 'No fields placed yet.'}
      size="2xl"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>Close</Button>
          <Button variant="secondary" onClick={download} loading={downloading}>Download</Button>
          {onEdit && <Button onClick={onEdit}>Edit fields</Button>}
        </>
      }
    >
      {error && (
        <p className="py-6 text-center text-[13px] text-accent-red">
          Could not display the document. Try downloading it instead.
        </p>
      )}

      {!error && (
        <div className="flex items-start gap-4">
          <div className="min-w-0 flex-1">
            {loading && <LoadingState label="Loading document" />}
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
                      return (
                        <div
                          key={index}
                          ref={(el) => { if (el) fieldRefs.current.set(index, el) }}
                          className="absolute flex items-center justify-center overflow-hidden rounded
                            border-2 border-dashed border-brand bg-brand-tint/50"
                          style={{
                            left: `${field.xPct}%`, top: `${field.yPct}%`,
                            width: `${field.widthPct}%`, height: `${field.heightPct}%`,
                          }}
                        >
                          <span className="truncate px-1 text-[10px] font-semibold uppercase
                            tracking-wide text-brand">
                            {fieldTypeMeta(field.type).label}
                          </span>
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

          {/* Sticky so it stays usable however far down the letter HR scrolls. */}
          <div className="sticky top-0 w-56 shrink-0 self-start">
            <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.1em] text-ink-muted">
              Placed fields
            </p>
            {fields.length === 0 ? (
              <p className="rounded border border-dashed border-surface-line px-3 py-3 text-[12px]
                leading-5 text-ink-muted">
                Nothing placed yet. Use <span className="font-medium">Edit fields</span> to add some.
              </p>
            ) : (
              <ul className="space-y-1">
                {fields.map((field, index) => (
                  <li key={index}>
                    <button
                      type="button"
                      onClick={() => jumpTo(index)}
                      className="flex w-full items-center justify-between gap-2 rounded border
                        border-surface-line bg-white px-2 py-1.5 text-left text-[12px] text-ink-body
                        transition hover:border-brand/40 hover:bg-brand-tint/30"
                    >
                      <span className="truncate font-medium">{fieldTypeMeta(field.type).label}</span>
                      <span className="shrink-0 text-[11px] text-ink-muted">p{field.page}</span>
                    </button>
                    {field.prefill && (
                      <p className="mt-0.5 truncate pl-2 text-[11px] text-ink-muted">
                        Pre-filled: {field.prefill}
                      </p>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}
    </Modal>
  )
}
