import { useEffect, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { adminApi } from '../api/client.js'
import { useAuth } from '../auth/useAuth.js'
import { AdminNav } from '../components/AdminNav.jsx'

const KIND = { DEVICE: '같은 기기', IP: '같은 IP' }
const STATUS = { ACTIVE: '', SUSPENDED: '정지', WITHDRAWN: '탈퇴' }

/**
 * 관리자 화면: 다중 계정 의심 (/admin/devices). 관리자(role = ADMIN)만 들어올 수 있다.
 * 가입 · 로그인 때 남은 기기 지문과 IP 를 보고, 같은 기기를 쓴 계정이 3개 이상이거나 같은 IP 를 쓴 계정이 5개 이상인 묶음을 보여 준다.
 * 같은 기종끼리 지문이 겹치거나 가족 · 학교가 IP 를 같이 쓸 수 있어서 여기서는 보여 주기만 한다 (계정을 막지 않는다).
 */
export function AdminDevicesPage() {
  const { user } = useAuth()
  const [state, setState] = useState({ loaded: false, groups: [], error: '' })
  const isAdmin = user.role === 'ADMIN'

  useEffect(() => {
    if (!isAdmin) return
    let cancelled = false
    adminApi
      .devices()
      .then((groups) => !cancelled && setState({ loaded: true, groups, error: '' }))
      .catch((err) => !cancelled && setState({ loaded: true, groups: [], error: err.message }))
    return () => {
      cancelled = true
    }
  }, [isAdmin])

  if (!isAdmin) return <Navigate to="/" replace />

  return (
    <div className="container page">
      <div className="ch-head cl-head">
        <div>
          <h1 className="page-title">관리자 · 다중 계정 의심</h1>
          <p className="page-sub">
            같은 기기를 쓴 계정이 3개 이상이거나 같은 IP 를 쓴 계정이 5개 이상인 묶음이에요. 표시만 하고 계정을 막지는 않아요.
          </p>
        </div>
      </div>
      <AdminNav />

      {state.error && <p className="form-error">{state.error}</p>}
      {!state.loaded ? (
        <p className="muted">불러오는 중…</p>
      ) : state.groups.length === 0 ? (
        !state.error && <p className="muted">의심되는 묶음이 없어요.</p>
      ) : (
        <ul className="inquiry-list adm-list">
          {state.groups.map((g) => (
            <li key={`${g.kind}:${g.key}`}>
              <div className="inquiry-head">
                <span className="inquiry-status">{KIND[g.kind]}</span>
                <strong>계정 {g.accounts}개</strong>
                <small>
                  {g.kind === 'DEVICE' ? `기기 ${g.key}…` : g.key} · 마지막 접속 {g.lastSeenAt.slice(0, 10)}
                </small>
              </div>
              <ul className="adm-device-members">
                {g.members.map((m) => (
                  <li key={m.userId}>
                    <Link to={`/users/${m.userId}`}>{m.nickname}</Link>
                    <span className="muted">{m.email}</span>
                    <span className="muted">가입 {m.joinedAt.slice(0, 10)}</span>
                    <span className="muted">접속 {m.lastSeenAt.slice(0, 10)}</span>
                    {STATUS[m.status] && <span className="inquiry-status is-rejected">{STATUS[m.status]}</span>}
                  </li>
                ))}
              </ul>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
