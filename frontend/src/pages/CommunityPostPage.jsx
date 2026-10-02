import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { communityApi } from '../api/client.js'
import { useAuth } from '../auth/useAuth.js'
import { MAX_COMMENT, TOPIC_LABEL, timeAgo } from '../community/format.js'
import { CommentIcon, FlagIcon, HeartIcon } from '../community/icons.jsx'
import { VerifyBadge } from '../community/VerifyBadge.jsx'
import { Avatar } from '../components/UserMenu.jsx'

const REPORT_REASONS = ['광고 · 도배예요', '욕설 · 비방이 있어요', '커뮤니티와 관계없는 내용이에요', '그 밖의 이유']

/**
 * 커뮤니티 글 상세 (/community/:id). 비로그인도 읽을 수 있고, 좋아요 · 댓글 · 신고는 로그인해야 한다.
 */
export function CommunityPostPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const { status } = useAuth()
  const authed = status === 'authed'
  const [state, setState] = useState({ key: null, post: null, error: '' })
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [reporting, setReporting] = useState(false)
  // 답글 입력칸이 열려 있는 댓글 id (없으면 null)
  const [replyTo, setReplyTo] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const loadKey = `${id}|${status}`

  useEffect(() => {
    if (status === 'loading') return
    let cancelled = false
    communityApi
      .get(id)
      .then((post) => !cancelled && setState({ key: loadKey, post, error: '' }))
      .catch((err) => !cancelled && setState({ key: loadKey, post: null, error: err.message }))
    return () => {
      cancelled = true
    }
  }, [id, status, loadKey])

  if (state.key !== loadKey) return <p className="loading">불러오는 중…</p>
  if (state.error) {
    return (
      <div className="container page cm-post-page">
        <div className="invite-missing card">
          <h1>글을 열 수 없어요</h1>
          <p>{state.error}</p>
          <Link to="/community" className="btn btn-dark-outline">
            커뮤니티로
          </Link>
        </div>
      </div>
    )
  }

  const post = state.post
  const setPost = (change) => setState((s) => ({ ...s, post: { ...s.post, ...change(s.post) } }))
  const loginLink = (
    <Link to="/login" state={{ from: location.pathname }}>
      로그인
    </Link>
  )

  // 댓글을 달거나 지운 뒤: 서버가 정한 시각 · 글쓴이 · 지워진 댓글 자리로 다시 받는다
  const reload = () =>
    communityApi
      .get(post.id)
      .then((fresh) => setState((s) => ({ ...s, post: fresh })))
      .catch(() => {})
  const patchComment = (commentId, change) =>
    setPost((p) => ({ comments: p.comments.map((x) => (x.id === commentId ? { ...x, ...change } : x)) }))
  const commentActions = {
    reload,
    // 답글 입력칸은 한 번에 하나만 연다 (다른 댓글의 [답글]을 누르면 먼저 열린 칸은 닫힌다)
    replyTo,
    setReplyTo,
    onReported: (commentId) => patchComment(commentId, { reported: true }),
    onLiked: (commentId, result) => patchComment(commentId, result),
    onNeedLogin: () => navigate('/login', { state: { from: location.pathname } }),
  }
  // 답글은 원 댓글 아래에 모은다. 원 댓글이 안 보이면(차단한 사람의 댓글 등) 보통 댓글처럼 보여 준다
  const commentIds = new Set(post.comments.map((c) => c.id))
  const roots = post.comments.filter((c) => !c.parentId || !commentIds.has(c.parentId))

  async function toggleLike() {
    if (!authed) {
      navigate('/login', { state: { from: location.pathname } })
      return
    }
    setBusy(true)
    setError('')
    try {
      const result = await communityApi.like(post.id, !post.liked)
      setPost(() => result)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function remove() {
    setBusy(true)
    setError('')
    try {
      await communityApi.remove(post.id)
      navigate('/community', { replace: true })
    } catch (err) {
      setError(err.message)
      setBusy(false)
    }
  }

  return (
    <div className="container page cm-post-page">
      <p className="cm-back">
        <Link to="/community" className="back-button">
          <svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" aria-hidden="true">
            <path d="M6.5 1.5L2 5l4.5 3.5" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          커뮤니티
        </Link>
      </p>

      <article className="card cm-post">
        <header className="cm-post-head">
          <span className={`cm-topic is-${post.topic}`}>{TOPIC_LABEL[post.topic]}</span>
          <h1>{post.title}</h1>
          <div className="cm-post-by">
            <Link to={`/users/${post.author.id}`} className="cm-author">
              <Avatar src={post.author.profileImageUrl} size={34} />
              <strong>{post.author.nickname}</strong>
            </Link>
            <time dateTime={post.createdAt}>
              {timeAgo(post.createdAt)}
              {post.updatedAt && ' · 수정됨'}
            </time>
          </div>
        </header>

        <VerifyBadge verify={post.verify} />

        <p className="cm-post-content">{post.content}</p>

        {post.imageIds.length > 0 && (
          <div className={`cm-post-images is-${post.imageIds.length}`}>
            {post.imageIds.map((imageId) => (
              <a key={imageId} href={communityApi.imageUrl(imageId)} target="_blank" rel="noreferrer">
                <img src={communityApi.imageUrl(imageId)} alt="글에 올린 사진" loading="lazy" />
              </a>
            ))}
          </div>
        )}

        <footer className="cm-post-foot">
          <button
            type="button"
            className={`cm-like${post.liked ? ' is-on' : ''}`}
            aria-pressed={post.liked}
            disabled={busy}
            onClick={toggleLike}
          >
            <HeartIcon filled={post.liked} size={17} />
            좋아요 {post.likeCount}
          </button>
          <span className="cm-stat">
            <CommentIcon size={17} /> 댓글 {post.commentCount}
          </span>

          <span className="cm-post-actions">
            {post.mine ? (
              confirmDelete ? (
                <>
                  <span className="cm-confirm">이 글을 지울까요?</span>
                  <button type="button" className="cm-text-btn is-danger" disabled={busy} onClick={remove}>
                    지우기
                  </button>
                  <button type="button" className="cm-text-btn" disabled={busy} onClick={() => setConfirmDelete(false)}>
                    취소
                  </button>
                </>
              ) : (
                <>
                  <Link to={`/community/${post.id}/edit`} className="cm-text-btn">
                    수정
                  </Link>
                  <button type="button" className="cm-text-btn" onClick={() => setConfirmDelete(true)}>
                    삭제
                  </button>
                </>
              )
            ) : (
              authed &&
              (post.reported ? (
                <span className="cm-reported">신고한 글이에요</span>
              ) : (
                <button type="button" className="cm-text-btn" onClick={() => setReporting((v) => !v)}>
                  <FlagIcon size={14} /> 신고
                </button>
              ))
            )}
          </span>
        </footer>
        {error && <p className="form-error">{error}</p>}
        {reporting && !post.reported && (
          <ReportForm
            label="이 글"
            send={(reason) => communityApi.reportPost(post.id, reason)}
            onDone={() => {
              setReporting(false)
              setPost(() => ({ reported: true }))
            }}
            onCancel={() => setReporting(false)}
          />
        )}
      </article>

      <section className="card cm-comments" aria-label="댓글">
        <h2>댓글 {post.commentCount}</h2>
        {post.comments.length === 0 ? (
          <p className="cm-comments-empty">아직 댓글이 없어요. 첫 댓글을 남겨 보세요.</p>
        ) : (
          <ul>
            {roots.map((c) => (
              <CommentRow
                key={c.id}
                comment={c}
                replies={post.comments.filter((r) => r.parentId === c.id)}
                postId={post.id}
                authed={authed}
                actions={commentActions}
              />
            ))}
          </ul>
        )}
        {authed ? (
          <CommentForm postId={post.id} onSaved={reload} />
        ) : (
          <p className="cm-login-hint">{loginLink}하면 댓글을 남길 수 있어요.</p>
        )}
      </section>
    </div>
  )
}

/**
 * 댓글 한 줄. replies 가 있으면 아래에 들여 써서 답글(대댓글)을 보여 준다 (답글은 한 단계만).
 * 답글에서 [답글]을 누르면 같은 원 댓글 아래에 이어 달린다.
 */
function CommentRow({ comment, replies = [], postId, authed, actions, isReply = false }) {
  const [reporting, setReporting] = useState(false)
  const replying = actions.replyTo === comment.id
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function remove() {
    setBusy(true)
    setError('')
    try {
      await communityApi.removeComment(comment.id)
      await actions.reload()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function toggleLike() {
    if (!authed) {
      actions.onNeedLogin()
      return
    }
    setBusy(true)
    setError('')
    try {
      actions.onLiked(comment.id, await communityApi.likeComment(comment.id, !comment.liked))
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const replyList = replies.length > 0 && (
    <ul className="cm-replies">
      {replies.map((r) => (
        <CommentRow key={r.id} comment={r} postId={postId} authed={authed} actions={actions} isReply />
      ))}
    </ul>
  )

  // 지웠거나 가려진 댓글: 답글이 달려 있어 자리만 남는다
  if (comment.removed) {
    return (
      <li className="cm-comment is-removed">
        <div className="cm-comment-body">
          <p className="cm-comment-removed">삭제된 댓글이에요.</p>
          {replyList}
        </div>
      </li>
    )
  }

  return (
    <li className={`cm-comment${isReply ? ' is-reply' : ''}`}>
      <Link to={`/users/${comment.author.id}`} className="cm-comment-avatar" aria-label={`${comment.author.nickname} 프로필`}>
        <Avatar src={comment.author.profileImageUrl} size={32} />
      </Link>
      <div className="cm-comment-body">
        <div className="cm-comment-top">
          <Link to={`/users/${comment.author.id}`}>{comment.author.nickname}</Link>
          <time dateTime={comment.createdAt}>{timeAgo(comment.createdAt)}</time>
          {/* 좋아요는 댓글 오른쪽 위 */}
          <button
            type="button"
            className={`cm-comment-like${comment.liked ? ' is-on' : ''}`}
            aria-pressed={comment.liked}
            aria-label={`댓글 좋아요 ${comment.likeCount}`}
            disabled={busy}
            onClick={toggleLike}
          >
            <HeartIcon filled={comment.liked} size={15} />
            {comment.likeCount > 0 && comment.likeCount}
          </button>
        </div>
        {/* 삭제(내 댓글) · 신고(남의 댓글)는 글 바로 뒤에 붙는다 (오른쪽 끝의 하트 밑으로 가지 않게) */}
        <div className="cm-comment-line">
          <span className="cm-comment-text">{comment.content}</span>
          <span className="cm-comment-actions">
            {authed && (
              <button
                type="button"
                className="cm-text-btn"
                aria-expanded={replying}
                onClick={() => actions.setReplyTo(replying ? null : comment.id)}
              >
                답글
              </button>
            )}
            {comment.mine ? (
              <button type="button" className="cm-text-btn" disabled={busy} onClick={remove}>
                삭제
              </button>
            ) : (
              authed &&
              (comment.reported ? (
                <span className="cm-reported">신고함</span>
              ) : (
                <button type="button" className="cm-text-btn" onClick={() => setReporting((v) => !v)}>
                  신고
                </button>
              ))
            )}
          </span>
        </div>
        {error && <p className="form-error">{error}</p>}
        {reporting && !comment.reported && (
          <ReportForm
            label="이 댓글"
            send={(reason) => communityApi.reportComment(comment.id, reason)}
            onDone={() => {
              setReporting(false)
              actions.onReported(comment.id)
            }}
            onCancel={() => setReporting(false)}
          />
        )}
        {replying && (
          <CommentForm
            postId={postId}
            parentId={comment.id}
            placeholder={`${comment.author.nickname}님에게 답글 남기기`}
            onSaved={() => {
              actions.setReplyTo(null)
              return actions.reload()
            }}
          />
        )}
        {replyList}
      </div>
    </li>
  )
}

function CommentForm({ postId, parentId = null, placeholder, onSaved }) {
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function submit(e) {
    e.preventDefault()
    const content = text.trim()
    if (!content || busy) return
    setBusy(true)
    setError('')
    try {
      await communityApi.comment(postId, content, parentId)
      setText('')
      onSaved()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="cm-comment-form" onSubmit={submit}>
      <textarea
        value={text}
        maxLength={MAX_COMMENT}
        rows={2}
        autoFocus={parentId != null}
        placeholder={placeholder ?? '댓글을 남겨 보세요 (Enter 로 등록, Shift+Enter 로 줄바꿈)'}
        aria-label={parentId != null ? '답글' : '댓글'}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={(e) => {
          // 한글 조합 중의 Enter 는 글자 확정이라 보내지 않는다
          if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) submit(e)
        }}
      />
      <button type="submit" className="btn btn-dark" disabled={busy || !text.trim()}>
        등록
      </button>
      {error && <p className="form-error">{error}</p>}
    </form>
  )
}

/** 글 · 댓글 신고: 이유를 고르고(자세한 내용은 선택) 보내면 관리자가 확인한다 */
function ReportForm({ label, send, onDone, onCancel }) {
  const [reason, setReason] = useState(REPORT_REASONS[0])
  const [detail, setDetail] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      const text = detail.trim()
      await send(text ? `${reason} — ${text}` : reason)
      onDone()
    } catch (err) {
      setError(err.message)
      setBusy(false)
    }
  }

  return (
    <form className="cm-report" onSubmit={submit}>
      <p className="cm-report-title">{label}을 신고하는 이유를 골라 주세요</p>
      <div className="cm-report-reasons" role="radiogroup" aria-label="신고 이유">
        {REPORT_REASONS.map((r) => (
          <button
            key={r}
            type="button"
            role="radio"
            aria-checked={reason === r}
            className={`cm-report-reason${reason === r ? ' is-active' : ''}`}
            onClick={() => setReason(r)}
          >
            {r}
          </button>
        ))}
      </div>
      <input
        type="text"
        value={detail}
        maxLength={120}
        placeholder="자세한 내용 (선택)"
        aria-label="자세한 내용"
        onChange={(e) => setDetail(e.target.value)}
      />
      {error && <p className="form-error">{error}</p>}
      <div className="cm-report-foot">
        <button type="button" className="btn btn-outline btn-sm" disabled={busy} onClick={onCancel}>
          취소
        </button>
        <button type="submit" className="btn btn-dark btn-sm" disabled={busy}>
          {busy ? '보내는 중…' : '신고하기'}
        </button>
      </div>
    </form>
  )
}
