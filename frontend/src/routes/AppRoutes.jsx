import { useEffect, useState } from 'react'
import { clearSession, readSession } from '../api/session'
import { AppLayout } from '../layouts/AppLayout'
import { LoginPage, RegisterPage, ResetPasswordPage, VerifyEmailPage } from '../pages/AuthPages'
import { DashboardPage } from '../pages/DashboardPage'
import { DeploymentPage } from '../pages/DeploymentPage'
import { DeploymentsPage } from '../pages/DeploymentsPage'
import { navigate, useLocation } from './navigate'

export function AppRoutes() {
  const location = useLocation()
  const path = location.split('?')[0]
  const [session, setSession] = useState(readSession)

  useEffect(() => {
    const sync = () => setSession(readSession())
    window.addEventListener('deployhub-session', sync)
    return () => window.removeEventListener('deployhub-session', sync)
  }, [])

  const page = publicPage(path) || (session ? privatePage(path) : <LoginPage />)

  return (
    <AppLayout session={session} current={path} onLogout={() => { clearSession(); navigate('/') }}>
      {page}
    </AppLayout>
  )
}

function publicPage(path) {
  if (path === '/register') return <RegisterPage />
  if (path === '/verify-email') return <VerifyEmailPage />
  if (path === '/reset-password') return <ResetPasswordPage />
  return null
}

function privatePage(path) {
  if (path === '/deployments') return <DeploymentsPage />
  if (path.startsWith('/deployments/')) {
    const publicId = decodeURIComponent(path.slice('/deployments/'.length))
    return <DeploymentPage key={publicId} publicId={publicId} />
  }
  return <DashboardPage />
}
