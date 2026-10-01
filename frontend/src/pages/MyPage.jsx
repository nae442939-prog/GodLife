import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { profileApi, rankingApi, userApi, verificationApi, walletApi } from '../api/client.js'
import { useAuth } from '../auth/useAuth.js'
import { FollowListPopup } from '../profile/FollowListPopup.jsx'
import { ProfileBadges, TierChip } from '../profile/ProfileCard.jsx'
import { ProfileEditPopup } from '../profile/ProfileEditPopup.jsx'

/**
 * 마이페이지 (/me, 사용자 시안): 큰 동그란 사진(카메라 버튼) | 닉네임 [수정] · 한 줄 소개 · 이메일 | 팔로워 · 팔로잉,
 * 제목 줄 오른쪽에 [설정] → 뱃지 → 한눈에 보기(내 챌린지 · 포인트 지갑 · 뱃지 · 랭킹의 지금 숫자)
 * → 계정(알림 설정 · 비밀번호 변경 · 로그아웃) → 포인트 안내.
 * 팔로워 · 팔로잉 숫자를 누르면 목록 창이 떠서 팔로우를 취소하거나 맞팔로우할 수 있다.
 * [수정]을 누르면(또는 ?edit=1 로 들어오면) 프로필 수정 창이 뜬다. 프로필 수정은 설정이 아니라 여기서 한다.
 */
export function MyPage() {
  const { user, updateUser, logout } = useAuth()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  // 프로필 수정 창: 주소의 ?edit=1 로 여닫는다 (다른 화면의 [프로필 수정]에서도 바로 열 수 있게)
  const editing = params.get('edit') === '1'
  const openEdit = useCallback(() => setParams({ edit: '1' }, { replace: true }), [setParams])
  const closeEdit = useCallback(() => setParams({}, { replace: true }), [setParams])
  const [state, setState] = useState({ profile: null, error: '' })
  const [photo, setPhoto] = useState({ busy: false, error: '' })
  // 한눈에 보기 카드의 숫자 (못 불러온 것은 숫자 없이 보여 준다)
  const [glance, setGlance] = useState({ ongoing: null, points: null, rank: undefined })
  // 떠 있는 팔로우 목록 창: 'following' | 'followers' | null
  const [followList, setFollowList] = useState(null)
  // 팔로우를 바꾸면 숫자를 다시 불러온다
  const [reload, setReload] = useState(0)
  const fileRef = useRef(null)

  useEffect(() => {
    let cancelled = false
    profileApi
      .get(user.id)
      .then((profile) => !cancelled && setState({ profile, error: '' }))
      .catch((err) => !cancelled && setState({ profile: null, error: err.message }))
    return () => {
      cancelled = true
    }
    // 닉네임 · 사진 · 소개를 바꾸거나 팔로우가 바뀌면 다시 불러온다
  }, [user.id, user.nickname, user.profileImageUrl, user.bio, reload])

  useEffect(() => {
    let cancelled = false
    Promise.allSettled([verificationApi.myChallenges(), walletApi.get(), rankingApi.users('month_verify')]).then(
      ([challenges, wallet, ranking]) => {
        if (cancelled) return
        setGlance({
          ongoing:
            challenges.status === 'fulfilled' ? challenges.value.filter((c) => c.joined && c.inProgress).length : null,
          points: wallet.status === 'fulfilled' ? wallet.value.chargedBalance + wallet.value.rewardBalance : null,
          rank: ranking.status === 'fulfilled' ? (ranking.value.me?.rank ?? null) : undefined,
        })
      },
    )
    return () => {
      cancelled = true
    }
  }, [])

  // 사진 위 카메라 버튼: 고르는 즉시 프로필 사진이 바뀐다
  async function changePhoto(e) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    setPhoto({ busy: true, error: '' })
    try {
      updateUser(await userApi.changeProfileImage(file))
      setPhoto({ busy: false, error: '' })
    } catch (err) {
      setPhoto({ busy: false, error: err.message })
    }
  }

  async function onLogout() {
    await logout()
    navigate('/', { replace: true })
  }

  const closeFollowList = useCallback((changed) => {
    setFollowList(null)
    if (changed) setReload((n) => n + 1)
  }, [])

  const u = state.profile

  return (
    <div className="container page mp-page">
      <div className="mp-top">
        <h1 className="mp-heading">마이페이지</h1>
        <Link to="/settings" className="mp-settingsBtn">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
            <circle cx="12" cy="12" r="3.2" strokeWidth="1.8" />
            <path
              d="M12 3v2.4M12 18.6V21M3 12h2.4M18.6 12H21M5.6 5.6l1.7 1.7M16.7 16.7l1.7 1.7M5.6 18.4l1.7-1.7M16.7 7.3l1.7-1.7"
              strokeWidth="1.8"
              strokeLinecap="round"
            />
          </svg>
          설정
        </Link>
      </div>

      <div className="mp-profile">
        <div className="mp-avatarWrap">
          {user.profileImageUrl ? (
            <img className="mp-avatarBig" src={user.profileImageUrl} alt="" />
          ) : (
            <span className="mp-avatarBig" aria-hidden="true">
              {user.nickname.slice(0, 1)}
            </span>
          )}
          <button
            type="button"
            className="mp-avatarEditBtn"
            onClick={() => fileRef.current?.click()}
            disabled={photo.busy}
            aria-label="프로필 사진 바꾸기"
          >
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
              <path
                d="M4 7h3l2-2h6l2 2h3a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1z"
                strokeWidth="1.7"
                strokeLinejoin="round"
              />
              <circle cx="12" cy="13" r="3" strokeWidth="1.7" />
            </svg>
          </button>
          <input ref={fileRef} type="file" accept="image/jpeg,image/png" hidden onChange={changePhoto} />
        </div>

        <div className="mp-profileText">
          <div className="mp-nameRow">
            <span className="mp-nameValue">{user.nickname}</span>
            {u && <TierChip tier={u.tier} />}
            <button type="button" className="mp-fieldEditBtn" onClick={openEdit}>
              수정
            </button>
          </div>
          {user.bio ? (
            <p className="mp-bio">{user.bio}</p>
          ) : (
            <button type="button" className="mp-bio mp-bio-empty" onClick={openEdit}>
              한 줄 소개를 남겨 보세요 ✎
            </button>
          )}
          <p className="mp-email">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
              <circle cx="12" cy="8" r="4" strokeWidth="1.8" />
              <path d="M4 20c0-4 3.6-7 8-7s8 3 8 7" strokeWidth="1.8" strokeLinecap="round" />
            </svg>
            {user.email}
          </p>
          {photo.error && (
            <p className="field-error" role="alert">
              {photo.error}
            </p>
          )}
        </div>

        {u && (
          <div className="mp-follow">
            <button type="button" className="mp-followBtn" onClick={() => setFollowList('followers')}>
              <strong>{u.followerCount}</strong>
              팔로워
            </button>
            <button type="button" className="mp-followBtn" onClick={() => setFollowList('following')}>
              <strong>{u.followingCount}</strong>
              팔로잉
            </button>
          </div>
        )}
      </div>

      {state.error && <p className="form-error">{state.error}</p>}
      {u && (
        <>
          <section className="pf-card mp-badgeCard" id="badges">
            <ProfileBadges u={u} allTo="/me/badges" />
          </section>

          <ul className="mp-menuRow">
            <MenuCard
              to="/challenges/mine"
              kind="chall"
              title="내 챌린지"
              desc={['참여 중인 챌린지와', '인증 현황']}
              preview={glance.ongoing == null ? '' : `진행 중 ${glance.ongoing}개`}
              icon={
                <>
                  <circle cx="11" cy="11" r="7" strokeWidth="2" />
                  <circle cx="11" cy="11" r="3" strokeWidth="2" />
                  <path d="M20 20l-4-4" strokeWidth="2" strokeLinecap="round" />
                </>
              }
            />
            <MenuCard
              to="/wallet"
              kind="point"
              title="포인트 지갑"
              desc={['보유 포인트와', '거래 내역']}
              preview={glance.points == null ? '' : `${glance.points.toLocaleString()}P 보유`}
              icon={
                <>
                  <rect x="3" y="6" width="18" height="13" rx="2.5" strokeWidth="2" />
                  <path d="M3 10h18" strokeWidth="2" />
                  <circle cx="17" cy="14.5" r="1.3" fill="currentColor" stroke="none" />
                </>
              }
            />
            <MenuCard
              to="/me/badges"
              kind="badge"
              title="뱃지"
              desc={['획득한 칭호와', '업적 모아보기']}
              preview={`${u.badges.filter((b) => b.earned).length}개 보유`}
              icon={
                <>
                  <circle cx="12" cy="9" r="6" strokeWidth="2" />
                  <path d="M8.5 14L6 21l6-3 6 3-2.5-7" strokeWidth="2" strokeLinejoin="round" />
                </>
              }
            />
            <MenuCard
              to="/rankings"
              kind="rank"
              title="랭킹"
              desc={['내 순위와 성공률,', '연속 달성']}
              preview={
                glance.rank === undefined ? '' : glance.rank == null ? '이번 달 순위 없음' : `이번 달 ${glance.rank}위`
              }
              icon={
                <>
                  <path d="M7 4h10v5a5 5 0 0 1-10 0V4z" strokeWidth="2" strokeLinejoin="round" />
                  <path d="M12 14v4M9 21h6" strokeWidth="2" strokeLinecap="round" />
                  <path d="M7 6H4v2a3 3 0 0 0 3 3M17 6h3v2a3 3 0 0 1-3 3" strokeWidth="1.8" strokeLinecap="round" />
                </>
              }
            />
          </ul>
        </>
      )}

      <h2 className="mp-sectionTitle">계정</h2>
      <ul className="mp-acctList">
        <li>
          <Link to="/settings?tab=alarm" className="mp-acctRow">
            <span className="mp-acctIcon" aria-hidden="true">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                <path
                  d="M12 3a5 5 0 0 0-5 5v3.5c0 1-.4 2-1.2 2.7L4 16h16l-1.8-1.8c-.8-.7-1.2-1.7-1.2-2.7V8a5 5 0 0 0-5-5z"
                  strokeWidth="1.7"
                  strokeLinejoin="round"
                />
                <path d="M9.5 19a2.5 2.5 0 0 0 5 0" strokeWidth="1.7" strokeLinecap="round" />
              </svg>
            </span>
            <span className="mp-acctLabel">알림 설정</span>
            <span className="mp-soonTag">준비 중</span>
            <Chevron />
          </Link>
        </li>
        <li>
          <Link to="/settings?tab=password" className="mp-acctRow">
            <span className="mp-acctIcon" aria-hidden="true">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                <rect x="5" y="11" width="14" height="9" rx="2" strokeWidth="1.7" />
                <path d="M8 11V8a4 4 0 0 1 8 0v3" strokeWidth="1.7" />
              </svg>
            </span>
            <span className="mp-acctLabel">비밀번호 변경</span>
            <Chevron />
          </Link>
        </li>
        <li>
          <button type="button" className="mp-acctRow" onClick={onLogout}>
            <span className="mp-acctIcon is-danger" aria-hidden="true">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" strokeWidth="1.7" strokeLinecap="round" />
                <path d="M16 17l5-5-5-5M21 12H9" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </span>
            <span className="mp-acctLabel is-danger">로그아웃</span>
          </button>
        </li>
      </ul>

      <div className="mp-noticeBanner">
        <svg className="mp-noticeIcon" width="15" height="15" viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="12" cy="12" r="9" stroke="#c2932a" strokeWidth="1.8" fill="none" />
          <path d="M12 8v5" stroke="#c2932a" strokeWidth="1.8" strokeLinecap="round" />
          <circle cx="12" cy="16" r="1" fill="#c2932a" />
        </svg>
        <span className="mp-noticeText">
          갓생살기의 포인트는 챌린지 참여와 포인트 상점에서만 사용할 수 있습니다.
        </span>
      </div>

      {followList && <FollowListPopup key={followList} mode={followList} onClose={closeFollowList} />}
      {editing && <ProfileEditPopup onClose={closeEdit} />}
    </div>
  )
}

/** 한눈에 보기 카드 (시안): 아이콘 배지 + 제목 + 두 줄 설명 + 지금 숫자 */
function MenuCard({ to, kind, title, desc, preview, icon }) {
  return (
    <li>
      <Link to={to} className={`mp-menuCard is-${kind}`}>
        <span className="mp-menuIcon" aria-hidden="true">
          <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor">
            {icon}
          </svg>
        </span>
        <span className="mp-menuTitle">{title}</span>
        <span className="mp-menuDesc">
          {desc[0]}
          <br />
          {desc[1]}
        </span>
        <span className="mp-menuPreview">{preview}</span>
      </Link>
    </li>
  )
}

function Chevron() {
  return (
    <svg className="mp-acctChevron" width="14" height="14" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M9 5l7 7-7 7" stroke="currentColor" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}
