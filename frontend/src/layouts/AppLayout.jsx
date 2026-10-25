import { BrandMark } from '../components/BrandMark'
import { followLink } from '../routes/navigate'

export function AppLayout({ children, session, current, onLogout }) {
  return (
    <div className="shell">
      <header className="topbar">
        <a className="brand-link" href="/" onClick={followLink}>
          <BrandMark />
        </a>
        {session ? (
          <nav className="nav" aria-label="Primary">
            <a href="/" aria-current={current === '/' ? 'page' : undefined} onClick={followLink}>Dashboard</a>
            <a href="/deployments" aria-current={current.startsWith('/deployments') ? 'page' : undefined} onClick={followLink}>Deployments</a>
            <span className="who">{session.displayName || session.email} · {session.tenantSlug}</span>
            <button type="button" className="ghost" onClick={onLogout}>Log out</button>
          </nav>
        ) : null}
      </header>
      <main>{children}</main>
    </div>
  )
}
