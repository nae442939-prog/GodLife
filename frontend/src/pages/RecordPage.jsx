import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { recordApi } from '../api/client.js'
import { parseDate, toIsoDate } from '../challenge/format.js'
import { CategoryIcon } from '../challenge/icons.jsx'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']
const RESULT_TEXT = { DONE: '성공', FAIL: '실패', PENDING: '오늘 아직', REST: '쉬는 날' }
const MOODS = {
  GREAT: ['🤩', '최고예요'],
  GOOD: ['🙂', '좋아요'],
  OKAY: ['😐', '보통이에요'],
  SAD: ['😔', '아쉬워요'],
  HARD: ['😣', '힘들었어요'],
}

/**
 * 갓생기록 캘린더 (/records/calendar): 날짜별 성공·실패를 큰 달력으로. 헤더 메뉴나 일기장(/records)의 [캘린더 보기]에서 온다.
 * 일기장과 같은 종이 한 장 디자인. 맨 위에 고른 날의 챌린지 결과 · 내 인증 사진, 그 아래에 달력.
 * 달력에서 날짜 칸을 누르면 작은 창이 떠서 그날 쓴 일기 목록(하루에 여러 개일 수 있다)을 보여 준다.
 * 달과 고른 날짜는 주소(?month=&date=)에 둔다.
 * 포인트 금액은 보여 주지 않는다. 일기는 여기서 읽기만 하고, 쓰기·고치기는 일기장에서 한다.
 */
export function RecordPage() {
  const [params, setParams] = useSearchParams()
  const todayIso = toIsoDate(new Date())
  const month = /^\d{4}-\d{2}$/.test(params.get('month') ?? '') ? params.get('month') : todayIso.slice(0, 7)
  const [data, setData] = useState({ key: null, value: null, error: '' })
  const picked = params.get('date') ?? ''
  const selected = /^\d{4}-\d{2}-\d{2}$/.test(picked) && picked <= todayIso ? picked : todayIso
  // 날짜 칸을 누르면 뜨는 일기 목록 창 (그 날짜)
  const [popup, setPopup] = useState(null)

  useEffect(() => {
    let cancelled = false
    recordApi
      .month(month)
      .then((value) => !cancelled && setData({ key: month, value, error: '' }))
      .catch((err) => !cancelled && setData({ key: month, value: null, error: err.message }))
    return () => {
      cancelled = true
    }
  }, [month])

  function update(name, value) {
    const next = new URLSearchParams(params)
    next.set(name, value)
    setParams(next, { replace: true })
  }

  function moveMonth(diff) {
    const [y, m] = month.split('-').map(Number)
    const d = new Date(y, m - 1 + diff, 1)
    update('month', toIsoDate(d).slice(0, 7))
  }

  // 달을 옮겨도 이전 달 내용이 잠깐 남지 않게, 지금 주소의 것일 때만 보여 준다
  const record = data.key === month ? data.value : null
  const today = record?.today ?? todayIso
  const [year, monthNum] = month.split('-').map(Number)
  const isThisMonth = month === today.slice(0, 7)

  return (
    <div className="dwp-page">
      <div className="dwp-sheet dwp-sheet--cal">
        <span className="dwp-tape" aria-hidden="true" />
        <span className="dwp-tape dwp-tape--2" aria-hidden="true" />
        <span className="dwp-pin" aria-hidden="true">
          <svg width="18" height="18" viewBox="0 0 24 24">
            <path d="M12 2a5 5 0 0 0-1 9.9V17l1 4 1-4v-5.1A5 5 0 0 0 12 2z" fill="#b7a67c" />
          </svg>
        </span>

        <Link to={`/records?date=${selected}`} className="rec-back">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
            <path d="M15 5l-7 7 7 7" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          뒤로 가기
        </Link>
        <div className="dwp-topRow">
          <div>
            <div className="dwp-eyebrow">내가 얼마나 해냈는지 하루하루 돌아봐요</div>
            <h1 className="dwp-title">갓생기록 캘린더</h1>
          </div>
          <div className="dwp-dateNav">
            <button type="button" className="dwp-dateArrow" onClick={() => moveMonth(-1)} aria-label="이전 달">
              <svg width="13" height="13" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M15 5l-7 7 7 7" stroke="#63573c" strokeWidth="2.2" fill="none" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </button>
            <span className="dwp-dateLabel">
              {year}년 {monthNum}월
            </span>
            <button
              type="button"
              className="dwp-dateArrow"
              onClick={() => moveMonth(1)}
              disabled={isThisMonth}
              aria-label="다음 달"
            >
              <svg width="13" height="13" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M9 5l7 7-7 7" stroke="#63573c" strokeWidth="2.2" fill="none" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </button>
          </div>
        </div>

        {data.key === month && data.error ? (
          <p className="dwp-notice is-error">{data.error}</p>
        ) : (
          <>
            {/* 고른 날의 결과가 맨 위, 그 아래에 달력 */}
            <DayPanel key={selected} date={selected} today={today} />

            <section className="rec-cal" aria-label={`${year}년 ${monthNum}월 캘린더`}>
              <div className="rec-grid rec-weekdays" aria-hidden="true">
                {WEEKDAYS.map((w) => (
                  <span key={w}>{w}</span>
                ))}
              </div>
              {!record ? (
                <p className="dwp-loading">불러오는 중…</p>
              ) : (
                <div className="rec-grid">
                  {Array.from({ length: parseDate(record.days[0].date).getDay() }, (_, i) => (
                    <span key={`blank-${i}`} />
                  ))}
                  {record.days.map((d) => (
                    <DayCell
                      key={d.date}
                      day={d}
                      today={today}
                      selected={selected === d.date}
                      onSelect={() => {
                        update('date', d.date)
                        setPopup(d.date)
                      }}
                    />
                  ))}
                </div>
              )}
              <p className="rec-legend">
                <span className="rec-mark is-done" aria-hidden="true" /> 성공
                <span className="rec-mark is-fail" aria-hidden="true" /> 실패
                <span className="rec-mark is-pending" aria-hidden="true" /> 오늘 아직
              </p>
              {record && record.challenges.length === 0 && (
                <p className="rec-empty">
                  아직 시작한 챌린지가 없어요. <Link to="/challenges">챌린지 둘러보기</Link>
                </p>
              )}
            </section>
          </>
        )}
      </div>

      {popup && <DiaryPopup key={popup} date={popup} onClose={() => setPopup(null)} />}
    </div>
  )
}

/** 캘린더 한 칸: 다 했으면 성공, 하나라도 놓쳤으면 실패, 오늘 아직 남았으면 진행 중. 여러 개면 '2/3' */
function DayCell({ day, today, selected, onSelect }) {
  const num = Number(day.date.slice(8))
  const future = day.date > today
  const failed = day.total - day.done - day.pending
  const kind = day.total === 0 ? '' : failed > 0 ? 'is-fail' : day.pending > 0 ? 'is-pending' : 'is-done'
  const label =
    day.total === 0
      ? '기록 없음'
      : `${day.total}개 중 ${day.done}개 성공${day.pending > 0 ? `, ${day.pending}개는 오늘 아직` : ''}`

  return (
    <button
      type="button"
      className={`rec-day${selected ? ' is-selected' : ''}${day.date === today ? ' is-today' : ''}`}
      onClick={onSelect}
      disabled={future}
      aria-pressed={selected}
      aria-label={`${num}일, ${label}${day.diary ? ', 일기 있음' : ''}`}
    >
      <span className="rec-day-num">{num}</span>
      {kind && <span className={`rec-mark ${kind}`} aria-hidden="true" />}
      {day.total > 1 && (
        <span className="rec-day-count" aria-hidden="true">
          {day.done}/{day.total}
        </span>
      )}
      {day.diary && <span className="rec-diary-dot" aria-hidden="true" />}
    </button>
  )
}

/** 고른 날: 챌린지별 결과와 내 인증 사진 */
function DayPanel({ date, today }) {
  const [state, setState] = useState({ loaded: false, items: [], error: '' })

  useEffect(() => {
    let cancelled = false
    recordApi
      .day(date)
      .then((r) => !cancelled && setState({ loaded: true, items: r.items, error: '' }))
      .catch((err) => !cancelled && setState({ loaded: true, items: [], error: err.message }))
    return () => {
      cancelled = true
    }
  }, [date])

  const d = parseDate(date)
  const title = `${d.getMonth() + 1}월 ${d.getDate()}일 ${WEEKDAYS[d.getDay()]}요일`

  return (
    <section className="rec-panel" aria-label={`${title} 기록`}>
      <h2 className="dwp-sectionTitle">
        📌 {title}
        {date === today && <small>오늘</small>}
      </h2>

      {state.error && <p className="dwp-notice is-error">{state.error}</p>}
      {!state.loaded ? (
        <p className="rec-none">불러오는 중…</p>
      ) : state.items.length === 0 ? (
        <p className="rec-none">이날은 진행한 챌린지가 없어요.</p>
      ) : (
        <ul className="rec-items">
          {state.items.map((it) => (
            <li key={it.challengeId} className={`rec-item is-${it.result.toLowerCase()}`}>
              {it.verificationId ? (
                <RecordPhoto verificationId={it.verificationId} alt={`${it.title} 인증 사진`} />
              ) : (
                <span className="rec-photo is-none" aria-hidden="true">
                  <CategoryIcon id={it.categoryId} size={20} strokeWidth={1.6} />
                </span>
              )}
              <span className="rec-item-body">
                <Link to={`/challenges/${it.challengeId}`} className="rec-item-title">
                  {it.title}
                </Link>
                <span className="rec-item-result">
                  <span className={`rec-mark is-${it.result.toLowerCase()}`} aria-hidden="true" />
                  {RESULT_TEXT[it.result]}
                  {it.result === 'REST' && ' · 인증하지 않아도 되는 날이에요'}
                </span>
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

/**
 * 날짜 칸을 누르면 뜨는 작은 창: 그날 쓴 일기 목록. 바깥을 누르거나 Esc · ✕ 로 닫는다.
 * 일기마다 쓴 시각 · 기분 · 글 · 태그한 챌린지 · 사진. 쓰기·고치기는 일기장으로 간다.
 */
function DiaryPopup({ date, onClose }) {
  const [state, setState] = useState({ day: null, error: '' })
  const closeRef = useRef(null)

  useEffect(() => {
    let cancelled = false
    recordApi
      .day(date)
      .then((day) => !cancelled && setState({ day, error: '' }))
      .catch((err) => !cancelled && setState({ day: null, error: err.message }))
    return () => {
      cancelled = true
    }
  }, [date])

  useEffect(() => {
    function onKeyDown(e) {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    closeRef.current?.focus()
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  const d = parseDate(date)
  const title = `${d.getMonth() + 1}월 ${d.getDate()}일 ${WEEKDAYS[d.getDay()]}요일`
  const day = state.day

  return (
    <div className="rec-pop-back" onClick={onClose}>
      <div
        className="rec-pop"
        role="dialog"
        aria-modal="true"
        aria-label={`${title} 일기 목록`}
        onClick={(e) => e.stopPropagation()}
      >
        <span className="dwp-tape rec-pop-tape" aria-hidden="true" />
        <div className="rec-diary-head">
          <h3>
            📝 {title} 일기 {day && day.diaries.length > 0 && <span>{day.diaries.length}개</span>}
          </h3>
          <button type="button" className="rec-pop-close" onClick={onClose} aria-label="닫기" ref={closeRef}>
            ✕
          </button>
        </div>

        {state.error ? (
          <p className="dwp-notice is-error">{state.error}</p>
        ) : !day ? (
          <p className="rec-none">불러오는 중…</p>
        ) : (
          <>
            <DayDiaries day={day} />
            <Link to={`/records?date=${date}`} className="rec-pop-write">
              {day.diaries.length > 0 ? '+ 일기 하나 더 쓰기' : '+ 일기 쓰러 가기'}
            </Link>
          </>
        )}
      </div>
    </div>
  )
}

/** 그날 쓴 일기 목록 (읽기) */
function DayDiaries({ day }) {
  return (
    <div className="rec-diary">
      {day.diaries.length === 0 ? (
        <p className="rec-none">이날은 일기를 쓰지 않았어요.</p>
      ) : (
        <ul className="rec-diary-list">
          {day.diaries.map((entry) => {
            const tagged = day.items.filter((it) => entry.tags.includes(it.challengeId))
            return (
              <li key={entry.id} className="rec-diary-card">
                <div className="rec-diary-meta">
                  <time dateTime={entry.createdAt}>{entry.createdAt.slice(11, 16)}</time>
                  {entry.mood && (
                    <span className="rec-diary-mood">
                      <span aria-hidden="true">{MOODS[entry.mood][0]}</span> {MOODS[entry.mood][1]}
                    </span>
                  )}
                  <Link to={`/records?date=${day.date}&entry=${entry.id}`} className="rec-diary-edit">
                    수정
                  </Link>
                </div>
                {entry.content && <p className="rec-diary-text">{entry.content}</p>}
                {entry.photo && <DiaryPhoto id={entry.id} />}
                {tagged.length > 0 && (
                  <p className="rec-diary-tags">
                    {tagged.map((it) => (
                      <span key={it.challengeId}>#{it.title}</span>
                    ))}
                  </p>
                )}
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

/** 일기에 붙인 사진. 본인만 받을 수 있어 토큰을 붙여 받은 뒤 보여 준다. */
function DiaryPhoto({ id }) {
  const [src, setSrc] = useState(null)

  useEffect(() => {
    let cancelled = false
    let url = null
    recordApi
      .diaryPhotoBlob(id)
      .then((blob) => {
        if (cancelled) return
        url = URL.createObjectURL(blob)
        setSrc(url)
      })
      .catch(() => {})
    return () => {
      cancelled = true
      if (url) URL.revokeObjectURL(url)
    }
  }, [id])

  if (!src) return null
  return <img className="rec-diary-photo" src={src} alt="일기에 붙인 사진" />
}

// 한 번 받은 사진은 다시 받지 않는다
const photoCache = new Map()

function loadPhoto(verificationId) {
  if (!photoCache.has(verificationId)) {
    const pending = recordApi
      .photoBlob(verificationId)
      .then((blob) => URL.createObjectURL(blob))
      .catch((err) => {
        photoCache.delete(verificationId) // 실패하면 다음에 다시 시도
        throw err
      })
    photoCache.set(verificationId, pending)
  }
  return photoCache.get(verificationId)
}

/** 내 인증 사진. 본인만 받을 수 있어 토큰을 붙여 받은 뒤 보여 준다. */
function RecordPhoto({ verificationId, alt }) {
  const [src, setSrc] = useState(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let cancelled = false
    loadPhoto(verificationId)
      .then((url) => !cancelled && setSrc(url))
      .catch(() => !cancelled && setFailed(true))
    return () => {
      cancelled = true
    }
  }, [verificationId])

  if (failed) return <span className="rec-photo is-none">사진 없음</span>
  if (!src) return <span className="rec-photo is-none" aria-label="사진 불러오는 중" />
  return (
    <a href={src} target="_blank" rel="noreferrer" className="rec-photo-link">
      <img className="rec-photo" src={src} alt={alt} />
    </a>
  )
}
