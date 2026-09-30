import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { messageApi } from '../api/client.js'
import { Avatar } from '../components/UserMenu.jsx'

const POLL_MS = 10_000

/** 메시지 (대화 목록). 최근 대화부터, 안 읽은 메시지 수 표시. 10초마다 새로 불러온다. */
export function MessagesPage() {
  const [state, setState] = useState({ loaded: false, items: [], error: '' })

  useEffect(() => {
    let cancelled = false
    function load() {
      messageApi
        .conversations()
        .then((items) => !cancelled && setState({ loaded: true, items, error: '' }))
        .catch((err) => !cancelled && setState((s) => ({ ...s, loaded: true, error: err.message })))
    }
    load()
    const timer = setInterval(() => document.visibilityState === 'visible' && load(), POLL_MS)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [])

  return (
    <div className="container page">
      <div className="ch-head cl-head">
        <div>
          <h1 className="page-title">메시지</h1>
          <p className="page-sub">서로 팔로우한 친구와 1:1로 이야기해요.</p>
        </div>
      </div>

      <div className="dm-body">
        {state.error && <p className="form-error">{state.error}</p>}
        {!state.loaded ? (
          <p className="muted">불러오는 중…</p>
        ) : state.items.length === 0 ? (
          <div className="my-ch-empty">
            <p>
              아직 대화가 없어요.
              <br />
              랭킹이나 챌린지 참가자에서 친구를 찾아 서로 팔로우하면 메시지를 보낼 수 있어요.
            </p>
            <Link to="/rankings?tab=users" className="btn btn-dark">
              친구 찾으러 가기
            </Link>
          </div>
        ) : (
          <ul className="dm-list">
            {state.items.map((c) => (
              <li key={c.partner.id}>
                <Link to={`/messages/${c.partner.id}`} className={`dm-conv${c.unread > 0 ? ' is-unread' : ''}`}>
                  {c.partner.profileImageUrl ? (
                    <Avatar src={c.partner.profileImageUrl} size={44} />
                  ) : (
                    <span className="dm-initial" aria-hidden="true">
                      {c.partner.nickname.slice(0, 1)}
                    </span>
                  )}
                  <span className="dm-conv-text">
                    <span className="dm-conv-top">
                      <strong>{c.partner.nickname}</strong>
                      <time dateTime={c.lastAt}>{whenText(c.lastAt)}</time>
                    </span>
                    <span className="dm-conv-last">
                      {c.lastMine && '나: '}
                      {c.lastContent}
                    </span>
                  </span>
                  {c.unread > 0 && <span className="dm-badge">{c.unread > 99 ? '99+' : c.unread}</span>}
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}

/** 오늘이면 시각, 아니면 월.일 */
function whenText(iso) {
  const d = new Date(iso)
  const now = new Date()
  const p = (n) => String(n).padStart(2, '0')
  if (d.toDateString() === now.toDateString()) return `${p(d.getHours())}:${p(d.getMinutes())}`
  return `${d.getMonth() + 1}.${p(d.getDate())}`
}
