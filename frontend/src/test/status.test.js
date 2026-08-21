import { describe, expect, it } from 'vitest'
import {
  awaitingReviewCount,
  documentProgressMeta,
  bondStatusMeta,
  documentStatusMeta,
  offerStatusMeta,
  pipelineStatusMeta,
  stageMeta,
} from '../utils/status'

describe('status metadata', () => {
  it('maps every backend stage to a label and tone', () => {
    expect(stageMeta('docs_pending').label).toBe('Documents Pending')
    expect(stageMeta('docs_approved').label).toBe('Verified')
    expect(stageMeta('offer_accepted').label).toBe('Offer Accepted')
    expect(stageMeta('bond_signed').label).toBe('Onboarding Complete')
  })

  it('falls back safely for unknown values', () => {
    expect(stageMeta('something_new').tone).toBe('grey')
    expect(documentStatusMeta(undefined).tone).toBe('grey')
  })

  it('treats missing offer and bond as not prepared', () => {
    expect(offerStatusMeta(null).label).toBe('Not prepared')
    expect(bondStatusMeta(undefined).label).toBe('Not prepared')
  })

  it('maps document statuses used by the portal', () => {
    expect(documentStatusMeta('pending').label).toBe('Not uploaded')
    expect(documentStatusMeta('submitted').label).toBe('Awaiting review')
    expect(documentStatusMeta('verified').tone).toBe('green')
    expect(documentStatusMeta('rejected').tone).toBe('red')
  })
})

describe('pipeline status', () => {
  const base = {
    stage: 'docs_pending',
    documentsRequired: 3,
    documentsVerified: 0,
    documentsSubmitted: 0,
    documentsRejected: 0,
    documentsMissing: 3,
    submittedForReviewAt: null,
  }

  it('reads as upload pending while the candidate still has documents to send', () => {
    expect(pipelineStatusMeta(base).label).toBe('Upload pending')
    expect(pipelineStatusMeta({ ...base, documentsMissing: 1, documentsSubmitted: 2 }).label)
      .toBe('Upload pending')
  })

  it('reads as needs review once everything is in', () => {
    const allUploaded = { ...base, documentsMissing: 0, documentsSubmitted: 3 }
    expect(pipelineStatusMeta(allUploaded).label).toBe('Needs review')
    // Or as soon as the candidate submits, even mid-review.
    expect(pipelineStatusMeta({ ...base, documentsMissing: 0, documentsSubmitted: 1,
      documentsVerified: 2, submittedForReviewAt: '2026-08-21T00:00:00Z' }).label).toBe('Needs review')
  })

  it('calls out a rejected document ahead of anything else', () => {
    expect(pipelineStatusMeta({ ...base, documentsMissing: 0, documentsRejected: 1 }).label)
      .toBe('Re-upload needed')
  })

  it('reads as verified once HR approves the stage', () => {
    expect(pipelineStatusMeta({ ...base, stage: 'docs_approved' }).label).toBe('Verified')
    expect(pipelineStatusMeta({ ...base, stage: 'offer_accepted' }).label).toBe('Offer Accepted')
    expect(pipelineStatusMeta({ ...base, stage: 'bond_signed' }).label).toBe('Onboarding Complete')
  })

  it('counts only documents actually sitting in HR queue', () => {
    expect(awaitingReviewCount({ ...base, documentsSubmitted: 2 })).toBe(2)
    // Past the document stage there is nothing left to review.
    expect(awaitingReviewCount({ ...base, stage: 'docs_approved', documentsSubmitted: 2 })).toBe(0)
    expect(awaitingReviewCount(null)).toBe(0)
  })
})

describe('document progress', () => {
  const base = {
    documentsRequired: 3,
    documentsVerified: 0,
    documentsSubmitted: 0,
    documentsRejected: 0,
    documentsMissing: 3,
  }

  it('fills as the candidate uploads, not as HR verifies', () => {
    expect(documentProgressMeta(base)).toMatchObject({ uploaded: 0, total: 3 })
    // All three uploaded and none verified yet: the bar is full, HR has work to do.
    const uploaded = { ...base, documentsMissing: 0, documentsSubmitted: 3 }
    expect(documentProgressMeta(uploaded)).toMatchObject({ uploaded: 3, total: 3, verified: 0 })
    expect(documentProgressMeta(uploaded).caption).toBe('3 to review')
  })

  it('counts a rejected document as uploaded but still calls it out', () => {
    const meta = documentProgressMeta({
      ...base, documentsMissing: 0, documentsVerified: 2, documentsRejected: 1,
    })
    expect(meta.uploaded).toBe(3)
    expect(meta.caption).toBe('1 rejected')
    expect(meta.tone).toBe('amber')
  })

  it('lists everything outstanding when a candidate is mid-way', () => {
    expect(documentProgressMeta({
      ...base, documentsMissing: 1, documentsSubmitted: 1, documentsRejected: 1,
    }).caption).toBe('1 rejected · 1 to review · 1 awaiting upload')
  })

  it('turns green only when every document is verified', () => {
    const done = { ...base, documentsMissing: 0, documentsVerified: 3 }
    expect(documentProgressMeta(done).caption).toBe('All verified')
    expect(documentProgressMeta(done).tone).toBe('green')
  })

  it('handles a candidate with no requirements', () => {
    expect(documentProgressMeta({ ...base, documentsRequired: 0, documentsMissing: 0 }))
      .toMatchObject({ uploaded: 0, total: 0, caption: 'None requested' })
  })
})
