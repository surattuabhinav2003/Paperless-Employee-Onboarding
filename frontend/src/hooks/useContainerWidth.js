import { useCallback, useEffect, useState } from 'react'

/**
 * The width available to an element, capped, as a callback ref.
 *
 * <p>Written for the PDF pages. They used to render at a fixed 680px, which on
 * a phone is wider than the screen: the canvas scaled down to fit, the box
 * around it kept the height it had been given, and the signature fields -
 * positioned as percentages of that box - landed somewhere other than where
 * they are on the page.
 *
 * <p>A callback ref rather than a ref object, because the element it measures
 * appears only after the document has loaded. An effect keyed on a ref object
 * runs once, finds `current` still null, and never attaches an observer at all.
 *
 * <p>The width is quantised, and that is the important part. Rendering a PDF
 * makes the page taller, which can bring in a scrollbar, which makes the column
 * ~15px narrower, which re-renders the PDF: a loop that never settles and
 * leaves the document permanently blank. Snapping to a step means an unrelated
 * few pixels cannot start it.
 */
const STEP = 24

export function useContainerWidth(max = 680) {
  const [node, setNode] = useState(null)
  const [width, setWidth] = useState(max)

  useEffect(() => {
    if (!node) return undefined

    const measure = () => {
      const available = node.clientWidth
      if (available <= 0) return
      const capped = Math.min(max, available)
      // Snap down to a step, and never below one step.
      const quantised = Math.max(STEP, Math.floor(capped / STEP) * STEP)
      setWidth((current) => (current === quantised ? current : quantised))
    }

    measure()

    if (typeof ResizeObserver === 'undefined') {
      window.addEventListener('resize', measure)
      return () => window.removeEventListener('resize', measure)
    }

    const observer = new ResizeObserver(measure)
    observer.observe(node)
    return () => observer.disconnect()
  }, [node, max])

  const ref = useCallback((element) => setNode(element), [])

  return [width, ref]
}
