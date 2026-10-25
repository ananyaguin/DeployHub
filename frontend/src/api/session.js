const KEY = 'deployhub.session'

export function readSession() {
  try {
    const raw = sessionStorage.getItem(KEY)
    if (!raw) return null
    const value = JSON.parse(raw)
    if (!value?.accessToken || !value.expiresAt || value.expiresAt <= Date.now()) {
      sessionStorage.removeItem(KEY)
      return null
    }
    return value
  } catch {
    sessionStorage.removeItem(KEY)
    return null
  }
}

export function saveSession(login, identity) {
  sessionStorage.setItem(KEY, JSON.stringify({
    accessToken: login.accessToken,
    tokenType: login.tokenType || 'Bearer',
    expiresAt: Date.now() + Number(login.expiresIn) * 1000,
    email: identity.email,
    tenantSlug: identity.tenantSlug,
    displayName: identity.displayName || '',
  }))
  window.dispatchEvent(new Event('deployhub-session'))
}

export function clearSession() {
  sessionStorage.removeItem(KEY)
  window.dispatchEvent(new Event('deployhub-session'))
}
