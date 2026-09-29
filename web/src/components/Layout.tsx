import { NavLink, Outlet } from 'react-router-dom'
import { Icon } from './Icon'
import { UpdateToast } from './UpdateToast'

const NAV_ITEMS = [
  { to: '/photos', label: 'Photos', icon: 'photos' as const },
  { to: '/albums', label: 'Albums', icon: 'albums' as const },
  { to: '/favourites', label: 'Favourites', icon: 'favourites' as const },
  { to: '/search', label: 'Search', icon: 'search' as const },
  { to: '/locked', label: 'Locked', icon: 'locked' as const },
  { to: '/settings', label: 'Settings', icon: 'settings' as const },
]

export function Layout(): React.ReactElement {
  return (
    <div className="app-shell">
      <nav className="left-rail" aria-label="Main navigation">
        <img src="logo.svg" alt="Prism" width={40} height={40} style={{ marginBottom: 12 }} />
        {NAV_ITEMS.map((item) => (
          <NavLink key={item.to} to={item.to} className={({ isActive }) => `rail-link${isActive ? ' active' : ''}`} aria-label={item.label}>
            <Icon name={item.icon} />
            <span>{item.label}</span>
          </NavLink>
        ))}
      </nav>
      <main className="app-main">
        <Outlet />
      </main>
      <nav className="bottom-tabs" aria-label="Main navigation">
        {NAV_ITEMS.map((item) => (
          <NavLink key={item.to} to={item.to} className={({ isActive }) => `tab-link${isActive ? ' active' : ''}`} aria-label={item.label}>
            <Icon name={item.icon} size={22} />
            <span>{item.label}</span>
          </NavLink>
        ))}
      </nav>
      <UpdateToast />
    </div>
  )
}
