import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, Navigate, useSearchParams } from 'react-router-dom'
import { shopAdminApi, shopApi } from '../api/client.js'
import { useAuth } from '../auth/useAuth.js'
import { AdminNav } from '../components/AdminNav.jsx'
import { ORDER_STATUS, PRODUCT_STATUS, TYPE_LABEL, dateTime, points } from '../shop/format.js'
import { ProductImage } from '../shop/ProductImage.jsx'

const TABS = [
  { value: 'orders', label: '주문' },
  { value: 'products', label: '상품' },
  { value: 'sponsors', label: '스폰서' },
]

/**
 * 관리자 화면: 포인트 상점 (/admin/shop?tab=orders|products|sponsors). 관리자(role = ADMIN)만 들어올 수 있다.
 * 주문을 보내고(운송장 번호) 완료 처리하며, 스폰서와 상품을 등록 · 수정한다.
 */
export function AdminShopPage() {
  const { user } = useAuth()
  const [params, setParams] = useSearchParams()
  const tab = TABS.some((t) => t.value === params.get('tab')) ? params.get('tab') : 'orders'

  if (user.role !== 'ADMIN') return <Navigate to="/" replace />

  return (
    <div className="container page">
      <div className="ch-head cl-head">
        <div>
          <h1 className="page-title">관리자 · 포인트 상점</h1>
          <p className="page-sub">주문을 보내고, 스폰서와 상품을 관리해요.</p>
        </div>
      </div>
      <AdminNav />

      <div className="cl-tabs" role="group" aria-label="상점 관리">
        {TABS.map((t) => (
          <button
            key={t.value}
            type="button"
            className={`cl-tab${tab === t.value ? ' is-active' : ''}`}
            aria-pressed={tab === t.value}
            onClick={() => setParams({ tab: t.value }, { replace: true })}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'orders' && <OrdersTab />}
      {tab === 'products' && <ProductsTab />}
      {tab === 'sponsors' && <SponsorsTab />}
    </div>
  )
}

// ---------- 주문 ----------

const ORDER_FILTERS = [
  { value: 'PREPARING', label: '보낼 주문' },
  { value: 'SHIPPING', label: '배송 중' },
  { value: 'DELIVERED', label: '수령 완료' },
  { value: 'CANCELED', label: '취소' },
  { value: '', label: '전체' },
]

function OrdersTab() {
  const [filter, setFilter] = useState('PREPARING')
  const [state, setState] = useState({ key: null, items: [], error: '' })
  const [busy, setBusy] = useState(null)
  const [tracking, setTracking] = useState({})

  const load = useCallback(
    () =>
      shopAdminApi
        .orders(filter)
        .then((items) => setState({ key: filter, items, error: '' }))
        .catch((err) => setState({ key: filter, items: [], error: err.message })),
    [filter],
  )

  useEffect(() => {
    load()
  }, [load])

  async function act(orderId, action) {
    setBusy(orderId)
    try {
      await action()
      await load()
    } catch (err) {
      setState((s) => ({ ...s, error: err.message }))
    } finally {
      setBusy(null)
    }
  }

  return (
    <>
      <div className="adm-filter" role="group" aria-label="주문 상태">
        {ORDER_FILTERS.map((f) => (
          <button
            key={f.value}
            type="button"
            className={`adm-nav-link${filter === f.value ? ' is-active' : ''}`}
            aria-pressed={filter === f.value}
            onClick={() => setFilter(f.value)}
          >
            {f.label}
          </button>
        ))}
      </div>

      {state.error && <p className="form-error">{state.error}</p>}
      {state.key !== filter ? (
        <p className="muted">불러오는 중…</p>
      ) : state.items.length === 0 ? (
        !state.error && <p className="muted">{filter === 'PREPARING' ? '보낼 주문이 없어요.' : '주문이 없어요.'}</p>
      ) : (
        <ul className="inquiry-list adm-list">
          {state.items.map(({ userId, nickname, order: o }) => (
            <li key={o.id}>
              <div className="inquiry-head">
                <span className={`sh-status is-${o.status}`}>{ORDER_STATUS[o.status]}</span>
                <strong>
                  주문 {o.id} · <Link to={`/users/${userId}`}>{nickname}</Link>
                </strong>
                <small>
                  {dateTime(o.orderedAt)} · {points(o.totalPoints)}
                </small>
              </div>
              <ul className="adm-reasons">
                {o.items.map((item) => (
                  <li key={item.productId}>
                    {item.name} × {item.quantity}
                    {item.coupons.length > 0 && ' (쿠폰 발급됨)'}
                  </li>
                ))}
              </ul>
              {o.shipping && (
                <p className="inquiry-content">
                  {o.shipping.recipient} · {o.shipping.phone}
                  {'\n'}({o.shipping.zipcode}) {o.shipping.address1} {o.shipping.address2}
                  {o.trackingNo && `\n운송장 ${o.trackingNo}`}
                </p>
              )}
              {o.status === 'PREPARING' && (
                <div className="adm-answer">
                  <div className="adm-answer-foot">
                    <input
                      type="text"
                      className="adm-tracking"
                      value={tracking[o.id] ?? ''}
                      maxLength={50}
                      placeholder="운송장 번호"
                      aria-label={`주문 ${o.id} 운송장 번호`}
                      onChange={(e) => setTracking((t) => ({ ...t, [o.id]: e.target.value }))}
                    />
                    <button
                      type="button"
                      className="btn btn-outline btn-sm"
                      disabled={busy === o.id || !o.cancelable}
                      title={o.cancelable ? undefined : '쿠폰이 발급된 주문은 취소할 수 없어요'}
                      onClick={() => act(o.id, () => shopAdminApi.cancel(o.id))}
                    >
                      주문 취소
                    </button>
                    <button
                      type="button"
                      className="btn btn-dark btn-sm"
                      disabled={busy === o.id || !(tracking[o.id] ?? '').trim()}
                      onClick={() => act(o.id, () => shopAdminApi.ship(o.id, tracking[o.id].trim()))}
                    >
                      발송 처리
                    </button>
                  </div>
                </div>
              )}
              {o.status === 'SHIPPING' && (
                <div className="adm-answer">
                  <div className="adm-answer-foot">
                    <button
                      type="button"
                      className="btn btn-dark btn-sm"
                      disabled={busy === o.id}
                      onClick={() => act(o.id, () => shopAdminApi.deliver(o.id))}
                    >
                      배송 완료 처리
                    </button>
                  </div>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </>
  )
}

// ---------- 상품 ----------

const EMPTY_PRODUCT = {
  sponsorId: '',
  categoryId: '',
  type: 'PHYSICAL',
  name: '',
  description: '',
  pricePoints: '1000',
  stock: '10',
  status: 'ON_SALE',
}

function ProductsTab() {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  // null = 닫힘, 'new' = 새 상품, 숫자 = 그 상품 수정
  const [editing, setEditing] = useState(null)

  const load = useCallback(
    () =>
      Promise.all([shopAdminApi.products(), shopAdminApi.sponsors(), shopApi.categories()])
        .then(([products, sponsors, categories]) => {
          setData({ products, sponsors, categories })
          setError('')
        })
        .catch((err) => {
          setData((d) => d ?? { products: [], sponsors: [], categories: [] })
          setError(err.message)
        }),
    [],
  )

  useEffect(() => {
    load()
  }, [load])

  if (!data) return <p className="muted">불러오는 중…</p>

  return (
    <>
      {error && <p className="form-error">{error}</p>}
      {data.sponsors.length === 0 ? (
        <p className="muted">상품을 등록하려면 먼저 스폰서 탭에서 스폰서를 등록해 주세요.</p>
      ) : editing === null ? (
        <p className="adm-add">
          <button type="button" className="btn btn-dark-outline" onClick={() => setEditing('new')}>
            상품 등록
          </button>
        </p>
      ) : (
        <ProductForm
          key={editing}
          product={editing === 'new' ? null : data.products.find((p) => p.id === editing)}
          sponsors={data.sponsors}
          categories={data.categories}
          onSaved={async () => {
            await load()
            setEditing(null)
          }}
          onCancel={() => setEditing(null)}
        />
      )}

      {data.products.length === 0 ? (
        <p className="muted">등록한 상품이 없어요.</p>
      ) : (
        <ul className="inquiry-list adm-list">
          {data.products.map((p) => (
            <li key={p.id} className="adm-product">
              <ProductImage product={p} />
              <div className="adm-product-main">
                <div className="inquiry-head">
                  <span className={`inquiry-status${p.status === 'ON_SALE' ? ' is-answered' : ''}`}>
                    {PRODUCT_STATUS[p.status]}
                  </span>
                  <strong>{p.name}</strong>
                  <small>
                    {p.categoryName} · {p.sponsorName} · {TYPE_LABEL[p.type]}
                  </small>
                </div>
                <p className="adm-product-facts">
                  {points(p.pricePoints)} · 재고 {p.stock.toLocaleString()}개 · 판매 {p.soldCount.toLocaleString()}개
                </p>
              </div>
              <button type="button" className="btn btn-outline btn-sm" onClick={() => setEditing(p.id)}>
                수정
              </button>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}

function ProductForm({ product, sponsors, categories, onSaved, onCancel }) {
  const fileInput = useRef(null)
  const [form, setForm] = useState(
    product
      ? {
          sponsorId: String(product.sponsorId),
          categoryId: String(product.categoryId),
          type: product.type,
          name: product.name,
          description: product.description,
          pricePoints: String(product.pricePoints),
          stock: String(product.stock),
          status: product.status,
        }
      : { ...EMPTY_PRODUCT, sponsorId: String(sponsors[0].id), categoryId: String(categories[0]?.id ?? '') },
  )
  const [file, setFile] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const onChange = (e) => setForm((f) => ({ ...f, [e.target.name]: e.target.value }))

  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      const body = {
        sponsorId: Number(form.sponsorId),
        categoryId: Number(form.categoryId),
        type: form.type,
        name: form.name.trim(),
        description: form.description.trim(),
        pricePoints: Number(form.pricePoints),
        stock: Number(form.stock),
        status: form.status,
      }
      let id = product?.id
      if (product) await shopAdminApi.updateProduct(id, body)
      else id = (await shopAdminApi.addProduct(body)).id
      if (file) await shopAdminApi.changeProductImage(id, file)
      await onSaved()
    } catch (err) {
      const first = Object.values(err.fieldErrors ?? {})[0]
      setError(first ?? err.message)
      setBusy(false)
    }
  }

  return (
    <form className="card adm-form" onSubmit={submit}>
      <h2>{product ? '상품 수정' : '상품 등록'}</h2>
      <div className="adm-form-grid">
        <label>
          스폰서
          <select name="sponsorId" value={form.sponsorId} onChange={onChange}>
            {sponsors.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
                {!s.active && ' (정지)'}
              </option>
            ))}
          </select>
        </label>
        <label>
          카테고리
          <select name="categoryId" value={form.categoryId} onChange={onChange}>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          종류
          <select name="type" value={form.type} onChange={onChange}>
            <option value="PHYSICAL">배송 상품 (실물)</option>
            <option value="COUPON">쿠폰 상품 (이용권 · 상품권)</option>
          </select>
        </label>
        <label>
          판매 상태
          <select name="status" value={form.status} onChange={onChange}>
            {Object.entries(PRODUCT_STATUS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label className="is-wide">
          상품 이름
          <input name="name" value={form.name} onChange={onChange} maxLength={150} required />
        </label>
        <label className="is-wide">
          상품 설명
          <textarea name="description" value={form.description} onChange={onChange} maxLength={2000} rows={4} required />
        </label>
        <label>
          가격 (P)
          <input name="pricePoints" type="number" min={100} step={100} value={form.pricePoints} onChange={onChange} required />
        </label>
        <label>
          재고
          <input name="stock" type="number" min={0} value={form.stock} onChange={onChange} required />
        </label>
        <label className="is-wide">
          상품 사진 <span className="muted">(JPG·PNG 5MB 이하, 없으면 기본 그림)</span>
          <input
            ref={fileInput}
            type="file"
            accept="image/jpeg,image/png"
            onChange={(e) => setFile(e.target.files[0] ?? null)}
          />
        </label>
      </div>
      {error && <p className="form-error">{error}</p>}
      <div className="adm-answer-foot">
        <button type="button" className="btn btn-outline btn-sm" disabled={busy} onClick={onCancel}>
          취소
        </button>
        <button type="submit" className="btn btn-dark btn-sm" disabled={busy}>
          {busy ? '저장하는 중…' : '저장'}
        </button>
      </div>
    </form>
  )
}

// ---------- 스폰서 ----------

function SponsorsTab() {
  const [sponsors, setSponsors] = useState(null)
  const [error, setError] = useState('')
  const [editing, setEditing] = useState(null)

  const load = useCallback(
    () =>
      shopAdminApi
        .sponsors()
        .then((list) => {
          setSponsors(list)
          setError('')
        })
        .catch((err) => {
          setSponsors((s) => s ?? [])
          setError(err.message)
        }),
    [],
  )

  useEffect(() => {
    load()
  }, [load])

  if (!sponsors) return <p className="muted">불러오는 중…</p>

  return (
    <>
      {error && <p className="form-error">{error}</p>}
      {editing === null ? (
        <p className="adm-add">
          <button type="button" className="btn btn-dark-outline" onClick={() => setEditing('new')}>
            스폰서 등록
          </button>
        </p>
      ) : (
        <SponsorForm
          key={editing}
          sponsor={editing === 'new' ? null : sponsors.find((s) => s.id === editing)}
          onSaved={async () => {
            await load()
            setEditing(null)
          }}
          onCancel={() => setEditing(null)}
        />
      )}

      {sponsors.length === 0 ? (
        <p className="muted">등록한 스폰서가 없어요.</p>
      ) : (
        <ul className="inquiry-list adm-list">
          {sponsors.map((s) => (
            <li key={s.id} className="adm-product">
              <div className="adm-product-main">
                <div className="inquiry-head">
                  <span className={`inquiry-status${s.active ? ' is-answered' : ' is-rejected'}`}>
                    {s.active ? '입점 중' : '정지'}
                  </span>
                  <strong>{s.name}</strong>
                  <small>
                    {s.contactEmail} · 상품 {s.productCount}개
                  </small>
                </div>
              </div>
              <button type="button" className="btn btn-outline btn-sm" onClick={() => setEditing(s.id)}>
                수정
              </button>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}

function SponsorForm({ sponsor, onSaved, onCancel }) {
  const [form, setForm] = useState(
    sponsor
      ? { name: sponsor.name, contactEmail: sponsor.contactEmail, active: sponsor.active }
      : { name: '', contactEmail: '', active: true },
  )
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      const body = { name: form.name.trim(), contactEmail: form.contactEmail.trim(), active: form.active }
      if (sponsor) await shopAdminApi.updateSponsor(sponsor.id, body)
      else await shopAdminApi.addSponsor(body)
      await onSaved()
    } catch (err) {
      const first = Object.values(err.fieldErrors ?? {})[0]
      setError(first ?? err.message)
      setBusy(false)
    }
  }

  return (
    <form className="card adm-form" onSubmit={submit}>
      <h2>{sponsor ? '스폰서 수정' : '스폰서 등록'}</h2>
      <div className="adm-form-grid">
        <label>
          스폰서 이름
          <input value={form.name} maxLength={100} required onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} />
        </label>
        <label>
          담당자 이메일
          <input
            type="email"
            value={form.contactEmail}
            maxLength={255}
            required
            onChange={(e) => setForm((f) => ({ ...f, contactEmail: e.target.value }))}
          />
        </label>
        <label className="is-wide adm-check">
          <input
            type="checkbox"
            checked={form.active}
            onChange={(e) => setForm((f) => ({ ...f, active: e.target.checked }))}
          />
          입점 중 (끄면 이 스폰서의 상품이 상점에서 보이지 않아요)
        </label>
      </div>
      {error && <p className="form-error">{error}</p>}
      <div className="adm-answer-foot">
        <button type="button" className="btn btn-outline btn-sm" disabled={busy} onClick={onCancel}>
          취소
        </button>
        <button type="submit" className="btn btn-dark btn-sm" disabled={busy}>
          {busy ? '저장하는 중…' : '저장'}
        </button>
      </div>
    </form>
  )
}
