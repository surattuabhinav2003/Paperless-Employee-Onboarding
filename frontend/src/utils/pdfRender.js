/*
 * pdf.js is by far the heaviest thing this app depends on - the library and its
 * worker together are larger than everything else put together. Only four
 * screens ever render a PDF, so it is loaded on first use rather than imported
 * at the top: signing in, the dashboard and the candidate list now cost nothing
 * for a library they never touch.
 *
 * The import is memoised, so the second PDF on a page reuses the first load.
 */
let pdfjsPromise = null

function pdfjs() {
  if (!pdfjsPromise) {
    pdfjsPromise = Promise.all([
      import('pdfjs-dist'),
      import('pdfjs-dist/build/pdf.worker.min.mjs?url'),
    ]).then(([lib, worker]) => {
      lib.GlobalWorkerOptions.workerSrc = worker.default
      return lib
    }).catch((error) => {
      // Let the next attempt retry rather than caching a failed network fetch.
      pdfjsPromise = null
      throw error
    })
  }
  return pdfjsPromise
}

/** Loads a PDF from raw bytes (an ArrayBuffer) into a pdf.js document. */
export async function loadPdf(arrayBuffer) {
  const pdfjsLib = await pdfjs()
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
