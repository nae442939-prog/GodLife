import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { challengeApi } from '../api/client.js'
import { ChallengeCard } from '../challenge/ChallengeCard.jsx'
import { MODE_LABEL, SORT_OPTIONS } from '../challenge/format.js'

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
      <div className="page-head">
        <div>
          <h1 className="page-title">챌린지</h1>
          <p className="page-sub">지금 모집 중인 챌린지에 참여하거나, 직접 만들어 보세요.</p>
        </div>
        <Link to="/challenges/new" className="btn btn-primary">
          챌린지 만들기
        </Link>
      </div>

      <div className="filters">
        <div className="chips" role="group" aria-label="챌린지 종류">
          {MODES.map((m) => (
            <button
              key={m.value}
              type="button"
              className={`chip ${mode === m.value ? 'is-active' : ''}`}
              aria-pressed={mode === m.value}
              onClick={() => update('mode', m.value)}
            >
              {m.label}
            </button>
          ))}
        </div>

        <div className="chips" role="group" aria-label="카테고리">
          <button
            type="button"
            className={`chip ${categoryId === '' ? 'is-active' : ''}`}
            aria-pressed={categoryId === ''}
            onClick={() => update('categoryId', '')}
          >
            전체 카테고리
          </button>
          {categories.map((c) => (
            <button
              key={c.id}
              type="button"
              className={`chip ${categoryId === String(c.id) ? 'is-active' : ''}`}
              aria-pressed={categoryId === String(c.id)}
              onClick={() => update('categoryId', String(c.id))}
            >
              {c.name}
            </button>
          ))}
        </div>

        <div className="filter-row">
          <form className="search" role="search" onSubmit={onSearch}>
            <input
              type="search"
              placeholder="챌린지 제목으로 검색"
              aria-label="챌린지 검색"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
            />
            <button type="submit" className="btn btn-outline btn-sm">
              검색
            </button>
          </form>
          <select aria-label="정렬" className="select" value={sort} onChange={(e) => update('sort', e.target.value)}>
            {SORT_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {loading ? (
        <p className="loading">불러오는 중…</p>
      ) : result.error ? (
        <p className="form-error">{result.error}</p>
      ) : result.items.length === 0 ? (
        <div className="empty">
          <p>조건에 맞는 모집 중인 챌린지가 없어요.</p>
          <Link to="/challenges/new" className="btn btn-outline btn-sm">
            첫 챌린지 만들기
          </Link>
        </div>
      ) : (
        <>
          <ul className="challenge-grid">
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
  )
}
