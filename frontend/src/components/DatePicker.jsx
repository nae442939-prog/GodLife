import { useEffect, useId, useRef, useState } from 'react'
import { parseDate, toIsoDate } from '../challenge/format.js'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

/** '2026-09-30' → '2026. 9. 30 (수)' */
function displayDate(iso) {
  const d = parseDate(iso)
  return `${d.getFullYear()}. ${d.getMonth() + 1}. ${d.getDate()} (${WEEKDAYS[d.getDay()]})`
}

/** 해당 달 달력 칸: 앞쪽은 지난달 빈칸(null), 이후 1일부터 말일까지 ISO 날짜 */
function monthCells(year, month) {
  const first = new Date(year, month, 1).getDay()
  const last = new Date(year, month + 1, 0).getDate()
  const cells = Array.from({ length: first }, () => null)
  for (let day = 1; day <= last; day++) cells.push(toIsoDate(new Date(year, month, day)))
  return cells
}

/**
 * 브라우저 기본 달력은 크기를 바꿀 수 없어서 직접 그리는 큰 달력.
 * value / min 은 'YYYY-MM-DD'. rangeStart~rangeEnd 가 있으면 그 기간을 옅게 칠한다.
 * align="right" 면 달력이 칸의 오른쪽 끝에 맞춰 열린다 (오른쪽 칸에서 화면 밖으로 나가지 않게).
 */
export function DatePicker({ label, value, min, onChange, error, hint, rangeStart, rangeEnd, align = 'left' }) {
  const id = useId()
  const rootRef = useRef(null)
  const [open, setOpen] = useState(false)
  const [view, setView] = useState(() => {
    const d = parseDate(value || toIsoDate(new Date()))
    return { year: d.getFullYear(), month: d.getMonth() }
  })
  const today = toIsoDate(new Date())

  useEffect(() => {
    if (!open) return
    function onPointerDown(e) {
      if (!rootRef.current?.contains(e.target)) setOpen(false)
    }
    function onKeyDown(e) {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  function toggle() {
    if (!open && value) {
      // 열 때는 고른 날짜가 있는 달을 보여 준다.
      const d = parseDate(value)
      setView({ year: d.getFullYear(), month: d.getMonth() })
    }
    setOpen((v) => !v)
  }

  function moveMonth(delta) {
    setView(({ year, month }) => {
      const d = new Date(year, month + delta, 1)
      return { year: d.getFullYear(), month: d.getMonth() }
    })
  }

  function pick(iso) {
    onChange(iso)
    setOpen(false)
  }

  const cells = monthCells(view.year, view.month)
  const messageId = `${id}-msg`

  return (
    <div className="field dp" ref={rootRef}>
      <label htmlFor={id}>{label}</label>
      <button
        id={id}
        type="button"
        className="dp-trigger"
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-invalid={error ? 'true' : undefined}
        aria-describedby={error || hint ? messageId : undefined}
        onClick={toggle}
      >
        <span>{value ? displayDate(value) : '날짜 선택'}</span>
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
          <rect x="3.5" y="5" width="17" height="15" rx="3" strokeWidth="1.7" />
          <path d="M3.5 10h17M8 3v4M16 3v4" strokeWidth="1.7" strokeLinecap="round" />
        </svg>
      </button>

      {open && (
        <div className={`dp-pop ${align === 'right' ? 'is-right' : ''}`} role="dialog" aria-label={`${label} 고르기`}>
          <div className="dp-head">
            <button type="button" className="dp-nav" onClick={() => moveMonth(-1)} aria-label="이전 달">
              ‹
            </button>
            <strong>
              {view.year}년 {view.month + 1}월
            </strong>
            <button type="button" className="dp-nav" onClick={() => moveMonth(1)} aria-label="다음 달">
              ›
            </button>
          </div>
          <div className="dp-grid">
            {WEEKDAYS.map((w, i) => (
              <span key={w} className={`dp-week ${i === 0 ? 'is-sun' : ''} ${i === 6 ? 'is-sat' : ''}`}>
                {w}
              </span>
            ))}
            {cells.map((iso, i) => {
              if (!iso) return <span key={`blank-${i}`} />
              const disabled = Boolean(min && iso < min)
              const inRange = rangeStart && rangeEnd && iso >= rangeStart && iso <= rangeEnd
              const classes = [
                'dp-day',
                iso === value && 'is-selected',
                iso === today && 'is-today',
                inRange && 'is-range',
              ]
                .filter(Boolean)
                .join(' ')
              return (
                <button
                  key={iso}
                  type="button"
                  className={classes}
                  disabled={disabled}
                  aria-pressed={iso === value}
                  aria-label={displayDate(iso)}
                  onClick={() => pick(iso)}
                >
                  {Number(iso.slice(8))}
                </button>
              )
            })}
          </div>
          <div className="dp-foot">
            <button type="button" className="dp-today" onClick={() => pick(min && today < min ? min : today)}>
              {min && today < min ? '가장 빠른 날' : '오늘'}
            </button>
          </div>
        </div>
      )}

      {error ? (
        <p id={messageId} className="field-error" role="alert">
          {error}
        </p>
      ) : hint ? (
        <p id={messageId} className="field-hint">
          {hint}
        </p>
      ) : null}
    </div>
  )
}
