import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { shopApi } from '../api/client.js'
import { ORDER_STATUS, dateTime, points } from '../shop/format.js'
import { ProductImage } from '../shop/ProductImage.jsx'

const PAGE = 20

/** 내 주문 내역 (/shop/orders). 최근 주문부터, 누르면 주문 상세로 간다. */
export function ShopOrdersPage() {
  const [state, setState] = useState({ items: null, page: 0, hasMore: false, error: '' })
  const [loadingMore, setLoadingMore] = useState(false)

  useEffect(() => {
    let cancelled = false
    shopApi
      .orders(0)
      .then((items) => !cancelled && setState({ items, page: 0, hasMore: items.length === PAGE, error: '' }))
      .catch((err) => !cancelled && setState({ items: [], page: 0, hasMore: false, error: err.message }))
    return () => {
      cancelled = true
    }
  }, [])

  async function loadMore() {
    setLoadingMore(true)
    try {
      const more = await shopApi.orders(state.page + 1)
      setState((s) => ({ ...s, items: [...s.items, ...more], page: s.page + 1, hasMore: more.length === PAGE }))
    } catch (err) {
      setState((s) => ({ ...s, error: err.message }))
    } finally {
      setLoadingMore(false)
    }
  }

  if (state.items === null) return <p className="loading">불러오는 중…</p>

  return (
    <div className="container page">
      <div className="ch-head sh-head">
        <div>
          <h1 className="page-title">주문 내역</h1>
          <p className="page-sub">
            <Link to="/shop" className="back-button">
              <svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" aria-hidden="true">
                <path d="M6.5 1.5L2 5l4.5 3.5" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
              포인트 상점
            </Link>
          </p>
        </div>
      </div>

      {state.error && <p className="form-error">{state.error}</p>}
      {state.items.length === 0 ? (
        !state.error && (
          <div className="cl-empty">
            <h3>아직 주문한 상품이 없어요</h3>
            <p>챌린지로 모은 포인트를 상점에서 써 보세요.</p>
            <Link to="/shop" className="btn btn-dark-outline">
              상점 둘러보기
            </Link>
          </div>
        )
      ) : (
        <>
          <ul className="sh-orders">
            {state.items.map((o) => (
              <li key={o.id}>
                <Link to={`/shop/orders/${o.id}`} className="sh-order">
                  <ProductImage product={o} />
                  <span className="sh-order-main">
                    <span className={`sh-status is-${o.status}`}>{ORDER_STATUS[o.status]}</span>
                    <strong>
                      {o.firstItemName}
                      {o.itemCount > 1 && ` 외 ${o.itemCount - 1}가지`}
                    </strong>
                    <span className="sh-order-date">{dateTime(o.orderedAt)}</span>
                  </span>
                  <span className="sh-price">{points(o.totalPoints)}</span>
                </Link>
              </li>
            ))}
          </ul>
          {state.hasMore && (
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
