/**
 * Remembers that this browser has already confirmed an emailed code for a given
 * onboarding link, so a candidate is asked once rather than on every visit.
 *
 * Keyed by the portal token: two candidates sharing a computer, or one candidate
 * whose link was regenerated, must each verify on their own. The marker is
 * signed server-side and carries nothing secret, so localStorage is the right
 * place for it - losing it only means answering one more code.
 */

const PREFIX = 'cf_portal_device:'

/* A short, stable fingerprint of the token - the full token is already in the
   URL, but there is no reason to copy it into storage as well. */
function keyFor(token) {
  let hash = 0
  const value = String(token || '')
  for (let i = 0; i < value.length; i += 1) {
    hash = (hash * 31 + value.charCodeAt(i)) | 0
  }
  return PREFIX + (hash >>> 0).toString(36)
}

export const deviceStore = {
  get(token) {
    try {
      return window.localStorage?.getItem(keyFor(token)) || null
    } catch {
      // Private browsing can block storage; the candidate just verifies again.
      return null
    }
  },

  set(token, marker) {
    try {
      window.localStorage?.setItem(keyFor(token), marker)
    } catch {
      // Not fatal - the marker still works for this page load.
    }
  },

  clear(token) {
    try {
      window.localStorage?.removeItem(keyFor(token))
    } catch {
      // Nothing to do.
    }
  },
}

