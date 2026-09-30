import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { messageApi } from '../api/client.js'
import { Avatar } from '../components/UserMenu.jsx'

const POLL_MS = 3000
const PAGE = 50
const MAX = 500
// 전화번호·링크가 보이면 보내기 전에 한 번 더 확인하게 한다 (외부로 끌어내는 사기 조심)
const RISKY = /(01[016789][-\s.]?\d{3,4}[-\s.]?\d{4})|(https?:\/\/|www\.)/i

const REASON_TEXT = {
  NOT_FOLLOWING: '메시지는 서로 팔로우한 친구끼리만 보낼 수 있어요. 먼저 팔로우해 보세요.',
  NOT_FOLLOWED_BACK: '상대도 나를 팔로우하면 메시지를 보낼 수 있어요.',
  BLOCKED: '메시지를 보낼 수 없는 회원이에요.',
}

/**
 * 1:1 대화방 (/messages/:userId). 3초마다 새 메시지를 불러오고 항상 맨 아래로 내린다.
 * 맞팔로우가 아니면 입력창 대신 이유를 보여 준다 (지난 대화는 볼 수 있다).
 */
export function MessageRoomPage() {
  const { userId } = useParams()
  const [room, setRoom] = useState({ key: null, data: null, error: '' })
  const [messages, setMessages] = useState([])
  const [hasOlder, setHasOlder] = useState(false)
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const listRef = useRef(null)
  const stickBottom = useRef(true)
  // 3초마다 '이것보다 새 메시지'를 물어볼 기준
  const lastId = useRef(0)

  useEffect(() => {
    lastId.current = messages[messages.length - 1]?.id ?? 0
  }, [messages])

  useEffect(() => {
    let cancelled = false
    Promise.all([messageApi.room(userId), messageApi.list(userId)])
      .then(([data, list]) => {
        if (cancelled) return
        setRoom({ key: userId, data, error: '' })
        setMessages(list)
        setHasOlder(list.length === PAGE)
        stickBottom.current = true
      })
      .catch((err) => !cancelled && setRoom({ key: userId, data: null, error: err.message }))
    return () => {
      cancelled = true
    }
  }, [userId])

  // 3초마다 새 메시지
  useEffect(() => {
    if (room.key !== userId || !room.data) return
    let cancelled = false
    const timer = setInterval(() => {
      if (document.visibilityState !== 'visible') return
      messageApi
        .list(userId, { after: lastId.current })
        .then((fresh) => {
          if (cancelled || fresh.length === 0) return
          stickBottom.current = true
          setMessages((cur) => {
            const seen = new Set(cur.map((m) => m.id))
            return [...cur, ...fresh.filter((m) => !seen.has(m.id))]
          })
        })
        .catch(() => {}) // 잠깐 끊겨도 다음 차례에 다시
    }, POLL_MS)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [room.key, room.data, userId])

  // 새 메시지가 오면 무조건 맨 아래로 (이전 메시지 더 보기만 예외)
  useLayoutEffect(() => {
    if (stickBottom.current && listRef.current) {
      listRef.current.scrollTop = listRef.current.scrollHeight
    }
  }, [messages])

  const loadOlder = useCallback(async () => {
    const first = messages[0]?.id
    if (!first) return
    const older = await messageApi.list(userId, { before: first })
    stickBottom.current = false
    setHasOlder(older.length === PAGE)
    setMessages((cur) => [...older, ...cur])
  }, [messages, userId])

  async function send(e) {
    e.preventDefault()
    const content = text.trim()
    if (!content || busy) return
    setBusy(true)
    setError('')
    try {
      const sent = await messageApi.send(userId, content)
      setText('')
      stickBottom.current = true
      setMessages((cur) => (cur.some((m) => m.id === sent.id) ? cur : [...cur, sent]))
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  if (room.key !== userId) return <p className="loading">불러오는 중…</p>
  if (room.error) {
    return (
      <div className="container page">
        <p className="form-error">{room.error}</p>
        <Link to="/messages">메시지 목록으로</Link>
      </div>
    )
  }

  const { partner, canSend, reason } = room.data
  const lastMine = [...messages].reverse().find((m) => m.mine)

  return (
    <div className="container page dm-room-page">
      <div className="dm-room">
        <header className="dm-room-head">
          <Link to="/messages" className="dm-back" aria-label="메시지 목록으로">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
              <path d="M15 5l-7 7 7 7" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </Link>
          <Link to={`/users/${partner.id}`} className="dm-partner">
            {partner.profileImageUrl ? (
              <Avatar src={partner.profileImageUrl} size={34} />
            ) : (
              <span className="dm-initial is-small" aria-hidden="true">
                {partner.nickname.slice(0, 1)}
              </span>
            )}
            <strong>{partner.nickname}</strong>
          </Link>
        </header>

        <div className="dm-messages" ref={listRef}>
          {hasOlder && (
            <button type="button" className="dm-older" onClick={loadOlder}>
              이전 메시지 더 보기
            </button>
          )}
          {messages.length === 0 && (
            <p className="dm-empty">
              {canSend ? `${partner.nickname}님에게 첫 메시지를 보내 보세요. 같이 할 챌린지 얘기도 좋아요!` : ''}
            </p>
          )}
          {messages.map((m, i) => {
            const prev = messages[i - 1]
            const newDay = !prev || prev.createdAt.slice(0, 10) !== m.createdAt.slice(0, 10)
            return (
              <div key={m.id}>
                {newDay && <p className="dm-day">{dayText(m.createdAt)}</p>}
                <div className={`dm-msg${m.mine ? ' is-mine' : ''}`}>
                  <p className="dm-bubble">{m.content}</p>
                  <span className="dm-meta">
                    {m.mine && m.id === lastMine?.id && m.read && <span className="dm-read">읽음</span>}
                    <time dateTime={m.createdAt}>{m.createdAt.slice(11, 16)}</time>
                  </span>
                </div>
              </div>
            )
          })}
        </div>

        {canSend ? (
          <form className="dm-input" onSubmit={send}>
            {RISKY.test(text) && (
              <p className="dm-warn">전화번호나 링크가 있어요. 모르는 사람에게 개인정보를 보내지 않도록 조심하세요.</p>
            )}
            <div className="dm-input-row">
              <textarea
                value={text}
                maxLength={MAX}
                rows={1}
                placeholder="메시지를 입력하세요"
                onChange={(e) => setText(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) send(e)
                }}
              />
              <button type="submit" className="btn btn-dark" disabled={busy || !text.trim()}>
                보내기
              </button>
            </div>
            {error && <p className="form-error">{error}</p>}
          </form>
        ) : (
          <div className="dm-locked">
            <p>{REASON_TEXT[reason] ?? '지금은 메시지를 보낼 수 없어요.'}</p>
            {reason !== 'BLOCKED' && (
              <Link to={`/users/${partner.id}`} className="btn btn-outline">
                {partner.nickname}님 프로필 보기
              </Link>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

function dayText(iso) {
  const d = new Date(iso)
  const days = ['일', '월', '화', '수', '목', '금', '토']
  return `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일 ${days[d.getDay()]}요일`
}
