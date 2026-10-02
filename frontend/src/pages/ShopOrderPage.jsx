import { useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { shopApi } from '../api/client.js'
import { ORDER_STATUS, ORDER_STEPS, dateTime, points } from '../shop/format.js'
import { ProductImage } from '../shop/ProductImage.jsx'

/**
 * 주문 상세 (/shop/orders/:id): 진행 상태 · 상품 · 쿠폰 번호 · 배송 정보 · 결제 내역.
 * '준비 중'이면 취소할 수 있고(쿠폰이 발급된 주문은 제외), '배송 중'이면 수령 확인을 누를 수 있다.
 */
export function ShopOrderPage() {
  const { id } = useParams()
  const location = useLocation()
  const [state, setState] = useState({ key: null, order: null, error: '' })
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    shopApi
      .orderDetail(id)
      .then((order) => !cancelled && setState({ key: id, order, error: '' }))
      .catch((err) => !cancelled && setState({ key: id, order: null, error: err.message }))
    return () => {
      cancelled = true
    }
  }, [id])

  if (state.key !== id) return <p className="loading">불러오는 중…</p>
  if (state.error) {
    return (
      <div className="container page">
        <div className="invite-missing card">
          <h1>주문을 열 수 없어요</h1>
          <p>{state.error}</p>
          <Link to="/shop/orders" className="btn btn-dark-outline">
            주문 내역으로
          </Link>
        </div>
      </div>
    )
  }

  const o = state.order
  const step = ORDER_STEPS.indexOf(o.status)

  // 서버가 바뀐 주문을 돌려준다
  async function act(action) {
    setBusy(true)
    setError('')
    try {
      const order = await action(o.id)
      setState((s) => ({ ...s, order }))
      setConfirming(false)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="container page sh-order-page">
      <p className="cm-back">
        <Link to="/shop/orders" className="back-button">
          <svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" aria-hidden="true">
            <path d="M6.5 1.5L2 5l4.5 3.5" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          주문 내역
        </Link>
      </p>

      {location.state?.justOrdered && (
        <p className="sh-done" role="status">
          주문이 완료됐어요! {o.shipping ? '상품을 준비해서 보내 드릴게요.' : '아래에서 쿠폰 번호를 확인해 보세요.'}
        </p>
      )}

      <section className="card sh-section">
        <div className="sh-section-head">
          <h1 className="sh-order-title">
            <span className={`sh-status is-${o.status}`}>{ORDER_STATUS[o.status]}</span>
            주문 번호 {o.id}
          </h1>
          <span className="sh-order-date">{dateTime(o.orderedAt)}</span>
        </div>

        {o.shipping && o.status !== 'CANCELED' && (
          <ol className="sh-steps" aria-label="배송 진행">
            {ORDER_STEPS.map((s, i) => (
              <li key={s} className={i <= step ? 'is-done' : ''} aria-current={i === step ? 'step' : undefined}>
                {ORDER_STATUS[s]}
              </li>
            ))}
          </ol>
        )}
        {o.status === 'CANCELED' && (
          <p className="muted">{dateTime(o.canceledAt)}에 취소했어요. 쓴 포인트는 그대로 돌려 드렸어요.</p>
        )}
        {o.trackingNo && (
          <p className="sh-tracking">
            운송장 번호 <strong>{o.trackingNo}</strong>
          </p>
        )}

        <ul className="sh-lines is-plain">
          {o.items.map((item) => (
            <li key={item.productId} className="sh-line">
              <Link to={`/shop/products/${item.productId}`} className="sh-line-image">
                <ProductImage product={item} />
              </Link>
              <div className="sh-line-main">
                <Link to={`/shop/products/${item.productId}`} className="sh-line-name">
                  {item.name}
                </Link>
                <span className="sh-line-price">
                  {points(item.unitPoints)} × {item.quantity}
                </span>
                {item.coupons.length > 0 && (
                  <ul className="sh-coupons" aria-label="쿠폰 번호">
                    {item.coupons.map((code) => (
                      <li key={code}>{code}</li>
                    ))}
                  </ul>
                )}
              </div>
              <strong className="sh-line-sum">{points(item.unitPoints * item.quantity)}</strong>
            </li>
          ))}
        </ul>

        {(o.cancelable || o.status === 'SHIPPING') && (
          <div className="sh-order-actions">
            {o.status === 'SHIPPING' && (
              <button type="button" className="btn btn-dark" disabled={busy} onClick={() => act(shopApi.receiveOrder)}>
                수령 확인
              </button>
            )}
            {o.cancelable &&
              (confirming ? (
                <>
                  <span className="cm-confirm">이 주문을 취소할까요?</span>
                  <button type="button" className="btn btn-dark btn-sm" disabled={busy} onClick={() => act(shopApi.cancelOrder)}>
                    주문 취소
                  </button>
                  <button type="button" className="btn btn-outline btn-sm" disabled={busy} onClick={() => setConfirming(false)}>
                    그대로 두기
                  </button>
                </>
              ) : (
                <button type="button" className="btn btn-outline" onClick={() => setConfirming(true)}>
                  주문 취소
                </button>
              ))}
          </div>
        )}
        {error && <p className="form-error">{error}</p>}
      </section>

      <div className="sh-order-grid">
        {o.shipping && (
          <section className="card sh-section">
            <h2>배송 정보</h2>
            <dl className="sh-facts">
              <div>
                <dt>받는 사람</dt>
                <dd>{o.shipping.recipient}</dd>
              </div>
              <div>
                <dt>연락처</dt>
                <dd>{o.shipping.phone}</dd>
              </div>
              <div>
                <dt>주소</dt>
                <dd>
                  ({o.shipping.zipcode}) {o.shipping.address1} {o.shipping.address2}
                </dd>
              </div>
            </dl>
          </section>
        )}
        <section className="card sh-section">
          <h2>결제 내역</h2>
          <dl className="sh-facts">
            <div>
              <dt>보상 포인트</dt>
              <dd>{points(o.rewardPoints)}</dd>
            </div>
            <div>
              <dt>충전 포인트</dt>
              <dd>{points(o.chargedPoints)}</dd>
            </div>
            <div className="is-total">
              <dt>합계</dt>
              <dd>{points(o.totalPoints)}</dd>
            </div>
          </dl>
        </section>
      </div>
    </div>
  )
}
