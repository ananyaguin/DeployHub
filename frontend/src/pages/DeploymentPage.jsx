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

  useDocumentTitle(deployment ? `${deployment.name} · DeployHub` : 'Deployment · DeployHub')

  const load = useCallback(() => {
    if (!validId) return Promise.resolve()
    return Promise.all([
      api(`/api/v1/deployments/${publicId}`),
      api(`/api/v1/deployments/${publicId}/versions?page=0&size=100`),
      api(`/api/v1/deployments/${publicId}/events?page=0&size=20`),
    ]).then(
      ([nextDeployment, nextVersions, nextEvents]) => {
        setDeployment(nextDeployment)
        setName(nextDeployment.name)
        setVersions(nextVersions)
        setEvents(nextEvents)
        setError('')
        setLoading(false)
      },
      (caught) => {
        setError(caught.status === 404 ? 'Deployment not found.' : errorText(caught))
        setLoading(false)
      },
    )
  }, [publicId, validId])

  useEffect(() => {
    if (!validId) return undefined
    let live = true
    Promise.all([
      api(`/api/v1/deployments/${publicId}`),
      api(`/api/v1/deployments/${publicId}/versions?page=0&size=100`),
      api(`/api/v1/deployments/${publicId}/events?page=0&size=20`),
    ]).then(
      ([nextDeployment, nextVersions, nextEvents]) => {
        if (!live) return
        setDeployment(nextDeployment)
        setName(nextDeployment.name)
        setVersions(nextVersions)
        setEvents(nextEvents)
        setLoading(false)
      },
      (caught) => {
        if (!live) return
        setError(caught.status === 404 ? 'Deployment not found.' : errorText(caught))
        setLoading(false)
      },
    )
    return () => { live = false }
  }, [publicId, validId])

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
    try {
      const queued = await api('/api/v1/executions', {
        method: 'POST',
        body: { deploymentId: deployment.publicId, versionId: version.publicId, taskType },
      })
      setExecution(queued)
      setNotice(`${taskType} queued. This page polls that execution. A worker has to claim it.`)
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

  return (
    <section>
      <p className="crumb"><a href="/deployments" onClick={followLink}>Deployments</a></p>
      <div className="page-head">
        <div>
          <h1>{deployment.name}</h1>
          <p className="lede">{deployment.slug} · current version {deployment.currentVersion ?? 'none'}</p>
        </div>
        <StatusBadge value={deployment.status} />
        <button type="button" className="ghost" onClick={load}>Refresh</button>
      </div>
      <Alert>{error}</Alert>
      <Alert tone="ok">{notice}</Alert>
      {execution ? (
        <article className="panel execution">
          <h2>Latest queued task</h2>
          <p>
            <StatusBadge value={execution.status} /> {execution.taskType}
          </p>
          <p className="muted">Events below are the history for this deployment.</p>
        </article>
      ) : null}
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
                      <button type="button" className="ghost" disabled={pending !== ''} onClick={() => run(version, 'ROLLBACK')}>Rollback</button>
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
