import { useCallback, useEffect, useMemo, useState } from 'react'
import { authApi, refreshAccessToken, userApi } from '../api/client.js'
import { AuthContext } from './AuthContext.js'

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [status, setStatus] = useState('loading')

  // 앱이 열릴 때: 쿠키에 리프레시 토큰이 있으면 조용히 로그인 상태를 복구한다.
  useEffect(() => {
    let cancelled = false
    refreshAccessToken()
      .then(() => userApi.me())
      .then((me) => {
        if (!cancelled) {
          setUser(me)
          setStatus('authed')
        }
      })
      .catch(() => {
        if (!cancelled) setStatus('anon')
      })
    return () => {
      cancelled = true
    }
  }, [])

  const login = useCallback(async (credentials) => {
    await authApi.login(credentials)
    const me = await userApi.me()
    setUser(me)
    setStatus('authed')
  }, [])

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } finally {
      setUser(null)
      setStatus('anon')
    }
  }, [])

  const value = useMemo(
    () => ({ user, status, login, signup: authApi.signup, logout }),
    [user, status, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
