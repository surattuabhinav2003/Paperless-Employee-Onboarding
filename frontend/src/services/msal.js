import { PublicClientApplication } from '@azure/msal-browser'

/**
 * Microsoft (Entra ID) sign-in for HR, via the redirect flow.
 *
 * <p>Redirect rather than popup: popups are blocked by many browsers and are
 * awkward to hand back to the app, whereas a full-page redirect works
 * everywhere. The client id and tenant id are public; the backend is what
 * actually validates the token this returns.
 */
const clientId = import.meta.env.VITE_AZURE_CLIENT_ID
const tenantId = import.meta.env.VITE_AZURE_TENANT_ID

export const microsoftLoginEnabled = Boolean(clientId && tenantId)

export const msalInstance = microsoftLoginEnabled
  ? new PublicClientApplication({
      auth: {
        clientId,
        authority: `https://login.microsoftonline.com/${tenantId}`,
        // Must match a SPA redirect URI on the app registration.
        redirectUri: window.location.origin,
      },
      cache: { cacheLocation: 'sessionStorage' },
    })
  : null

/*
 * The auth code comes back in the URL hash. React Router navigates away from
 * "/" the instant the app renders, which would wipe the hash before MSAL's
 * async init finishes - so we snapshot it synchronously here, at module load
 * (before any render), and hand that exact value to handleRedirectPromise.
 */
const initialHash = typeof window !== 'undefined' ? window.location.hash : ''

export const microsoftRedirectResult = msalInstance
  ? msalInstance
      .initialize()
      .then(() => msalInstance.handleRedirectPromise(initialHash))
      .catch(() => null)
  : Promise.resolve(null)

async function ensureMsal() {
  if (!msalInstance) {
    throw new Error('Microsoft sign-in is not configured.')
  }
  // The same promise that also drives the redirect result; awaiting it
  // guarantees initialize() has completed.
  await microsoftRedirectResult
  return msalInstance
}

/** Sends the browser to Microsoft to sign in. The page navigates away. */
export async function startMicrosoftRedirect() {
  const msal = await ensureMsal()
  await msal.loginRedirect({
    scopes: ['openid', 'profile', 'email'],
    prompt: 'select_account',
  })
}

/**
 * On app load, finishes a redirect coming back from Microsoft and returns the
 * ID token; returns null on a normal (non-redirect) load.
 */
export async function completeMicrosoftRedirect() {
  const result = await microsoftRedirectResult
  return result ? result.idToken : null
}
