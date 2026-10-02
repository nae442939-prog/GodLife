import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { shopApi } from '../api/client.js'
import { ProductCard } from '../shop/ProductCard.jsx'

/** 찜한 상품 (/shop/wishlist). 하트를 다시 누르면 목록에서 빠진다. */
export function ShopWishlistPage() {
  const [items, setItems] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    shopApi
      .wishlist()
      .then((list) => !cancelled && setItems(list))
      .catch((err) => {
        if (cancelled) return
        setItems([])
        setError(err.message)
      })
    return () => {
      cancelled = true
    }
  }, [])

  async function unwish(product) {
    setError('')
    try {
      await shopApi.wish(product.id, false)
      setItems((list) => list.filter((p) => p.id !== product.id))
    } catch (err) {
      setError(err.message)
    }
  }

  if (items === null) return <p className="loading">불러오는 중…</p>

  return (
    <div className="container page">
      <div className="ch-head sh-head">
        <div>
          <h1 className="page-title">찜한 상품</h1>
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

      {error && <p className="form-error">{error}</p>}
      {items.length === 0 ? (
        !error && (
          <div className="cl-empty">
            <h3>찜한 상품이 없어요</h3>
            <p>마음에 드는 상품의 하트를 눌러 모아 보세요.</p>
            <Link to="/shop" className="btn btn-dark-outline">
              상점 둘러보기
            </Link>
          </div>
        )
      ) : (
        <ul className="sh-grid">
          {items.map((p) => (
            <ProductCard key={p.id} product={p} onWish={unwish} />
          ))}
        </ul>
      )}
    </div>
  )
}
