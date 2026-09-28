import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/useAuth.js'

/** 로그인해야 볼 수 있는 화면을 감싼다. 로그인 후 원래 가려던 곳으로 돌아오도록 경로를 넘긴다. */
export function RequireAuth() {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') return <p className="loading">불러오는 중…</p>
  if (status === 'anon') return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <Outlet />
}
