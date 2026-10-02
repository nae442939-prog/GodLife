import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { shopApi } from '../api/client.js'
import { MAX_QUANTITY, points } from '../shop/format.js'
import { CloseIcon } from '../shop/icons.jsx'
import { ProductImage } from '../shop/ProductImage.jsx'
import { QuantityStepper } from '../shop/QuantityStepper.jsx'

/**
 * 장바구니 (/shop/cart). 수량을 바꾸거나 빼고, 살 수 있는 상품만 모아 결제 화면으로 간다.
 * 품절됐거나 재고가 모자란 상품은 표시만 하고 주문에서는 뺀다.
 */
export function ShopCartPage() {
  const navigate = useNavigate()
  const [items, setItems] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    shopApi
      .cart()
      .then((cart) => !cancelled && setItems(cart))
      .catch((err) => {
        if (cancelled) return
        setItems([])
        setError(err.message)
      })
    return () => {
      cancelled = true
    }
  }, [])

  // 서버가 바뀐 장바구니를 돌려준다
  async function change(action) {
    setBusy(true)
    setError('')
    try {
      setItems(await action())
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  if (items === null) return <p className="loading">불러오는 중…</p>

  const buyable = items.filter((i) => i.available)
  const total = buyable.reduce((sum, i) => sum + i.pricePoints * i.quantity, 0)

  function checkout() {
    navigate('/shop/checkout', {
      state: { items: buyable.map((i) => ({ productId: i.productId, quantity: i.quantity })), fromCart: true },
    })
  }

  return (
    <div className="container page">
      <div className="ch-head sh-head">
        <div>
          <h1 className="page-title">장바구니</h1>
          <p className="page-sub">
            <Link to="/shop" className="back-button">
              <svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" aria-hidden="true">
                <path d="M6.5 1.5L2 5l4.5 3.5" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
              상점 계속 둘러보기
            </Link>
          </p>
        </div>
      </div>

      {error && <p className="form-error">{error}</p>}
      {items.length === 0 ? (
        <div className="cl-empty">
          <h3>장바구니가 비어 있어요</h3>
          <p>상점에서 마음에 드는 상품을 담아 보세요.</p>
          <Link to="/shop" className="btn btn-dark-outline">
            상점 둘러보기
          </Link>
        </div>
      ) : (
        <div className="sh-cart">
          {/* 사용자 시안: 상품 줄을 흰 카드 하나에 모은다 */}
          <ul className="sh-lines is-card">
            {items.map((i) => (
              <li key={i.productId} className={`sh-line${i.available ? '' : ' is-unavailable'}`}>
                <Link to={`/shop/products/${i.productId}`} className="sh-line-image">
                  <ProductImage product={i} />
                </Link>
                <div className="sh-line-main">
                  <Link to={`/shop/products/${i.productId}`} className="sh-line-name">
                    {i.name}
                  </Link>
                  <span className="sh-line-price">{points(i.pricePoints)}</span>
                  {!i.available && (
                    <span className="sh-line-warn">
                      {i.stock <= 0 ? '품절됐어요' : i.stock < i.quantity ? `재고가 ${i.stock}개 남았어요` : '지금은 살 수 없어요'}
                    </span>
                  )}
                </div>
                <QuantityStepper
                  value={i.quantity}
                  max={Math.max(1, Math.min(i.stock, MAX_QUANTITY))}
                  disabled={busy}
                  label={`${i.name} 수량`}
                  onChange={(q) => change(() => shopApi.putCart(i.productId, q))}
                />
                <strong className="sh-line-sum">{points(i.pricePoints * i.quantity)}</strong>
                <button
                  type="button"
                  className="sh-line-remove"
                  aria-label={`${i.name} 빼기`}
                  disabled={busy}
                  onClick={() => change(() => shopApi.removeCart(i.productId))}
                >
                  <CloseIcon />
                </button>
              </li>
            ))}
          </ul>

          <aside className="card sh-summary">
            <h2>주문 예상 금액</h2>
            <dl>
              <div>
                <dt>상품 {buyable.length}가지</dt>
                <dd>{points(total)}</dd>
              </div>
              <div className="is-total">
                <dt>결제할 포인트</dt>
                <dd>{points(total)}</dd>
              </div>
            </dl>
            {buyable.length < items.length && (
              <p className="sh-summary-note">살 수 없는 상품은 주문에서 빠져요.</p>
            )}
            <button type="button" className="sh-pay-btn" disabled={busy || buyable.length === 0} onClick={checkout}>
              주문하기
            </button>
          </aside>
        </div>
      )}
    </div>
  )
}
