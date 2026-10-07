import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { weeklyReportApi } from '../api/client.js'
import { shortDate } from '../challenge/format.js'
import { CategoryIcon } from '../challenge/icons.jsx'

const WEEKDAYS = ['', '월', '화', '수', '목', '금', '토', '일']

/**
 * 주간 회고 (/records/report): 한 주(월~일) 동안 인증해야 했던 날 가운데 얼마나 해냈는지.
 * 성공률 · 요일별 성공/실패 · 챌린지별 결과와, 그 기록을 보고 고른 코칭 문구를 보여 준다.
 * 이번 주는 진행 중이라 볼 때마다 지금까지의 기록으로 계산되고, 끝난 주는 월요일에 만들어진 리포트가 그대로 남는다.
 * 어느 주를 보는지는 주소(?week=월요일)에 둔다. 포인트 금액은 보여 주지 않는다(갓생기록과 같은 분위기).
 */
export function WeeklyReportPage() {
  const [params, setParams] = useSearchParams()
  const week = /^\d{4}-\d{2}-\d{2}$/.test(params.get('week') ?? '') ? params.get('week') : ''
  const [data, setData] = useState({ key: null, report: null, error: '' })

  useEffect(() => {
    let cancelled = false
    weeklyReportApi
      .get(week)
      .then((report) => !cancelled && setData({ key: week, report, error: '' }))
      .catch((err) => !cancelled && setData({ key: week, report: null, error: err.message }))
    return () => {
      cancelled = true
    }
  }, [week])

  return (
    <div className="container page">
      <div className="ch-head cl-head">
        <div>
          <h1 className="page-title">주간 회고</h1>
          <p className="page-sub">한 주 동안 얼마나 해냈는지 돌아봐요. 지난주 리포트는 매주 월요일에 도착해요.</p>
        </div>
      </div>

      {data.key !== week ? (
        <p className="muted">불러오는 중…</p>
      ) : data.error ? (
        <p className="form-error">{data.error}</p>
      ) : (
        <Report report={data.report} onWeek={(w) => setParams(w ? { week: w } : {})} />
      )}
    </div>
  )
}

function Report({ report: r, onWeek }) {
  const s = r.stats
  // weeks 는 최근 것부터: 한 칸 뒤가 이전 주, 한 칸 앞이 다음 주
  const at = r.weeks.indexOf(r.weekStart)
  const older = at >= 0 ? r.weeks[at + 1] : undefined
  const newer = at > 0 ? r.weeks[at - 1] : at < 0 ? r.weeks[0] : undefined
  const most = Math.max(1, ...s.weekdays.map((w) => w.done + w.fail))
  const diff = s.previousRate == null || r.successRate == null ? null : r.successRate - s.previousRate

  return (
    <div className="wr">
      <div className="wr-nav">
        <button type="button" className="wr-nav-btn" disabled={!older} onClick={() => onWeek(older)}>
          ‹ 이전 주
        </button>
        <div className="wr-week">
          <strong>
            {shortDate(r.weekStart)} ~ {shortDate(r.weekEnd)}
          </strong>
          <span className={`wr-badge${r.inProgress ? ' is-live' : ''}`}>{r.inProgress ? '이번 주 · 진행 중' : '확정'}</span>
        </div>
        <button
          type="button"
          className="wr-nav-btn"
          disabled={!newer}
          onClick={() => onWeek(newer === r.weeks[0] ? '' : newer)}
        >
          다음 주 ›
        </button>
      </div>

      {s.total === 0 ? (
        <div className="wr-card wr-empty">
          <p>{r.coaching}</p>
          <Link to="/challenges" className="btn btn-primary">
            챌린지 둘러보기
          </Link>
        </div>
      ) : (
        <>
          <div className="wr-summary">
            <div className="wr-card wr-stat">
              <span className="wr-stat-label">성공률</span>
              <strong className="wr-stat-value">{r.successRate}%</strong>
              <span className="wr-stat-sub">
                {diff == null
                  ? '그 전 주 리포트가 없어요'
                  : diff === 0
                    ? '그 전 주와 같아요'
                    : `그 전 주보다 ${Math.abs(diff)}%p ${diff > 0 ? '올랐어요' : '내려갔어요'}`}
              </span>
            </div>
            <div className="wr-card wr-stat">
              <span className="wr-stat-label">인증</span>
              <strong className="wr-stat-value">
                {s.done}
                <small> / {s.total}번</small>
              </strong>
              <span className="wr-stat-sub">인증해야 했던 날 기준 (쉬는 날은 빼요)</span>
            </div>
            <div className="wr-card wr-stat">
              <span className="wr-stat-label">가장 많이 놓친 요일</span>
              <strong className="wr-stat-value">{r.worstWeekday ? `${WEEKDAYS[r.worstWeekday]}요일` : '없음'}</strong>
              <span className="wr-stat-sub">
                {r.worstWeekday
                  ? `${s.weekdays[r.worstWeekday - 1].fail}번 놓쳤어요`
                  : '한 번도 놓치지 않았어요'}
              </span>
            </div>
          </div>

          <div className="wr-card">
            <h2 className="wr-title">이번 회고</h2>
            <div className="wr-coach">
              {r.coaching.split('\n').map((line, i) => (
                <p key={i}>{line}</p>
              ))}
            </div>
          </div>

          <div className="wr-card">
            <h2 className="wr-title">요일별</h2>
            <ol className="wr-days" aria-label="요일별 성공과 실패">
              {s.weekdays.map((w) => (
                <li key={w.weekday} className={`wr-day${w.weekday === r.worstWeekday ? ' is-worst' : ''}`}>
                  <div className="wr-bar" aria-hidden="true">
                    <span className="wr-bar-fail" style={{ height: `${(w.fail / most) * 100}%` }} />
                    <span className="wr-bar-done" style={{ height: `${(w.done / most) * 100}%` }} />
                  </div>
                  <span className="wr-day-name">{WEEKDAYS[w.weekday]}</span>
                  <span className="wr-day-count">
                    {w.done + w.fail === 0 ? '–' : `${w.done}/${w.done + w.fail}`}
                  </span>
                </li>
              ))}
            </ol>
            <p className="wr-legend">
              <span className="wr-dot is-done" /> 성공 <span className="wr-dot is-fail" /> 실패
            </p>
          </div>

          <div className="wr-card">
            <h2 className="wr-title">챌린지별</h2>
            <ul className="wr-challenges">
              {s.challenges.map((c) => (
                <li key={c.challengeId}>
                  <Link to={`/challenges/${c.challengeId}`} className="wr-ch">
                    <span className="wr-ch-icon" aria-hidden="true">
                      <CategoryIcon id={c.categoryId} size={20} strokeWidth={1.6} />
                    </span>
                    <span className="wr-ch-name">{c.title}</span>
                    <span className="wr-ch-meter" aria-hidden="true">
                      <span style={{ width: `${(c.done / (c.done + c.fail)) * 100}%` }} />
                    </span>
                    <span className="wr-ch-count">
                      {c.done}/{c.done + c.fail}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        </>
      )}
    </div>
  )
}
