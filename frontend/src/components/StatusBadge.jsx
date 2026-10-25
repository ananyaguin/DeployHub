export function StatusBadge({ value }) {
  const label = value || 'UNKNOWN'
  return <span className={`badge badge-${label}`}>{label}</span>
}
