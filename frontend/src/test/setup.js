import '@testing-library/jest-dom/vitest'

// Some jsdom setups do not expose Storage; the app treats it as optional, and
// tests that assert on the remembered preference need a working one.
if (typeof window !== 'undefined' && !window.localStorage) {
  const store = new Map()
  Object.defineProperty(window, 'localStorage', {
    configurable: true,
    value: {
      getItem: (key) => (store.has(key) ? store.get(key) : null),
      setItem: (key, value) => store.set(key, String(value)),
      removeItem: (key) => store.delete(key),
      clear: () => store.clear(),
      key: (index) => [...store.keys()][index] ?? null,
      get length() {
        return store.size
      },
    },
  })
}
