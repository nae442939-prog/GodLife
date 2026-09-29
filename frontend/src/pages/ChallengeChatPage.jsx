import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { challengeApi, chatApi } from '../api/client.js'
import { Avatar } from '../components/UserMenu.jsx'

const POLL_MS = 3000
const PAGE_SIZE = 50 // 서버 ChatService.PAGE_SIZE 와 같게
const MAX_LENGTH = 500
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

/** '2026-09-29T16:20:11.123' → '16:20' */
const timeOf = (iso) => iso.slice(11, 16)
/** '2026-09-29T…' → '2026년 9월 29일 (화)' */
function dayLabel(iso) {
  const [y, m, d] = iso.slice(0, 10).split('-').map(Number)
  return `${y}년 ${m}월 ${d}일 (${WEEKDAYS[new Date(y, m - 1, d).getDay()]})`
}

/** 새로 받은 메시지를 id 순서대로 합친다. (폴링과 내가 보낸 응답이 겹쳐도 한 번만) */
function merge(current, incoming) {
  const seen = new Set(current.map((m) => m.id))
  const added = incoming.filter((m) => !seen.has(m.id))
  if (added.length === 0) return current
  return [...current, ...added].sort((a, b) => a.id - b.id)
}

/**
 * 챌린지 오픈채팅 (1단계: 3초 폴링). 개설자·참가자만 들어올 수 있다.
 * 맨 아래를 보고 있을 때만 새 메시지에 맞춰 자동으로 내려간다. (위로 올려 읽는 중이면 그대로 둔다)
 */
export function ChallengeChatPage() {
  const { id } = useParams()
  const [challenge, setChallenge] = useState(null)
  const [messages, setMessages] = useState([])
  const [loaded, setLoaded] = useState(false)
  const [error, setError] = useState('')
  const [hasOlder, setHasOlder] = useState(false)
  const [loadingOlder, setLoadingOlder] = useState(false)
  const [text, setText] = useState('')
  const [sending, setSending] = useState(false)
  const [sendError, setSendError] = useState('')

  const listRef = useRef(null)
  const stickToBottom = useRef(true)
  const keepOffset = useRef(null) // 이전 메시지를 위에 붙일 때 보던 위치를 지키기 위한 값
  const lastId = messages.length ? messages[messages.length - 1].id : 0

  // 처음: 챌린지 제목 + 최근 메시지
  useEffect(() => {
    let cancelled = false
    Promise.all([challengeApi.get(id), chatApi.list(id)])
      .then(([c, list]) => {
        if (cancelled) return
        setChallenge(c)
        setMessages(list)
        setHasOlder(list.length === PAGE_SIZE)
        setLoaded(true)
      })
      .catch((err) => {
        if (cancelled) return
        setError(err.status === 404 ? '챌린지에 참여한 사람만 오픈채팅을 볼 수 있어요.' : err.message)
        setLoaded(true)
      })
    return () => {
      cancelled = true
    }
  }, [id])

  // 3초마다 새 메시지 (탭이 숨겨져 있으면 쉰다)
  const lastIdRef = useRef(0)
  useEffect(() => {
    lastIdRef.current = lastId
  }, [lastId])

  const poll = useCallback(async () => {
    if (document.hidden) return
    try {
      const fresh = await chatApi.list(id, { after: lastIdRef.current })
      if (fresh.length) setMessages((cur) => merge(cur, fresh))
    } catch {
      // 잠깐 끊겨도 다음 주기에 다시 시도한다.
    }
  }, [id])

  useEffect(() => {
    if (!loaded || error) return
    const timer = setInterval(poll, POLL_MS)
    return () => clearInterval(timer)
  }, [loaded, error, poll])

  // 메시지가 바뀐 뒤 스크롤 위치 맞추기
  useLayoutEffect(() => {
    const el = listRef.current
    if (!el) return
    if (keepOffset.current != null) {
      el.scrollTop = el.scrollHeight - keepOffset.current
      keepOffset.current = null
    } else if (stickToBottom.current) {
      el.scrollTop = el.scrollHeight
    }
  }, [messages])

  function onScroll() {
    const el = listRef.current
    stickToBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80
  }

  async function loadOlder() {
    if (!messages.length) return
    setLoadingOlder(true)
    try {
      const older = await chatApi.list(id, { before: messages[0].id })
      keepOffset.current = listRef.current.scrollHeight - listRef.current.scrollTop
      setMessages((cur) => merge(older, cur))
      setHasOlder(older.length === PAGE_SIZE)
    } catch (err) {
      setSendError(err.message)
    } finally {
      setLoadingOlder(false)
    }
  }

  async function send(e) {
    e?.preventDefault()
    const content = text.trim()
    if (!content || sending) return
    setSending(true)
    setSendError('')
    try {
      const saved = await chatApi.send(id, content)
      stickToBottom.current = true
      setMessages((cur) => merge(cur, [saved]))
      setText('')
    } catch (err) {
      setSendError(err.fieldErrors?.content ?? err.message)
    } finally {
      setSending(false)
    }
  }

  function onKeyDown(e) {
    // Enter = 보내기, Shift+Enter = 줄바꿈. 한글 조합 중 Enter 는 글자 확정이라 보내지 않는다.
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      send()
    }
  }

  if (!loaded) return <p className="loading">불러오는 중…</p>

  return (
    <div className="container page detail-page">
      <Link to={`/challenges/${id}`} className="back-button">
        <svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" aria-hidden="true">
          <path d="M6.5 1.5L2 5l4.5 3.5" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        챌린지 정보
      </Link>

      {error ? (
        <div className="invite-missing card">
          <h1>오픈채팅에 들어갈 수 없어요</h1>
          <p>{error}</p>
          <Link to={`/challenges/${id}`} className="btn btn-dark-outline">
            챌린지 보러 가기
          </Link>
        </div>
      ) : (
        <section className="card chat-card" aria-label="오픈채팅">
          <header className="chat-head">
            <div>
              <h1>{challenge.title}</h1>
              <p>참가자 {challenge.participantCount}명 · 서로 응원하고 인증 팁을 나눠요</p>
            </div>
          </header>

          <div className="chat-list" ref={listRef} onScroll={onScroll} aria-live="polite">
            {hasOlder && (
              <div className="chat-older">
                <button type="button" className="link-button is-muted" onClick={loadOlder} disabled={loadingOlder}>
                  {loadingOlder ? '불러오는 중…' : '이전 메시지 더 보기'}
                </button>
              </div>
            )}
            {messages.length === 0 && <p className="chat-empty">첫 메시지를 남겨 보세요. 오늘의 다짐도 좋아요!</p>}
            {messages.map((m, i) => {
              const prev = messages[i - 1]
              const newDay = !prev || prev.createdAt.slice(0, 10) !== m.createdAt.slice(0, 10)
              // 같은 사람이 이어서 보내면 이름·사진은 첫 메시지에만
              const showSender = !m.mine && (newDay || !prev || prev.mine || prev.senderNickname !== m.senderNickname)
              return (
                <div key={m.id}>
                  {newDay && <p className="chat-day">{dayLabel(m.createdAt)}</p>}
                  <div className={`chat-row ${m.mine ? 'is-mine' : ''} ${showSender ? 'has-sender' : ''}`}>
                    {!m.mine && (
                      <span className="chat-avatar">
                        {showSender && <Avatar src={m.senderProfileImageUrl} size={34} />}
                      </span>
                    )}
                    <div className="chat-body">
                      {showSender && <span className="chat-name">{m.senderNickname}</span>}
                      <div className="chat-bubble-line">
                        <p className="chat-bubble">{m.content}</p>
                        <time className="chat-time" dateTime={m.createdAt}>
                          {timeOf(m.createdAt)}
                        </time>
                      </div>
                    </div>
                  </div>
                </div>
              )
            })}
          </div>

          <form className="chat-input" onSubmit={send}>
            <textarea
              rows={2}
              value={text}
              maxLength={MAX_LENGTH}
              onChange={(e) => setText(e.target.value)}
              onKeyDown={onKeyDown}
              placeholder="메시지를 입력하세요 (Enter 보내기 · Shift+Enter 줄바꿈)"
              aria-label="메시지"
            />
            <div className="chat-input-side">
              <span className="chat-count">
                {text.length}/{MAX_LENGTH}
              </span>
              <button type="submit" className="btn btn-dark" disabled={sending || !text.trim()}>
                보내기
              </button>
            </div>
          </form>
          {sendError && <p className="form-error chat-error">{sendError}</p>}
        </section>
      )}
    </div>
  )
}
