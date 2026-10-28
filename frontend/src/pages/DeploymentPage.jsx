import { useCallback, useEffect, useState } from 'react'
import { api, errorText } from '../api/client'
import { Alert, Field } from '../components/Field'
import { StatusBadge } from '../components/StatusBadge'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { followLink } from '../routes/navigate'

const IMAGE = /^[a-z0-9]+(?:[._-][a-z0-9]+)*(?:\/[a-z0-9]+(?:[._-][a-z0-9]+)*)*(?::[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}|@sha256:[a-f0-9]{64})$/
const UUID = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/

export function DeploymentPage({ publicId }) {
  const [deployment, setDeployment] = useState(null)
  const [versions, setVersions] = useState(null)
  const [events, setEvents] = useState(null)
  const validId = UUID.test(publicId)
  const [error, setError] = useState(validId ? '' : 'Deployment not found.')
  const [notice, setNotice] = useState('')
  const [loading, setLoading] = useState(validId)
  const [name, setName] = useState('')
  const [image, setImage] = useState('')
  const [pending, setPending] = useState('')
  const [execution, setExecution] = useState(null)
  const [history, setHistory] = useState(null)
  const [historyError, setHistoryError] = useState('')
  const [selectedId, setSelectedId] = useState('')
  const [logLines, setLogLines] = useState(null)
  const [logError, setLogError] = useState('')
  const [logsFor, setLogsFor] = useState('')
  const [health, setHealth] = useState(null)
  const [healthError, setHealthError] = useState('')
  const [confirmVersion, setConfirmVersion] = useState('')

  useDocumentTitle(deployment ? `${deployment.name} · DeployHub` : 'Deployment · DeployHub')

  const load = useCallback((live = () => true) => {
    if (!validId) return Promise.resolve()
    const deploymentRequest = api(`/api/v1/deployments/${publicId}`)
    const versionRequest = api(`/api/v1/deployments/${publicId}/versions?page=0&size=100`)
    const eventRequest = api(`/api/v1/deployments/${publicId}/events?page=0&size=20`)
    const historyRequest = api(`/api/v1/deployments/${publicId}/executions?page=0&size=20`)
    const healthRequest = api(`/api/v1/deployments/${publicId}/health`)
    historyRequest.then(
      (page) => {
        if (!live()) return
        setHistory(page)
        setHistoryError('')
        setSelectedId((current) => current || page.items?.[0]?.publicId || '')
      },
      (caught) => {
        if (!live()) return
        setHistory(null)
        setHistoryError(errorText(caught))
      },
    )
    healthRequest.then(
      (next) => {
        if (!live()) return
        setHealth(next)
        setHealthError('')
      },
      (caught) => {
        if (!live()) return
        setHealth(null)
        setHealthError(errorText(caught))
      },
    )
    return Promise.all([deploymentRequest, versionRequest, eventRequest]).then(
      ([nextDeployment, nextVersions, nextEvents]) => {
        if (!live()) return
        setDeployment(nextDeployment)
        setName(nextDeployment.name)
        setVersions(nextVersions)
        setEvents(nextEvents)
        setError('')
        setLoading(false)
      },
      (caught) => {
        if (!live()) return
        setError(caught.status === 404 ? 'Deployment not found.' : errorText(caught))
        setLoading(false)
      },
    )
  }, [publicId, validId])

  useEffect(() => {
    if (!validId) return undefined
    let live = true
    load(() => live)
    return () => { live = false }
  }, [load, validId])

  const executionId = execution?.publicId
  const executionStatus = execution?.status
  useEffect(() => {
    if (executionStatus !== 'QUEUED' && executionStatus !== 'RUNNING') return undefined
    const timer = setInterval(() => {
      api(`/api/v1/executions/${executionId}`).then(
        (next) => {
          setExecution(next)
          if (next.status !== 'QUEUED' && next.status !== 'RUNNING') load()
        },
        (caught) => setError(errorText(caught)),
      )
    }, 3000)
    return () => clearInterval(timer)
  }, [executionId, executionStatus, load])

  useEffect(() => {
    if (!selectedId) return undefined
    let live = true
    const requested = selectedId
    api(`/api/v1/executions/${requested}/logs?page=0&size=20`).then(
      (page) => {
        if (!live) return
        setLogLines(page)
        setLogError('')
        setLogsFor(requested)
      },
      (caught) => {
        if (!live) return
        setLogLines(null)
        setLogError(errorText(caught))
        setLogsFor(requested)
      },
    )
    return () => { live = false }
  }, [selectedId])

  async function rename(event) {
    event.preventDefault()
    if (pending || !deployment) return
    if (!name.trim()) {
      setError('Name is required.')
      return
    }
    setPending('rename')
    setError('')
    try {
      const next = await api(`/api/v1/deployments/${deployment.publicId}`, {
        method: 'PATCH',
        body: { name: name.trim(), status: deployment.status },
      })
      setDeployment(next)
      setNotice('Name updated.')
    } catch (caught) {
      setError(errorText(caught))
    } finally {
      setPending('')
    }
  }

  async function addVersion(event) {
    event.preventDefault()
    if (pending || !deployment) return
    if (!IMAGE.test(image)) {
      setError('Image must be lowercase and include a tag, such as nginx:1.27.')
      return
    }
    setPending('version')
    setError('')
    try {
      await api(`/api/v1/deployments/${deployment.publicId}/versions`, { method: 'POST', body: { image } })
      setImage('')
      setNotice('Version recorded.')
      await load()
    } catch (caught) {
      setError(errorText(caught))
    } finally {
      setPending('')
    }
  }

  async function run(version, taskType) {
    if (pending || !deployment) return
    setPending(`${taskType}-${version.publicId}`)
    setError('')
    setNotice('')
    setConfirmVersion('')
    try {
      const queued = await api('/api/v1/executions', {
        method: 'POST',
        body: { deploymentId: deployment.publicId, versionId: version.publicId, taskType },
      })
      setExecution(queued)
      setNotice(queuedNotice(taskType, version))
    } catch (caught) {
      setError(errorText(caught))
    } finally {
      setPending('')
    }
  }

  if (loading) return <p className="muted">Loading deployment…</p>
  if (!deployment) {
    return (
      <section className="panel">
        <Alert>{error || 'Deployment not found.'}</Alert>
        <a href="/deployments" onClick={followLink}>Back to deployments</a>
      </section>
    )
  }

  const currentVersion = versions?.items?.find((version) => version.versionNumber === deployment.currentVersion) || null
  const selectedExecution = history?.items?.find((row) => row.publicId === selectedId)
  const failureDetail = logsFor === selectedId && selectedExecution?.status === 'FAILED'
    ? (logLines?.items?.find((line) => line.level === 'ERROR')?.message || 'No failure detail was recorded.')
    : ''

  return (
    <section>
      <p className="crumb"><a href="/deployments" onClick={followLink}>Deployments</a></p>
      <div className="page-head">
        <div>
          <h1>{deployment.name}</h1>
          <p className="lede">{deployment.slug} · current version {deployment.currentVersion ?? 'none'}</p>
        </div>
        <div>
          <p className="muted">Deployment status</p>
          <StatusBadge value={deployment.status} />
        </div>
        <button type="button" className="ghost" onClick={() => load()}>Refresh</button>
      </div>
      <Alert>{error}</Alert>
      <Alert tone="ok">{notice}</Alert>
      {execution ? (
        <article className="panel execution">
          <h2>Latest execution</h2>
          <p>
            Execution status <StatusBadge value={execution.status} /> {execution.taskType}
          </p>
          <p>{EXECUTION_FEEDBACK[execution.status] || 'This execution status is separate from the deployment status.'}</p>
          <p className="muted">Attempt {execution.attemptCount ?? 0}. Started {formatWhen(execution.startedAt) || '—'}. Finished {formatWhen(execution.finishedAt) || '—'}.</p>
        </article>
      ) : null}
      <div className="panel">
        <h2>Simulated health</h2>
        <p className="muted">This result is simulated. It is not the API health check, and no cloud provider is contacted.</p>
        {health == null && !healthError ? <p className="muted">Loading health…</p> : null}
        {healthError ? <Alert>{healthError}</Alert> : null}
        {health ? (
          <>
            <p>Deployment health <StatusBadge value={health.status} /></p>
            <p className="muted">Last check {formatWhen(health.checkedAt) || 'not recorded'}.</p>
            <p>{health.detail || 'No simulated health check has been recorded.'}</p>
            {health.failureStreak > 0 ? <p className="muted">Unhealthy results in a row: {health.failureStreak}. Recovery waits for three.</p> : null}
            <p className="muted">{health.workerAvailable
              ? 'A worker is online. Worker availability is separate from deployment health.'
              : 'No worker is currently available. That does not by itself make this deployment unhealthy.'}</p>
          </>
        ) : null}
        {deployment.status === 'ACTIVE' && currentVersion ? (
          <button type="button" disabled={pending !== ''} onClick={() => run(currentVersion, 'HEALTH')}>
            {pending === `HEALTH-${currentVersion.publicId}` ? 'Queuing…' : 'Run simulated health check'}
          </button>
        ) : <p className="muted">A simulated health check needs an active version.</p>}
      </div>
      <div className="panel">
        <h2>Execution history</h2>
        <p className="muted">Deployment status is the release. Each row is one execution.</p>
        {history == null && !historyError ? <p className="muted">Loading execution history…</p> : null}
        {historyError ? <Alert>{historyError}</Alert> : null}
        {history && !historyError && !history.items?.length ? <p className="muted">No executions yet.</p> : null}
        {history?.items?.length ? (
          <div className="table-wrap">
            <table className="table">
              <caption className="sr-only">Executions for {deployment.name}</caption>
              <thead>
                <tr>
                  <th scope="col">Execution status</th>
                  <th scope="col">Task</th>
                  <th scope="col">Attempt</th>
                  <th scope="col">Started</th>
                  <th scope="col">Finished</th>
                  <th scope="col">Logs</th>
                </tr>
              </thead>
              <tbody>
                {history.items.map((row) => (
                  <tr key={row.publicId}>
                    <td><StatusBadge value={row.status} /></td>
                    <td>{row.taskType}</td>
                    <td>{row.attemptCount}</td>
                    <td>{formatWhen(row.startedAt) || '—'}</td>
                    <td>{formatWhen(row.finishedAt) || '—'}</td>
                    <td>
                      <button type="button" className="ghost" onClick={() => setSelectedId(row.publicId)}>
                        {selectedId === row.publicId ? 'Showing' : 'Show'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
        {selectedId ? (
          <>
            {logsFor === selectedId && logError ? <Alert>{logError}</Alert> : null}
            {logsFor !== selectedId ? <p className="muted">Loading logs…</p> : null}
            {logsFor === selectedId && logLines && !logError && !logLines.items?.length ? <p className="muted">No log lines yet.</p> : null}
            {failureDetail ? <p><strong>Failure detail.</strong> {failureDetail}</p> : null}
            {logsFor === selectedId && logLines?.items?.length ? (
              <ul className="activity">
                {logLines.items.map((line) => (
                  <li key={line.sequenceNumber}>
                    <strong>{line.level}</strong>
                    <span>{line.message}</span>
                  </li>
                ))}
              </ul>
            ) : null}
          </>
        ) : null}
      </div>
      <form className="panel form" onSubmit={rename} noValidate>
        <h2>Rename</h2>
        <Field label="Name" name="rename" value={name} onChange={(event) => setName(event.target.value)} required />
        <button type="submit" disabled={pending !== ''}>{pending === 'rename' ? 'Saving…' : 'Save name'}</button>
      </form>
      <form className="panel form" onSubmit={addVersion} noValidate>
        <h2>New version</h2>
        <Field label="Image" name="image" hint="Tag required, for example nginx:1.27." value={image} onChange={(event) => setImage(event.target.value)} required />
        <button type="submit" disabled={pending !== '' || deployment.status === 'ARCHIVED'}>{pending === 'version' ? 'Saving…' : 'Record version'}</button>
      </form>
      <div className="table-wrap panel">
        <h2>Versions</h2>
        {versions?.items?.length ? (
          <table className="table">
            <caption className="sr-only">Versions for {deployment.name}</caption>
            <thead>
              <tr>
                <th scope="col">Version</th>
                <th scope="col">Image</th>
                <th scope="col">Container</th>
                <th scope="col">Actions</th>
              </tr>
            </thead>
            <tbody>
              {versions.items.map((version) => (
                <tr key={version.publicId}>
                  <td>{version.versionNumber}</td>
                  <td>{version.image || '—'}</td>
                  <td className="mono">{version.containerId || '—'}</td>
                  <td className="actions">
                    {canDeploy(deployment, version) ? (
                      <button type="button" disabled={pending !== ''} onClick={() => run(version, 'DEPLOY')}>Deploy</button>
                    ) : null}
                    {canRollback(deployment, version) ? (
                      confirmVersion === version.publicId ? (
                        <>
                          <button type="button" disabled={pending !== ''} onClick={() => run(version, 'ROLLBACK')}>
                            Confirm rollback to v{version.versionNumber}
                          </button>
                          <button type="button" className="ghost" disabled={pending !== ''} onClick={() => setConfirmVersion('')}>Cancel</button>
                        </>
                      ) : (
                        <button type="button" className="ghost" disabled={pending !== ''} onClick={() => setConfirmVersion(version.publicId)}>Rollback</button>
                      )
                    ) : null}
                    {canStop(deployment, version) ? (
                      <button type="button" className="ghost" disabled={pending !== ''} onClick={() => run(version, 'STOP')}>Stop</button>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : <p className="muted">No versions yet.</p>}
        {Number(versions?.total) > (versions?.items?.length || 0) ? <p className="muted">Showing the first {versions.items.length} versions.</p> : null}
      </div>
      <div className="panel">
        <h2>Events</h2>
        {events?.items?.length ? (
          <ul className="activity">
            {events.items.map((event) => (
              <li key={`${event.createdAt}-${event.eventType}-${event.message}`}>
                <strong>{event.eventType}</strong>
                <span>{event.message}</span>
                <time dateTime={event.createdAt}>{formatWhen(event.createdAt)}</time>
              </li>
            ))}
          </ul>
        ) : <p className="muted">No events yet.</p>}
      </div>
    </section>
  )
}

const EXECUTION_FEEDBACK = {
  QUEUED: 'Queued. A worker has not claimed this execution yet.',
  RUNNING: 'Running. This execution status is separate from the deployment status.',
  SUCCEEDED: 'Succeeded. The deployment version changes only after the backend accepts this result.',
  FAILED: 'Failed. The previous deployment version stays in place.',
}

function queuedNotice(taskType, version) {
  if (taskType === 'ROLLBACK') {
    return `Rollback to version ${version.versionNumber} queued. The active version changes only if this execution succeeds.`
  }
  if (taskType === 'HEALTH') {
    return 'Simulated health check queued. No cloud provider is contacted.'
  }
  return `${taskType} queued. This page polls that execution. A worker has to claim it.`
}

function canDeploy(deployment, version) {
  if (deployment.status === 'ARCHIVED') return false
  if (deployment.status === 'DRAFT' || deployment.currentVersion == null) return true
  return version.versionNumber > deployment.currentVersion
}

function canRollback(deployment, version) {
  return deployment.status === 'ACTIVE'
    && deployment.currentVersion != null
    && version.versionNumber < deployment.currentVersion
}

function canStop(deployment, version) {
  return deployment.status === 'ACTIVE' && deployment.currentVersion === version.versionNumber
}

function formatWhen(value) {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}
