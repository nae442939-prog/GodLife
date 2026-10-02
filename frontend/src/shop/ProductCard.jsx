import { Link } from 'react-router-dom'
import { HeartIcon } from '../community/icons.jsx'
import { points } from './format.js'
import { ProductImage } from './ProductImage.jsx'

/**
 * 상품 카드 (상점 목록 · 찜 목록). 카드 전체가 상세로 가는 링크이고, 오른쪽 위 하트로 찜한다.
 * onWish 가 없으면(비로그인) 하트를 누를 때 로그인으로 보낸다 — 부르는 쪽이 정한다.
 */
export function ProductCard({ product: p, onWish }) {
  return (
    <li className={`sh-card${p.soldOut ? ' is-soldout' : ''}`}>
      <Link to={`/shop/products/${p.id}`} className="sh-card-link">
        <ProductImage product={p} />
        <span className="sh-card-body">
          <span className="sh-card-meta">
            {p.categoryName} · {p.sponsorName}
          </span>
          <strong className="sh-card-name">{p.name}</strong>
          <span className="sh-card-foot">
            <span className="sh-price">{points(p.pricePoints)}</span>
            {/* 시안: 쿠폰 상품은 '쿠폰', 실물 상품은 '정품' 꼬리표 (품절이면 '품절') */}
            {p.soldOut ? (
              <span className="sh-tag is-soldout">품절</span>
            ) : p.type === 'COUPON' ? (
              <span className="sh-tag is-coupon">쿠폰</span>
            ) : (
              <span className="sh-tag is-genuine">정품</span>
            )}
          </span>
        </span>
      </Link>
      <button
        type="button"
        className={`sh-wish${p.wished ? ' is-on' : ''}`}
        aria-pressed={p.wished}
        aria-label={p.wished ? `${p.name} 찜 풀기` : `${p.name} 찜하기`}
        onClick={() => onWish(p)}
      >
        <HeartIcon filled={p.wished} size={13} />
      </button>
    </li>
  )
}
