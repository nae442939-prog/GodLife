import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { communityApi } from '../api/client.js'
import { PlusIcon, SearchIcon } from '../challenge/icons.jsx'
import { CommentIcon, HeartIcon } from '../community/icons.jsx'
import { POST_SORTS, TOPICS, TOPIC_LABEL, timeAgo } from '../community/format.js'
import { VerifyBadge } from '../community/VerifyBadge.jsx'

const TABS = [{ value: '', label: '전체' }, ...TOPICS]

/**
 * 커뮤니티 글 목록 (/community). 비로그인도 볼 수 있다.
 * 말머리 · 검색 · 정렬은 주소(?topic=TIP&q=러닝&sort=popular)에 둔다. 뒤로 가기·공유해도 같은 목록이 보인다.
 */
export function CommunityPage() {
  const [params, setParams] = useSearchParams()
  const topic = params.get('topic') ?? ''
  const q = params.get('q') ?? ''
  const sort = params.get('sort') ?? 'latest'

  const [keyword, setKeyword] = useState(q)
  // key 는 이 결과가 어떤 필터로 받은 것인지. 필터가 바뀌면 이전 결과를 보여주지 않는다.
  const [result, setResult] = useState({ key: null, items: [], page: 0, totalPages: 0, error: '' })
  const [loadingMore, setLoadingMore] = useState(false)

  const filterKey = `${topic}|${q}|${sort}`
  const loading = result.key !== filterKey

  useEffect(() => {
    let cancelled = false
    communityApi
      .list({ topic, q, sort })
      .then((data) => !cancelled && setResult({ key: filterKey, ...data, error: '' }))
      .catch(
        (err) => !cancelled && setResult({ key: filterKey, items: [], page: 0, totalPages: 0, error: err.message }),
      )
    return () => {
      cancelled = true
    }
  }, [filterKey, topic, q, sort])

  function update(name, value) {
    const next = new URLSearchParams(params)
    if (value) next.set(name, value)
    else next.delete(name)
    setParams(next, { replace: true })
  }

  function onSearch(e) {
    e.preventDefault()
    update('q', keyword.trim())
  }

  async function loadMore() {
    setLoadingMore(true)
    try {
      const data = await communityApi.list({ topic, q, sort, page: result.page + 1 })
      setResult((r) => ({ ...r, items: [...r.items, ...data.items], page: data.page, totalPages: data.totalPages }))
    } catch (err) {
      setResult((r) => ({ ...r, error: err.message }))
    } finally {
      setLoadingMore(false)
    }
  }

  return (
    <div className="container page">
      <div className="ch-head cl-head">
        <div>
          <h1 className="page-title">커뮤니티</h1>
          <p className="page-sub">오늘의 인증 이야기, 꾸준히 하는 요령을 나눠 보세요.</p>
        </div>
        <Link to="/community/new" className="btn btn-dark-outline">
          글쓰기
        </Link>
      </div>

      <div className="cl-tabs" role="group" aria-label="말머리">
        {TABS.map((t) => (
          <button
            key={t.value}
            type="button"
            className={`cl-tab ${topic === t.value ? 'is-active' : ''}`}
            aria-pressed={topic === t.value}
            onClick={() => update('topic', t.value)}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div className="cl-toolbar">
        <form className="cl-search" role="search" onSubmit={onSearch}>
          <SearchIcon />
          <input
            type="search"
            placeholder="제목이나 내용으로 검색"
            aria-label="글 검색"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
        </form>
        <select aria-label="정렬" className="cl-sort" value={sort} onChange={(e) => update('sort', e.target.value)}>
          {POST_SORTS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </div>

      {loading ? (
        <p className="loading">불러오는 중…</p>
      ) : result.error ? (
        <p className="form-error">{result.error}</p>
      ) : result.items.length === 0 ? (
        <div className="cl-empty">
          <span className="cl-empty-icon">
            <PlusIcon />
          </span>
          <h3>{q || topic ? '조건에 맞는 글이 없어요' : '아직 글이 없어요'}</h3>
          <p>오늘 인증한 이야기나 꾸준히 하는 요령을 처음으로 남겨 보세요.</p>
          <Link to="/community/new" className="btn btn-dark-outline">
            첫 글 쓰기
          </Link>
        </div>
      ) : (
        <>
          <ul className="cm-list">
            {result.items.map((p) => (
              <PostRow key={p.id} post={p} />
            ))}
          </ul>
          {result.page + 1 < result.totalPages && (
            <div className="more">
              <button type="button" className="btn btn-outline" onClick={loadMore} disabled={loadingMore}>
                {loadingMore ? '불러오는 중…' : '더 보기'}
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}

function PostRow({ post }) {
  return (
    <li>
      <Link to={`/community/${post.id}`} className="cm-row">
        <div className="cm-row-main">
          <div className="cm-row-tags">
            <span className={`cm-topic is-${post.topic}`}>{TOPIC_LABEL[post.topic]}</span>
            <VerifyBadge verify={post.verify} compact />
          </div>
          <h2>{post.title}</h2>
          <p>{post.excerpt}</p>
          <div className="cm-row-meta">
            <span>{post.author.nickname}</span>
            <time dateTime={post.createdAt}>{timeAgo(post.createdAt)}</time>
            <span className="cm-stat">
              <HeartIcon /> {post.likeCount}
            </span>
            <span className="cm-stat">
              <CommentIcon /> {post.commentCount}
            </span>
          </div>
        </div>
        {post.thumbnailId && (
          <span className="cm-thumb">
            <img src={communityApi.imageUrl(post.thumbnailId)} alt="" loading="lazy" />
            {post.imageCount > 1 && <span className="cm-thumb-count">{post.imageCount}</span>}
          </span>
        )}
      </Link>
    </li>
  )
}
