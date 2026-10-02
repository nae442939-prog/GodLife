import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { communityApi } from '../api/client.js'
import { MAX_CONTENT, MAX_IMAGES, MAX_TITLE, TOPICS } from '../community/format.js'
import { VerifyBadge } from '../community/VerifyBadge.jsx'
import { Field } from '../components/Field.jsx'

const MAX_FILE_BYTES = 5 * 1024 * 1024

/**
 * 커뮤니티 글쓰기(/community/new) · 고치기(/community/:id/edit).
 * 인증 결과는 새 글을 쓸 때만 붙일 수 있다: 챌린지를 고르면 서버가 그날 인증 기록을 보고 붙이며 나중에 바꿀 수 없다.
 * 사진은 글을 먼저 저장한 뒤 한 장씩 올린다 (고칠 때는 [저장]을 눌러야 빼고 더한 것이 반영된다).
 */
export function CommunityWritePage() {
  const { id } = useParams()
  const editing = Boolean(id)
  const navigate = useNavigate()
  const location = useLocation()
  const fileInput = useRef(null)

  const [loaded, setLoaded] = useState(!editing)
  const [loadError, setLoadError] = useState('')
  const [form, setForm] = useState({ topic: 'FREE', title: '', content: '', challengeId: '' })
  const [attachable, setAttachable] = useState([])
  // 이미 올라간 사진 id (고칠 때), 뺄 사진 id, 새로 고른 파일
  const [savedImages, setSavedImages] = useState([])
  const [removed, setRemoved] = useState([])
  const [files, setFiles] = useState([])
  const [verify, setVerify] = useState(null)
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState(location.state?.notice ?? '')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!editing) {
      communityApi
        .attachable()
        .then(setAttachable)
        .catch(() => setAttachable([]))
      return
    }
    let cancelled = false
    communityApi
      .get(id)
      .then((post) => {
        if (cancelled) return
        if (!post.mine) {
          navigate(`/community/${id}`, { replace: true })
          return
        }
        setForm({ topic: post.topic, title: post.title, content: post.content, challengeId: '' })
        setSavedImages(post.imageIds)
        setVerify(post.verify)
        setLoaded(true)
      })
      .catch((err) => !cancelled && setLoadError(err.message))
    return () => {
      cancelled = true
    }
  }, [editing, id, navigate])

  // 새로 고른 사진의 미리보기 주소 (바뀌면 예전 주소를 놓아 준다)
  const previews = useMemo(() => files.map((f) => URL.createObjectURL(f)), [files])
  useEffect(() => () => previews.forEach((url) => URL.revokeObjectURL(url)), [previews])

  const keptImages = savedImages.filter((imageId) => !removed.includes(imageId))
  const imageCount = keptImages.length + files.length
  const picked = attachable.find((c) => String(c.challengeId) === form.challengeId)

  const onChange = (e) => {
    const { name, value } = e.target
    setForm((f) => ({ ...f, [name]: value }))
    setErrors((errs) => ({ ...errs, [name]: undefined }))
  }

  function onPickFiles(e) {
    const chosen = [...e.target.files]
    e.target.value = '' // 같은 파일을 다시 골라도 반응하게
    const room = MAX_IMAGES - imageCount
    const ok = chosen.filter((f) => /^image\/(jpeg|png)$/.test(f.type) && f.size <= MAX_FILE_BYTES)
    setFiles((cur) => [...cur, ...ok.slice(0, room)])
    setFormError(
      ok.length < chosen.length
        ? 'JPG·PNG, 5MB 이하 사진만 올릴 수 있어요.'
        : ok.length > room
          ? `사진은 ${MAX_IMAGES}장까지 올릴 수 있어요.`
          : '',
    )
  }

  async function onSubmit(e) {
    e.preventDefault()
    setFormError('')
    const found = {}
    if (!form.title.trim()) found.title = '제목을 입력해 주세요.'
    if (!form.content.trim()) found.content = '내용을 입력해 주세요.'
    setErrors(found)
    if (Object.keys(found).length > 0) return

    setSubmitting(true)
    const post = { topic: form.topic, title: form.title.trim(), content: form.content.trim() }
    let postId = id
    try {
      if (editing) {
        await communityApi.update(id, post)
        for (const imageId of removed) await communityApi.removeImage(id, imageId)
      } else {
        const created = await communityApi.create({
          ...post,
          challengeId: form.challengeId ? Number(form.challengeId) : null,
        })
        postId = created.id
      }
    } catch (err) {
      setErrors(err.fieldErrors ?? {})
      if (Object.keys(err.fieldErrors ?? {}).length === 0) setFormError(err.message)
      setSubmitting(false)
      return
    }

    // 글은 저장됐다. 사진을 못 올리면 고치기 화면에서 다시 올릴 수 있게 한다
    let failed = 0
    for (const file of files) {
      try {
        await communityApi.addImage(postId, file)
      } catch {
        failed += 1
      }
    }
    if (failed > 0) {
      navigate(`/community/${postId}/edit`, {
        replace: true,
        state: { notice: `글은 저장됐지만 사진 ${failed}장을 올리지 못했어요. 다시 올려 주세요.` },
      })
      // 같은 화면(고치기)에 머무는 경우를 위해 상태를 맞춘다
      setFiles([])
      setRemoved([])
      setFormError(`글은 저장됐지만 사진 ${failed}장을 올리지 못했어요. 다시 올려 주세요.`)
      if (editing) communityApi.get(id).then((p) => setSavedImages(p.imageIds)).catch(() => {})
      setSubmitting(false)
      return
    }
    navigate(`/community/${postId}`, { replace: true })
  }

  if (loadError) {
    return (
      <div className="container page">
        <p className="form-error">{loadError}</p>
        <Link to="/community">커뮤니티로</Link>
      </div>
    )
  }
  if (!loaded) return <p className="loading">불러오는 중…</p>

  return (
    <div className="container page cm-write-page">
      <div className="ch-head">
        <h1 className="page-title">{editing ? '글 고치기' : '글쓰기'}</h1>
        <p className="page-sub">
          <Link to={editing ? `/community/${id}` : '/community'} className="back-button">
            <svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" aria-hidden="true">
              <path d="M6.5 1.5L2 5l4.5 3.5" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
            {editing ? '글로 돌아가기' : '커뮤니티로 돌아가기'}
          </Link>
        </p>
      </div>

      <form className="card form-card cf" onSubmit={onSubmit} noValidate>
        <fieldset className="field">
          <legend className="field-label">말머리</legend>
          <div className="chips">
            {TOPICS.map((t) => (
              <label key={t.value} className={`chip ${form.topic === t.value ? 'is-active' : ''}`}>
                <input
                  type="radio"
                  name="topic"
                  value={t.value}
                  checked={form.topic === t.value}
                  onChange={onChange}
                  className="sr-only"
                />
                {t.label}
              </label>
            ))}
          </div>
        </fieldset>

        <Field
          label="제목"
          name="title"
          value={form.title}
          onChange={onChange}
          maxLength={MAX_TITLE}
          placeholder="제목을 입력해 주세요"
          error={errors.title}
        />

        <div className="field">
          <label htmlFor="cm-content">내용</label>
          <textarea
            id="cm-content"
            name="content"
            rows={10}
            maxLength={MAX_CONTENT}
            value={form.content}
            onChange={onChange}
            placeholder="오늘 인증한 이야기, 꾸준히 하는 요령, 궁금한 점을 자유롭게 적어 주세요."
            aria-invalid={errors.content ? 'true' : undefined}
          />
          <p className="field-hint cm-count">
            {form.content.length} / {MAX_CONTENT}
          </p>
          {errors.content && <p className="field-error">{errors.content}</p>}
        </div>

        <fieldset className="field">
          <legend className="field-label">
            사진 <span className="muted">(선택, {MAX_IMAGES}장까지)</span>
          </legend>
          <div className="cm-photos">
            {keptImages.map((imageId) => (
              <span key={imageId} className="cm-photo">
                <img src={communityApi.imageUrl(imageId)} alt="" />
                <button type="button" aria-label="사진 빼기" onClick={() => setRemoved((cur) => [...cur, imageId])}>
                  ×
                </button>
              </span>
            ))}
            {files.map((file, i) => (
              <span key={previews[i]} className="cm-photo">
                <img src={previews[i]} alt="" />
                <button
                  type="button"
                  aria-label="사진 빼기"
                  onClick={() => setFiles((cur) => cur.filter((f) => f !== file))}
                >
                  ×
                </button>
              </span>
            ))}
            {imageCount < MAX_IMAGES && (
              <button type="button" className="cm-photo-add" onClick={() => fileInput.current.click()}>
                <span aria-hidden="true">+</span>
                사진 추가
              </button>
            )}
          </div>
          <input
            ref={fileInput}
            type="file"
            accept="image/jpeg,image/png"
            multiple
            className="sr-only"
            tabIndex={-1}
            onChange={onPickFiles}
          />
          <p className="field-hint">JPG·PNG, 한 장에 5MB 이하. 위치 같은 촬영 정보는 지우고 저장해요.</p>
        </fieldset>

        {editing ? (
          verify && (
            <div className="field">
              <span className="field-label">붙인 인증 결과</span>
              <VerifyBadge verify={verify} />
              <p className="field-hint">인증 결과는 글을 쓸 때 서버가 붙인 값이라 바꿀 수 없어요.</p>
            </div>
          )
        ) : (
          <div className="field">
            <label htmlFor="cm-challenge">
              인증 결과 붙이기 <span className="muted">(선택)</span>
            </label>
            {attachable.length === 0 ? (
              <p className="field-hint">지금 참여해 진행 중인 챌린지가 있으면 오늘 인증 결과를 글에 붙일 수 있어요.</p>
            ) : (
              <>
                <select
                  id="cm-challenge"
                  name="challengeId"
                  className="select"
                  value={form.challengeId}
                  onChange={onChange}
                >
                  <option value="">붙이지 않기</option>
                  {attachable.map((c) => (
                    <option key={c.challengeId} value={c.challengeId}>
                      {c.title}
                    </option>
                  ))}
                </select>
                <p className="field-hint">
                  {picked
                    ? `지금 올리면 '오늘 ${picked.verifiedToday ? '인증 완료' : '아직 인증 전'}'으로 붙어요. 결과는 내가 적는 것이 아니라 서버가 인증 기록으로 붙이고, 올린 뒤에는 바뀌지 않아요.`
                    : '챌린지를 고르면 오늘 인증했는지가 글에 함께 표시돼요.'}
                </p>
              </>
            )}
          </div>
        )}

        {formError && <p className="form-error">{formError}</p>}
        <button type="submit" className="btn btn-dark btn-block cf-submit" disabled={submitting}>
          {submitting ? '저장하는 중…' : editing ? '저장하기' : '글 올리기'}
        </button>
      </form>
    </div>
  )
}
