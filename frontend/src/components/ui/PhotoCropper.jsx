import { useCallback, useEffect, useRef, useState } from 'react'
import '../../styles/photocropper.css'
import { Button } from './Button'

/*
 * Passport photos are portrait 35x45mm. The crop window keeps that ratio, and the
 * export is that size at 300dpi so HR receives a print-ready image.
 */
const CROP_W = 280
const CROP_H = 360
const OUT_W = 413
const OUT_H = 531

/**
 * A small in-browser photo editor: pick an image, then zoom and drag it inside a
 * fixed passport-shaped frame and save the crop. This is the same gesture people
 * know from every profile-photo uploader, so a candidate can straighten and frame
 * their photo without any outside tool.
 */
export function PhotoCropper({ open, file, onCancel, onConfirm }) {
  const [img, setImg] = useState(null)
  const [zoom, setZoom] = useState(1)
  const [offset, setOffset] = useState({ x: 0, y: 0 })
  const [saving, setSaving] = useState(false)
  const drag = useRef(null)
  const frameRef = useRef(null)

  // Load the chosen file into an Image and fit it to cover the frame, centred.
  useEffect(() => {
    if (!open || !file) return undefined
    const url = URL.createObjectURL(file)
    const image = new Image()
    image.onload = () => {
      setImg(image)
      setZoom(1)
      const base = Math.max(CROP_W / image.naturalWidth, CROP_H / image.naturalHeight)
      const w = image.naturalWidth * base
      const h = image.naturalHeight * base
      setOffset({ x: (CROP_W - w) / 2, y: (CROP_H - h) / 2 })
    }
    image.src = url
    return () => URL.revokeObjectURL(url)
  }, [open, file])

  const base = img ? Math.max(CROP_W / img.naturalWidth, CROP_H / img.naturalHeight) : 1
  const displayScale = base * zoom
  const dispW = img ? img.naturalWidth * displayScale : 0
  const dispH = img ? img.naturalHeight * displayScale : 0

  // The image must always cover the frame, so it can never be dragged past an edge.
  const clamp = useCallback(
    (next) => ({
      x: Math.min(0, Math.max(CROP_W - dispW, next.x)),
      y: Math.min(0, Math.max(CROP_H - dispH, next.y)),
    }),
    [dispW, dispH],
  )

  useEffect(() => {
    if (img) setOffset((current) => clamp(current))
  }, [zoom, img, clamp])

  const onPointerDown = (event) => {
    event.currentTarget.setPointerCapture(event.pointerId)
    drag.current = { px: event.clientX, py: event.clientY, ox: offset.x, oy: offset.y }
  }
  const onPointerMove = (event) => {
    if (!drag.current) return
    setOffset(
      clamp({
        x: drag.current.ox + (event.clientX - drag.current.px),
        y: drag.current.oy + (event.clientY - drag.current.py),
      }),
    )
  }
  const onPointerUp = () => {
    drag.current = null
  }

  const save = async () => {
    if (!img) return
    setSaving(true)
    try {
      const canvas = document.createElement('canvas')
      canvas.width = OUT_W
      canvas.height = OUT_H
      const ctx = canvas.getContext('2d')
      ctx.fillStyle = '#ffffff'
      ctx.fillRect(0, 0, OUT_W, OUT_H)
      // The frame's top-left in source pixels, and how much source it spans.
      const sx = -offset.x / displayScale
      const sy = -offset.y / displayScale
      const sW = CROP_W / displayScale
      const sH = CROP_H / displayScale
      ctx.drawImage(img, sx, sy, sW, sH, 0, 0, OUT_W, OUT_H)
      const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.92))
      const cropped = new File([blob], 'passport-photo.jpg', { type: 'image/jpeg' })
      await onConfirm(cropped)
    } finally {
      setSaving(false)
    }
  }

  if (!open) return null

  return (
    <div className="pc-overlay" role="dialog" aria-modal="true" aria-label="Adjust your photo">
      <div className="pc-card">
        <h3 className="pc-title">Adjust your photo</h3>
        <p className="pc-hint">Drag to position and use the slider to zoom. The frame is passport size.</p>

        <div
          ref={frameRef}
          className="pc-frame"
          style={{ width: CROP_W, height: CROP_H }}
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={onPointerUp}
          onPointerCancel={onPointerUp}
        >
          {img && (
            <img
              src={img.src}
              alt=""
              draggable="false"
              style={{
                position: 'absolute',
                left: offset.x,
                top: offset.y,
                width: dispW,
                height: dispH,
                maxWidth: 'none',
                userSelect: 'none',
              }}
            />
          )}
          <div className="pc-grid" aria-hidden="true" />
        </div>

        <label className="pc-zoom">
          <span aria-hidden="true">−</span>
          <input
            type="range"
            min="1"
            max="3"
            step="0.01"
            value={zoom}
            onChange={(event) => setZoom(Number(event.target.value))}
            aria-label="Zoom"
          />
          <span aria-hidden="true">+</span>
        </label>

        <div className="pc-actions">
          <Button variant="ghost" onClick={onCancel} disabled={saving}>Cancel</Button>
          <Button onClick={save} disabled={saving || !img}>
            {saving ? 'Saving...' : 'Use this photo'}
          </Button>
        </div>
      </div>
    </div>
  )
}
