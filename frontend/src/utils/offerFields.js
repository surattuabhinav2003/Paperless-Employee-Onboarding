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

/** Cursive faces for a typed signature. See ensureSignatureFonts below. */
export const SIGNATURE_FONTS = [
  { id: 'dancing', name: 'Dancing Script', family: "'Dancing Script', cursive" },
  { id: 'vibes', name: 'Great Vibes', family: "'Great Vibes', cursive" },
  { id: 'pacifico', name: 'Pacifico', family: "'Pacifico', cursive" },
  { id: 'allura', name: 'Allura', family: "'Allura', cursive" },
  { id: 'sacramento', name: 'Sacramento', family: "'Sacramento', cursive" },
]

const SIGNATURE_FONT_HREF =
  'https://fonts.googleapis.com/css2?family=Dancing+Script:wght@600'
  + '&family=Great+Vibes&family=Pacifico&family=Allura&family=Sacramento&display=swap'

let signatureFontsPromise = null

/**
 * Requests the five handwriting faces, once, and resolves when they are usable.
 *
 * <p>They used to sit in the document head, which meant every visit - the
 * sign-in screen, the dashboard, the candidate list - waited on five font files
 * for a feature on one screen. Only the signature pad needs them, so only the
 * signature pad asks. Resolving on error as well as success is deliberate: a
 * blocked font should degrade the typed signature to a fallback face, never
 * leave the pad stuck waiting.
 */
export function ensureSignatureFonts() {
  if (typeof document === 'undefined') return Promise.resolve()

  if (!signatureFontsPromise) {
    signatureFontsPromise = new Promise((resolve) => {
      const link = document.createElement('link')
      link.rel = 'stylesheet'
      link.href = SIGNATURE_FONT_HREF
      link.addEventListener('load', resolve, { once: true })
      link.addEventListener('error', resolve, { once: true })
      document.head.appendChild(link)
    }).then(() => Promise.all(
      SIGNATURE_FONTS.map((font) =>
        (document.fonts ? document.fonts.load(`48px ${font.family}`) : Promise.resolve())),
    )).catch(() => {})
  }
  return signatureFontsPromise
}

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

const places = (n) => (n === 1 ? '1 place' : `${n} places`)
const details = (n) => (n === 1 ? '1 other detail' : `${n} other details`)

/**
 * How much of the letter is left, counting signatures apart from everything
 * else.
 *
 * <p>"4 of 6 filled in" says nothing about the commitment being asked for: a
 * date box and a signature are not the same act, and how many times someone is
 * being asked to sign is the thing worth knowing before they start. So the
 * signatures are counted separately and named in the guidance.
 *
 * @param fields the offer's placed fields, in order
 * @param values what has been filled in so far, keyed by field index
 */
export function signatureProgress(fields = [], values = {}) {
  const filled = (index) => String(values[index] ?? '').trim().length > 0

  const total = fields.length
  const filledCount = fields.filter((_, index) => filled(index)).length
  const remaining = total - filledCount
  const allDone = total > 0 && remaining === 0

  const signatureTotal = fields.filter((field) => field.type === 'signature').length
  const signaturesDone = fields
    .filter((field, index) => field.type === 'signature' && filled(index)).length
  const signaturesLeft = signatureTotal - signaturesDone
  const otherLeft = remaining - signaturesLeft

  let guidance
  if (allDone) {
    guidance = 'Everything is filled in. Review it, then submit when you are happy.'
  } else if (signatureTotal === 0) {
    guidance = 'Read the letter below. The highlighted boxes are yours to fill in - '
      + 'click any one when you are ready.'
  } else if (signaturesDone === 0) {
    guidance = `Read the letter below. It needs your signature in ${places(signatureTotal)}`
      + `${otherLeft > 0 ? `, plus ${details(otherLeft)}` : ''}. `
      + 'The highlighted boxes are yours to fill in - click any one when you are ready.'
  } else if (signaturesLeft > 0) {
    guidance = `${signaturesDone} of ${signatureTotal} signatures done, ${signaturesLeft} still to go`
      + `${otherLeft > 0 ? `, and ${details(otherLeft)}` : ''}.`
  } else {
    guidance = `All ${signatureTotal === 1 ? 'signed' : `${signatureTotal} signatures done`}. `
      + `${details(otherLeft)} still to fill in.`
  }

  return { total, filledCount, remaining, allDone, signatureTotal, signaturesDone, signaturesLeft, otherLeft, guidance }
}
