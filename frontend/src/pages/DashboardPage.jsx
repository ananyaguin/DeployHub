import { useEffect, useState } from 'react'
import { api, errorText } from '../api/client'
import { Alert } from '../components/Field'
import { StatusBadge } from '../components/StatusBadge'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { followLink } from '../routes/navigate'

export function DashboardPage() {
  useDocumentTitle('Dashboard · DeployHub')
  const [state, setState] = useState({
    status: 'loading', deployments: [], total: 0, complete: true, events: [], audit: [], notices: [], operations: null, error: '',
  })

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
      const audit = await api('/api/v1/audit?page=0&size=8').catch(() => null)
      const notices = await api('/api/v1/notification-deliveries?page=0&size=8').catch(() => null)
      const operations = await api('/api/v1/operations').catch(() => null)
      return {
        deployments: items,
        total: Number(first.total),
        complete: items.length >= Number(first.total),
        events,
        audit: audit?.items || [],
        notices: notices?.items || [],
        operations,
      }
    }
    load().then(
      (data) => { if (live) setState({ status: 'ready', error: '', ...data }) },
      (error) => { if (live) setState({ status: 'error', deployments: [], total: 0, complete: true, events: [], audit: [], notices: [], operations: null, error: errorText(error) }) },
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
          <h1>Dashboard</h1>
        </div>
        <a className="button" href="/deployments" onClick={followLink}>View deployments</a>
      </div>
      {state.status === 'loading' ? <p className="muted">Loading deployments…</p> : null}
      <Alert>{state.status === 'error' ? state.error : ''}</Alert>
      {state.status === 'ready' ? <OperationsSummary operations={state.operations} /> : null}
      {state.status === 'ready' && state.total === 0 ? (
        <div className="panel">
          <h2>No deployments yet</h2>
          <p className="lede">Create a deployment to record a version and queue work.</p>
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
          <h2>Notices</h2>
          <p className="muted">In-app notices for failures, unhealthy simulated checks, and rollback results. Repeated reports do not add another notice.</p>
          {state.notices.length === 0 ? <p className="muted">No notices yet.</p> : (
            <ul className="activity">
              {state.notices.map((notice) => (
                <li key={`${notice.sentAt}-${notice.summary}`}>
                  <StatusBadge value={notice.channel} />
                  <span>{notice.summary || notice.status}</span>
                  <time dateTime={notice.sentAt}>{formatWhen(notice.sentAt)}</time>
                </li>
              ))}
            </ul>
          )}
          <h2>Audit</h2>
          <p className="muted">Tenant audit history. Capacity figures are declared, not measured on the host.</p>
          {state.audit.length === 0 ? <p className="muted">No audit events yet.</p> : (
            <ul className="activity">
              {state.audit.map((event) => (
                <li key={`${event.deploymentId}-${event.createdAt}-${event.eventType}-${event.message}`}>
                  <strong>{event.eventType}</strong>
                  <span>{event.actor || 'system'}</span>
                  <span className="muted">{event.message}</span>
                  <time dateTime={event.createdAt}>{formatWhen(event.createdAt)}</time>
                </li>
              ))}
            </ul>
          )}
          <h2>Recent activity</h2>
          <p className="muted">From the newest deployments on this page.</p>
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

function OperationsSummary({ operations }) {
  if (!operations) return <p className="muted">Operations summary unavailable.</p>
  const items = [
    ['Queued', operations.queued],
    ['Running', operations.running],
    ['Succeeded', operations.succeeded],
    ['Failed', operations.failed],
    ['Retried', operations.retried],
    ['Rollback succeeded', operations.rollbackSucceeded],
    ['Rollback failed', operations.rollbackFailed],
    ['Scheduling blocked', operations.schedulingBlocked],
    ['Workers online', operations.workersOnline],
    ['Workers offline', operations.workersOffline],
  ]
  return (
    <div className="panel">
      <h2>Operations</h2>
      <p className="muted">Execution counts are this tenant. Worker totals are the shared pool. Capacity is declared, not measured. This summary is separate from API liveness and from a deployment&apos;s simulated health.</p>
      <div className="stats">
        {items.map(([label, value]) => (
          <article className="stat" key={label}><span>{label}</span><strong>{value}</strong></article>
        ))}
      </div>
    </div>
  )
}

function formatWhen(value) {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}
