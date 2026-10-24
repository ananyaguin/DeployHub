import { apiBaseUrl } from '../utils/config'

export function apiUrl(path) {
  const base = apiBaseUrl.replace(/\/$/, '')
  const suffix = path.startsWith('/') ? path : `/${path}`
  return `${base}${suffix}`
}
