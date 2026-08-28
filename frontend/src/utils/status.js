/**
 * Presentation-only mapping of backend statuses to pill styles. The backend
 * remains the source of truth for what a status means and what it unlocks.
 */
export const STAGE_META = {
  docs_pending: { label: 'Documents Pending', tone: 'slate' },
  docs_approved: { label: 'Verification done', tone: 'blue' },
  /* Not "Complete": the milestone is the signature on the offer letter, and
     saying so is the difference between HR knowing what happened and guessing. */
  offer_accepted: { label: 'Offer letter signed', tone: 'green' },
}

/*
 * "Reviewed", not "Verified": HR ticking off one document says they have looked
 * at it, nothing more. Verification is only finished when HR approves the
 * candidate and emails them, so that word is reserved for after that click -
 * see documentStatusMeta below, which upgrades the label once it happens.
 */
export const DOCUMENT_STATUS_META = {
  pending: { label: 'Not uploaded', tone: 'grey' },
  submitted: { label: 'Awaiting review', tone: 'amber' },
  verified: { label: 'Reviewed', tone: 'green' },
  rejected: { label: 'Rejected', tone: 'red' },
}

/*
 * Two states, which is all HR acts on: the offer is out, or it is signed.
 * "Viewed" folds into Sent - knowing the candidate opened it changes nothing
 * about what HR does next, and the exact timestamp is on the record.
 */
export const OFFER_STATUS_META = {
  draft: { label: 'Draft', tone: 'amber' },
  sent: { label: 'Sent', tone: 'blue' },
  viewed: { label: 'Sent', tone: 'blue' },
  accepted: { label: 'Signed', tone: 'green' },
}

export const TONE_CLASSES = {
  grey: 't-grey',
  blue: 't-blue',
  slate: 't-slate',
  amber: 't-amber',
  green: 't-green',
  teal: 't-teal',
  red: 't-red',
}

/** The leading bar on a status tag, and the dot variant. */
export const TONE_BARS = {
  grey: 'd-grey',
  blue: 'd-blue',
  slate: 'd-slate',
  amber: 'd-amber',
  green: 'd-green',
  teal: 'd-teal',
  red: 'd-red',
}

/*
 * What each status actually means. Shown on hover so the row stays clean while
 * the vocabulary is never ambiguous - "Verification done" in particular means HR
 * has approved the candidate and emailed them, not merely that the documents
 * were uploaded or individually checked off.
 */
export const STATUS_MEANING = {
  'Upload pending': 'Waiting on the candidate to upload their documents',
  'Needs review': 'Documents uploaded - waiting on your review',
  'Re-upload needed': 'A document was sent back and needs replacing',
  'Ready to approve': 'Every document is reviewed - approve to unlock the offer',
  'Verification done': 'Approved by HR and the candidate emailed - ready for an offer',
  'Offer letter signed': 'The candidate has signed and returned their offer letter',
  'Documents Pending': 'Waiting on the candidate to upload their documents',
}

export function statusMeaning(label) {
  return STATUS_MEANING[label] || undefined
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
  // Nothing left to review, but the candidate is not approved until HR says so -
  // surface that it is their click the record is waiting on.
  if (candidate.readyForApproval) {
    return { label: 'Ready to approve', tone: 'teal' }
  }
  const everythingUploaded = candidate.documentsMissing === 0 && candidate.documentsRequired > 0
  if (candidate.submittedForReviewAt || (everythingUploaded && candidate.documentsSubmitted > 0)) {
    return { label: 'Needs review', tone: 'blue' }
  }
  return { label: 'Upload pending', tone: 'slate' }
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

  // Same rule as the per-document pill: everything checked off is only
  // "verified" once HR has actually approved the candidate.
  const approved = candidate?.stage === 'docs_approved' || candidate?.stage === 'offer_accepted'
  const allDone = approved ? 'All verified' : 'All reviewed'

  return {
    uploaded,
    total,
    verified,
    caption: parts.length ? parts.join(' · ') : total > 0 ? allDone : 'None requested',
    tone: total > 0 && verified === total ? 'green' : rejected > 0 ? 'amber' : 'brand',
  }
}

/** Documents sitting in HR's queue for this candidate. */
export function awaitingReviewCount(candidate) {
  return candidate?.stage === 'docs_pending' ? candidate.documentsSubmitted || 0 : 0
}

/**
 * @param approved whether HR has approved the candidate and emailed them. Only
 *   then does a checked-off document become "Verified" - before that it is
 *   merely "Reviewed", because HR can still reopen or reject it.
 */
export function documentStatusMeta(status, approved = false) {
  const meta = DOCUMENT_STATUS_META[status] || { label: status || 'Unknown', tone: 'grey' }
  if (approved && status === 'verified') {
    return { ...meta, label: 'Verified' }
  }
  return meta
}

export function offerStatusMeta(status) {
  if (!status) return { label: 'Not sent', tone: 'slate' }
  return OFFER_STATUS_META[status] || { label: status, tone: 'grey' }
}

