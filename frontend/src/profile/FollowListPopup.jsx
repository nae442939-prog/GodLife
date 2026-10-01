import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { profileApi } from '../api/client.js'

const TITLE = { following: '팔로잉', followers: '팔로워' }

/**
 * 마이페이지에서 팔로잉 · 팔로워 숫자를 누르면 뜨는 작은 창: 그 사람들 목록과 [팔로우 취소] / [맞팔로우하기].
 * 취소한 사람은 창을 닫기 전까지 목록에 남아 있어 잘못 눌렀으면 바로 [다시 팔로우]할 수 있다.
 * @param mode    'following'(내가 팔로우하는 사람) | 'followers'(나를 팔로우하는 사람)
 * @param onClose 닫을 때 부른다. 바뀐 것이 있으면 changed = true
 */
export function FollowListPopup({ mode, onClose }) {
  const [state, setState] = useState({ users: null, error: '' })
  // 회원 id → 지금 내가 팔로우 중인지 (누를 때마다 바뀐다)
  const [mine, setMine] = useState({})
  const [busyId, setBusyId] = useState(null)
  const changed = useRef(false)
  const closeRef = useRef(null)

  useEffect(() => {
    let cancelled = false
    profileApi[mode]()
      .then((users) => {
        if (cancelled) return
        setState({ users, error: '' })
        // 팔로잉 목록은 전부 내가 팔로우 중, 팔로워 목록은 맞팔로우인 사람만
        setMine(Object.fromEntries(users.map((u) => [u.userId, mode === 'following' || u.mutual])))
      })
      .catch((err) => !cancelled && setState({ users: null, error: err.message }))
    return () => {
      cancelled = true
    }
  }, [mode])

  useEffect(() => {
    function onKeyDown(e) {
      if (e.key === 'Escape') onClose(changed.current)
    }
    document.addEventListener('keydown', onKeyDown)
    closeRef.current?.focus()
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  async function toggle(u) {
    setBusyId(u.userId)
    try {
      await (mine[u.userId] ? profileApi.unfollow(u.userId) : profileApi.follow(u.userId))
      changed.current = true
      setMine((cur) => ({ ...cur, [u.userId]: !cur[u.userId] }))
      setState((s) => ({ ...s, error: '' }))
    } catch (err) {
      setState((s) => ({ ...s, error: err.message }))
    } finally {
      setBusyId(null)
    }
  }

  const users = state.users

  return (
    <div className="fl-back" onClick={() => onClose(changed.current)}>
      <div
        className="fl-pop"
        role="dialog"
        aria-modal="true"
        aria-label={`${TITLE[mode]} 목록`}
        onClick={(e) => e.stopPropagation()}
      >
        <header className="fl-head">
          <h2>
            {TITLE[mode]} {users && <small>{users.length}</small>}
          </h2>
          <button
            type="button"
            className="fl-close"
            onClick={() => onClose(changed.current)}
            aria-label="닫기"
            ref={closeRef}
          >
            ✕
          </button>
        </header>

        {state.error && <p className="form-error fl-error">{state.error}</p>}
        {!users ? (
          !state.error && <p className="muted fl-empty">불러오는 중…</p>
        ) : users.length === 0 ? (
          <p className="muted fl-empty">
            {mode === 'following' ? '아직 팔로우한 사람이 없어요.' : '아직 나를 팔로우하는 사람이 없어요.'}
          </p>
        ) : (
          <ul className="fl-list">
            {users.map((u) => {
              const following = mine[u.userId]
              // 서로 팔로우 = 내가 팔로우 중이고, 상대도 나를 팔로우한다 (팔로워 목록은 상대가 항상 나를 팔로우)
              const mutual = following && (mode === 'followers' || u.mutual)
              return (
                <li key={u.userId} className="fl-row">
                  <Link to={`/users/${u.userId}`} className="fl-user">
                    {u.profileImageUrl ? (
                      <img className="fl-avatar" src={u.profileImageUrl} alt="" />
                    ) : (
                      <span className="fl-avatar" aria-hidden="true">
                        {u.nickname.slice(0, 1)}
                      </span>
                    )}
                    <span className="fl-text">
                      <strong>
                        {u.nickname}
                        {mutual && <small>맞팔로우</small>}
                      </strong>
                      {u.bio && <span>{u.bio}</span>}
                    </span>
                  </Link>
                  <button
                    type="button"
                    className={`fl-btn${following ? ' is-cancel' : ''}`}
                    onClick={() => toggle(u)}
                    disabled={busyId === u.userId}
                  >
                    {following ? '팔로우 취소' : mode === 'followers' ? '맞팔로우하기' : '다시 팔로우'}
                  </button>
                </li>
              )
            })}
          </ul>
        )}
      </div>
    </div>
  )
}
