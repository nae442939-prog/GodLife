import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { messageApi } from '../api/client.js'
import { Avatar } from '../components/UserMenu.jsx'

const POLL_MS = 10_000

/**
 * 메시지 (대화 목록). 최근 대화부터, 안 읽은 메시지 수 표시. 10초마다 새로 불러온다.
 * [대화] = 바로 대화하는 사이와 내가 보낸 요청, [요청] = 받은 메시지 요청 (수락 · 거절).
 */
export function MessagesPage() {
  const [state, setState] = useState({ loaded: false, items: [], error: '' })
  const [tab, setTab] = useState('chats')
  const [busyId, setBusyId] = useState(null)

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

  async function respond(partnerId, action) {
    setBusyId(partnerId)
    try {
      await messageApi[action](partnerId)
      setState((s) => ({
        ...s,
        error: '',
        items:
          action === 'accept'
            ? s.items.map((c) => (c.partner.id === partnerId ? { ...c, request: null } : c))
            : s.items.filter((c) => c.partner.id !== partnerId),
      }))
    } catch (err) {
      setState((s) => ({ ...s, error: err.message }))
    } finally {
      setBusyId(null)
    }
  }

  const requests = state.items.filter((c) => c.request === 'RECEIVED')
  const chats = state.items.filter((c) => c.request !== 'RECEIVED')
  const shown = tab === 'requests' ? requests : chats

  return (
    <div className="container page">
      <div className="ch-head cl-head">
        <div>
          <h1 className="page-title">메시지</h1>
          <p className="page-sub">친구나 같은 챌린지 참가자와 1:1로 이야기해요.</p>
        </div>
      </div>

      <div className="dm-body">
        <div className="cl-tabs" role="group" aria-label="메시지 종류">
          <button
            type="button"
            className={`cl-tab${tab === 'chats' ? ' is-active' : ''}`}
            aria-pressed={tab === 'chats'}
            onClick={() => setTab('chats')}
          >
            대화
          </button>
          <button
            type="button"
            className={`cl-tab${tab === 'requests' ? ' is-active' : ''}`}
            aria-pressed={tab === 'requests'}
            onClick={() => setTab('requests')}
          >
            요청
            {requests.length > 0 && <span className="dm-tab-count">{requests.length}</span>}
          </button>
        </div>
        {tab === 'requests' && requests.length > 0 && (
          <p className="dm-request-help">
            맞팔로우도, 같은 챌린지 참가자도 아닌 사람이 보낸 메시지예요. 수락하거나 답장하면 대화가 시작되고, 수락하기
            전에는 읽어도 상대에게 표시되지 않아요.
          </p>
        )}
        {state.error && <p className="form-error">{state.error}</p>}
        {!state.loaded ? (
          <p className="muted">불러오는 중…</p>
        ) : tab === 'requests' && shown.length === 0 ? (
          <div className="my-ch-empty">
            <p className="dm-empty-only">받은 메시지 요청이 없어요.</p>
          </div>
        ) : shown.length === 0 ? (
          <div className="my-ch-empty">
            <p>
              아직 대화가 없어요.
              <br />
              랭킹이나 챌린지 참가자에서 프로필을 눌러 메시지를 보내 보세요.
            </p>
            <Link to="/rankings?tab=users" className="btn btn-dark">
              친구 찾으러 가기
            </Link>
          </div>
        ) : (
          <ul className="dm-list">
            {shown.map((c) => (
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
                      <strong>
                        {c.partner.nickname}
                        {c.request === 'SENT' && <small className="dm-conv-tag">요청 보냄</small>}
                      </strong>
                      <time dateTime={c.lastAt}>{whenText(c.lastAt)}</time>
                    </span>
                    <span className="dm-conv-last">
                      {c.lastMine && '나: '}
                      {c.lastContent}
                    </span>
                  </span>
                  {c.request !== 'RECEIVED' && c.unread > 0 && (
                    <span className="dm-badge">{c.unread > 99 ? '99+' : c.unread}</span>
                  )}
                </Link>
                {c.request === 'RECEIVED' && (
                  <span className="dm-conv-actions">
                    <button
                      type="button"
                      className="dm-accept"
                      disabled={busyId === c.partner.id}
                      onClick={() => respond(c.partner.id, 'accept')}
                    >
                      수락
                    </button>
                    <button
                      type="button"
                      className="dm-decline"
                      disabled={busyId === c.partner.id}
                      onClick={() => respond(c.partner.id, 'decline')}
                    >
                      거절
                    </button>
                  </span>
                )}
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
