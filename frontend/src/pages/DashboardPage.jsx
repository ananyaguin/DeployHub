import { useEffect, useState } from 'react'
import { api, errorText } from '../api/client'
import { Alert } from '../components/Field'
import { StatusBadge } from '../components/StatusBadge'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { followLink } from '../routes/navigate'

export function DashboardPage() {
  useDocumentTitle('Dashboard · DeployHub')
  const [state, setState] = useState({ status: 'loading', deployments: [], total: 0, complete: true, events: [], error: '' })

  useEffect(() => {
    let live = true
    async function load() {
      const first = await api('/api/v1/deployments?page=0&size=100')
      const items = [...(first.items || [])]
      const pageCount = Math.min(Math.ceil(Number(first.total) / 100), 5)
      for (let page = 1; page < pageCount; page += 1) {
        const next = await api(`/api/v1/deployments?page=${page}&size=100`)
        items.push(...(next.items || []))
      }
      const sample = items.slice(0, 4)
      const eventPages = await Promise.all(sample.map(async (deployment) => {
        const page = await api(`/api/v1/deployments/${deployment.publicId}/events?page=0&size=5`)
        return (page.items || []).map((event) => ({ ...event, deployment }))
      }))
      const events = eventPages.flat().sort((left, right) => String(right.createdAt).localeCompare(String(left.createdAt))).slice(0, 8)
      return { deployments: items, total: Number(first.total), complete: items.length >= Number(first.total), events }
    }
    load().then(
      (data) => { if (live) setState({ status: 'ready', error: '', ...data }) },
      (error) => { if (live) setState({ status: 'error', deployments: [], total: 0, complete: true, events: [], error: errorText(error) }) },
    )
    return () => { live = false }
  }, [])

  const counts = { DRAFT: 0, ACTIVE: 0, ARCHIVED: 0 }
  for (const deployment of state.deployments) {
    if (deployment.status in counts) counts[deployment.status] += 1
  }

  return (
    <section>
      <div className="page-head">
        <div>
          <p className="eyebrow">Dashboard</p>
          <h1>Deployments</h1>
        </div>
        <a className="button" href="/deployments" onClick={followLink}>View deployments</a>
      </div>
      {state.status === 'loading' ? <p className="muted">Loading deployments…</p> : null}
      <Alert>{state.status === 'error' ? state.error : ''}</Alert>
      {state.status === 'ready' && state.total === 0 ? (
        <div className="panel">
          <h2>No deployments yet</h2>
          <p className="lede">Create one to record a version and queue a deploy. This screen only shows what the API returns.</p>
          <a className="button" href="/deployments" onClick={followLink}>Create a deployment</a>
        </div>
      ) : null}
      {state.status === 'ready' && state.total > 0 ? (
        <>
          <div className="stats">
            <article className="stat"><span>Total</span><strong>{state.total}</strong></article>
            <article className="stat"><span>Draft</span><strong>{counts.DRAFT}</strong></article>
            <article className="stat"><span>Active</span><strong>{counts.ACTIVE}</strong></article>
            <article className="stat"><span>Archived</span><strong>{counts.ARCHIVED}</strong></article>
          </div>
          {state.complete ? null : <p className="muted">Status counts cover the first {state.deployments.length} of {state.total} deployments.</p>}
          <h2>Recent activity</h2>
          <p className="muted">Events from the newest loaded deployments. The API has no account-wide activity feed.</p>
          {state.events.length === 0 ? <p className="muted">No events yet.</p> : (
            <ul className="activity">
              {state.events.map((event) => (
                <li key={`${event.deployment.publicId}-${event.createdAt}-${event.eventType}`}>
                  <StatusBadge value={event.deployment.status} />
                  <a href={`/deployments/${event.deployment.publicId}`} onClick={followLink}>{event.deployment.name}</a>
                  <span>{event.eventType}</span>
                  <span className="muted">{event.message}</span>
                  <time dateTime={event.createdAt}>{formatWhen(event.createdAt)}</time>
                </li>
              ))}
            </ul>
          )}
        </>
      ) : null}
    </section>
  )
}

function formatWhen(value) {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}
