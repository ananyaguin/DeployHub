export function Field({ label, hint, ...props }) {
  const id = props.id || props.name
  return (
    <label className="field" htmlFor={id}>
      <span>{label}</span>
      <input id={id} {...props} />
      {hint ? <small>{hint}</small> : null}
    </label>
  )
}

export function Alert({ tone = 'error', children }) {
  if (!children) return null
  return (
    <p className={`alert alert-${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      {children}
    </p>
  )
}
