import { useEffect, useRef, useState } from 'react'
import { SIGNATURE_FONTS, ensureSignatureFonts, typedSignatureToDataUrl } from '../../utils/offerFields'

const TABS = [
  { key: 'type', label: 'Type' },
  { key: 'draw', label: 'Draw' },
  { key: 'upload', label: 'Upload' },
]

const MAX_UPLOAD_BYTES = 2 * 1024 * 1024

/**
 * Signature capture with the three ways people actually sign: type it in a
 * handwriting face, draw it, or upload a photo of a real signature. All three
 * produce a PNG data URI, so everything downstream handles one shape.
 *
 * Typing is the default because it is the one that works on every device
 * without a stylus or a scanner.
 *
 * <p>The tab, the typed text and the chosen face are held by the caller, not
 * here. A document can carry several signature fields, each opened in its own
 * dialog, and a person has one signature - keeping the choice inside this
 * component reset it every time a dialog opened, so the second signature came
 * out in a different hand from the first. See `useSignatureStyle`.
 */
export function SignaturePad({ value, onChange, style, onStyleChange }) {
  const { tab, typed, fontIndex } = style
  const [uploadError, setUploadError] = useState(null)
  const [fontsReady, setFontsReady] = useState(false)

  const setTab = (next) => onStyleChange({ ...style, tab: next })
  const setTyped = (next) => onStyleChange({ ...style, typed: next })
  const setFontIndex = (next) => onStyleChange({ ...style, fontIndex: next })

  const canvasRef = useRef(null)
  const drawingRef = useRef(false)
  const lastPointRef = useRef(null)
  const fileInputRef = useRef(null)

  // The typed tab cannot rasterise until the webfonts have actually loaded, and
  // this is the screen that asks for them in the first place.
  useEffect(() => {
    let cancelled = false
    ensureSignatureFonts().then(() => !cancelled && setFontsReady(true))
    return () => { cancelled = true }
  }, [])

  // Re-render the typed signature whenever the text, face, or font readiness changes.
  useEffect(() => {
    if (tab !== 'type') return
    onChange(typedSignatureToDataUrl(typed, SIGNATURE_FONTS[fontIndex].family))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, typed, fontIndex, fontsReady])

  const pointFromEvent = (event) => {
    const rect = canvasRef.current.getBoundingClientRect()
    return {
      x: (event.clientX - rect.left) * (canvasRef.current.width / rect.width),
      y: (event.clientY - rect.top) * (canvasRef.current.height / rect.height),
    }
  }

  const startDraw = (event) => {
    event.preventDefault()
    canvasRef.current.setPointerCapture(event.pointerId)
    drawingRef.current = true
    lastPointRef.current = pointFromEvent(event)
  }

  const moveDraw = (event) => {
    if (!drawingRef.current) return
    event.preventDefault()
    const ctx = canvasRef.current.getContext('2d')
    const point = pointFromEvent(event)
    ctx.strokeStyle = '#111827'
    ctx.lineWidth = 2.6
    ctx.lineCap = 'round'
    ctx.lineJoin = 'round'
    ctx.beginPath()
    ctx.moveTo(lastPointRef.current.x, lastPointRef.current.y)
    ctx.lineTo(point.x, point.y)
    ctx.stroke()
    lastPointRef.current = point
    onChange(canvasRef.current.toDataURL('image/png'))
  }

  const endDraw = (event) => {
    drawingRef.current = false
    if (canvasRef.current?.hasPointerCapture?.(event.pointerId)) {
      canvasRef.current.releasePointerCapture(event.pointerId)
    }
  }

  const clearDrawing = () => {
    const canvas = canvasRef.current
    canvas.getContext('2d').clearRect(0, 0, canvas.width, canvas.height)
    onChange(null)
  }

  const handleUpload = (event) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    setUploadError(null)
    if (!/^image\/(png|jpe?g)$/i.test(file.type)) {
      setUploadError('Upload a PNG or JPG image.')
      return
    }
    if (file.size > MAX_UPLOAD_BYTES) {
      setUploadError('That image is larger than 2MB.')
      return
    }
    const reader = new FileReader()
    reader.onload = () => onChange(String(reader.result))
    reader.onerror = () => setUploadError('That image could not be read.')
    reader.readAsDataURL(file)
  }

  const switchTab = (next) => {
    setTab(next)
    setUploadError(null)
    // Each tab owns its own result, so leaving one clears what it produced -
    // otherwise the preview could show a drawing while the Type tab is open.
    if (next === 'type') {
      onChange(typedSignatureToDataUrl(typed, SIGNATURE_FONTS[fontIndex].family))
    } else {
      onChange(null)
      if (next === 'draw') requestAnimationFrame(() => clearCanvasSilently())
    }
  }

  const clearCanvasSilently = () => {
    const canvas = canvasRef.current
    if (canvas) canvas.getContext('2d').clearRect(0, 0, canvas.width, canvas.height)
  }

  return (
    <div>
      <div className="flex gap-1 rounded border border-surface-line bg-surface-canvas p-1">
        {TABS.map((item) => (
          <button
            key={item.key}
            type="button"
            onClick={() => switchTab(item.key)}
            className={`flex-1 rounded px-3 py-1.5 text-[12.5px] font-medium transition ${
              tab === item.key ? 'bg-white text-brand shadow-sm' : 'text-ink-muted hover:text-ink'
            }`}
          >
            {item.label}
          </button>
        ))}
      </div>

      <div className="mt-3">
        {tab === 'type' && (
          <div>
            <input
              type="text"
              value={typed}
              onChange={(event) => setTyped(event.target.value)}
              placeholder="Type your full name"
              className="w-full rounded border border-surface-line px-3 py-2 text-[14px]
                outline-none focus:border-brand"
              style={{ fontFamily: SIGNATURE_FONTS[fontIndex].family, fontSize: 22 }}
            />
            <p className="mt-3 text-[11px] font-semibold uppercase tracking-[0.1em] text-ink-muted">
              Choose a style
            </p>
            <div className="mt-1.5 max-h-52 space-y-1.5 overflow-y-auto pr-1">
              {SIGNATURE_FONTS.map((font, index) => (
                <button
                  key={font.id}
                  type="button"
                  onClick={() => setFontIndex(index)}
                  className={`w-full rounded border px-3 py-2 text-left transition ${
                    fontIndex === index
                      ? 'border-brand bg-brand-tint/50'
                      : 'border-surface-line bg-white hover:border-brand/40'
                  }`}
                >
                  <span
                    className={`block truncate text-[22px] leading-tight ${
                      typed.trim() ? 'text-ink' : 'italic text-ink-muted'
                    }`}
                    style={{ fontFamily: font.family }}
                  >
                    {typed.trim() || 'Your name'}
                  </span>
                  <span className="mt-0.5 block text-[11px] text-ink-muted">{font.name}</span>
                </button>
              ))}
            </div>
          </div>
        )}

        {tab === 'draw' && (
          <div>
            <canvas
              ref={canvasRef}
              width={640}
              height={320}
              style={{ height: 160, touchAction: 'none' }}
              className="w-full cursor-crosshair rounded border border-dashed border-surface-line bg-[#FAFBFF]"
              onPointerDown={startDraw}
              onPointerMove={moveDraw}
              onPointerUp={endDraw}
              onPointerCancel={endDraw}
            />
            <div className="mt-2 flex items-center justify-between">
              <p className="text-[11.5px] text-ink-muted">Draw your signature above.</p>
              <button
                type="button"
                onClick={clearDrawing}
                className="text-[12px] font-medium text-brand transition hover:underline"
              >
                Clear
              </button>
            </div>
          </div>
        )}

        {tab === 'upload' && (
          <div>
            {value ? (
              <div className="rounded border border-surface-line bg-white p-3">
                <img src={value} alt="Your signature" className="mx-auto max-h-32 object-contain" />
              </div>
            ) : (
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                className="flex h-40 w-full flex-col items-center justify-center gap-2 rounded border
                  border-dashed border-surface-line bg-[#FAFBFF] text-ink-muted transition
                  hover:border-brand/50 hover:text-ink"
              >
                <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor"
                  strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M12 16V4m0 0L8 8m4-4 4 4M4 16v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" />
                </svg>
                <span className="text-[13px] font-medium">Upload a picture of your signature</span>
                <span className="text-[11.5px]">PNG or JPG, up to 2MB</span>
              </button>
            )}
            <input
              ref={fileInputRef}
              type="file"
              accept="image/png,image/jpeg"
              className="hidden"
              onChange={handleUpload}
            />
            {value && (
              <button
                type="button"
                onClick={() => { onChange(null); setUploadError(null) }}
                className="mt-2 text-[12px] font-medium text-brand transition hover:underline"
              >
                Choose a different image
              </button>
            )}
            {uploadError && <p className="mt-2 text-[12px] text-accent-red">{uploadError}</p>}
          </div>
        )}
      </div>
    </div>
  )
}
