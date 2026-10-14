import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { profileApi } from '../api/client.js'
import { useAuth } from '../auth/useAuth.js'
import { badgeImage } from '../profile/badges.js'
import { CHECK_EVENT } from './badgeCheck.js'


// 이 브라우저에서 이미 축하한 뱃지 코드. 저장소를 못 쓰면 undefined (그때는 축하 창을 띄우지 않는다)
const seenKey = (userId) => `godlife.seenBadges.${userId}`

function readSeen(userId) {
  try {
    const value = localStorage.getItem(seenKey(userId))
    return value === null ? null : JSON.parse(value)
  } catch {
    return undefined
  }
}

function writeSeen(userId, codes) {
  try {
    localStorage.setItem(seenKey(userId), JSON.stringify(codes))
  } catch {
    // 저장하지 못하면 다음에 또 확인할 뿐이다
  }
}

/**
 * 새로 딴 뱃지가 있으면 '뱃지를 받았어요!' 창을 띄운다. 화면을 옮길 때와 checkBadges() 를 부를 때 확인한다.
 * 뱃지는 서버가 기록으로 그때그때 계산하므로, 이미 축하한 뱃지는 브라우저에 적어 두고 새로 생긴 것만 띄운다.
 * 이 브라우저에서 처음 확인할 때는 지금 가진 뱃지를 적어만 둔다 (예전에 딴 뱃지를 한꺼번에 축하하지 않게).
 */
export function BadgeWatcher() {
  const { user, status } = useAuth()
  const { pathname } = useLocation()
  const [tick, setTick] = useState(0)
  const [fresh, setFresh] = useState([])
  const [equip, setEquip] = useState({ busy: false, done: false, error: '' })
  const userId = status === 'authed' ? user.id : null

  useEffect(() => {
    const onCheck = () => setTick((t) => t + 1)
    window.addEventListener(CHECK_EVENT, onCheck)
    return () => window.removeEventListener(CHECK_EVENT, onCheck)
  }, [])

  useEffect(() => {
    if (!userId) return
    let cancelled = false
    profileApi
      .get(userId)
      .then((profile) => {
        if (cancelled) return
        const earned = profile.badges.filter((b) => b.earned)
        const seen = readSeen(userId)
        if (seen === undefined) return
        writeSeen(userId, earned.map((b) => b.code))
        if (seen === null) return
        const news = earned.filter((b) => !seen.includes(b.code))
        if (news.length > 0) {
          setFresh(news)
          setEquip({ busy: false, done: false, error: '' })
        }
      })
      .catch(() => {
        // 축하 창은 덤이라 못 불러오면 조용히 넘어간다
      })
    return () => {
      cancelled = true
    }
  }, [userId, pathname, tick])

  if (fresh.length === 0) return null
  const badge = fresh[0]
  const close = () => setFresh([])

  async function wear() {
    setEquip({ busy: true, done: false, error: '' })
    try {
      await profileApi.equipBadge(badge.code)
      setEquip({ busy: false, done: true, error: '' })
    } catch (err) {
      setEquip({ busy: false, done: false, error: err.message })
    }
  }

  return (
    <div className="fl-back cele-back" onClick={close}>
      <div
        className="fl-pop cele-pop"
        role="dialog"
        aria-modal="true"
        aria-label="뱃지를 받았어요"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="cele-burst" aria-hidden="true">
          <img src={badgeImage(badge.code)} alt="" />
        </div>
        <p className="cele-eyebrow">축하해요!</p>
        <h2 className="cele-title">뱃지를 받았어요 🎉</h2>
        <p className="cele-name">{badge.name}</p>
        <p className="cele-desc">{badge.description}</p>
        {fresh.length > 1 && <p className="cele-more">이 밖에도 뱃지 {fresh.length - 1}개를 더 받았어요.</p>}
        {equip.error && <p className="form-error">{equip.error}</p>}
        <div className="cele-actions">
          {equip.done ? (
            <p className="cele-done">착용했어요! 이름 옆에 보여요.</p>
          ) : (
            <button type="button" className="btn btn-outline" onClick={wear} disabled={equip.busy}>
              {equip.busy ? '착용하는 중…' : '이름 옆에 착용하기'}
            </button>
          )}
          <button type="button" className="btn btn-dark" onClick={close}>
            확인
          </button>
        </div>
        <Link to="/me/badges" className="cele-link" onClick={close}>
          내 뱃지 전체 보기 ›
        </Link>
      </div>
    </div>
  )
}

/** 회원가입을 마치고 처음 홈에 왔을 때 한 번 띄우는 환영 창 (시안: 어두운 막 위에 상자 없이 떠 있는 민트 동그라미) */
export function WelcomePopup({ nickname, onClose }) {
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose()
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    // 어두운 막 자체를 눌렀을 때만 닫힌다 (글자 · 버튼 사이 빈 곳은 그대로)
    <div className="wel-overlay" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <div className="wel-modal" role="dialog" aria-modal="true" aria-labelledby="wel-title">
        <div className="wel-badge" aria-hidden="true">
          <svg width="30" height="30" viewBox="0 0 24 24">
            <path d="M5 12.5l4.5 4.5L19 7" stroke="#ffffff" strokeWidth="2.6" fill="none" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </div>
        <h2 id="wel-title" className="wel-title">
          {nickname}님, 가입을 축하해요!
        </h2>
        <p className="wel-desc">
          갓생살기에 오신 걸 환영해요.
          <br />
          이렇게 시작해 보세요.
        </p>
        <ol className="wel-steps">
          <li>오늘 시작하는 챌린지에 참여해요</li>
          <li>카메라로 인증 사진을 찍어 올려요</li>
          <li>첫 인증에 성공하면 첫 뱃지를 받아요</li>
        </ol>
        <div className="wel-actions">
          <button type="button" className="wel-btn wel-btn--ghost" onClick={onClose}>
            닫기
          </button>
          <Link to="/challenges" className="wel-btn wel-btn--primary" onClick={onClose}>
            챌린지 둘러보기
          </Link>
        </div>
      </div>
    </div>
  )
}
