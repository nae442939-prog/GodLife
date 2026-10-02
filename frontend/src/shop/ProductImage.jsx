import { BoxIcon, TicketIcon } from './icons.jsx'

/**
 * 상품 사진. 사진이 없는 상품은 종류에 맞는 기본 그림(실물 = 상자, 쿠폰 = 티켓)을 크림색 바탕에 보여 준다 (사용자 시안).
 */
export function ProductImage({ product, className = '' }) {
  const url = product.imageUrl ?? product.firstImageUrl
  const type = product.type ?? product.firstType
  if (url) {
    return (
      <span className={`sh-image ${className}`}>
        <img src={url} alt="" loading="lazy" />
      </span>
    )
  }
  return (
    <span className={`sh-image is-empty is-${type} ${className}`} aria-hidden="true">
      {type === 'COUPON' ? <TicketIcon /> : <BoxIcon />}
    </span>
  )
}
