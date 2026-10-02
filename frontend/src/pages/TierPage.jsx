import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { tierApi } from '../api/client.js'
import { TierChip } from '../profile/ProfileCard.jsx'

const TIER_LABEL = { BRONZE: '브론즈', SILVER: '실버', GOLD: '골드', PLATINUM: '플래티넘', DIAMOND: '다이아몬드' }

const p = (n) => `${n.toLocaleString()}P`

/**
 * 내 칭호 (/me/tier): 마이페이지의 칭호 카드 · 승급/강등 알림에서 온다.
 * 지금 점수와 다음 칭호까지 남은 점수, 점수가 어떻게 쌓이고 깎이는지, 칭호별 혜택(베팅 한도 · 고액 챌린지)을 보여 준다.
 * 점수는 인증 · 완주 기록으로만 정해진다 (포인트 · 돈과 상관없다).
 */
export function TierPage() {
  const [state, setState] = useState({ data: null, error: '' })

  useEffect(() => {
    let cancelled = false
    tierApi
      .me()
      .then((data) => !cancelled && setState({ data, error: '' }))
      .catch((err) => !cancelled && setState({ data: null, error: err.message }))
    return () => {
      cancelled = true
    }
  }, [])

  const t = state.data

  return (
    <div className="container page">
      <div className="ch-head cl-head">
        <div>
          <h1 className="page-title">내 칭호</h1>
          <p className="page-sub">꾸준히 인증하면 칭호가 오르고, 챌린지를 실패하거나 포기하면 내려가요.</p>
        </div>
        <Link to="/me" className="btn btn-dark-outline">
          마이페이지로
        </Link>
      </div>

      {state.error && <p className="form-error">{state.error}</p>}
      {!t ? (
        !state.error && <p className="muted">불러오는 중…</p>
      ) : (
        <>
          <TierProgress t={t} />

          <section className="bg-group">
            <h2>점수는 이렇게 정해져요</h2>
            <ul className="tr-rules">
              {/* 관리자는 칭호 대상이 아니라 내 기록 횟수를 보여 주지 않는다 */}
              <Rule sign="+10" label="인증 성공 1회" count={t.admin ? null : t.record.verified} unit="회" />
              <Rule sign="+50" label="챌린지 완주" count={t.admin ? null : t.record.completed} unit="번" />
              <Rule sign="−30" label="챌린지 실패" count={t.admin ? null : t.record.failed} unit="번" minus />
              <Rule sign="−50" label="중간에 포기" count={t.admin ? null : t.record.gaveUp} unit="번" minus />
            </ul>
          </section>

          <section className="bg-group">
            <h2>칭호별 혜택</h2>
            <div className="tr-tableWrap">
              <table className="tr-table">
                <thead>
                  <tr>
                    <th>칭호</th>
                    <th>필요 점수</th>
                    <th>하루 한도</th>
                    <th>한 달 한도</th>
                    <th>고액 챌린지</th>
                  </tr>
                </thead>
                <tbody>
                  {t.tiers.map((tier) => (
                    <tr key={tier.id} className={tier.id === t.tier.id ? 'is-mine' : undefined}>
                      <td>
                        <TierChip tier={tier.name} />
                        {tier.id === t.tier.id && <small>지금</small>}
                      </td>
                      <td>{tier.minScore.toLocaleString()}점</td>
                      <td>{p(tier.dailyBetLimit)}</td>
                      <td>{p(tier.monthlyBetLimit)}</td>
                      <td>{tier.highStakeAllowed ? '참여 가능' : '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="tr-note">
              한도는 챌린지에 걸 수 있는 포인트예요. 가입 30일 이내에는 칭호와 상관없이 하루 10,000P · 한 달 50,000P까지만
              걸 수 있어요. 고액 챌린지는 참가 포인트가 {p(t.highStakeMinFee)} 이상인 챌린지예요.
            </p>
          </section>
        </>
      )}
    </div>
  )
}

/** 지금 칭호 · 점수 · 다음 칭호까지의 진행 막대 (마이페이지 카드와 같이 쓴다) */
export function TierProgress({ t, to }) {
  // 관리자는 점수 · 승급 · 강등이 없다. 혜택은 가장 높은 칭호와 같다
  if (t.admin) {
    return (
      <div className="bg-summary tr-summary">
        <div className="tr-now">
          <TierChip tier="ADMIN" />
        </div>
        <p>관리자 계정은 칭호 점수 대상이 아니에요. 한도와 고액 챌린지 참여는 가장 높은 칭호와 같아요.</p>
      </div>
    )
  }
  const span = t.next ? t.next.minScore - t.tier.minScore : 1
  const percent = t.next ? Math.min(100, Math.round(((t.score - t.tier.minScore) / span) * 100)) : 100
  const body = (
    <>
      <div className="tr-now">
        <TierChip tier={t.tier.name} />
        <strong>
          {t.score.toLocaleString()}
          <small>점</small>
        </strong>
      </div>
      <div className="bg-bar" aria-hidden="true">
        <span style={{ width: `${percent}%` }} />
      </div>
      <p>
        {t.next
          ? `${TIER_LABEL[t.next.name]}까지 ${(t.next.minScore - t.score).toLocaleString()}점 남았어요.`
          : '가장 높은 칭호예요! 🎉'}
      </p>
    </>
  )
  return to ? (
    <Link to={to} className="bg-summary tr-summary is-link">
      {body}
    </Link>
  ) : (
    <div className="bg-summary tr-summary">{body}</div>
  )
}

function Rule({ sign, label, count, unit, minus = false }) {
  return (
    <li className={minus ? 'is-minus' : undefined}>
      <strong>{sign}</strong>
      <span>{label}</span>
      {count != null && (
        <small>
          지금까지 {count.toLocaleString()}
          {unit}
        </small>
      )}
    </li>
  )
}
