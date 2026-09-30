import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { rankingApi } from '../api/client.js'
import { useAuth } from '../auth/useAuth.js'
import { Avatar } from '../components/UserMenu.jsx'
import { MyChallengeRanking } from '../ranking/MyChallengeRanking.jsx'
import { Podium } from '../ranking/Podium.jsx'

// 헤더 [랭킹] 메뉴에서 고른 랭킹 하나만 그 이름을 제목으로 보여 준다 (?tab=)
const PAGES = {
  users: { title: '전체 랭킹', sub: '누가 가장 꾸준히 갓생 살고 있을까요?' },
  friends: { title: '친구 랭킹', sub: '팔로우한 친구들끼리 비교해요.' },
  challenges: { title: '챌린지 랭킹', sub: '내가 하는 챌린지에서 누가 가장 꾸준할까요?' },
}

const METRICS = [
  { value: 'month_verify', label: '이번 달 인증', unit: '회' },
  { value: 'max_streak', label: '최장 연속', unit: '일' },
  { value: 'month_reward', label: '이번 달 보상', unit: 'P' },
  { value: 'success_rate', label: '누적 성공률', unit: '%' },
]

/**
 * 랭킹: 전체(개인) · 친구(팔로우, 준비 중) · 챌린지(팀) 중 하나. 어느 랭킹인지와 기준은 주소(?tab=&metric=)에 둔다.
 * 비로그인도 볼 수 있고, 로그인하면 내 순위가 맨 위에 따로 나온다.
 */
export function RankingPage() {
  const [params, setParams] = useSearchParams()
  const tab = PAGES[params.get('tab')] ? params.get('tab') : 'users'
  const metric = params.get('metric') ?? 'month_verify'
  const page = PAGES[tab]

  function update(name, value) {
    const next = new URLSearchParams(params)
    next.set(name, value)
    setParams(next, { replace: true })
  }

  return (
    <div className="rn-page">
      <div className="container page">
        <div className="ch-head cl-head">
          <div>
            <h1 className="page-title">{page.title}</h1>
            <p className="page-sub">{page.sub}</p>
          </div>
        </div>

        <div className="rn-body">
          {tab === 'users' && <UserRanking metric={metric} onMetric={(m) => update('metric', m)} />}
          {tab === 'friends' && <UserRanking friends metric={metric} onMetric={(m) => update('metric', m)} />}
          {tab === 'challenges' && (
            <MyChallengeRanking selectedId={params.get('challenge')} onSelect={(id) => update('challenge', id)} />
          )}
        </div>
      </div>
    </div>
  )
}

/** 전체 랭킹 · 친구 랭킹(friends: 나 + 내가 팔로우한 사람, 로그인 필요)이 같이 쓴다 */
function UserRanking({ metric, onMetric, friends = false }) {
  const { status } = useAuth()
  const [data, setData] = useState({ key: null, top: [], me: null, error: '' })
  const key = `${friends}|${metric}|${status}`
  const m = METRICS.find((x) => x.value === metric) ?? METRICS[0]

  useEffect(() => {
    if (status === 'loading' || (friends && status !== 'authed')) return
    let cancelled = false
    rankingApi[friends ? 'friends' : 'users'](metric)
      .then((r) => !cancelled && setData({ key, top: r.top, me: r.me, error: '' }))
      .catch((err) => !cancelled && setData({ key, top: [], me: null, error: err.message }))
    return () => {
      cancelled = true
    }
  }, [metric, status, key, friends])

  const value = (v) => `${m.value === 'success_rate' ? v.toFixed(1) : Math.round(v).toLocaleString()}${m.unit}`

  if (friends && status === 'anon') {
    return (
      <div className="my-ch-empty">
        <p>로그인하면 팔로우한 친구들과 순위를 비교할 수 있어요.</p>
        <Link to="/login" state={{ from: '/rankings?tab=friends' }} className="btn btn-dark">
          로그인
        </Link>
      </div>
    )
  }
  // 친구 랭킹인데 팔로우한 사람이 없으면(나만 있거나 아무도 없음) 팔로우 안내
  const lonely = friends && data.key === key && !data.error && data.top.every((r) => r.mine)

  return (
    <>
      {lonely && (
        <div className="rn-hint">
          아직 팔로우한 친구가 없어요. <Link to="/rankings?tab=users">전체 랭킹</Link>이나 챌린지 참가자에서 프로필을
          눌러 팔로우해 보세요.
        </div>
      )}
      <div className="rn-metrics" role="group" aria-label="랭킹 기준">
        {METRICS.map((x) => (
          <button
            key={x.value}
            type="button"
            className={`rn-metric${x.value === m.value ? ' is-active' : ''}`}
            aria-pressed={x.value === m.value}
            onClick={() => onMetric(x.value)}
          >
            {x.label}
          </button>
        ))}
      </div>

      {data.key !== key ? (
        <p className="muted">불러오는 중…</p>
      ) : data.error ? (
        <p className="form-error">{data.error}</p>
      ) : (
        <>
          <Podium entries={data.top} format={value} />
          {status === 'authed' && (
            <div className="rn-me">
              {data.me ? (
                <>
                  <span>내 순위</span>
                  <strong>{data.me.rank}위</strong>
                  <span className="rn-me-value">{value(data.me.value)}</span>
                </>
              ) : (
                <span>아직 기록이 없어요. 챌린지에 참여하고 인증하면 순위에 올라요!</span>
              )}
            </div>
          )}
          {data.top.length === 0 ? (
            <p className="muted">아직 랭킹에 오른 사람이 없어요.</p>
          ) : (
            <ol className="rk">
              {data.top.map((r, i) => (
                <li
                  key={i}
                  className={`rk-row is-link${r.mine ? ' is-mine' : ''}${r.rank <= 3 ? ` is-top${r.rank}` : ''}`}
                >
                  {/* 줄 전체가 프로필 링크: 마우스를 올리면 누구를 고르는지 보이게 떠오른다 */}
                  <Link to={`/users/${r.userId}`} className="rk-row-link" aria-label={`${r.nickname} 프로필 보기`}>
                    <span className="rk-rank">{r.rank}</span>
                    {r.profileImageUrl ? (
                      <Avatar src={r.profileImageUrl} size={30} />
                    ) : (
                      <span className="rk-initial" aria-hidden="true">
                        {r.nickname.slice(0, 1)}
                      </span>
                    )}
                    <span className="rk-name">
                      {r.nickname}
                      {r.mine && <small>나</small>}
                    </span>
                    <span className="rn-value">{value(r.value)}</span>
                    <span className="rk-go" aria-hidden="true">
                      프로필 보기 ›
                    </span>
                  </Link>
                </li>
              ))}
            </ol>
          )}
        </>
      )}
    </>
  )
}
