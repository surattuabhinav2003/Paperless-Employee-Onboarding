import { describe, expect, it } from 'vitest'
import {
  bondStatusMeta,
  documentStatusMeta,
  offerStatusMeta,
  stageMeta,
} from '../utils/status'

describe('status metadata', () => {
  it('maps every backend stage to a label and tone', () => {
    expect(stageMeta('docs_pending').label).toBe('Documents Pending')
    expect(stageMeta('docs_approved').label).toBe('Documents Approved')
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
