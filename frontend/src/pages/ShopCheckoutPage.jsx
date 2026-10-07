import { useCallback, useEffect, useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { shopApi, walletApi } from '../api/client.js'
import { AddressForm } from '../shop/AddressForm.jsx'
import { newRequestKey, points, splitPayment } from '../shop/format.js'
import { ProductImage } from '../shop/ProductImage.jsx'

/**
 * 주문 · 결제 (/shop/checkout). 상품 상세의 [바로 구매]나 장바구니의 [주문하기]가 고른 상품을 들고 온다
 * (location.state = { items: [{ productId, quantity }], fromCart }). 주소로 바로 들어오면 장바구니로 보낸다.
 * 결제는 보상 포인트 먼저, 모자란 만큼 충전 포인트. 화면의 금액은 미리 보기이고 실제 차감은 서버가 다시 계산한다.
 */
export function ShopCheckoutPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const wanted = location.state?.items
  const fromCart = Boolean(location.state?.fromCart)

  const [data, setData] = useState(null)
  const [loadError, setLoadError] = useState('')
  const [addressId, setAddressId] = useState(null)
  // 'new' = 새 배송지 입력, 숫자 = 그 배송지 수정, null = 닫힘
  const [editing, setEditing] = useState(null)
  // 이 화면에서 누르는 결제는 모두 같은 요청으로 본다 (더블클릭 · 다시 시도해도 한 번만 주문된다)
  const [requestKey] = useState(newRequestKey)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const loadAddresses = useCallback(async (pick) => {
    const addresses = await shopApi.addresses()
    setData((d) => (d ? { ...d, addresses } : d))
    setAddressId(pick ?? addresses.find((a) => a.isDefault)?.id ?? addresses[0]?.id ?? null)
    return addresses
  }, [])

  useEffect(() => {
    if (!wanted?.length) return
    let cancelled = false
    Promise.all([
      Promise.all(wanted.map((w) => shopApi.product(w.productId))),
      walletApi.get(),
      shopApi.addresses(),
    ])
      .then(([products, wallet, addresses]) => {
        if (cancelled) return
        setData({
          lines: products.map((p, i) => ({ product: p, quantity: wanted[i].quantity })),
          wallet,
          addresses,
        })
        setAddressId(addresses.find((a) => a.isDefault)?.id ?? addresses[0]?.id ?? null)
        // 배송받을 상품이 있는데 저장한 배송지가 없으면 바로 입력 칸을 연다 (쿠폰만 사면 배송지가 필요 없다)
        if (addresses.length === 0 && products.some((p) => p.type === 'PHYSICAL')) setEditing('new')
      })
      .catch((err) => !cancelled && setLoadError(err.message))
    return () => {
      cancelled = true
    }
  }, [wanted])

  if (!wanted?.length) return <Navigate to="/shop/cart" replace />
  if (loadError) {
    return (
      <div className="container page">
        <p className="form-error">{loadError}</p>
        <Link to="/shop/cart">장바구니로</Link>
      </div>
    )
  }
  if (!data) return <p className="loading">불러오는 중…</p>

  const { lines, wallet, addresses } = data
  const total = lines.reduce((sum, l) => sum + l.product.pricePoints * l.quantity, 0)
  const needsShipping = lines.some((l) => l.product.type === 'PHYSICAL')
  const hasCoupon = lines.some((l) => l.product.type === 'COUPON')
  const pay = splitPayment(total, wallet)
  const blocked = lines.find((l) => l.product.soldOut || l.product.stock < l.quantity)
  // 배송지는 실물 상품이 있을 때만 필요하다. 입력 중이면 저장해야 결제할 수 있다
  const addressReady = !needsShipping || (addressId != null && editing === null)
  const canPay = !busy && pay.shortage === 0 && !blocked && addressReady

  async function submit() {
    setBusy(true)
    setError('')
    try {
      const created = await shopApi.order({
        items: lines.map((l) => ({ productId: l.product.id, quantity: l.quantity })),
        addressId: needsShipping ? addressId : null,
        requestKey,
        fromCart,
      })
      navigate(`/shop/orders/${created.id}`, { replace: true, state: { justOrdered: true } })
    } catch (err) {
      setError(err.message)
      setBusy(false)
    }
  }

  return (
    <div className="container page">
      <div className="ch-head sh-head">
        <div>
          <h1 className="page-title">주문 · 결제</h1>
          <p className="page-sub">
            <Link to={fromCart ? '/shop/cart' : `/shop/products/${lines[0].product.id}`} className="back-button">
              <svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" aria-hidden="true">
                <path d="M6.5 1.5L2 5l4.5 3.5" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
              {fromCart ? '장바구니로 돌아가기' : '상품으로 돌아가기'}
            </Link>
          </p>
        </div>
      </div>

      <div className="sh-cart">
        <div className="sh-checkout-main">
          <section className="card sh-section">
            <h2>주문 상품</h2>
            <ul className="sh-lines is-plain">
              {lines.map((l) => (
                <li key={l.product.id} className="sh-line">
                  <span className="sh-line-image">
                    <ProductImage product={l.product} />
                  </span>
                  <div className="sh-line-main">
                    <span className="sh-line-name">{l.product.name}</span>
                    <span className="sh-line-price">
                      {points(l.product.pricePoints)} × {l.quantity}
                      {l.product.type === 'COUPON' && ' · 쿠폰 상품'}
                    </span>
                    {(l.product.soldOut || l.product.stock < l.quantity) && (
                      <span className="sh-line-warn">
                        {l.product.soldOut ? '품절됐어요' : `재고가 ${l.product.stock}개 남았어요`}
                      </span>
                    )}
                  </div>
                  <strong className="sh-line-sum">{points(l.product.pricePoints * l.quantity)}</strong>
                </li>
              ))}
            </ul>
          </section>

          {needsShipping && (
            <section className="card sh-section">
              <div className="sh-section-head">
                <h2>배송지</h2>
                {editing === null && (
                  <button type="button" className="cm-text-btn" onClick={() => setEditing('new')}>
                    + 새 배송지
                  </button>
                )}
              </div>
              {addresses.length === 0 && editing === null && (
                <p className="muted">저장한 배송지가 없어요. 새 배송지를 입력해 주세요.</p>
              )}
              {editing === null && (
                <ul className="sh-addresses" role="radiogroup" aria-label="배송지">
                  {addresses.map((a) => (
                    <li key={a.id}>
                      <label className={`sh-address${addressId === a.id ? ' is-active' : ''}`}>
                        <input
                          type="radio"
                          name="address"
                          checked={addressId === a.id}
                          onChange={() => setAddressId(a.id)}
                        />
                        <span>
                          <strong>
                            {a.recipient}
                            {a.isDefault && <em>기본</em>}
                          </strong>
                          <span>{a.phone}</span>
                          <span>
                            ({a.zipcode}) {a.address1} {a.address2}
                          </span>
                        </span>
                      </label>
                      <button type="button" className="cm-text-btn" onClick={() => setEditing(a.id)}>
                        수정
                      </button>
                    </li>
                  ))}
                </ul>
              )}
              {editing !== null && (
                <AddressForm
                  key={editing}
                  address={editing === 'new' ? null : addresses.find((a) => a.id === editing)}
                  onSaved={async (id) => {
                    await loadAddresses(id)
                    setEditing(null)
                  }}
                  onCancel={addresses.length > 0 ? () => setEditing(null) : undefined}
                />
              )}
            </section>
          )}

          {hasCoupon && (
            <p className="sh-coupon-note">
              쿠폰 상품은 결제하는 즉시 쿠폰 번호가 발급돼요. 쿠폰이 들어 있는 주문은 취소할 수 없어요.
            </p>
          )}
        </div>

        <aside className="card sh-summary">
          <h2>결제</h2>
          <dl>
            <div>
              <dt>상품 금액</dt>
              <dd>{points(total)}</dd>
            </div>
            <div>
              <dt>
                보상 포인트 사용 <small>({points(wallet.rewardBalance)} 보유)</small>
              </dt>
              <dd>- {points(pay.reward)}</dd>
            </div>
            <div>
              <dt>
                충전 포인트 사용 <small>({points(wallet.chargedBalance)} 보유)</small>
              </dt>
              <dd>- {points(pay.charged)}</dd>
            </div>
            <div className="is-total">
              <dt>결제할 포인트</dt>
              <dd>{points(total)}</dd>
            </div>
          </dl>
          <p className="sh-summary-note">
            상점에서만 쓸 수 있는 보상 포인트를 먼저 쓰고, 모자란 만큼 충전 포인트를 써요.
          </p>
          {pay.shortage > 0 && (
            <p className="sh-shortage">
              포인트가 {points(pay.shortage)} 모자라요. <Link to="/wallet">포인트 지갑에서 충전하기</Link>
            </p>
          )}
          {/* 버튼이 눌리지 않는 이유를 알려 준다 */}
          {!addressReady && (
            <p className="sh-shortage">
              {editing !== null ? '배송지를 입력하고 [배송지 저장]을 누르면 결제할 수 있어요.' : '배송받을 주소를 골라 주세요.'}
            </p>
          )}
          {blocked && <p className="sh-shortage">살 수 없는 상품이 있어요. 수량을 줄이거나 상품을 빼 주세요.</p>}
          {error && <p className="form-error">{error}</p>}
          <button type="button" className="sh-pay-btn" disabled={!canPay} onClick={submit}>
            {busy ? '결제하는 중…' : `${points(total)} 결제하기`}
          </button>
        </aside>
      </div>
    </div>
  )
}
