import * as pdfjsLib from 'pdfjs-dist'
import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'

pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorkerUrl

/** Loads a PDF from raw bytes (an ArrayBuffer) into a pdf.js document. */
export async function loadPdf(arrayBuffer) {
  const task = pdfjsLib.getDocument({ data: arrayBuffer })
  return task.promise
}

/**
 * Renders one page onto a <canvas>, scaled for the element's own CSS width so
 * it stays crisp at any zoom, and returns the page's rendered CSS size - the
 * frame every signature-field percentage is measured against.
 *
 * pdf.js refuses to run two render() calls against the same canvas at once.
 * React 18 StrictMode's dev-only double-invoke of effects can otherwise start
 * exactly that, so any earlier task on this canvas is cancelled first - a
 * resulting RenderingCancelledException is expected and swallowed here.
 */
export async function renderPageToCanvas(pdf, pageNumber, canvas, cssWidth) {
  if (canvas.__pdfRenderTask) {
    canvas.__pdfRenderTask.cancel()
  }

  const page = await pdf.getPage(pageNumber)
  const unscaledViewport = page.getViewport({ scale: 1 })
  const devicePixelRatio = window.devicePixelRatio || 1
  const scale = cssWidth / unscaledViewport.width
  const viewport = page.getViewport({ scale: scale * devicePixelRatio })

  canvas.width = viewport.width
  canvas.height = viewport.height
  canvas.style.width = `${cssWidth}px`
  canvas.style.height = `${viewport.height / devicePixelRatio}px`

  const context = canvas.getContext('2d')
  const task = page.render({ canvasContext: context, viewport })
  canvas.__pdfRenderTask = task
  try {
    await task.promise
  } catch (err) {
    if (err?.name !== 'RenderingCancelledException') throw err
  } finally {
    if (canvas.__pdfRenderTask === task) canvas.__pdfRenderTask = null
  }

  return { width: cssWidth, height: viewport.height / devicePixelRatio }
}
