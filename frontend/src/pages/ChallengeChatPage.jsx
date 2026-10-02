import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { blockApi, challengeApi, chatApi } from '../api/client.js'
import { ConfirmDialog, MessageMenu, NoticeBar, ReportAlerts, ReportDialog } from '../chat/ChatParts.jsx'
import { ChatImage } from '../chat/ChatImage.jsx'
import { Avatar } from '../components/UserMenu.jsx'

const POLL_MS = 3000
const ALERT_POLL_MS = 15000
const PAGE_SIZE = 50 // 서버 ChatService.PAGE_SIZE 와 같게
const MAX_LENGTH = 500
const PHOTO_TYPES = ['image/jpeg', 'image/png']
const MAX_PHOTO_BYTES = 5 * 1024 * 1024 // 서버 spring.servlet.multipart.max-file-size 와 같게
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
 *
 * 스크롤 규칙: 새 메시지가 오면 항상 맨 아래(마지막 메시지)로 간다.
 * - 메시지 렌더 직후 + 다음 프레임(이모지·글꼴이 늦게 그려져 높이가 바뀌는 경우)에 한 번 더 내린다
 * - 공지·신고 알림이 생겨 목록 높이가 바뀌거나 창 크기가 바뀌어도 ResizeObserver 로 다시 내린다
 * - 예외는 '이전 메시지 더 보기' 뿐: 보던 위치를 지키고, 그다음 새 메시지가 오면 다시 맨 아래로 간다
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
  const [photo, setPhoto] = useState(null) // { file, preview } 보내기 전 고른 사진
  const [sending, setSending] = useState(false)
  const [notice, setNotice] = useState('') // 화면 아래 잠깐 뜨는 안내 (신고 완료 등)
  const [sendError, setSendError] = useState('')
  const [alerts, setAlerts] = useState([])
  const [dialog, setDialog] = useState(null) // { kind: 'report'|'block'|'kick', message? , user? }
  const [busy, setBusy] = useState(false)
  const [dialogError, setDialogError] = useState('')

  const listRef = useRef(null)
  const contentRef = useRef(null)
  const noticeRef = useRef(null)
  const [noticeHeight, setNoticeHeight] = useState(0)
  const [scrolledUp, setScrolledUp] = useState(false)
  const pinBottom = useRef(true)
  const keepOffset = useRef(null)
  const prevLastId = useRef(0)
  const lastId = messages.length ? messages[messages.length - 1].id : 0
  const lastIdRef = useRef(0)
  const isHost = Boolean(challenge?.host)

  // ---------- 불러오기 ----------

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

  useEffect(() => {
    lastIdRef.current = lastId
  }, [lastId])

  const poll = useCallback(async () => {
    if (document.hidden) return
    try {
      const fresh = await chatApi.list(id, { after: lastIdRef.current })
      if (fresh.length) setMessages((cur) => merge(cur, fresh))
    } catch (err) {
      // 강퇴되면 404 → 채팅방에서 나가게 한다. 그 밖의 잠깐 끊김은 다음 주기에 다시 시도.
      if (err.status === 404) setError('방장이 이 챌린지에서 내보냈거나, 더 이상 참여 중이 아니에요.')
    }
  }, [id])

  useEffect(() => {
    if (!loaded || error) return
    const timer = setInterval(poll, POLL_MS)
    // 다른 탭에 있다 돌아오면 3초 기다리지 않고 바로 새 메시지를 가져온다
    document.addEventListener('visibilitychange', poll)
    return () => {
      clearInterval(timer)
      document.removeEventListener('visibilitychange', poll)
    }
  }, [loaded, error, poll])

  // 방장: 신고 알림 (15초마다)
  const loadAlerts = useCallback(() => {
    chatApi
      .reportAlerts(id)
      .then(setAlerts)
      .catch(() => {})
  }, [id])

  useEffect(() => {
    if (!isHost || error) return
    loadAlerts()
    const timer = setInterval(loadAlerts, ALERT_POLL_MS)
    return () => clearInterval(timer)
  }, [isHost, error, loadAlerts])

  // ---------- 스크롤: 항상 마지막 메시지 ----------

  const scrollToBottom = useCallback(() => {
    const el = listRef.current
    if (el) el.scrollTop = el.scrollHeight
  }, [])

  useLayoutEffect(() => {
    const el = listRef.current
    if (!el) return
    if (keepOffset.current != null) {
      // 이전 메시지를 위에 붙였을 때: 보던 메시지가 그 자리에 있게
      el.scrollTop = el.scrollHeight - keepOffset.current
      keepOffset.current = null
      pinBottom.current = false
    } else if (lastId !== prevLastId.current) {
      // 새 메시지가 왔으면 무조건 맨 아래로
      pinBottom.current = true
    }
    prevLastId.current = lastId
    if (pinBottom.current) {
      scrollToBottom()
      const frame = requestAnimationFrame(scrollToBottom)
      return () => cancelAnimationFrame(frame)
    }
  }, [messages, lastId, scrollToBottom])

  useEffect(() => {
    // 목록 높이·창 크기가 바뀌어도(공지·알림이 생기거나 사라짐, 이미지·글꼴 로드) 맨 아래 유지
    if (!listRef.current || !contentRef.current) return
    const observer = new ResizeObserver(() => {
      if (pinBottom.current) scrollToBottom()
    })
    observer.observe(listRef.current)
    observer.observe(contentRef.current)
    return () => observer.disconnect()
  }, [loaded, error, scrollToBottom])

  // 떠 있는 공지 높이만큼 목록 위쪽을 비워 둬서, 맨 위 메시지가 공지에 가려지지 않게
  useLayoutEffect(() => {
    // 공지가 바뀐 직후 한 번 (ResizeObserver 가 늦게 불리는 환경 대비)
    if (noticeRef.current) setNoticeHeight(noticeRef.current.offsetHeight)
  }, [challenge?.notice, isHost, loaded])

  useEffect(() => {
    const el = noticeRef.current
    if (!el) return
    const observer = new ResizeObserver(() => setNoticeHeight(el.offsetHeight))
    observer.observe(el)
    return () => observer.disconnect()
  }, [loaded, error])

  function onListScroll() {
    const el = listRef.current
    // 맨 아래에서 80px 넘게 올라가 있으면 '지난 메시지 보는 중'
    setScrolledUp(el.scrollHeight - el.scrollTop - el.clientHeight > 80)
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

  // ---------- 보내기 ----------

  async function send(e) {
    e?.preventDefault()
    const content = text.trim()
    if ((!content && !photo) || sending) return
    setSending(true)
    setSendError('')
    try {
      // 사진이 있으면 사진 + 글(선택)을 한 메시지로, 없으면 글만
      const saved = photo ? await chatApi.sendImage(id, photo.file, content) : await chatApi.send(id, content)
      pinBottom.current = true
      setMessages((cur) => merge(cur, [saved]))
      setText('')
      clearPhoto()
    } catch (err) {
      setSendError(err.fieldErrors?.content ?? err.message)
    } finally {
      setSending(false)
    }
  }

  // ---------- 사진 고르기 ----------

  function pickPhoto(e) {
    const file = e.target.files?.[0]
    e.target.value = '' // 같은 사진을 다시 골라도 onChange 가 불리게
    if (!file) return
    if (!PHOTO_TYPES.includes(file.type)) {
      setSendError('JPG·PNG 사진만 보낼 수 있어요.')
      return
    }
    if (file.size > MAX_PHOTO_BYTES) {
      setSendError('사진은 5MB까지 보낼 수 있어요.')
      return
    }
    setSendError('')
    clearPhoto()
    setPhoto({ file, preview: URL.createObjectURL(file) })
  }

  function clearPhoto() {
    setPhoto((cur) => {
      if (cur) URL.revokeObjectURL(cur.preview)
      return null
    })
  }

  function onKeyDown(e) {
    // Enter = 보내기, Shift+Enter = 줄바꿈. 한글 조합 중 Enter 는 글자 확정이라 보내지 않는다.
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      send()
    }
  }

  // ---------- 관리: 공지 · 신고 · 차단 · 내보내기 ----------

  function flash(message) {
    setNotice(message)
    setTimeout(() => setNotice(''), 2500)
  }

  function closeDialog() {
    if (busy) return
    setDialog(null)
    setDialogError('')
  }

  async function run(fn) {
    setBusy(true)
    setDialogError('')
    try {
      await fn()
      setDialog(null)
    } catch (err) {
      setDialogError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function saveNotice(content) {
    setBusy(true)
    try {
      setChallenge(await chatApi.setNotice(id, content))
      await poll() // '공지를 올렸어요' 안내를 바로 보여 준다
    } catch (err) {
      flash(err.message)
    } finally {
      setBusy(false)
    }
  }

  const report = (reason, detail) =>
    run(async () => {
      await chatApi.report(id, dialog.message.id, reason, detail)
      flash('신고했어요. 방장이 확인할 수 있게 전달돼요.')
    })

  const block = () =>
    run(async () => {
      const { userId, nickname } = dialog.user
      await blockApi.block(userId)
      setMessages((cur) => cur.filter((m) => m.type === 'SYSTEM' || m.senderId !== userId))
      flash(`${nickname}님을 차단했어요. 설정에서 해제할 수 있어요.`)
    })

  const kick = () =>
    run(async () => {
      const { userId } = dialog.user
      setChallenge(await chatApi.kick(id, userId))
      setMessages((cur) =>
        cur.map((m) => (m.senderId === userId && m.type === 'USER' ? { ...m, hidden: true, content: null } : m)),
      )
      setAlerts((cur) => cur.filter((a) => a.userId !== userId))
      await poll()
    })

  async function dismiss(alert) {
    setBusy(true)
    try {
      await chatApi.dismissAlert(id, alert.userId)
      setAlerts((cur) => cur.filter((a) => a.userId !== alert.userId))
    } finally {
      setBusy(false)
    }
  }

  const senderOf = (m) => ({ userId: m.senderId, nickname: m.senderNickname })

  // ---------- 화면 ----------

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
          <Link to="/challenges" className="btn btn-dark-outline">
            챌린지 둘러보기
          </Link>
        </div>
      ) : (
        <section className="card chat-card" aria-label="오픈채팅">
          <header className="chat-head">
            <div>
              <h1>{challenge.title}</h1>
              <p>
                참가자 {challenge.participantCount}명 · 서로 응원하고 인증 팁을 나눠요
                {isHost && <span className="chat-host-badge">방장</span>}
              </p>
            </div>
          </header>

          {isHost && (
            <ReportAlerts
              alerts={alerts}
              busy={busy}
              onDismiss={dismiss}
              onKick={(a) => setDialog({ kind: 'kick', user: { userId: a.userId, nickname: a.nickname } })}
            />
          )}

          <div className="chat-list-wrap">
            {/* 공지는 목록 위에 떠 있고, 위로 스크롤하는 동안에는 투명해진다 */}
            <div className="notice-slot" ref={noticeRef}>
              <NoticeBar notice={challenge.notice} isHost={isHost} busy={busy} onSave={saveNotice} faded={scrolledUp} />
            </div>
            <div
              className="chat-list"
              ref={listRef}
              onScroll={onListScroll}
              style={{ paddingTop: noticeHeight + 18 }}
              aria-live="polite"
            >
              <div ref={contentRef}>
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
                  if (m.type === 'SYSTEM') {
                    return (
                      <div key={m.id}>
                        {newDay && <p className="chat-day">{dayLabel(m.createdAt)}</p>}
                        <p className="chat-system">{m.content}</p>
                      </div>
                    )
                  }
                  // 같은 사람이 이어서 보내면 이름·사진은 첫 메시지에만
                  const showSender =
                    !m.mine && (newDay || !prev || prev.mine || prev.type === 'SYSTEM' || prev.senderId !== m.senderId)
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
                            {/* ⋯ 메뉴는 말풍선 오른쪽 위 모서리에 걸쳐 둔다 */}
                            <span className="chat-bubble-wrap">
                              {m.hidden ? (
                                <p className="chat-bubble is-hidden">(내보내진 참가자의 메시지예요)</p>
                              ) : m.hasImage ? (
                                <div className="chat-bubble has-image">
                                  <ChatImage
                                    challengeId={id}
                                    messageId={m.id}
                                    onLoad={() => pinBottom.current && scrollToBottom()}
                                  />
                                  {m.content && <p className="chat-caption">{m.content}</p>}
                                </div>
                              ) : (
                                <p className="chat-bubble">{m.content}</p>
                              )}
                              {!m.mine && !m.hidden && (
                                <MessageMenu
                                  isHost={isHost}
                                  onReport={() => setDialog({ kind: 'report', message: m })}
                                  onBlock={() => setDialog({ kind: 'block', user: senderOf(m) })}
                                  onKick={() => setDialog({ kind: 'kick', user: senderOf(m) })}
                                />
                              )}
                            </span>
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
            </div>
          </div>

          {photo && (
            <div className="chat-photo-preview">
              <img src={photo.preview} alt="보낼 사진 미리보기" />
              <span>사진에 붙일 말이 있으면 아래에 적어 주세요. (없어도 돼요)</span>
              <button type="button" className="link-button is-muted" onClick={clearPhoto} disabled={sending}>
                빼기
              </button>
            </div>
          )}
          <form className="chat-input" onSubmit={send}>
            <label className="chat-photo-btn" title="사진 보내기 (JPG·PNG, 5MB까지)">
              <input
                type="file"
                accept="image/jpeg,image/png"
                className="sr-only"
                onChange={pickPhoto}
                disabled={sending}
              />
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
                <rect x="3" y="6" width="18" height="14" rx="3" strokeWidth="1.7" />
                <path d="M8.5 6l1.4-2h4.2l1.4 2" strokeWidth="1.7" strokeLinejoin="round" />
                <circle cx="12" cy="13" r="3.5" strokeWidth="1.7" />
              </svg>
              <span className="sr-only">사진 보내기</span>
            </label>
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
              <button type="submit" className="btn btn-dark" disabled={sending || (!text.trim() && !photo)}>
                {sending ? '보내는 중…' : '보내기'}
              </button>
            </div>
          </form>
          {sendError && <p className="form-error chat-error">{sendError}</p>}
        </section>
      )}

      {notice && (
        <p className="chat-toast" role="status">
          {notice}
        </p>
      )}

      {dialog?.kind === 'report' && (
        <ReportDialog
          message={dialog.message}
          busy={busy}
          error={dialogError}
          onSubmit={report}
          onClose={closeDialog}
        />
      )}
      {dialog?.kind === 'block' && (
        <ConfirmDialog
          title={`${dialog.user.nickname}님을 차단할까요?`}
          body="차단하면 이 사람의 채팅이 내 화면에서 보이지 않아요. 상대에게는 알리지 않고, 설정에서 언제든 해제할 수 있어요."
          confirmLabel="차단하기"
          busy={busy}
          onConfirm={block}
          onClose={closeDialog}
        />
      )}
      {dialog?.kind === 'kick' && (
        <ConfirmDialog
          title={`${dialog.user.nickname}님을 내보낼까요?`}
          body="내보내면 이 챌린지에 다시 참여할 수 없고(초대 링크 포함), 지금까지 보낸 메시지는 가려져요. 채팅방에 안내가 남아요."
          confirmLabel="내보내기"
          danger
          busy={busy}
          onConfirm={kick}
          onClose={closeDialog}
        />
      )}
      {dialogError && dialog?.kind !== 'report' && <p className="chat-toast is-error">{dialogError}</p>}
    </div>
  )
}
