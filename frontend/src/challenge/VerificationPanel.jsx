import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { verificationApi } from '../api/client.js'
import { closedText, hasProgress, timeText } from './format.js'

const POLL_MS = 10_000

/**
 * 챌린지 상세의 '오늘의 인증' 칸 (개설자·참가자에게만 보인다).
 * 여기서는 진행 현황만 보여 주고, 사진 찍기·다른 사람 사진 보기는 [인증하러 가기] 화면에서 한다.
 */
export function VerificationPanel({ challenge: c }) {
  const [data, setData] = useState({ mine: null, count: 0, error: '' })

  const showMine = hasProgress(c)

  // 다른 참가자가 인증하면 게이지가 따라 올라가도록 10초마다 다시 불러온다 (화면을 보고 있을 때만)
  useEffect(() => {
    let cancelled = false
    function load() {
      Promise.all([showMine ? verificationApi.mine(c.id) : Promise.resolve(null), verificationApi.list(c.id)])
        .then(([mine, items]) => !cancelled && setData({ mine, count: items.length, error: '' }))
        .catch((err) => !cancelled && setData((d) => ({ ...d, error: err.message })))
    }
    load()
    const timer = setInterval(() => document.visibilityState === 'visible' && load(), POLL_MS)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [c.id, showMine])

  const { mine, count, error } = data
  const verifyPath = `/challenges/${c.id}/verify`
  const todayPercent = c.participantCount > 0 ? Math.round((count / c.participantCount) * 100) : 0

  return (
    <section className="dt-section vf">
      <div className="vf-head">
        <h2>오늘의 인증</h2>
        {/* 오늘 인증을 마쳤으면 큰 박스 대신 제목 옆 작은 표시로 */}
        {mine?.status === 'COMPLETED' ? (
          <span className="vf-done-pill">성공 🎉</span>
        ) : mine?.status === 'FAILED' ? (
          <span className="vf-done-pill is-failed">아쉽게 실패</span>
        ) : (
          mine?.state === 'DONE_TODAY' && (
            <span className="vf-done-pill">
              <CheckIcon />
              오늘 인증 완료
            </span>
          )
        )}
      </div>
      {error && <p className="form-error">{error}</p>}
      {mine && <Progress mine={mine} challenge={c} />}
      <Gauge label="오늘 진행" percent={todayPercent} detail={`참가자 ${count} / ${c.participantCount}명 인증`} tone="today" />

      {mine?.state === 'OPEN' ? (
        <Link to={verifyPath} className="btn btn-dark btn-block dt-btn vf-shoot">
          <CameraIcon />
          인증하러 가기
        </Link>
      ) : (
        <>
          {mine && mine.state !== 'DONE_TODAY' && (
            <button type="button" className="btn btn-block dt-btn dt-btn-off vf-closed" disabled>
              {closedText(mine.state, c)}
            </button>
          )}
          <Link to={verifyPath} className="btn btn-outline btn-block dt-btn">
            오늘 올라온 인증 보러 가기
          </Link>
        </>
      )}
      {mine?.state === 'OPEN' && c.verifyFrom && (
        <p className="dt-note">
          {timeText(c.verifyFrom)} ~ {timeText(c.verifyUntil)}에만 인증할 수 있어요
        </p>
      )}
    </section>
  )
}

/** 내 달성률: 끝까지 성공하려면 필요한 인증 횟수 중 몇 %를 채웠는지 */
function Progress({ mine, challenge: c }) {
  const percent = mine.targetCount > 0 ? Math.min(100, Math.round((mine.successDays / mine.targetCount) * 100)) : 0
  return (
    <Gauge
      label="내 달성률"
      percent={percent}
      detail={`${mine.successDays} / ${mine.targetCount}회${mine.currentStreak > 0 ? ` · 연속 ${mine.currentStreak}일째` : ''}`}
      sub={mine.weekCount != null ? `이번 주 ${mine.weekCount} / ${c.weeklyCount}회` : null}
    />
  )
}

/** 이름 + 큰 % + 오른쪽 작은 설명 + 막대. 내 달성률·오늘 진행이 같은 모양을 쓴다. */
function Gauge({ label, percent, detail, sub, tone }) {
  return (
    <div className="vf-progress">
      <div className="vf-progress-head">
        <span className="vf-percent">
          {label} <strong>{percent}%</strong>
        </span>
        <span className="vf-streak">{detail}</span>
      </div>
      <div
        className={`vf-bar${tone ? ` is-${tone}` : ''}`}
        role="progressbar"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
      >
        <span style={{ width: `${percent}%` }} />
      </div>
      {sub && <p className="vf-sub">{sub}</p>}
    </div>
  )
}

function CheckIcon() {
  return (
    <svg width="12" height="12" viewBox="0 0 12 12" fill="none" stroke="currentColor" aria-hidden="true">
      <path d="M2 6l2.6 2.6L10 3" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

export function CameraIcon({ size = 18 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
      <path
        d="M4 8h3l1.6-2.2A1.5 1.5 0 0 1 9.8 5h4.4a1.5 1.5 0 0 1 1.2.8L17 8h3a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z"
        strokeWidth="1.7"
        strokeLinejoin="round"
      />
      <circle cx="12" cy="13" r="3.5" strokeWidth="1.7" />
    </svg>
  )
}
