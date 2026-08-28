import { useCallback, useEffect, useState } from 'react'

/**
 * One signature per person, across every field on a document.
 *
 * <p>A letter can ask for a signature in several places, and each one is filled
 * in its own dialog. The handwriting face used to be chosen inside the dialog,
 * so it reset to the first face every time one opened: sign in three places and
 * you could end up with three different hands on the same page.
 *
 * <p>The choice lives here instead, above the dialogs, and this also tracks
 * which fields were filled by typing. Changing the face on the last field
 * re-renders the ones already signed - drawn and uploaded signatures are left
 * alone, since they are not ours to restyle.
 *
 * @param defaultName pre-fills the typed signature, and updates until the
 *                    person types something of their own
 */
export function useSignatureStyle(defaultName = '') {
  const [style, setStyle] = useState({ tab: 'type', typed: defaultName, fontIndex: 0 })
  const [typedFields, setTypedFields] = useState(() => new Set())
  const [touched, setTouched] = useState(false)

  /* The name arrives with the page load, which can land after this mounts. It
     seeds the field until the person edits it, and never overwrites them. */
  useEffect(() => {
    if (touched || !defaultName) return
    setStyle((current) => (current.typed ? current : { ...current, typed: defaultName }))
  }, [defaultName, touched])

  const changeStyle = useCallback((next) => {
    setTouched(true)
    setStyle(next)
  }, [])

  /** Records how a signature field was filled, so restyling knows what it owns. */
  const rememberSource = useCallback((key, wasTyped) => {
    setTypedFields((current) => {
      const has = current.has(key)
      if (wasTyped === has) return current
      const next = new Set(current)
      if (wasTyped) next.add(key)
      else next.delete(key)
      return next
    })
  }, [])

  return { style, changeStyle, typedFields, rememberSource }
}
