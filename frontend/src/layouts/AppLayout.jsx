import { BrandMark } from '../components/BrandMark'

export function AppLayout({ children }) {
  return (
    <div className="shell">
      <header className="topbar">
        <BrandMark />
      </header>
      <main>{children}</main>
    </div>
  )
}
