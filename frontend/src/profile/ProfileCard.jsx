import { Link } from 'react-router-dom'
import { BADGE_EMOJI } from './badges.js'

const TIER = {
  BRONZE: { label: '브론즈', emoji: '🥉' },
  SILVER: { label: '실버', emoji: '🥈' },
  GOLD: { label: '골드', emoji: '🥇' },
  PLATINUM: { label: '플래티넘', emoji: '💠' },
  DIAMOND: { label: '다이아몬드', emoji: '💎' },
}
// 프로필 카드에는 일부만 보여 준다: 딴 뱃지 먼저, 남는 자리는 아직인 뱃지 (전체는 마이페이지의 '뱃지 전체 보기')
const BADGE_PREVIEW = 7

/**
 * 프로필 카드 (프로필 화면 · 마이페이지가 같이 쓴다): 사진 · 닉네임 · 칭호(티어) · 한 줄 소개 · 팔로워 → 뱃지 → 기록 4칸.
 * @param u       프로필 (GET /api/users/:id/profile)
 * @param actions 오른쪽 위 버튼들 (팔로우·메시지 또는 프로필 수정·설정)
 * @param bioHint 한 줄 소개가 없을 때 대신 보여 줄 것 (내 프로필에서 '소개를 남겨 보세요')
 * @param meta    가입일 옆에 이어 붙일 것 (남의 프로필의 [차단] 글씨 버튼)
 */
export function ProfileCard({ u, actions, bioHint, meta, error }) {
  const tier = TIER[u.tier] ?? TIER.BRONZE
  return (
    <section className="pf-card">
      <div className="pf-head">
        <div className="pf-identity">
          {u.profileImageUrl ? (
            <img className="pf-avatar" src={u.profileImageUrl} alt="" />
          ) : (
            <span className="pf-avatar" aria-hidden="true">
              {u.nickname.slice(0, 1)}
            </span>
          )}
          <div className="pf-info">
            <h1 className="pf-name">
              {u.nickname}
              <span className={`pf-tier is-${u.tier.toLowerCase()}`} title="칭호">
                <span aria-hidden="true">{tier.emoji}</span> {tier.label}
              </span>
              {u.mine && <small>나</small>}
              {u.following && u.followsMe && <small className="is-mutual">맞팔로우</small>}
              {!u.following && u.followsMe && <small className="is-follows-me">나를 팔로우해요</small>}
            </h1>
            {u.bio ? <p className="pf-bio">{u.bio}</p> : bioHint}
            <p className="pf-joined">
              팔로워 <strong>{u.followerCount}</strong> · 팔로잉 <strong>{u.followingCount}</strong>
              {u.joinedAt && ` · ${u.joinedAt.replaceAll('-', '.')} 가입`}
              {meta && <> · {meta}</>}
            </p>
          </div>
        </div>
        {actions && <div className="pf-actions">{actions}</div>}
      </div>

      {error && <p className="form-error">{error}</p>}

      <ProfileBadges u={u} />
      <ProfileStats u={u} />
    </section>
  )
}

/**
 * 뱃지 줄 (미리보기 7개): 딴 것 먼저 또렷하게, 남는 자리는 아직인 것을 흐리게 + 따는 조건.
 * @param allTo 주면 제목 옆에 [전체 보기] 링크가 붙는다 (마이페이지 → /me/badges)
 */
export function ProfileBadges({ u, allTo }) {
  const earned = u.badges.filter((b) => b.earned)
  const shown = [...earned, ...u.badges.filter((b) => !b.earned)].slice(0, BADGE_PREVIEW)
  return (
    <div className="pf-badges">
      <h2>
        뱃지{' '}
        <small>
          {earned.length}/{u.badges.length}
        </small>
        {allTo && (
          <Link to={allTo} className="pf-badges-all">
            전체 보기 ›
          </Link>
        )}
      </h2>
      <ul>
        {shown.map((b) => (
          <li key={b.code} className={`pf-badge${b.earned ? ' is-earned' : ''}`} title={b.description}>
            <span className="pf-badge-icon" aria-hidden="true">
              {BADGE_EMOJI[b.code] ?? '🏅'}
            </span>
            <span className="pf-badge-name">{b.name}</span>
            <span className="pf-badge-desc">{b.earned ? '획득' : b.description}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

/** 기록 4칸: 이번 달 성공 · 최장 연속 · 누적 성공률 · 완주한 챌린지 */
export function ProfileStats({ u }) {
  const stats = [
    { key: 'verify', label: '이번 달 성공', value: `${u.monthVerify}회`, icon: <CheckIcon /> },
    { key: 'streak', label: '최장 연속', value: `${u.maxStreak}일`, icon: <FlameIcon /> },
    {
      key: 'rate',
      label: '누적 성공률',
      value: u.successRate == null ? '—' : `${u.successRate.toFixed(1)}%`,
      empty: u.successRate == null,
      icon: <RateIcon />,
    },
    { key: 'done', label: '완주한 챌린지', value: `${u.completedCount}개`, icon: <StarIcon /> },
  ]
  return (
    <ul className="pf-stats">
      {stats.map((s) => (
        <li key={s.key} className={`pf-stat is-${s.key}`}>
          <span className="pf-stat-icon" aria-hidden="true">
            {s.icon}
          </span>
          <strong className={s.empty ? 'is-empty' : undefined}>{s.value}</strong>
          <span>{s.label}</span>
        </li>
      ))}
    </ul>
  )
}

/** 칭호(티어) 칩 */
export function TierChip({ tier }) {
  const t = TIER[tier] ?? TIER.BRONZE
  return (
    <span className={`pf-tier is-${(tier ?? 'BRONZE').toLowerCase()}`} title="칭호">
      <span aria-hidden="true">{t.emoji}</span> {t.label}
    </span>
  )
}

function CheckIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none">
      <path d="M4 12l5 5L20 6" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function FlameIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24">
      <path d="M12 2c-3 4-7 8-7 12a7 7 0 0 0 14 0c0-4-4-8-7-12z" fill="currentColor" />
    </svg>
  )
}

function RateIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor">
      <circle cx="12" cy="12" r="8" strokeWidth="2" />
      <path d="M9 12h6" strokeWidth="2" strokeLinecap="round" />
    </svg>
  )
}

function StarIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24">
      <path d="M12 2l2.4 6.6L21 9l-5.4 4.4L17.4 21 12 17l-5.4 4 1.8-7.6L3 9l6.6-.4L12 2z" fill="currentColor" />
    </svg>
  )
}

/** 내 프로필에 한 줄 소개가 없을 때: 남기러 가는 링크 */
export function BioHint() {
  return (
    <Link to="/me?edit=1" className="pf-bio pf-bio-empty">
      한 줄 소개를 남겨 보세요 ✎
    </Link>
  )
}

/** 내 프로필 카드의 버튼: [프로필 수정] [설정] */
export function MyProfileActions() {
  return (
    <>
      <Link to="/me?edit=1" className="pf-follow">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
          <path d="M4 20h4L19 9l-4-4L4 16v4z" strokeWidth="1.8" strokeLinejoin="round" />
          <path d="M13.5 6.5l4 4" strokeWidth="1.8" />
        </svg>
        프로필 수정
      </Link>
      <Link to="/settings" className="pf-msg">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
          <circle cx="12" cy="12" r="3.2" strokeWidth="1.8" />
          <path
            d="M12 3v2.4M12 18.6V21M3 12h2.4M18.6 12H21M5.6 5.6l1.7 1.7M16.7 16.7l1.7 1.7M5.6 18.4l1.7-1.7M16.7 7.3l1.7-1.7"
            strokeWidth="1.8"
            strokeLinecap="round"
          />
        </svg>
        설정
      </Link>
    </>
  )
}
