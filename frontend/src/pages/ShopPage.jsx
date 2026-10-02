import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { shopApi, walletApi } from '../api/client.js'
import { useAuth } from '../auth/useAuth.js'
import { PlusIcon, SearchIcon } from '../challenge/icons.jsx'
import { SHOP_SORTS, points } from '../shop/format.js'
import { CartIcon } from '../shop/icons.jsx'
import { ProductCard } from '../shop/ProductCard.jsx'

/**
 * 포인트 상점 (/shop). 비로그인도 볼 수 있다.
 * 카테고리 · 검색 · 정렬은 주소(?categoryId=1&q=매트&sort=price_asc)에 둔다. 뒤로 가기·공유해도 같은 목록이 보인다.
 */
export function ShopPage() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const location = useLocation()
  const { status } = useAuth()
  const authed = status === 'authed'
  const categoryId = params.get('categoryId') ?? ''
  const q = params.get('q') ?? ''
  const sort = params.get('sort') ?? 'popular'

  const [categories, setCategories] = useState([])
  const [wallet, setWallet] = useState(null)
  const [keyword, setKeyword] = useState(q)
  // key 는 이 결과가 어떤 필터로 받은 것인지. 필터가 바뀌면 이전 결과를 보여주지 않는다.
  const [result, setResult] = useState({ key: null, items: [], page: 0, totalPages: 0, error: '' })
  const [loadingMore, setLoadingMore] = useState(false)

  const filterKey = `${categoryId}|${q}|${sort}|${status}`
  const loading = result.key !== filterKey

  useEffect(() => {
    shopApi
      .categories()
      .then(setCategories)
      .catch(() => setCategories([]))
  }, [])

  useEffect(() => {
    if (!authed) return
    let cancelled = false
    walletApi
      .get()
      .then((w) => !cancelled && setWallet(w))
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [authed])

  useEffect(() => {
    if (status === 'loading') return
    let cancelled = false
    shopApi
      .products({ categoryId, q, sort })
      .then((data) => !cancelled && setResult({ key: filterKey, ...data, error: '' }))
      .catch(
        (err) => !cancelled && setResult({ key: filterKey, items: [], page: 0, totalPages: 0, error: err.message }),
      )
    return () => {
      cancelled = true
    }
  }, [filterKey, categoryId, q, sort, status])

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
      const data = await shopApi.products({ categoryId, q, sort, page: result.page + 1 })
      setResult((r) => ({ ...r, items: [...r.items, ...data.items], page: data.page, totalPages: data.totalPages }))
    } catch (err) {
      setResult((r) => ({ ...r, error: err.message }))
    } finally {
      setLoadingMore(false)
    }
  }

  async function toggleWish(product) {
    if (!authed) {
      navigate('/login', { state: { from: location.pathname + location.search } })
      return
    }
    const wished = !product.wished
    const patch = (value) =>
      setResult((r) => ({ ...r, items: r.items.map((p) => (p.id === product.id ? { ...p, wished: value } : p)) }))
    patch(wished) // 먼저 바꿔 보이고, 실패하면 되돌린다
    try {
      await shopApi.wish(product.id, wished)
    } catch {
      patch(!wished)
    }
  }

  return (
    <div className="container page">
      {/* 사용자 시안: 제목 줄 오른쪽에 찜한 상품 · 주문 내역 · 장바구니(알약 버튼) */}
      <div className="ch-head sh-head">
        <div>
          <h1 className="page-title">포인트 상점</h1>
          <p className="page-sub">챌린지로 모은 포인트를 상품으로 바꿔 보세요.</p>
        </div>
        {authed && (
          <div className="sh-head-links">
            <Link to="/shop/wishlist">찜한 상품</Link>
            <Link to="/shop/orders">주문 내역</Link>
            <Link to="/shop/cart" className="sh-cart-btn">
              <CartIcon />
              장바구니
            </Link>
          </div>
        )}
      </div>

      {/* 포인트 잔액 배너: 왼쪽 합계 + 안내, 오른쪽 출처별 칩 */}
      {wallet && (
        <Link to="/wallet" className="sh-wallet">
          <span className="sh-wallet-main">
            <span className="sh-wallet-total">
              <span>내 포인트</span>
              <strong>{points(wallet.balance)}</strong>
            </span>
          </span>
          <span className="sh-wallet-chips">
            <span>보상 {points(wallet.rewardBalance)}</span>
            <span>충전 {points(wallet.chargedBalance)}</span>
          </span>
        </Link>
      )}

      <div className="sh-tabs" role="group" aria-label="카테고리">
        <button
          type="button"
          className={`sh-tab ${categoryId === '' ? 'is-active' : ''}`}
          aria-pressed={categoryId === ''}
          onClick={() => update('categoryId', '')}
        >
          전체
        </button>
        {categories.map((c) => (
          <button
            key={c.id}
            type="button"
            className={`sh-tab ${categoryId === String(c.id) ? 'is-active' : ''}`}
            aria-pressed={categoryId === String(c.id)}
            onClick={() => update('categoryId', String(c.id))}
          >
            {c.name}
          </button>
        ))}
      </div>

      <div className="sh-tools">
        <form className="sh-search" role="search" onSubmit={onSearch}>
          <SearchIcon />
          <input
            type="search"
            placeholder="상품 이름으로 검색"
            aria-label="상품 검색"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
        </form>
        <select aria-label="정렬" className="sh-sort" value={sort} onChange={(e) => update('sort', e.target.value)}>
          {SHOP_SORTS.map((o) => (
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
          <h3>{q || categoryId ? '조건에 맞는 상품이 없어요' : '아직 상품이 없어요'}</h3>
          <p>곧 새로운 상품이 들어올 거예요.</p>
        </div>
      ) : (
        <>
          <ul className="sh-grid">
            {result.items.map((p) => (
              <ProductCard key={p.id} product={p} onWish={toggleWish} />
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
