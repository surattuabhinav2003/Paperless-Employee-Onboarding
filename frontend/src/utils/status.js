/**
 * Presentation-only mapping of backend statuses to pill styles. The backend
 * remains the source of truth for what a status means and what it unlocks.
 */
export const STAGE_META = {
  docs_pending: { label: 'Documents Pending', tone: 'amber' },
  docs_approved: { label: 'Verified', tone: 'green' },
  offer_accepted: { label: 'Offer Accepted', tone: 'teal' },
  bond_signed: { label: 'Onboarding Complete', tone: 'green' },
}

export const DOCUMENT_STATUS_META = {
  pending: { label: 'Not uploaded', tone: 'grey' },
  submitted: { label: 'Awaiting review', tone: 'amber' },
  verified: { label: 'Verified', tone: 'green' },
  rejected: { label: 'Rejected', tone: 'red' },
}

export const OFFER_STATUS_META = {
  sent: { label: 'Sent', tone: 'blue' },
  viewed: { label: 'Viewed', tone: 'amber' },
  accepted: { label: 'Accepted', tone: 'green' },
}

export const BOND_STATUS_META = {
  not_initiated: { label: 'Ready to sign', tone: 'grey' },
  awaiting_signature: { label: 'Awaiting signature', tone: 'amber' },
  signed: { label: 'Signed', tone: 'green' },
  failed: { label: 'Signature failed', tone: 'red' },
}

export const TONE_CLASSES = {
  grey: 'bg-surface-offwhite text-ink-muted ring-1 ring-inset ring-surface-line',
  blue: 'bg-brand-tint text-brand ring-1 ring-inset ring-brand/20',
  amber: 'bg-[#FFF4EC] text-[#B94A18] ring-1 ring-inset ring-[#FE5833]/25',
  green: 'bg-[#E8FAF1] text-[#0E7A47] ring-1 ring-inset ring-accent-green/30',
  teal: 'bg-[#E5FBF9] text-[#0C8B83] ring-1 ring-inset ring-accent-teal/30',
  red: 'bg-[#FFECEC] text-[#C21414] ring-1 ring-inset ring-accent-red/25',
}

export function stageMeta(stage) {
  return STAGE_META[stage] || { label: stage || 'Unknown', tone: 'grey' }
}

/**
 * What HR needs to know at a glance, which is finer than the backend stage:
 * while a candidate sits in docs_pending they may still be uploading, be waiting
 * on a review, or have something sent back. Purely presentational - the stage
 * itself is still what gates the workflow.
 */
export function pipelineStatusMeta(candidate) {
  if (!candidate) return { label: 'Unknown', tone: 'grey' }

  if (candidate.stage !== 'docs_pending') {
    return stageMeta(candidate.stage)
  }
  if (candidate.documentsRejected > 0) {
    return { label: 'Re-upload needed', tone: 'red' }
  }
  const everythingUploaded = candidate.documentsMissing === 0 && candidate.documentsRequired > 0
  if (candidate.submittedForReviewAt || (everythingUploaded && candidate.documentsSubmitted > 0)) {
    return { label: 'Needs review', tone: 'blue' }
  }
  return { label: 'Upload pending', tone: 'amber' }
}

/**
 * The documents bar tracks what the candidate has actually provided, so it moves
 * the moment they upload rather than waiting on HR. What is still outstanding -
 * for either side - is spelled out in the caption.
 */
export function documentProgressMeta(candidate) {
  const total = candidate?.documentsRequired ?? 0
  const missing = candidate?.documentsMissing ?? 0
  const submitted = candidate?.documentsSubmitted ?? 0
  const rejected = candidate?.documentsRejected ?? 0
  const verified = candidate?.documentsVerified ?? 0
  const uploaded = Math.max(0, total - missing)

  const parts = []
  if (rejected > 0) parts.push(`${rejected} rejected`)
  if (submitted > 0) parts.push(`${submitted} to review`)
  if (missing > 0) parts.push(`${missing} awaiting upload`)

  return {
    uploaded,
    total,
    verified,
    caption: parts.length ? parts.join(' · ') : total > 0 ? 'All verified' : 'None requested',
    tone: total > 0 && verified === total ? 'green' : rejected > 0 ? 'amber' : 'brand',
  }
}

/** Documents sitting in HR's queue for this candidate. */
export function awaitingReviewCount(candidate) {
  return candidate?.stage === 'docs_pending' ? candidate.documentsSubmitted || 0 : 0
}

export function documentStatusMeta(status) {
  return DOCUMENT_STATUS_META[status] || { label: status || 'Unknown', tone: 'grey' }
}

export function offerStatusMeta(status) {
  if (!status) return { label: 'Not prepared', tone: 'grey' }
  return OFFER_STATUS_META[status] || { label: status, tone: 'grey' }
}

export function bondStatusMeta(status) {
  if (!status) return { label: 'Not prepared', tone: 'grey' }
  return BOND_STATUS_META[status] || { label: status, tone: 'grey' }
}
