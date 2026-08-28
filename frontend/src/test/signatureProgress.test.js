import { describe, expect, it } from 'vitest'
import { signatureProgress } from '../utils/offerFields'

/*
 * The candidate used to be told only "4 of 6 filled in", which lumps a date box
 * in with putting their name to a job offer. How many times they are being
 * asked to sign is the thing worth knowing before they start, so it is counted
 * and named separately.
 */

const sig = { type: 'signature' }
const date = { type: 'date' }
const text = { type: 'text' }

describe('signatureProgress', () => {
  it('counts signatures apart from the other boxes', () => {
    const progress = signatureProgress([sig, date, sig, text], {})

    expect(progress.signatureTotal).toBe(2)
    expect(progress.signaturesDone).toBe(0)
    expect(progress.otherLeft).toBe(2)
  })

  it('says up front how many places need signing', () => {
    const progress = signatureProgress([sig, sig, sig, date], {})
    expect(progress.guidance).toContain('needs your signature in 3 places')
    expect(progress.guidance).toContain('plus 1 other detail')
  })

  it('reads naturally when there is only one signature and nothing else', () => {
    const progress = signatureProgress([sig], {})
    expect(progress.guidance).toContain('needs your signature in 1 place')
    expect(progress.guidance).not.toContain('plus')
  })

  it('counts down as they sign', () => {
    const progress = signatureProgress([sig, sig, sig], { 0: 'data:image/png;base64,AAA' })

    expect(progress.signaturesDone).toBe(1)
    expect(progress.signaturesLeft).toBe(2)
    expect(progress.guidance).toContain('1 of 3 signatures done, 2 still to go')
  })

  it('does not call a signature done just because another box is filled', () => {
    // The date is filled, the signature is not - the count must not move.
    const progress = signatureProgress([sig, date], { 1: '2026-08-29' })

    expect(progress.signaturesDone).toBe(0)
    expect(progress.filledCount).toBe(1)
  })

  it('switches to the remaining details once every signature is in', () => {
    const progress = signatureProgress([sig, sig, text], {
      0: 'data:image/png;base64,AAA',
      1: 'data:image/png;base64,AAA',
    })

    expect(progress.signaturesLeft).toBe(0)
    expect(progress.guidance).toContain('All 2 signatures done')
    expect(progress.guidance).toContain('1 other detail still to fill in')
  })

  it('treats whitespace as unfilled, so a stray space is not a signature', () => {
    const progress = signatureProgress([sig], { 0: '   ' })

    expect(progress.signaturesDone).toBe(0)
    expect(progress.allDone).toBe(false)
  })

  it('hands over to review once nothing is outstanding', () => {
    const progress = signatureProgress([sig, date], {
      0: 'data:image/png;base64,AAA',
      1: '2026-08-29',
    })

    expect(progress.allDone).toBe(true)
    expect(progress.guidance).toContain('Review it, then submit')
  })

  it('says nothing about signatures on a letter that asks for none', () => {
    const progress = signatureProgress([date, text], {})

    expect(progress.signatureTotal).toBe(0)
    expect(progress.guidance).not.toContain('signature')
  })
})
