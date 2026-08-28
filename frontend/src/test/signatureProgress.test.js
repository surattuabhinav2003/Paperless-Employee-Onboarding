import { describe, expect, it } from 'vitest'
import { pageList, signatureProgress } from '../utils/offerFields'

/*
 * The candidate used to be told only "4 of 6 filled in", which lumps a date box
 * in with putting their name to a job offer. How many times they are being
 * asked to sign is the thing worth knowing before they start, so it is counted
 * and named separately.
 */

/* Fields carry the page they sit on: a five-page letter with signatures on the
   second and the last needs to say so, or "3 places" tells nobody where to go. */
const sig = (page = 1) => ({ type: 'signature', page })
const date = (page = 1) => ({ type: 'date', page })
const text = (page = 1) => ({ type: 'text', page })

describe('signatureProgress', () => {
  it('counts signatures apart from the other boxes', () => {
    const progress = signatureProgress([sig(1), date(1), sig(2), text(2)], {})

    expect(progress.signatureTotal).toBe(2)
    expect(progress.signaturesDone).toBe(0)
    expect(progress.otherLeft).toBe(2)
  })

  it('says up front how many places need signing', () => {
    const progress = signatureProgress([sig(1), sig(2), sig(3), date(3)], {})
    expect(progress.guidance).toContain('needs your signature in 3 places')
    expect(progress.guidance).toContain('on pages 1, 2 and 3')
    expect(progress.guidance).toContain('plus 1 other detail')
  })

  it('reads naturally when there is only one signature and nothing else', () => {
    const progress = signatureProgress([sig(1)], {})
    expect(progress.guidance).toContain('needs your signature in 1 place')
    expect(progress.guidance).not.toContain('plus')
  })

  it('counts down as they sign', () => {
    const progress = signatureProgress([sig(1), sig(2), sig(4)], { 0: 'data:image/png;base64,AAA' })

    expect(progress.signaturesDone).toBe(1)
    expect(progress.signaturesLeft).toBe(2)
    expect(progress.guidance).toContain('1 of 3 signatures done')
    // Which pages are still outstanding, not just how many.
    expect(progress.guidance).toContain('Still to sign on pages 2 and 4')
  })

  it('does not call a signature done just because another box is filled', () => {
    // The date is filled, the signature is not - the count must not move.
    const progress = signatureProgress([sig(1), date(1)], { 1: '2026-08-29' })

    expect(progress.signaturesDone).toBe(0)
    expect(progress.filledCount).toBe(1)
  })

  it('switches to the remaining details once every signature is in', () => {
    const progress = signatureProgress([sig(1), sig(2), text(2)], {
      0: 'data:image/png;base64,AAA',
      1: 'data:image/png;base64,AAA',
    })

    expect(progress.signaturesLeft).toBe(0)
    expect(progress.guidance).toContain('All 2 signatures done')
    expect(progress.guidance).toContain('1 other detail still to fill in')
  })

  it('treats whitespace as unfilled, so a stray space is not a signature', () => {
    const progress = signatureProgress([sig(1)], { 0: '   ' })

    expect(progress.signaturesDone).toBe(0)
    expect(progress.allDone).toBe(false)
  })

  it('hands over to review once nothing is outstanding', () => {
    const progress = signatureProgress([sig(1), date(1)], {
      0: 'data:image/png;base64,AAA',
      1: '2026-08-29',
    })

    expect(progress.allDone).toBe(true)
    expect(progress.guidance).toContain('Review it, then submit')
  })

  it('says nothing about signatures on a letter that asks for none', () => {
    const progress = signatureProgress([date(1), text(1)], {})

    expect(progress.signatureTotal).toBe(0)
    expect(progress.guidance).not.toContain('signature')
  })
  it('lists the pages that still need something, for the page badges', () => {
    const progress = signatureProgress([sig(1), date(3), sig(5)], { 0: 'data:image/png;base64,AAA' })

    expect(progress.signaturePages).toEqual([1, 5])
    expect(progress.outstandingPages).toEqual([3, 5])
  })
})

describe('pageList', () => {
  it('reads the way a person would say it', () => {
    expect(pageList([2])).toBe('page 2')
    expect(pageList([1, 3])).toBe('pages 1 and 3')
    expect(pageList([1, 2, 5])).toBe('pages 1, 2 and 5')
  })

  it('sorts and de-duplicates, since two fields can share a page', () => {
    expect(pageList([3, 1, 3])).toBe('pages 1 and 3')
  })

  it('says nothing when there is nothing to say', () => {
    expect(pageList([])).toBe('')
  })
})
