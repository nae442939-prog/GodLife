import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { challengeApi } from '../api/client.js'
import { ChallengeCard } from '../challenge/ChallengeCard.jsx'
import { MODE_LABEL, SORT_OPTIONS } from '../challenge/format.js'
import { CategoryIcon, GridIcon, PlusIcon, SearchIcon } from '../challenge/icons.jsx'

const MODES = [
  { value: '', label: '전체' },
  { value: 'FREE', label: MODE_LABEL.FREE },
  { value: 'BET', label: MODE_LABEL.BET },
]

// 필터는 주소(?mode=FREE&categoryId=1&q=러닝&sort=deadline)에 둔다. 뒤로 가기·공유해도 같은 목록이 보인다.
export function ChallengeListPage() {
  const [params, setParams] = useSearchParams()
  const mode = params.get('mode') ?? ''
  const categoryId = params.get('categoryId') ?? ''
  const q = params.get('q') ?? ''
  const sort = params.get('sort') ?? 'popular'

  const [categories, setCategories] = useState([])
  const [keyword, setKeyword] = useState(q)
  // key 는 이 결과가 어떤 필터로 받은 것인지. 필터가 바뀌면 이전 결과를 보여주지 않는다.
  const [result, setResult] = useState({ key: null, items: [], page: 0, totalPages: 0, error: '' })
  const [loadingMore, setLoadingMore] = useState(false)

  const filterKey = `${mode}|${categoryId}|${q}|${sort}`
  const loading = result.key !== filterKey

  useEffect(() => {
    challengeApi.categories().then(setCategories).catch(() => setCategories([]))
  }, [])

  useEffect(() => {
    let cancelled = false
    challengeApi
      .list({ mode, categoryId, q, sort })
      .then((data) => {
        if (!cancelled) setResult({ key: filterKey, ...data, error: '' })
      })
      .catch((err) => {
        if (!cancelled) setResult({ key: filterKey, items: [], page: 0, totalPages: 0, error: err.message })
      })
    return () => {
      cancelled = true
    }
  }, [filterKey, mode, categoryId, q, sort])

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
      const data = await challengeApi.list({ mode, categoryId, q, sort, page: result.page + 1 })
      setResult((r) => ({ ...r, items: [...r.items, ...data.items], page: data.page, totalPages: data.totalPages }))
    } catch (err) {
      setResult((r) => ({ ...r, error: err.message }))
    } finally {
      setLoadingMore(false)
    }
  }

  return (
    <div className="container page">
      <div>
        <div className="ch-head cl-head">
          <div>
            <h1 className="page-title">챌린지</h1>
            <p className="page-sub">지금 모집 중인 챌린지에 참여하거나, 직접 만들어 보세요.</p>
          </div>
          <Link to="/challenges/new" className="btn btn-dark-outline">
            챌린지 만들기
          </Link>
        </div>

        <div className="cl-tabs" role="group" aria-label="챌린지 종류">
          {MODES.map((m) => (
            <button
              key={m.value}
              type="button"
              className={`cl-tab ${mode === m.value ? 'is-active' : ''}`}
              aria-pressed={mode === m.value}
              onClick={() => update('mode', m.value)}
            >
              {m.label}
            </button>
          ))}
        </div>

        <div className="cl-cats" role="group" aria-label="카테고리">
          <button
            type="button"
            className={`cl-cat is-all ${categoryId === '' ? 'is-active' : ''}`}
            aria-pressed={categoryId === ''}
            onClick={() => update('categoryId', '')}
          >
            <GridIcon size={14} />
            전체
          </button>
          {categories.map((c) => (
            <button
              key={c.id}
              type="button"
              className={`cl-cat cat-${c.id} ${categoryId === String(c.id) ? 'is-active' : ''}`}
              aria-pressed={categoryId === String(c.id)}
              onClick={() => update('categoryId', String(c.id))}
            >
              <CategoryIcon id={c.id} size={14} />
              {c.name}
            </button>
          ))}
        </div>

        <div className="cl-toolbar">
          <form className="cl-search" role="search" onSubmit={onSearch}>
            <SearchIcon />
            <input
              type="search"
              placeholder="챌린지 제목으로 검색"
              aria-label="챌린지 검색"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
            />
          </form>
          <select aria-label="정렬" className="cl-sort" value={sort} onChange={(e) => update('sort', e.target.value)}>
            {SORT_OPTIONS.map((o) => (
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
            <h3>조건에 맞는 모집 중인 챌린지가 없어요</h3>
            <p>필터를 바꿔 보거나, 원하는 챌린지가 없다면 직접 만들어 보세요.</p>
            <Link to="/challenges/new" className="btn btn-dark-outline">
              첫 챌린지 만들기
            </Link>
          </div>
        ) : (
          <>
            <ul className="cl-grid">
              {result.items.map((c) => (
                <ChallengeCard key={c.id} challenge={c} />
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
    </div>
  )
}
