import { apiUrl } from '../api/client'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { apiBaseUrl } from '../utils/config'

export function HomePage() {
  useDocumentTitle('CloudFlow')

  return (
    <section className="panel">
      <p className="eyebrow">Control plane</p>
      <h1>Deployment and job orchestration</h1>
      <p className="lede">
        CloudFlow is the workspace shell for deployments and the jobs that run them.
      </p>
      <dl className="facts">
        <div>
          <dt>API</dt>
          <dd>{apiBaseUrl}</dd>
        </div>
        <div>
          <dt>Health</dt>
          <dd>{apiUrl('/api/health')}</dd>
        </div>
      </dl>
    </section>
  )
}
