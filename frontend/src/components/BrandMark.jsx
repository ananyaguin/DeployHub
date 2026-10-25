import logo from '../assets/logo.svg'

export function BrandMark() {
  return (
    <span className="brand">
      <img src={logo} alt="" width="28" height="28" />
      <span>DeployHub</span>
    </span>
  )
}
