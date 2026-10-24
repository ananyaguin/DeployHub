import logo from '../assets/logo.svg'

export function BrandMark() {
  return (
    <span className="brand">
      <img src={logo} alt="" width="32" height="32" />
      <span>CloudFlow</span>
    </span>
  )
}
