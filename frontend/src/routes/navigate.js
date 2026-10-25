import { useEffect, useState } from 'react'

export function navigate(path) {
  const next = path.startsWith('/') ? path : `/${path}`
  if (`${window.location.pathname}${window.location.search}` === next) return
  window.history.pushState(null, '', next)
  window.dispatchEvent(new PopStateEvent('popstate'))
}

export function useLocation() {
  const [value, setValue] = useState(() => `${window.location.pathname}${window.location.search}`)
  useEffect(() => {
    const sync = () => setValue(`${window.location.pathname}${window.location.search}`)
    window.addEventListener('popstate', sync)
    return () => window.removeEventListener('popstate', sync)
  }, [])
  return value
}

export function followLink(event) {
  if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || event.button !== 0) return
  event.preventDefault()
  navigate(event.currentTarget.getAttribute('href'))
}
