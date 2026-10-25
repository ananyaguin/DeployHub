import { useEffect, useState } from 'react'
import { api, errorText } from '../api/client'
import { Alert, Field } from '../components/Field'
import { StatusBadge } from '../components/StatusBadge'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { followLink, navigate } from '../routes/navigate'

const SLUG = /^[a-z0-9]+(?:-[a-z0-9]+)*$/

export function DeploymentsPage() {
  useDocumentTitle('Deployments · DeployHub')
  const [rows, setRows] = useState(null)
  const [error, setError] = useState('')
  const [form, setForm] = useState({ name: '', slug: '' })
  const [pending, setPending] = useState(false)

  function load() {
    return api('/api/v1/deployments?page=0&size=100').then(
      (page) => { setRows(page); setError('') },
      (caught) => { setRows(null); setError(errorText(caught)) },
    )
  }

  useEffect(() => {
    let live = true
    api('/api/v1/deployments?page=0&size=100').then(
      (page) => { if (live) setRows(page) },
      (caught) => { if (live) setError(errorText(caught)) },
    )
    return () => { live = false }
  }, [])

  async function submit(event) {
    event.preventDefault()
    if (pending) return
    if (!form.name.trim() || !SLUG.test(form.slug)) {
      setError('Name is required. Slug must be lowercase letters, numbers, and hyphens.')
      return
    }
    setPending(true)
    setError('')
    try {
      const created = await api('/api/v1/deployments', { method: 'POST', body: form })
      navigate(`/deployments/${created.publicId}`)
    } catch (caught) {
      setError(errorText(caught))
      setPending(false)
    }
  }

  return (
    <section>
      <div className="page-head">
        <div>
          <p className="eyebrow">Deployments</p>
          <h1>Your tenant</h1>
        </div>
        <button type="button" className="ghost" onClick={load}>Refresh</button>
      </div>
      <Alert>{error}</Alert>
      <form className="panel form create-form" onSubmit={submit}>
        <h2>Create a deployment</h2>
        <div className="split">
          <Field label="Name" name="name" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} required />
          <Field label="Slug" name="slug" hint="Lowercase, unique in this tenant." value={form.slug} onChange={(event) => setForm({ ...form, slug: event.target.value })} required />
        </div>
        <button type="submit" disabled={pending}>{pending ? 'Creating…' : 'Create deployment'}</button>
      </form>
      {rows == null ? <p className="muted">Loading deployments…</p> : null}
      {rows && rows.total === 0 ? <p className="panel muted">No deployments in this tenant yet.</p> : null}
      {rows && rows.items?.length > 0 ? (
        <div className="table-wrap panel">
          <table className="table">
            <caption className="sr-only">Deployments in the signed-in tenant</caption>
            <thead>
              <tr>
                <th scope="col">Name</th>
                <th scope="col">Slug</th>
                <th scope="col">Status</th>
                <th scope="col">Current version</th>
              </tr>
            </thead>
            <tbody>
              {rows.items.map((deployment) => (
                <tr key={deployment.publicId}>
                  <td><a href={`/deployments/${deployment.publicId}`} onClick={followLink}>{deployment.name}</a></td>
                  <td>{deployment.slug}</td>
                  <td><StatusBadge value={deployment.status} /></td>
                  <td>{deployment.currentVersion ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {Number(rows.total) > rows.items.length ? <p className="muted">Showing {rows.items.length} of {rows.total}.</p> : null}
        </div>
      ) : null}
    </section>
  )
}
