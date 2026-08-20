import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * Small data-loading hook: keeps { data, error, loading } in sync and exposes
 * reload() so pages can refresh after a mutation.
 */
export function useAsync(loader, deps = [], { immediate = true } = {}) {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(immediate)
  const mounted = useRef(true)

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  const run = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const result = await loader()
      if (mounted.current) {
        setData(result)
      }
      return result
    } catch (err) {
      if (mounted.current) {
        setError(err)
      }
      throw err
    } finally {
      if (mounted.current) {
        setLoading(false)
      }
    }
  }, deps) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (immediate) {
      run().catch(() => {})
    }
  }, [run, immediate])

  return { data, error, loading, reload: run, setData }
}
