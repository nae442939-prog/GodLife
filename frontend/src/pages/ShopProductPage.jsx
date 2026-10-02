import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { shopApi } from '../api/client.js'
import { useAuth } from '../auth/useAuth.js'
import { HeartIcon } from '../community/icons.jsx'
import { MAX_QUANTITY, TYPE_LABEL, points } from '../shop/format.js'
import { ProductImage } from '../shop/ProductImage.jsx'
import { QuantityStepper } from '../shop/QuantityStepper.jsx'

/**
 * 상품 상세 (/shop/products/:id). 비로그인도 볼 수 있고, 찜 · 장바구니 · 구매는 로그인해야 한다.
 * [바로 구매]는 이 상품만 들고 결제 화면으로 간다 (장바구니를 거치지 않는다).
 */
export function ShopProductPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const { status } = useAuth()
  const authed = status === 'authed'
  const [state, setState] = useState({ key: null, product: null, error: '' })
  const [quantity, setQuantity] = useState(1)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState({ text: '', error: false })

  const loadKey = `${id}|${status}`

  useEffect(() => {
    if (status === 'loading') return
    let cancelled = false
    shopApi
      .product(id)
      .then((product) => !cancelled && setState({ key: loadKey, product, error: '' }))
      .catch((err) => !cancelled && setState({ key: loadKey, product: null, error: err.message }))
    return () => {
      cancelled = true
    }
  }, [id, status, loadKey])

  if (state.key !== loadKey) return <p className="loading">불러오는 중…</p>
  if (state.error) {
    return (
      <div className="container page">
        <div className="invite-missing card">
          <h1>상품을 열 수 없어요</h1>
          <p>{state.error}</p>
          <Link to="/shop" className="btn btn-dark-outline">
            상점으로
          </Link>
        </div>
      </div>
    )
  }

  const p = state.product
  const max = Math.min(p.stock, MAX_QUANTITY)
  const needLogin = () => navigate('/login', { state: { from: location.pathname } })

  async function toggleWish() {
    if (!authed) return needLogin()
    const wished = !p.wished
    setState((s) => ({ ...s, product: { ...s.product, wished } }))
    try {
      await shopApi.wish(p.id, wished)
    } catch {
      setState((s) => ({ ...s, product: { ...s.product, wished: !wished } }))
    }
  }

  async function addToCart() {
    if (!authed) return needLogin()
    setBusy(true)
    setNotice({ text: '', error: false })
    try {
      // 이미 담긴 상품이면 수량을 더한다
      const cart = await shopApi.cart()
      const inCart = cart.find((c) => c.productId === p.id)?.quantity ?? 0
      await shopApi.putCart(p.id, Math.min(inCart + quantity, max))
      setNotice({ text: '장바구니에 담았어요.', error: false })
    } catch (err) {
      setNotice({ text: err.message, error: true })
    } finally {
      setBusy(false)
    }
  }

  function buyNow() {
    if (!authed) return needLogin()
    navigate('/shop/checkout', { state: { items: [{ productId: p.id, quantity }], fromCart: false } })
  }

  return (
    <div className="container page">
      <p className="cm-back">
        <Link to="/shop" className="back-button">
          <svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" aria-hidden="true">
            <path d="M6.5 1.5L2 5l4.5 3.5" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          포인트 상점
        </Link>
      </p>

      <div className="card sh-detail">
        <ProductImage product={p} className="sh-detail-image" />
        <div className="sh-detail-info">
          <p className="sh-card-meta">
            {p.categoryName} · {p.sponsorName}
          </p>
          <h1>{p.name}</h1>
          <p className="sh-detail-price">{points(p.pricePoints)}</p>

          <dl className="sh-facts">
            <div>
              <dt>받는 방법</dt>
              <dd>
                {TYPE_LABEL[p.type]}
                {p.type === 'COUPON'
                  ? ' — 결제하면 쿠폰 번호가 주문 내역에 바로 나와요'
                  : ' — 배송지를 입력하면 보내 드려요'}
              </dd>
            </div>
            <div>
              <dt>남은 수량</dt>
              <dd>{p.soldOut ? '품절' : `${p.stock.toLocaleString()}개`}</dd>
            </div>
          </dl>

          {p.soldOut ? (
            <p className="sh-soldout-note">지금은 품절이에요. 찜해 두면 찜한 상품에서 다시 볼 수 있어요.</p>
          ) : (
            <div className="sh-buy">
              <QuantityStepper value={quantity} max={max} onChange={setQuantity} />
              <span className="sh-buy-total">
                합계 <strong>{points(p.pricePoints * quantity)}</strong>
              </span>
            </div>
          )}

          <div className="sh-detail-actions">
            <button
              type="button"
              className={`sh-wish-btn${p.wished ? ' is-on' : ''}`}
              aria-pressed={p.wished}
              onClick={toggleWish}
            >
              <HeartIcon filled={p.wished} size={18} />
              {p.wished ? '찜함' : '찜하기'}
            </button>
            {!p.soldOut && (
              <>
                <button type="button" className="btn btn-dark-outline" disabled={busy} onClick={addToCart}>
                  장바구니 담기
                </button>
                <button type="button" className="btn btn-dark" disabled={busy} onClick={buyNow}>
                  바로 구매
                </button>
              </>
            )}
          </div>
          {notice.text && (
            <p className={notice.error ? 'form-error' : 'sh-notice'} role="status">
              {notice.text} {!notice.error && <Link to="/shop/cart">장바구니 보기</Link>}
            </p>
          )}
        </div>
      </div>

      <section className="card sh-desc">
        <h2>상품 설명</h2>
        <p>{p.description}</p>
        {p.type === 'COUPON' && (
          <p className="sh-desc-note">
            쿠폰 상품은 결제하는 즉시 쿠폰 번호가 발급돼 주문을 취소할 수 없어요. (포트폴리오용 가상 쿠폰 번호예요)
          </p>
        )}
      </section>
    </div>
  )
}
