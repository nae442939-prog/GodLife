import { useEffect, useRef, useState } from 'react'
import { userApi } from '../api/client.js'
import { NICKNAME_HINT, nicknameError } from '../auth/rules.js'
import { useAuth } from '../auth/useAuth.js'
import { Field } from '../components/Field.jsx'

const BIO_MAX = 200

/**
 * 프로필 수정 창 (마이페이지의 [수정]): 사진 · 닉네임 · 한 줄 소개.
 * 위쪽 미리보기에 고치는 대로 바로 보인다. 사진은 고르는 즉시 바뀌고, 닉네임 · 한 줄 소개는 [저장]을 눌러야 바뀐다.
 */
export function ProfileEditPopup({ onClose }) {
  const { user, updateUser } = useAuth()
  const [nickname, setNickname] = useState(user.nickname)
  const [bio, setBio] = useState(user.bio ?? '')
  const [errors, setErrors] = useState({})
  const [busy, setBusy] = useState(false)
  const [photoBusy, setPhotoBusy] = useState(false)
  const fileRef = useRef(null)

  useEffect(() => {
    function onKeyDown(e) {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  const changed = nickname !== user.nickname || bio.trim() !== (user.bio ?? '')

  async function save(e) {
    e.preventDefault()
    const problem = nicknameError(nickname)
    if (problem) {
      setErrors({ nickname: problem })
      return
    }
    setBusy(true)
    setErrors({})
    try {
      updateUser(await userApi.updateProfile({ nickname, bio: bio.trim() }))
      onClose()
    } catch (err) {
      if (err.code === 'DUPLICATE_NICKNAME') setErrors({ nickname: err.message })
      else setErrors({ form: err.message })
      setBusy(false)
    }
  }

  async function changePhoto(e) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    setPhotoBusy(true)
    setErrors((cur) => ({ ...cur, photo: '' }))
    try {
      updateUser(await userApi.changeProfileImage(file))
    } catch (err) {
      setErrors((cur) => ({ ...cur, photo: err.message }))
    } finally {
      setPhotoBusy(false)
    }
  }

  async function removePhoto() {
    setPhotoBusy(true)
    setErrors((cur) => ({ ...cur, photo: '' }))
    try {
      updateUser(await userApi.removeProfileImage())
    } catch (err) {
      setErrors((cur) => ({ ...cur, photo: err.message }))
    } finally {
      setPhotoBusy(false)
    }
  }

  return (
    <div className="fl-back" onClick={onClose}>
      <div
        className="fl-pop pe-pop"
        role="dialog"
        aria-modal="true"
        aria-label="프로필 수정"
        onClick={(e) => e.stopPropagation()}
      >
        <header className="fl-head">
          <h2>프로필 수정</h2>
          <button type="button" className="fl-close" onClick={onClose} aria-label="닫기">
            ✕
          </button>
        </header>

        <form className="pe-body" onSubmit={save} noValidate>
          {/* 미리보기: 고치는 대로 프로필에 어떻게 보일지 */}
          <div className="settings-preview">
            <div className="settings-avatar">
              {user.profileImageUrl ? (
                <img className="pf-avatar" src={user.profileImageUrl} alt="" />
              ) : (
                <span className="pf-avatar" aria-hidden="true">
                  {(nickname || user.nickname).slice(0, 1)}
                </span>
              )}
              <button
                type="button"
                className="settings-camera"
                onClick={() => fileRef.current?.click()}
                disabled={photoBusy}
                aria-label="프로필 사진 바꾸기"
              >
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
                  <path d="M4 8h3l2-2.5h6L17 8h3v11H4V8z" strokeWidth="1.8" strokeLinejoin="round" />
                  <circle cx="12" cy="13" r="3.2" strokeWidth="1.8" />
                </svg>
              </button>
            </div>
            <div className="settings-preview-text">
              <span className="settings-preview-label">미리보기</span>
              <strong>{nickname || '닉네임'}</strong>
              <p className={bio.trim() ? undefined : 'is-empty'}>{bio.trim() || '한 줄 소개가 여기에 보여요.'}</p>
            </div>
            <div className="settings-photo-actions">
              <button
                type="button"
                className="btn btn-outline btn-sm"
                onClick={() => fileRef.current?.click()}
                disabled={photoBusy}
              >
                {photoBusy ? '올리는 중…' : '사진 바꾸기'}
              </button>
              {user.profileImageUrl && (
                <button type="button" className="btn btn-ghost btn-sm" onClick={removePhoto} disabled={photoBusy}>
                  사진 빼기
                </button>
              )}
              <span className="field-hint">JPG · PNG, 5MB 이하</span>
            </div>
            <input ref={fileRef} type="file" accept="image/jpeg,image/png" hidden onChange={changePhoto} />
          </div>
          {errors.photo && (
            <p className="form-error" role="alert">
              {errors.photo}
            </p>
          )}

          <Field
            label="닉네임"
            name="nickname"
            value={nickname}
            maxLength={20}
            autoComplete="nickname"
            hint={NICKNAME_HINT}
            error={errors.nickname}
            onChange={(e) => {
              setNickname(e.target.value)
              // 입력하는 동안에는 길이 부족은 따지지 않고 못 쓰는 글자만 바로 알려 준다
              setErrors((cur) => ({ ...cur, nickname: nicknameError(e.target.value, { checkLength: false }) }))
            }}
          />
          <div className="field">
            <label htmlFor="pe-bio">한 줄 소개</label>
            <textarea
              id="pe-bio"
              value={bio}
              rows={2}
              maxLength={BIO_MAX}
              placeholder="어떤 갓생을 살고 있는지 한두 줄로 소개해 보세요."
              onChange={(e) => setBio(e.target.value)}
            />
            <p className="field-hint">
              {bio.length}/{BIO_MAX}
            </p>
          </div>
          {errors.form && (
            <p className="form-error" role="alert">
              {errors.form}
            </p>
          )}
          <div className="pe-actions">
            <button type="button" className="btn btn-outline" onClick={onClose}>
              닫기
            </button>
            <button type="submit" className="btn btn-dark" disabled={busy || !changed}>
              {busy ? '저장하는 중…' : '저장'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
