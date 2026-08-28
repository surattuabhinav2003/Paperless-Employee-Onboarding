import { act, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useContainerWidth } from '../hooks/useContainerWidth'

/*
 * PDF pages used to render at a fixed 680px. On a phone that is wider than the
 * screen: the canvas scaled down to fit, the box around it kept the height it
 * had been given, and the signature fields - positioned as percentages of that
 * box - landed somewhere other than where they are on the page. A candidate
 * could not sign their offer letter on the device the link arrives on.
 */

function elementOfWidth(width) {
  const element = document.createElement('div')
  Object.defineProperty(element, 'clientWidth', { value: width, configurable: true })
  return element
}

/* The hook hands back a callback ref; the test plays the part of React and
   hands it the element. */
function widthOf(element, max) {
  const rendered = renderHook(() => useContainerWidth(max))
  act(() => { rendered.result.current[1](element) })
  return { get current() { return rendered.result.current[0] } }
}

afterEach(() => vi.restoreAllMocks())

describe('useContainerWidth', () => {
  it('renders at the phone width when that is all there is', () => {
    // Quantised down to a step, so a stray pixel cannot retrigger a re-render.
    const result = widthOf(elementOfWidth(360), 680)
    expect(result.current).toBe(360)
  })

  it('never exceeds the cap on a wide screen', () => {
    // A letter stretched across a 1600px monitor would be unreadable.
    const result = widthOf(elementOfWidth(1600), 680)
    expect(result.current).toBe(672)
  })

  it('falls back to the cap when the element has not been laid out yet', () => {
    const result = widthOf(elementOfWidth(0), 680)
    expect(result.current).toBe(680)
  })

  it('follows the container when the phone is rotated', () => {
    const element = elementOfWidth(360)
    const result = widthOf(element, 680)
    expect(result.current).toBe(360)

    Object.defineProperty(element, 'clientWidth', { value: 780, configurable: true })
    act(() => { window.dispatchEvent(new Event('resize')) })

    expect(result.current).toBe(672)
  })

  it('does not move when a scrollbar appears', () => {
    const element = elementOfWidth(360)
    const result = widthOf(element, 680)

    /*
     * The loop this prevents: rendering the PDF makes the page taller, a
     * scrollbar appears, the column narrows by ~15px, the PDF re-renders, and
     * the document never settles - it just stays blank.
     */
    Object.defineProperty(element, 'clientWidth', { value: 345, configurable: true })
    act(() => { window.dispatchEvent(new Event('resize')) })

    expect(result.current).toBe(336)
    expect(result.current).not.toBe(345)
  })

  it('survives never being given an element rather than throwing', () => {
    const { result } = renderHook(() => useContainerWidth(680))
    expect(result.current[0]).toBe(680)
  })

  it('attaches when the element appears later, not only on first render', () => {
    // The page area only exists once the document has loaded. A ref object read
    // in an effect is still null then, and no observer is ever attached.
    const rendered = renderHook(() => useContainerWidth(680))
    expect(rendered.result.current[0]).toBe(680)

    act(() => { rendered.result.current[1](elementOfWidth(360)) })

    expect(rendered.result.current[0]).toBe(360)
  })
})
