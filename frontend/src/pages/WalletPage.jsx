import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { walletApi } from '../api/client.js'
import { ChargeBox } from '../wallet/ChargeBox.jsx'

const TYPE_LABEL = {
  CHARGE: '포인트 충전',
  CHARGE_CANCEL: '충전 포인트 환불',
  ENTRY_FEE: '챌린지 참가',
  REFUND: '참가비 환급',
  REWARD: '챌린지 보상',
  PURCHASE: '상점 구매',
  PURCHASE_CANCEL: '상점 주문 취소',
  SEASON_BONUS: '시즌 보너스',
  ADJUST: '조정',
}

/** 챌린지가 끝나고 한 번에 받은 몫은 '챌린지 정산 환급', 나머지는 종류 이름 */
function txLabel(t) {
  if (t.type === 'REFUND' && t.settlement) return '챌린지 정산 환급'
  return TYPE_LABEL[t.type] ?? t.type
}

const TIER_LABEL = { BRONZE: '브론즈', SILVER: '실버', GOLD: '골드', PLATINUM: '플래티넘', DIAMOND: '다이아몬드', ADMIN: '관리자' }

const SOURCE_LABEL = { CHARGED: '충전', REWARD: '보상', SHOP: '상점' }

const p = (n) => `${n.toLocaleString()}P`

/**
 * 포인트 지갑: 충전 포인트 / 보상 포인트를 따로 보여 주고, 충전(토스페이먼츠 테스트 결제)과 거래 내역을 둔다.
 * 현금 출금·환전 버튼은 없다 (충전 포인트 미사용분의 결제 취소 환불만 있다).
 */
export function WalletPage() {
  const [wallet, setWallet] = useState(null)
  const [more, setMore] = useState({ items: [], page: 0, done: false })
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    walletApi
      .get()
      .then((w) => !cancelled && setWallet(w))
      .catch((err) => !cancelled && setError(err.message))
    return () => {
      cancelled = true
    }
  }, [])

  async function loadMore() {
    try {
      const next = more.page + 1
      const items = await walletApi.transactions(next)
      setMore((m) => ({ items: [...m.items, ...items], page: next, done: items.length < 20 }))
    } catch (err) {
      setError(err.message)
    }
  }

  if (!wallet) {
    return (
      <div className="container page wallet-page">
        {error ? <p className="form-error">{error}</p> : <p className="muted">불러오는 중…</p>}
      </div>
    )
  }

  const transactions = [...wallet.transactions, ...more.items]
  const firstPageFull = wallet.transactions.length === 20

  return (
    <div className="container page wallet-page">
      <div className="ch-head">
        <h1 className="page-title">포인트 지갑</h1>
        <p className="page-sub">충전 포인트로 챌린지에 참여하고, 받은 보상 포인트는 포인트 상점에서 써요.</p>
      </div>

      <div className="wl-balances">
        <section className="wl-card">
          <p className="wl-label">충전 포인트</p>
          <p className="wl-amount">{p(wallet.chargedBalance)}</p>
          <p className="wl-help">
            챌린지 참가에 써요. 쓰지 않은 만큼만 결제 취소로 환불할 수 있어요.
            {wallet.refundable < wallet.chargedBalance && ` (지금 환불할 수 있는 금액 ${p(wallet.refundable)})`}
          </p>
          <RefundBox
            refundable={wallet.refundable}
            onDone={(w) => {
              setWallet(w)
              setMore({ items: [], page: 0, done: false })
            }}
          />
        </section>
        <section className="wl-card is-reward">
          <p className="wl-label">보상 포인트</p>
          <p className="wl-amount">{p(wallet.rewardBalance)}</p>
          <p className="wl-help">챌린지를 성공하면 받아요. 포인트 상점에서만 쓸 수 있고, 환불·현금화는 안 돼요.</p>
        </section>
      </div>

      <ChargeBox chargedToday={wallet.chargedToday} dailyLimit={wallet.chargeDailyLimit} />
      {error && <p className="form-error">{error}</p>}

      <section className="wl-section">
        <div className="wl-section-head">
          <h2>챌린지에 건 포인트</h2>
          {wallet.newbie ? (
            <span className="wl-sub">가입 30일 이내라 한도가 낮아요</span>
          ) : (
            // 가입 30일이 지나면 한도는 칭호에 따라 정해진다
            <Link to="/me/tier" className="wl-sub wl-orders-link">
              {wallet.tier === 'ADMIN' ? '관리자 한도 →' : `${TIER_LABEL[wallet.tier] ?? wallet.tier} 칭호 한도 →`}
            </Link>
          )}
        </div>
        <Limit label="오늘" used={wallet.betToday} limit={wallet.betDailyLimit} />
        <Limit label="이번 달" used={wallet.betThisMonth} limit={wallet.betMonthlyLimit} />
      </section>

      <section className="wl-section">
        <div className="wl-section-head">
          <h2>거래 내역</h2>
          <Link to="/shop/orders" className="wl-sub wl-orders-link">
            상점 주문 내역 →
          </Link>
        </div>
        {transactions.length === 0 ? (
          <p className="muted">아직 거래 내역이 없어요.</p>
        ) : (
          <ul className="wl-tx">
            {transactions.map((t) => (
              <li key={t.id}>
                <div className="wl-tx-main">
                  <strong>{txLabel(t)}</strong>
                  {t.orderId ? (
                    // 상점 구매 · 주문 취소: 주문한 상품을 보여 주고 주문 상세로 간다
                    <Link to={`/shop/orders/${t.orderId}`} className="wl-tx-order">
                      {t.orderTitle ?? `주문 ${t.orderId}`}
                    </Link>
                  ) : (
                    <span>
                      {t.challengeTitle ?? (t.type === 'ENTRY_FEE' || t.type === 'REFUND' ? '삭제된 챌린지' : '')}
                    </span>
                  )}
                </div>
                <div className="wl-tx-side">
                  <span className={`wl-tx-amount${t.amount > 0 ? ' is-plus' : ''}`}>
                    {t.amount > 0 ? '+' : '−'}
                    {p(Math.abs(t.amount))}
                  </span>
                  <span className="wl-tx-meta">
                    {SOURCE_LABEL[t.source]} {p(t.balanceAfter)} · {t.createdAt.slice(5, 10).replace('-', '.')}{' '}
                    {t.createdAt.slice(11, 16)}
                  </span>
                </div>
              </li>
            ))}
          </ul>
        )}
        {firstPageFull && !more.done && (
          <button type="button" className="btn btn-outline btn-block wl-more" onClick={loadMore}>
            더 보기
          </button>
        )}
      </section>

      <p className="wl-notice">
        갓생살기의 포인트는 챌린지 참여와 포인트 상점에서만 사용할 수 있습니다. 직접 충전한 포인트 중 사용하지 않은 금액만 결제
        취소로 환불받을 수 있으며, 챌린지나 이벤트로 얻은 포인트는 현금으로 출금하거나 환전할 수 없습니다.
      </p>
    </div>
  )
}

/**
 * 충전 포인트 환불: 쓰지 않은 충전 포인트까지만 (PG 결제 취소, 테스트 모드). 금액은 100P 단위, 기본은 전액.
 * 최근 결제부터 차례로 (부분) 취소된다. refundable 은 서버가 계산한 지금 환불할 수 있는 금액이다.
 */
function RefundBox({ refundable: charged, onDone }) {
  const [open, setOpen] = useState(false)
  const [amount, setAmount] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  if (!open) {
    return (
      <button
        type="button"
        className="wl-refund-open"
        onClick={() => {
          setAmount(String(charged))
          setError('')
          setOpen(true)
        }}
        disabled={charged < 100}
      >
        환불하기
      </button>
    )
  }

  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      const w = await walletApi.refund(Number(amount), crypto.randomUUID())
      setOpen(false)
      onDone(w)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="wl-refund" onSubmit={submit}>
      <label htmlFor="refund-amount">환불할 포인트 (최대 {p(charged)})</label>
      <div className="wl-refund-row">
        <input
          id="refund-amount"
          type="number"
          min={100}
          max={charged}
          step={100}
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
        />
        <button type="button" className="btn btn-outline" onClick={() => setOpen(false)} disabled={busy}>
          취소
        </button>
        <button type="submit" className="btn btn-dark" disabled={busy || !amount}>
          {busy ? '환불하는 중…' : '환불'}
        </button>
      </div>
      <p className="wl-help">결제 취소로 돌려드려요. 최근에 결제한 것부터 취소돼요. (테스트 결제라 실제 돈은 오가지 않아요)</p>
      {error && <p className="form-error">{error}</p>}
    </form>
  )
}

function Limit({ label, used, limit }) {
  const percent = limit > 0 ? Math.min(100, Math.round((used / limit) * 100)) : 0
  return (
    <div className="wl-limit">
      <div className="wl-limit-head">
        <span>{label}</span>
        <span>
          <strong>{p(used)}</strong> / {p(limit)}
        </span>
      </div>
      <div className="vf-bar" aria-hidden="true">
        <span style={{ width: `${percent}%` }} />
      </div>
    </div>
  )
}
