/**
 * The field types HR can place on an offer letter, and the handwriting faces
 * offered for a typed signature. Shared by the HR field editor and the
 * candidate signing page so the two always agree on labels and defaults.
 */

export const FIELD_TYPES = [
  { value: 'signature', label: 'Signature', hint: 'Draw, type or upload', width: 24, height: 8 },
  { value: 'name', label: 'Name', hint: 'Full name', width: 24, height: 4 },
  { value: 'title', label: 'Title', hint: 'Job title', width: 20, height: 4 },
  { value: 'date', label: 'Date', hint: "Defaults to today's date", width: 16, height: 4 },
  { value: 'text', label: 'Text', hint: 'Any other detail', width: 28, height: 5 },
]

export const fieldTypeMeta = (value) =>
  FIELD_TYPES.find((t) => t.value === value) || FIELD_TYPES[0]

/** Cursive faces for a typed signature; loaded from Google Fonts in index.html. */
export const SIGNATURE_FONTS = [
  { id: 'dancing', name: 'Dancing Script', family: "'Dancing Script', cursive" },
  { id: 'vibes', name: 'Great Vibes', family: "'Great Vibes', cursive" },
  { id: 'pacifico', name: 'Pacifico', family: "'Pacifico', cursive" },
  { id: 'allura', name: 'Allura', family: "'Allura', cursive" },
  { id: 'sacramento', name: 'Sacramento', family: "'Sacramento', cursive" },
]

/** PDF standard families a text field can be stamped in. */
export const TEXT_FONTS = [
  { value: 'helvetica', label: 'Helvetica', family: 'Helvetica, Arial, sans-serif' },
  { value: 'times', label: 'Times', family: '"Times New Roman", Times, serif' },
  { value: 'courier', label: 'Courier', family: '"Courier New", Courier, monospace' },
]

export const TEXT_COLORS = [
  { value: '#111827', label: 'Black' },
  { value: '#174F96', label: 'Blue' },
  { value: '#dc2626', label: 'Red' },
  { value: '#047857', label: 'Green' },
]

/** Today as YYYY-MM-DD, the format a date field is stamped in. */
export function todayIso() {
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

/**
 * Rasterises typed text in a handwriting face to a PNG data URI, so a typed
 * signature reaches the backend as an image exactly like a drawn one - the
 * cursive font never has to exist on the server.
 */
export function typedSignatureToDataUrl(text, fontFamily) {
  const value = (text || '').trim()
  if (!value) return null

  const fontSize = 48
  const shorthand = `${fontSize}px ${fontFamily}`
  // Rasterising before the webfont loads would silently bake in a fallback face.
  if (document.fonts && !document.fonts.check(shorthand)) {
    document.fonts.load(shorthand).catch(() => {})
    return null
  }

  const measure = document.createElement('canvas').getContext('2d')
  measure.font = shorthand
  const padding = 16
  const logicalWidth = Math.ceil(measure.measureText(value).width) + padding * 2
  const logicalHeight = fontSize + padding * 2

  // 2-4x oversample so cursive strokes stay crisp once scaled into the PDF box.
  const scale = Math.max(2, Math.min(4, (window.devicePixelRatio || 1) * 2))
  const canvas = document.createElement('canvas')
  canvas.width = Math.ceil(logicalWidth * scale)
  canvas.height = Math.ceil(logicalHeight * scale)

  const ctx = canvas.getContext('2d')
  ctx.setTransform(scale, 0, 0, scale, 0, 0)
  ctx.font = shorthand
  ctx.fillStyle = '#111827'
  ctx.textBaseline = 'middle'
  ctx.fillText(value, padding, logicalHeight / 2)
  return canvas.toDataURL('image/png')
}
