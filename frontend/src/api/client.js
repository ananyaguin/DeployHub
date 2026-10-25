import { apiBaseUrl } from '../utils/config'
import { clearSession, readSession } from './session'

export function apiUrl(path) {
  const base = apiBaseUrl.replace(/\/$/, '')
  const suffix = path.startsWith('/') ? path : `/${path}`
  return `${base}${suffix}`
}

export class ApiFailure extends Error {
  constructor(message, status, errors = []) {
    super(message)
    this.status = status
    this.errors = errors
  }
}

export function errorText(error) {
  if (error instanceof ApiFailure && error.errors.length > 0) return error.errors.join(' ')
  if (error instanceof Error && error.message) return error.message
  return 'The request could not be completed.'
}

export async function api(path, { method = 'GET', body, auth = true } = {}) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (auth) {
    const session = readSession()
    if (!session) {
      clearSession()
      throw new ApiFailure('Authentication required', 401)
    }
    headers.Authorization = `${session.tokenType} ${session.accessToken}`
  }

  let response
  try {
    response = await fetch(apiUrl(path), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiFailure('The API could not be reached.', 0)
  }

  if (response.status === 204) return null
  const text = await response.text()
  let payload = null
  if (text) {
    try {
      payload = JSON.parse(text)
    } catch {
      throw new ApiFailure('The API returned an unreadable response.', response.status)
    }
  }
  if (!response.ok) {
    if (response.status === 401) clearSession()
    throw new ApiFailure(payload?.message || 'The request could not be completed.', response.status, payload?.errors || [])
  }
  return payload
}
