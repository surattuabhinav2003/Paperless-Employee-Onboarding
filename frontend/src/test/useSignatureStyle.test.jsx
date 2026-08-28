import { act, renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { useSignatureStyle } from '../hooks/useSignatureStyle'

/*
 * A document can ask for a signature in several places, and each is filled in
 * its own dialog. The handwriting face used to be chosen inside the dialog, so
 * it reset to the first face every time one opened and a person signing in
 * three places could end up with three different hands on one page. These tests
 * pin the choice surviving between dialogs, and the bookkeeping that lets an
 * already-typed signature be restyled without touching a drawn one.
 */

describe('useSignatureStyle', () => {
  it('keeps the chosen face across every field on the document', () => {
    const { result } = renderHook(() => useSignatureStyle('Priya Sharma'))

    expect(result.current.style.fontIndex).toBe(0)

    // Signing the first field in the fourth face.
    act(() => result.current.changeStyle({ ...result.current.style, fontIndex: 3 }))

    // Opening the next field must not send them back to the first face.
    expect(result.current.style.fontIndex).toBe(3)
    expect(result.current.style.typed).toBe('Priya Sharma')
  })

  it('pre-fills the name, and stops once the person edits it', () => {
    const { result, rerender } = renderHook(({ name }) => useSignatureStyle(name), {
      initialProps: { name: '' },
    })

    // The name arrives with the page load, after this mounts.
    rerender({ name: 'Priya Sharma' })
    expect(result.current.style.typed).toBe('Priya Sharma')

    act(() => result.current.changeStyle({ ...result.current.style, typed: 'P. Sharma' }))
    rerender({ name: 'Priya Sharma' })

    // Their own spelling stands; a late re-render must not undo it.
    expect(result.current.style.typed).toBe('P. Sharma')
  })

  it('tracks which fields were typed, so only those get restyled', () => {
    const { result } = renderHook(() => useSignatureStyle('Priya Sharma'))

    act(() => {
      result.current.rememberSource(0, true)   // typed
      result.current.rememberSource(2, false)  // drawn
      result.current.rememberSource(5, true)   // typed
    })

    expect([...result.current.typedFields].sort()).toEqual([0, 5])
  })

  it('drops a field that was retyped and then drawn instead', () => {
    const { result } = renderHook(() => useSignatureStyle('Priya Sharma'))

    act(() => result.current.rememberSource(1, true))
    expect(result.current.typedFields.has(1)).toBe(true)

    // They went back and drew it, so it is no longer ours to restyle.
    act(() => result.current.rememberSource(1, false))
    expect(result.current.typedFields.has(1)).toBe(false)
  })
})
