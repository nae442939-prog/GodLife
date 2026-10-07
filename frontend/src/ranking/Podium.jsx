import { Link } from 'react-router-dom'

/**
 * 랭킹 시상대 (1·2·3등 단상). 순위를 억지로 3명으로 자르지 않고, 같은 기록끼리 묶어
 * 가장 높은 기록 = 금, 다음 = 은, 그다음 = 동 단상에 인원수만큼 아바타를 겹쳐 올린다.
 * 배치는 은 · 금 · 동 (가운데가 가장 높다).
 */
const SHOWN_AVATARS = 5
const SHOWN_NAMES = 4
const AVATAR_COLORS = ['#b8793f', '#6fa15a', '#5b8fb0', '#8a6bb0', '#c2932a', '#4f9c8f']

export function Podium({ entries, format }) {
  const tiers = []
  for (const e of entries) {
    const last = tiers[tiers.length - 1]
    if (last && last.value === e.value) last.people.push(e)
    else if (tiers.length < 3) tiers.push({ value: e.value, people: [e] })
    else break
  }
  if (tiers.length === 0) return null

  const [gold, silver, bronze] = tiers
  return (
    <div className="pd" aria-label="시상대">
      <Column tier={silver} place={2} kind="silver" format={format} />
      <Column tier={gold} place={1} kind="gold" format={format} />
      <Column tier={bronze} place={3} kind="bronze" format={format} />
    </div>
  )
}

function Column({ tier, place, kind, format }) {
  if (!tier) return <div className={`pd-col is-${kind} is-empty`} aria-hidden="true" />

  const people = tier.people
  const names = people.slice(0, SHOWN_NAMES).map((p) => p.nickname)
  const rest = people.length - names.length
  const mine = people.some((p) => p.mine)

  return (
    <div className={`pd-col is-${kind}`}>
      {place === 1 && (
        <svg className="pd-crown" width="26" height="20" viewBox="0 0 24 18" aria-hidden="true">
          <path d="M2 16L1 6l5 4 6-8 6 8 5-4-1 10H2z" fill="#e8a93f" stroke="#c2851f" strokeWidth="1" />
        </svg>
      )}
      <div className="pd-avatars">
        {people.slice(0, SHOWN_AVATARS).map((p, i) => (
          <Link key={i} to={`/users/${p.userId}`} className="pd-avatar-link" aria-label={`${p.nickname} 프로필`}>
            {p.profileImageUrl ? (
              <img className="pd-avatar" src={p.profileImageUrl} alt="" />
            ) : (
              <span className="pd-avatar" style={{ background: colorOf(p.nickname) }} aria-hidden="true">
                {p.nickname.slice(0, 1)}
              </span>
            )}
          </Link>
        ))}
        {people.length > SHOWN_AVATARS && (
          <span className="pd-avatar is-more" aria-hidden="true">
            +{people.length - SHOWN_AVATARS}
          </span>
        )}
      </div>
      <p className="pd-names">
        {people.slice(0, SHOWN_NAMES).map((p, i) => (
          <span key={i}>
            <Link to={`/users/${p.userId}`} className="pd-name">
              {p.nickname}
            </Link>
            {i < names.length - 1 && ','}
          </span>
        ))}
        {rest > 0 && ` 외 ${rest}명`}
        {mine && <span className="pd-me">나</span>}
      </p>
      <span className="pd-count">{format(tier.value)}</span>
      <div className="pd-block">
        <span className="pd-rank">{place}</span>
      </div>
    </div>
  )
}

/** 닉네임마다 늘 같은 색 */
function colorOf(nickname) {
  let h = 0
  for (const ch of nickname) h = (h * 31 + ch.codePointAt(0)) >>> 0
  return AVATAR_COLORS[h % AVATAR_COLORS.length]
}
