import { useState } from 'react'
import { api, errorText } from '../api/client'
import { saveSession } from '../api/session'
import { Alert, Field } from '../components/Field'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { followLink, navigate } from '../routes/navigate'

const SLUG = /^[a-z0-9]+(?:-[a-z0-9]+)*$/

function queryToken() {
  return new URLSearchParams(window.location.search).get('token') || ''
}

export function LoginPage() {
  useDocumentTitle('Sign in · DeployHub')
  const [form, setForm] = useState({ tenantSlug: '', email: '', password: '' })
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)

  function update(event) {
    setForm((current) => ({ ...current, [event.target.name]: event.target.value }))
  }

  async function submit(event) {
    event.preventDefault()
    if (pending) return
    if (!SLUG.test(form.tenantSlug) || !form.email || form.password.length < 1 || form.password.length > 72) {
      setError('Enter the tenant slug, email, and password for that account.')
      return
    }
    setPending(true)
    setError('')
    try {
      const login = await api('/api/v1/auth/login', { method: 'POST', auth: false, body: form })
      saveSession(login, { email: form.email, tenantSlug: form.tenantSlug })
      const path = window.location.pathname
      if (path === '/' || path === '/register' || path === '/verify-email' || path === '/reset-password') navigate('/')
    } catch (caught) {
      setError(caught.status === 403
        ? 'Email verification is required before this account can sign in.'
        : errorText(caught))
    } finally {
      setPending(false)
    }
  }

  return (
    <section className="panel narrow">
      <p className="eyebrow">DeployHub</p>
      <h1>Sign in</h1>
      <p className="lede">Use the tenant slug from registration. Access is decided by the API, not by a tenant id in the browser.</p>
      <form className="form" onSubmit={submit}>
        <Alert>{error}</Alert>
        <Field label="Tenant slug" name="tenantSlug" value={form.tenantSlug} onChange={update} autoComplete="organization" required />
        <Field label="Email" name="email" type="email" value={form.email} onChange={update} autoComplete="username" required />
        <Field label="Password" name="password" type="password" value={form.password} onChange={update} autoComplete="current-password" required />
        <button type="submit" disabled={pending}>{pending ? 'Signing in…' : 'Sign in'}</button>
      </form>
      <p className="links">
        <a href="/register" onClick={followLink}>Create an account</a>
        <a href="/reset-password" onClick={followLink}>Reset password</a>
      </p>
    </section>
  )
}

export function RegisterPage() {
  useDocumentTitle('Create account · DeployHub')
  const [form, setForm] = useState({ tenantName: '', tenantSlug: '', displayName: '', email: '', password: '' })
  const [error, setError] = useState('')
  const [done, setDone] = useState(false)
  const [pending, setPending] = useState(false)

  function update(event) {
    setForm((current) => ({ ...current, [event.target.name]: event.target.value }))
  }

  async function submit(event) {
    event.preventDefault()
    if (pending) return
    if (!form.tenantName.trim() || !SLUG.test(form.tenantSlug) || !form.displayName.trim()
      || !form.email || form.password.length < 8 || form.password.length > 72) {
      setError('Use a lowercase tenant slug, a display name, an email, and a password of 8 to 72 characters.')
      return
    }
    setPending(true)
    setError('')
    try {
      await api('/api/v1/auth/register', { method: 'POST', auth: false, body: form })
      setDone(true)
    } catch (caught) {
      setError(errorText(caught))
    } finally {
      setPending(false)
    }
  }

  return (
    <section className="panel narrow">
      <p className="eyebrow">DeployHub</p>
      <h1>Create an account</h1>
      {done ? (
        <>
          <Alert tone="ok">Account created. Verify the email before signing in. If mail is not configured, the API reports that the message could not be sent.</Alert>
          <p className="links"><a href="/" onClick={followLink}>Back to sign in</a></p>
        </>
      ) : (
        <form className="form" onSubmit={submit}>
          <Alert>{error}</Alert>
          <Field label="Tenant name" name="tenantName" value={form.tenantName} onChange={update} required />
          <Field label="Tenant slug" name="tenantSlug" hint="Lowercase letters, numbers, and hyphens." value={form.tenantSlug} onChange={update} required />
          <Field label="Your name" name="displayName" value={form.displayName} onChange={update} autoComplete="name" required />
          <Field label="Email" name="email" type="email" value={form.email} onChange={update} autoComplete="email" required />
          <Field label="Password" name="password" type="password" value={form.password} onChange={update} autoComplete="new-password" minLength={8} maxLength={72} required />
          <button type="submit" disabled={pending}>{pending ? 'Creating…' : 'Create account'}</button>
        </form>
      )}
    </section>
  )
}

export function VerifyEmailPage() {
  useDocumentTitle('Verify email · DeployHub')
  const [token, setToken] = useState(queryToken)
  const [error, setError] = useState('')
  const [done, setDone] = useState(false)
  const [pending, setPending] = useState(false)

  async function submit(event) {
    event.preventDefault()
    if (pending) return
    if (token.trim().length < 32) {
      setError('Paste the verification token from the email link.')
      return
    }
    setPending(true)
    setError('')
    try {
      await api('/api/v1/auth/verify-email', { method: 'POST', auth: false, body: { token: token.trim() } })
      setDone(true)
    } catch (caught) {
      setError(errorText(caught))
    } finally {
      setPending(false)
    }
  }

  return (
    <section className="panel narrow">
      <p className="eyebrow">DeployHub</p>
      <h1>Verify email</h1>
      {done ? (
        <>
          <Alert tone="ok">Email verified. You can sign in.</Alert>
          <p className="links"><a href="/" onClick={followLink}>Sign in</a></p>
        </>
      ) : (
        <form className="form" onSubmit={submit}>
          <Alert>{error}</Alert>
          <Field label="Verification token" name="token" value={token} onChange={(event) => setToken(event.target.value)} required />
          <button type="submit" disabled={pending}>{pending ? 'Verifying…' : 'Verify email'}</button>
        </form>
      )}
    </section>
  )
}

export function ResetPasswordPage() {
  useDocumentTitle('Reset password · DeployHub')
  const initialToken = queryToken()
  const [mode, setMode] = useState(initialToken ? 'confirm' : 'request')
  const [form, setForm] = useState({ tenantSlug: '', email: '', token: initialToken, password: '' })
  const [error, setError] = useState('')
  const [done, setDone] = useState('')
  const [pending, setPending] = useState(false)

  function update(event) {
    setForm((current) => ({ ...current, [event.target.name]: event.target.value }))
  }

  async function submit(event) {
    event.preventDefault()
    if (pending) return
    setPending(true)
    setError('')
    try {
      if (mode === 'request') {
        if (!SLUG.test(form.tenantSlug) || !form.email) {
          setError('Enter the tenant slug and email.')
          return
        }
        await api('/api/v1/auth/password-reset', { method: 'POST', auth: false, body: { tenantSlug: form.tenantSlug, email: form.email } })
        setDone('If that account exists, a reset message was sent.')
      } else {
        if (form.token.trim().length < 32 || form.password.length < 8 || form.password.length > 72) {
          setError('Use the reset token and a new password of 8 to 72 characters.')
          return
        }
        await api('/api/v1/auth/password-reset/confirm', {
          method: 'POST',
          auth: false,
          body: { token: form.token.trim(), password: form.password },
        })
        setDone('Password updated. Sign in with the new password.')
      }
    } catch (caught) {
      setError(errorText(caught))
    } finally {
      setPending(false)
    }
  }

  return (
    <section className="panel narrow">
      <p className="eyebrow">DeployHub</p>
      <h1>Reset password</h1>
      {done ? <Alert tone="ok">{done}</Alert> : (
        <form className="form" onSubmit={submit}>
          <Alert>{error}</Alert>
          {mode === 'request' ? (
            <>
              <Field label="Tenant slug" name="tenantSlug" value={form.tenantSlug} onChange={update} required />
              <Field label="Email" name="email" type="email" value={form.email} onChange={update} required />
            </>
          ) : (
            <>
              <Field label="Reset token" name="token" value={form.token} onChange={update} required />
              <Field label="New password" name="password" type="password" value={form.password} onChange={update} autoComplete="new-password" minLength={8} maxLength={72} required />
            </>
          )}
          <button type="submit" disabled={pending}>{pending ? 'Sending…' : mode === 'request' ? 'Send reset link' : 'Update password'}</button>
        </form>
      )}
      <p className="links">
        <button type="button" className="ghost" onClick={() => { setMode(mode === 'request' ? 'confirm' : 'request'); setDone(''); setError('') }}>
          {mode === 'request' ? 'I have a reset token' : 'Request a new link'}
        </button>
        <a href="/" onClick={followLink}>Sign in</a>
      </p>
    </section>
  )
}
