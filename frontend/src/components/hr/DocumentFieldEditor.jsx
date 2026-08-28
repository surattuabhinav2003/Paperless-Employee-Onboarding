import { useEffect, useRef, useState } from 'react'
import { Button } from '../ui/Button'
import { Modal } from '../ui/Modal'
import { LoadingState } from '../ui/Spinner'
import { useToast } from '../../context/ToastContext'
import { fetchBytes } from '../../services/apiClient'
import { loadPdf, renderPageToCanvas } from '../../utils/pdfRender'
import { FIELD_TYPES, TEXT_COLORS, TEXT_FONTS, fieldTypeMeta } from '../../utils/offerFields'

const MIN_WIDTH_PCT = 6
const MIN_HEIGHT_PCT = 2.5
const PAGE_CSS_WIDTH = 620

const clamp = (value, min, max) => Math.min(Math.max(value, min), max)
let nextId = 0

/**
 * DocuSign-style "prepare document": HR drags a field type from the palette
 * onto the rendered offer PDF, then drags to reposition, resizes from the
 * corner, and configures the selected field in the right-hand panel. Positions
 * are stored as percentages of the page, so they mean the same thing however
 * large the candidate's browser later renders it.
 */
/**
 * Places signable fields on a PDF.
 *
 * Document-agnostic: it is given the bytes to render, the fields already on
 * them, and a save callback. That is what lets the offer letter and the
 * combined NDA + NOC packet share one editor instead of two copies drifting
 * apart.
 */
export function DocumentFieldEditor({
  open,
  onClose,
  documentUrl,
  initialFields,
  onSave,
  onSaved,
  title = 'Prepare for signature',
  description = 'Drag a field from the left onto the document. Drag to move, use the corner to resize.',
}) {
  const toast = useToast()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [pages, setPages] = useState([])
  const [fields, setFields] = useState([])
  const [selectedId, setSelectedId] = useState(null)
  const [armedType, setArmedType] = useState(null)
  const [saving, setSaving] = useState(false)
  const canvasRefs = useRef(new Map())
  const fieldRefs = useRef(new Map())
  const dragRef = useRef(null)
  const paletteDragRef = useRef(null)

  useEffect(() => {
    if (!open) return undefined
    let cancelled = false
    setLoading(true)
    setError(null)
    setSelectedId(null)
    setFields((initialFields || []).map((f) => ({ id: `existing-${nextId++}`, ...f })))

    // Every page's wrapper mounts up front so canvas refs stay stable, then each
    // page's height is filled in as it renders - the wrapper clips via
    // overflow-hidden, so without this a long letter shows nothing until the
    // very last page is done.
    const prepare = async () => {
      try {
        const bytes = await fetchBytes(documentUrl)
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
    prepare()
    return () => { cancelled = true }
  }, [open, documentUrl, initialFields])

  // One persistent listener pair rather than attaching per drag - the handlers
  // read dragRef.current live, so a ref is enough.
  useEffect(() => {
    const onMove = (event) => {
      const drag = dragRef.current
      if (!drag) return
      const page = pages.find((p) => p.number === drag.page)
      if (!page || !page.height) return
      const dxPct = ((event.clientX - drag.startX) / page.width) * 100
      const dyPct = ((event.clientY - drag.startY) / page.height) * 100

      setFields((current) => current.map((f) => {
        if (f.id !== drag.id) return f
        if (drag.mode === 'move') {
          return {
            ...f,
            xPct: clamp(drag.origin.xPct + dxPct, 0, 100 - f.widthPct),
            yPct: clamp(drag.origin.yPct + dyPct, 0, 100 - f.heightPct),
          }
        }
        return {
          ...f,
          widthPct: clamp(drag.origin.widthPct + dxPct, MIN_WIDTH_PCT, 100 - f.xPct),
          heightPct: clamp(drag.origin.heightPct + dyPct, MIN_HEIGHT_PCT, 100 - f.yPct),
        }
      }))
    }
    const onUp = () => { dragRef.current = null }

    window.addEventListener('pointermove', onMove)
    window.addEventListener('pointerup', onUp)
    return () => {
      window.removeEventListener('pointermove', onMove)
      window.removeEventListener('pointerup', onUp)
    }
  }, [pages])

  const addField = (type, pageNumber, clientX, clientY, rect, page) => {
    const meta = fieldTypeMeta(type)
    const xPct = clamp(((clientX - rect.left) / page.width) * 100 - meta.width / 2, 0, 100 - meta.width)
    const yPct = clamp(((clientY - rect.top) / page.height) * 100 - meta.height / 2, 0, 100 - meta.height)
    const id = `new-${nextId++}`
    setFields((current) => [...current, {
      id,
      type,
      page: pageNumber,
      xPct,
      yPct,
      widthPct: meta.width,
      heightPct: meta.height,
      prefill: '',
      textColor: type === 'text' ? '#111827' : null,
      textFont: type === 'text' ? 'helvetica' : null,
    }])
    setSelectedId(id)
  }

  const startDrag = (event, field, mode) => {
    event.stopPropagation()
    event.preventDefault()
    setSelectedId(field.id)
    dragRef.current = {
      id: field.id,
      page: field.page,
      mode,
      startX: event.clientX,
      startY: event.clientY,
      origin: {
        xPct: field.xPct, yPct: field.yPct, widthPct: field.widthPct, heightPct: field.heightPct,
      },
    }
  }

  const updateSelected = (patch) =>
    setFields((current) => current.map((f) => (f.id === selectedId ? { ...f, ...patch } : f)))

  const removeField = (id) => {
    setFields((current) => current.filter((f) => f.id !== id))
    setSelectedId((current) => (current === id ? null : current))
  }

  const selected = fields.find((f) => f.id === selectedId) || null
  const signatureCount = fields.filter((f) => f.type === 'signature').length

  const save = async () => {
    if (signatureCount === 0) {
      toast.error('Add a signature field',
        'The candidate needs at least one place to sign before this can be sent.')
      return
    }
    setSaving(true)
    try {
      const payload = fields.map((f) => ({
        type: f.type,
        page: f.page,
        xPct: f.xPct,
        yPct: f.yPct,
        widthPct: f.widthPct,
        heightPct: f.heightPct,
        prefill: f.prefill || null,
        textColor: f.type === 'text' ? (f.textColor || null) : null,
        textFont: f.type === 'text' ? (f.textFont || null) : null,
      }))
      await onSave(payload)
      toast.success('Fields saved', `${fields.length} field${fields.length === 1 ? '' : 's'} placed.`)
      onSaved()
    } catch (err) {
      toast.apiError(err, 'Could not save the fields')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={title}
      description={description}
      size="2xl"
      footer={
        <>
          <span className="mr-auto text-[12px] text-ink-muted">
            {fields.length} field{fields.length === 1 ? '' : 's'}
            {signatureCount === 0 && fields.length > 0 && ' · needs a signature field'}
          </span>
          <Button variant="ghost" onClick={onClose} disabled={saving}>Cancel</Button>
          <Button onClick={save} loading={saving} disabled={signatureCount === 0}>Save fields</Button>
        </>
      }
    >
      {error && (
        <p className="py-6 text-center text-[13px] text-accent-red">
          Could not load the offer letter to prepare it. Try again.
        </p>
      )}

      {!error && (
        <div className="flex items-start gap-4">
          {/* Palette. Sticky because the whole dialog body is one scroll area -
              without this, scrolling to page 2 scrolls the palette out of reach
              and there is no way to add a field there. */}
          <div className="sticky top-0 w-40 shrink-0 self-start">
            <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.1em] text-ink-muted">
              Fields
            </p>
            <div className="space-y-1.5">
              {FIELD_TYPES.map((type) => {
                const armed = armedType === type.value
                return (
                  <button
                    key={type.value}
                    type="button"
                    draggable
                    onDragStart={() => { paletteDragRef.current = type.value }}
                    onDragEnd={() => { paletteDragRef.current = null }}
                    onClick={() => setArmedType(armed ? null : type.value)}
                    className={`w-full cursor-grab rounded border px-2.5 py-2 text-left transition
                      active:cursor-grabbing ${
                      armed
                        ? 'border-brand bg-brand-tint ring-2 ring-brand/25'
                        : 'border-surface-line bg-white hover:border-brand/50 hover:bg-brand-tint/30'
                    }`}
                  >
                    <span className="block text-[13px] font-medium text-ink">{type.label}</span>
                    <span className="mt-0.5 block text-[11px] text-ink-muted">
                      {armed ? 'Now click on the letter' : type.hint}
                    </span>
                  </button>
                )
              })}
            </div>
            <p className="mt-3 text-[11px] leading-4 text-ink-muted">
              {/* Click-to-place matters beyond convenience: HTML5 drag-and-drop
                  does not fire on touch devices at all. */}
              Drag a field onto the letter, or tap one then tap where it goes.
            </p>
          </div>

          {/* Pages */}
          <div className="min-w-0 flex-1">
            {loading && <LoadingState label="Loading offer letter" />}
            <div className="space-y-4">
              {pages.map((page) => (
                <div key={page.number}>
                  <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-[0.1em] text-ink-muted">
                    Page {page.number}
                  </p>
                  <div
                    className={`relative mx-auto select-none overflow-hidden rounded border
                      border-surface-line bg-white ${armedType ? 'cursor-copy' : ''}`}
                    style={{ width: page.width, height: page.height || undefined }}
                    onDragOver={(event) => event.preventDefault()}
                    onDrop={(event) => {
                      event.preventDefault()
                      const type = paletteDragRef.current
                      if (!type || !page.height) return
                      addField(type, page.number, event.clientX, event.clientY,
                        event.currentTarget.getBoundingClientRect(), page)
                    }}
                    onPointerDown={(event) => {
                      // Empty canvas only - clicking a placed field must not add another.
                      if (event.target !== event.currentTarget && event.target.tagName !== 'CANVAS') return
                      if (armedType && page.height) {
                        addField(armedType, page.number, event.clientX, event.clientY,
                          event.currentTarget.getBoundingClientRect(), page)
                        setArmedType(null)
                        return
                      }
                      setSelectedId(null)
                    }}
                  >
                    <canvas
                      ref={(el) => { if (el) canvasRefs.current.set(page.number, el) }}
                      className="pointer-events-none block"
                    />
                    {fields.filter((f) => f.page === page.number).map((field) => {
                      const isSelected = field.id === selectedId
                      return (
                        <div
                          key={field.id}
                          ref={(el) => { if (el) fieldRefs.current.set(field.id, el) }}
                          onPointerDown={(event) => startDrag(event, field, 'move')}
                          className={`absolute flex cursor-move items-center justify-center rounded border-2
                            ${isSelected ? 'border-brand bg-brand-tint/70' : 'border-brand/60 bg-brand-tint/40'}`}
                          style={{
                            left: `${field.xPct}%`, top: `${field.yPct}%`,
                            width: `${field.widthPct}%`, height: `${field.heightPct}%`,
                          }}
                        >
                          <span className="pointer-events-none truncate px-1 text-[10px] font-semibold
                            uppercase tracking-wide text-brand">
                            {fieldTypeMeta(field.type).label}
                          </span>
                          {isSelected && (
                            <>
                              <button
                                type="button"
                                onPointerDown={(event) => event.stopPropagation()}
                                onClick={(event) => { event.stopPropagation(); removeField(field.id) }}
                                className="absolute -right-2 -top-2 flex h-5 w-5 items-center justify-center
                                  rounded-full bg-accent-red text-white shadow"
                                aria-label={`Remove ${fieldTypeMeta(field.type).label} field`}
                              >
                                <svg viewBox="0 0 24 24" className="h-3 w-3" fill="none"
                                  stroke="currentColor" strokeWidth="3">
                                  <path d="M18 6 6 18M6 6l12 12" />
                                </svg>
                              </button>
                              <div
                                onPointerDown={(event) => startDrag(event, field, 'resize')}
                                className="absolute -bottom-1.5 -right-1.5 h-3.5 w-3.5 cursor-nwse-resize
                                  rounded-full border-2 border-white bg-brand"
                              />
                            </>
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

          {/* Placed fields + settings for the selected one. Sticky for the same
              reason as the palette: it must stay visible while the letter scrolls. */}
          <div className="sticky top-0 w-56 shrink-0 self-start">
            <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.1em] text-ink-muted">
              Placed fields
            </p>
            {fields.length === 0 ? (
              <p className="rounded border border-dashed border-surface-line px-3 py-3 text-[12px]
                leading-5 text-ink-muted">
                Nothing placed yet.
              </p>
            ) : (
              <ul className="mb-4 max-h-40 space-y-1 overflow-y-auto pr-1">
                {fields.map((field) => (
                  <li key={field.id}>
                    <button
                      type="button"
                      onClick={() => {
                        setSelectedId(field.id)
                        fieldRefs.current.get(field.id)
                          ?.scrollIntoView({ behavior: 'smooth', block: 'center' })
                      }}
                      className={`flex w-full items-center justify-between gap-2 rounded border px-2 py-1.5
                        text-left text-[12px] transition ${
                        field.id === selectedId
                          ? 'border-brand bg-brand-tint text-brand'
                          : 'border-surface-line bg-white text-ink-body hover:border-brand/40'
                      }`}
                    >
                      <span className="truncate font-medium">{fieldTypeMeta(field.type).label}</span>
                      <span className="shrink-0 text-[11px] text-ink-muted">p{field.page}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}

            <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.1em] text-ink-muted">
              Field settings
            </p>
            {!selected ? (
              <p className="rounded border border-dashed border-surface-line px-3 py-4 text-[12px]
                leading-5 text-ink-muted">
                Select a placed field to pre-fill it or change how it looks.
              </p>
            ) : (
              <div className="space-y-3 rounded border border-surface-line bg-surface-canvas p-3">
                <p className="text-[13px] font-semibold text-ink">{fieldTypeMeta(selected.type).label}</p>

                {selected.type !== 'signature' && (
                  <label className="block">
                    <span className="mb-1 block text-[11.5px] font-medium text-ink-body">
                      Pre-filled value
                    </span>
                    <input
                      type={selected.type === 'date' ? 'date' : 'text'}
                      value={selected.prefill || ''}
                      onChange={(event) => updateSelected({ prefill: event.target.value })}
                      placeholder={selected.type === 'date' ? '' : 'Optional'}
                      className="w-full rounded border border-surface-line px-2 py-1.5 text-[12.5px]
                        outline-none focus:border-brand"
                    />
                    <span className="mt-1 block text-[11px] leading-4 text-ink-muted">
                      The candidate sees this and can still change it.
                    </span>
                  </label>
                )}

                {selected.type === 'text' && (
                  <>
                    <label className="block">
                      <span className="mb-1 block text-[11.5px] font-medium text-ink-body">Font</span>
                      <select
                        value={selected.textFont || 'helvetica'}
                        onChange={(event) => updateSelected({ textFont: event.target.value })}
                        className="w-full rounded border border-surface-line bg-white px-2 py-1.5
                          text-[12.5px] outline-none focus:border-brand"
                      >
                        {TEXT_FONTS.map((font) => (
                          <option key={font.value} value={font.value}>{font.label}</option>
                        ))}
                      </select>
                    </label>
                    <div>
                      <span className="mb-1 block text-[11.5px] font-medium text-ink-body">Colour</span>
                      <div className="flex gap-1.5">
                        {TEXT_COLORS.map((color) => (
                          <button
                            key={color.value}
                            type="button"
                            title={color.label}
                            onClick={() => updateSelected({ textColor: color.value })}
                            className={`h-6 w-6 rounded-full border-2 transition ${
                              (selected.textColor || '#111827') === color.value
                                ? 'border-brand ring-2 ring-brand/25'
                                : 'border-white shadow-sm'
                            }`}
                            style={{ background: color.value }}
                          />
                        ))}
                      </div>
                    </div>
                  </>
                )}

                <button
                  type="button"
                  onClick={() => removeField(selected.id)}
                  className="text-[12px] font-medium text-accent-red transition hover:underline"
                >
                  Remove this field
                </button>
              </div>
            )}
          </div>
        </div>
      )}
    </Modal>
  )
}
