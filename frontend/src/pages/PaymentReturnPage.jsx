import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { paymentApi } from '../api/client.js'

// 같은 주문의 승인 요청은 한 번만 보낸다. (개발 모드에서는 화면이 두 번 그려져 effect 가 두 번 돈다.
// 서버도 한 번만 충전하지만, 두 번째 요청이 '이미 끝난 결제' 오류로 첫 번째의 진짜 오류를 가리지 않게 한다)
const approving = new Map()

function approveOnce(orderId, request) {
  if (!approving.has(orderId)) approving.set(orderId, request())
  return approving.get(orderId)
}

const p = (n) => `${n.toLocaleString()}P`

/**
 * 토스 결제창에서 돌아오는 곳.
 * - /wallet/charge/success?paymentKey=&orderId=&amount=   결제 성공 → 서버에 승인 요청 → '결제 성공' 화면
 * - /wallet/charge/fail?code=&message=&orderId=           결제를 그만뒀거나 실패 → 주문을 닫는다
 * 승인되면 포인트가 충전되고, 얼마가 충전됐는지 · 지금 충전 포인트가 얼마인지 보여 준다.
 */
export function PaymentReturnPage({ step }) {
  const [params] = useSearchParams()
  const failed = step === 'fail'
  const [error, setError] = useState(failed ? params.get('message') || '결제가 완료되지 않았어요.' : '')
  // 승인이 끝나면 충전된 지갑
  const [wallet, setWallet] = useState(null)
  const amount = Number(params.get('amount'))

  useEffect(() => {
    const orderId = params.get('orderId')
    if (failed) {
      if (orderId) paymentApi.fail(orderId).catch(() => {})
      return
    }
    let cancelled = false
    approveOnce(orderId, () => paymentApi.confirm(params.get('paymentKey'), orderId, amount))
      .then((w) => !cancelled && setWallet(w))
      .catch((err) => !cancelled && setError(err.message))
    return () => {
      cancelled = true
    }
    // 주소의 값으로 한 번만 승인한다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <div className="container page wallet-page">
      <div className="card wl-return">
        {error ? (
          <>
            <span className="wl-return-icon is-fail" aria-hidden="true">
              <svg width="30" height="30" viewBox="0 0 24 24" fill="none">
                <path d="M6 6l12 12M18 6L6 18" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" />
              </svg>
            </span>
            <h1>결제가 완료되지 않았어요</h1>
            <p className="wl-return-sub">{error}</p>
            <p className="wl-return-note">포인트는 충전되지 않았고, 결제 금액도 빠지지 않았어요.</p>
            <div className="wl-return-actions">
              <Link to="/wallet" className="btn btn-dark">
                포인트 지갑으로 돌아가기
              </Link>
            </div>
          </>
        ) : wallet ? (
          <>
            <span className="wl-return-icon" aria-hidden="true">
              <svg width="30" height="30" viewBox="0 0 24 24" fill="none">
                <path d="M4 12l5 5L20 6" stroke="currentColor" strokeWidth="2.8" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </span>
            <h1>결제 성공</h1>
            <p className="wl-return-sub">
              <strong>{p(amount)}</strong>가 충전됐어요.
            </p>
            <dl className="wl-return-list">
              <div>
                <dt>결제 금액</dt>
                <dd>{amount.toLocaleString()}원</dd>
              </div>
              <div>
                <dt>충전된 포인트</dt>
                <dd>+{p(amount)}</dd>
              </div>
              <div className="is-total">
                <dt>지금 충전 포인트</dt>
                <dd>{p(wallet.chargedBalance)}</dd>
              </div>
            </dl>
            <p className="wl-return-note">테스트 결제라 실제 돈은 빠지지 않았어요.</p>
            <div className="wl-return-actions">
              <Link to="/challenges" className="btn btn-outline">
                챌린지 둘러보기
              </Link>
              <Link to="/wallet" className="btn btn-dark" replace>
                포인트 지갑으로
              </Link>
            </div>
          </>
        ) : (
          <>
            <h1>결제를 확인하는 중…</h1>
            <p className="wl-return-sub">잠시만 기다려 주세요. 이 화면을 닫지 마세요.</p>
          </>
        )}
      </div>
    </div>
  )
}
