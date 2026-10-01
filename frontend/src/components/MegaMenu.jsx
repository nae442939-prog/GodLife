import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, useLocation } from 'react-router-dom'
import { rankingApi, recordApi, verificationApi } from '../api/client.js'
import { useAuth } from '../auth/useAuth.js'

/**
 * 헤더 메가 메뉴: 메뉴에 마우스를 올리거나(키보드 포커스) 헤더 바로 아래로 화면 전체 폭 패널이 내려온다.
 * 왼쪽은 링크 목록(links), 구분선 오른쪽은 aside(선택)를 둔다.
 */
export function MegaMenu({ label, to, links, aside }) {
  const [open, setOpen] = useState(false)
  const boxRef = useRef(null)
  const location = useLocation()
  const here = location.pathname + location.search
  const [lastPath, setLastPath] = useState(here)

  // 다른 화면(또는 같은 화면의 다른 탭 ?tab=)으로 이동하면 닫는다 (패널 안 링크를 눌렀을 때)
  if (lastPath !== here) {
    setLastPath(here)
    setOpen(false)
  }

  useEffect(() => {
    if (!open) return
    function onKeyDown(e) {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [open])

  return (
    <div
      ref={boxRef}
      className={`menu-item${open ? ' is-open' : ''}`}
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
      onFocus={() => setOpen(true)}
      onBlur={(e) => !boxRef.current.contains(e.relatedTarget) && setOpen(false)}
    >
      <NavLink to={to} aria-haspopup="true" aria-expanded={open}>
        {label}
      </NavLink>

      <div className="mega" aria-hidden={!open}>
        <div className="mega-inner">
          <ul className="mega-links">
            {links.map((link) => (
              <li key={link.to}>
                <Link to={link.to} className="mega-link" tabIndex={open ? 0 : -1}>
                  <span className="mega-dot" aria-hidden="true" />
                  {link.label}
                </Link>
              </li>
            ))}
          </ul>
          {aside && <div className="mega-aside">{aside(open)}</div>}
        </div>
      </div>
    </div>
  )
}

/** 챌린지 메가 메뉴 오른쪽: 로그인했으면 오늘 인증할 챌린지 (패널이 열릴 때 불러온다) */
export function TodayChallenges({ open }) {
  const { status } = useAuth()
  const [items, setItems] = useState(null)

  useEffect(() => {
    if (!open || status !== 'authed') return
    let cancelled = false
    verificationApi
      .myChallenges()
      .then((list) => !cancelled && setItems(list.filter((c) => c.joined && c.inProgress)))
      .catch(() => !cancelled && setItems([]))
    return () => {
      cancelled = true
    }
  }, [open, status])

  if (status !== 'authed') {
    return (
      <div className="mega-today">
        <p className="mega-today-title">오늘의 인증</p>
        <p className="mega-today-empty">
          <Link to="/login" tabIndex={open ? 0 : -1}>
            로그인
          </Link>
          하면 참여 중인 챌린지를 여기서 바로 인증할 수 있어요.
        </p>
      </div>
    )
  }

  return (
    <div className="mega-today">
      <p className="mega-today-title">오늘 인증할 챌린지</p>
      {items === null ? (
        <p className="mega-today-empty">불러오는 중…</p>
      ) : items.length === 0 ? (
        <p className="mega-today-empty">진행 중인 챌린지가 없어요.</p>
      ) : (
        <ul>
          {items.slice(0, 3).map((c) => (
            <li key={c.id}>
              <Link to={`/challenges/${c.id}`} className="mega-today-name" tabIndex={open ? 0 : -1}>
                {c.title}
              </Link>
              {c.verifyState === 'OPEN' ? (
                <Link to={`/challenges/${c.id}/verify`} className="mega-today-go" tabIndex={open ? 0 : -1}>
                  인증하기
                </Link>
              ) : c.verifyState === 'DONE_TODAY' ? (
                <span className="mega-today-done">✓ 완료</span>
              ) : null}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

const MOOD_EMOJI = { GREAT: '🤩', GOOD: '🙂', OKAY: '😐', SAD: '😔', HARD: '😣' }

/** 갓생기록 메가 메뉴 오른쪽: 로그인했으면 오늘 일기를 썼는지, 썼으면 가장 최근에 쓴 글 (패널이 열릴 때 불러온다) */
export function TodayDiary({ open }) {
  const { status } = useAuth()
  const [day, setDay] = useState(undefined)

  useEffect(() => {
    if (!open || status !== 'authed') return
    let cancelled = false
    const d = new Date()
    const p = (n) => String(n).padStart(2, '0')
    recordApi
      .day(`${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`)
      .then((r) => !cancelled && setDay(r))
      .catch(() => !cancelled && setDay(null))
    return () => {
      cancelled = true
    }
  }, [open, status])

  return (
    <div className="mega-today">
      <p className="mega-today-title">오늘의 일기</p>
      {status !== 'authed' ? (
        <p className="mega-today-empty">
          <Link to="/login" tabIndex={open ? 0 : -1}>
            로그인
          </Link>
          하면 오늘 하루를 일기로 남길 수 있어요.
        </p>
      ) : day === undefined ? (
        <p className="mega-today-empty">불러오는 중…</p>
      ) : day === null ? (
        <p className="mega-today-empty">일기를 불러오지 못했어요.</p>
      ) : day.diaries.length === 0 ? (
        <p className="mega-today-empty">
          오늘 일기를 아직 안 쓰셨어요.{' '}
          <Link to="/records" tabIndex={open ? 0 : -1}>
            지금 쓰러 가기
          </Link>
        </p>
      ) : (
        <TodayDiaryCard diaries={day.diaries} open={open} />
      )}
    </div>
  )
}

/** 오늘 쓴 일기 중 가장 최근 것. 여러 개면 '외 N개' */
function TodayDiaryCard({ diaries, open }) {
  const last = diaries[diaries.length - 1]
  return (
    <Link to="/records/calendar" className="mega-diary" tabIndex={open ? 0 : -1}>
      {last.mood && (
        <span className="mega-diary-mood" aria-hidden="true">
          {MOOD_EMOJI[last.mood]}
        </span>
      )}
      <span className="mega-diary-text">
        {last.content || (last.photo ? '사진으로 오늘을 남겼어요.' : '기분만 남겼어요. 한 줄 더 적어 볼까요?')}
        {diaries.length > 1 && <small> 외 {diaries.length - 1}개</small>}
      </span>
    </Link>
  )
}

/** 랭킹 메가 메뉴 오른쪽: 로그인했으면 이번 달 랭킹(이번 달 인증 성공 횟수) 내 순위 (패널이 열릴 때 불러온다) */
export function MyRankSummary({ open }) {
  const { status } = useAuth()
  const [me, setMe] = useState(undefined)

  useEffect(() => {
    if (!open || status !== 'authed') return
    let cancelled = false
    rankingApi
      .users('month_verify')
      .then((r) => !cancelled && setMe(r.me))
      .catch(() => !cancelled && setMe(null))
    return () => {
      cancelled = true
    }
  }, [open, status])

  return (
    <div className="mega-today">
      <p className="mega-today-title">이번 달 랭킹 내 순위</p>
      {status !== 'authed' ? (
        <p className="mega-today-empty">
          <Link to="/login" tabIndex={open ? 0 : -1}>
            로그인
          </Link>
          하면 내 순위를 볼 수 있어요.
        </p>
      ) : me === undefined ? (
        <p className="mega-today-empty">불러오는 중…</p>
      ) : me === null ? (
        <p className="mega-today-empty">아직 이번 달 랭킹에 없어요. 오늘 인증하고 순위에 올라 봐요!</p>
      ) : (
        <p className="mega-rank">
          <strong>{me.rank}위</strong>
          <span>이번 달 성공 {Math.round(me.value)}회</span>
        </p>
      )}
    </div>
  )
}
