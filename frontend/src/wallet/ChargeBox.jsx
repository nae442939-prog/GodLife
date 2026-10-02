import { useEffect, useState } from 'react'
import { paymentApi } from '../api/client.js'

const p = (n) => `${n.toLocaleString()}P`

let tossSdk = null

/** 토스페이먼츠 결제창 SDK 를 처음 쓸 때 한 번만 불러온다 */
function loadTossSdk() {
  if (!tossSdk) {
    tossSdk = new Promise((resolve, reject) => {
      const script = document.createElement('script')
      script.src = 'https://js.tosspayments.com/v2/standard'
      script.onload = () => resolve(window.TossPayments)
      script.onerror = () => {
        tossSdk = null
        reject(new Error('결제창을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'))
      }
      document.head.appendChild(script)
    })
  }
  return tossSdk
}

/**
 * 포인트 충전: 금액을 고르고 토스페이먼츠 결제창(테스트 모드)을 띄운다.
 * 서버가 주문을 먼저 만들고(금액을 적어 둠), 결제가 끝나면 PaymentReturnPage 가 승인을 요청한다.
 * 카드 정보는 토스 결제창에서만 입력한다. 실제 돈은 빠지지 않는다.
 */
export function ChargeBox({ chargedToday, dailyLimit }) {
  const [config, setConfig] = useState(null)
  const [amount, setAmount] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    paymentApi
      .config()
      .then((c) => !cancelled && setConfig(c))
      .catch((err) => !cancelled && setError(err.message))
    return () => {
      cancelled = true
    }
  }, [])

  const left = Math.max(0, dailyLimit - chargedToday)

  async function pay() {
    setBusy(true)
    setError('')
    try {
      const order = await paymentApi.ready(amount)
      const TossPayments = await loadTossSdk()
      const payment = TossPayments(config.tossClientKey).payment({ customerKey: TossPayments.ANONYMOUS })
      try {
        await payment.requestPayment({
          method: 'CARD',
          amount: { currency: 'KRW', value: order.amount },
          orderId: order.orderId,
          orderName: order.orderName,
          successUrl: `${window.location.origin}/wallet/charge/success`,
          failUrl: `${window.location.origin}/wallet/charge/fail`,
        })
      } catch (err) {
        // 결제창을 닫았거나 결제가 실패했다: 만들어 둔 주문을 닫는다
        paymentApi.fail(order.orderId).catch(() => {})
        if (err.code !== 'USER_CANCEL') setError(err.message || '결제가 완료되지 않았어요.')
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="wl-section">
      <div className="wl-section-head">
        <h2>포인트 충전</h2>
        <span className="wl-sub">
          토스페이먼츠 테스트 결제 · 오늘 {p(chargedToday)} / {p(dailyLimit)}
        </span>
      </div>

      {config && (
        <>
          <div className="wl-charge" role="group" aria-label="충전 금액">
            {config.amounts.map((a) => (
              <button
                key={a}
                type="button"
                className={`wl-charge-btn${amount === a ? ' is-active' : ''}`}
                aria-pressed={amount === a}
                onClick={() => setAmount(a)}
                disabled={busy || !config.enabled || a > left}
              >
                {p(a)}
              </button>
            ))}
          </div>

          <button
            type="button"
            className="btn btn-primary btn-block wl-pay"
            onClick={pay}
            disabled={busy || !config.enabled || !amount || amount > left}
          >
            {busy
              ? '결제창을 여는 중…'
              : amount
                ? `${amount.toLocaleString()}원 결제하기`
                : '충전할 금액을 골라 주세요'}
          </button>
          <p className="wl-help">
            {config.enabled
              ? '테스트 결제라 실제 돈은 빠지지 않아요. 카드 정보는 토스페이먼츠 결제창에서만 입력하고 갓생살기에는 저장되지 않아요.'
              : '결제 테스트 키가 아직 설정되지 않았어요. (backend/.env)'}
          </p>
        </>
      )}
      {error && <p className="form-error">{error}</p>}
    </section>
  )
}
