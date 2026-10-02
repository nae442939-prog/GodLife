// 포인트 상점 화면들이 같이 쓰는 표시용 문구

export const SHOP_SORTS = [
  { value: 'popular', label: '인기순' },
  { value: 'latest', label: '새 상품순' },
  { value: 'price_asc', label: '낮은 가격순' },
  { value: 'price_desc', label: '높은 가격순' },
]

export const ORDER_STATUS = {
  PREPARING: '준비 중',
  SHIPPING: '배송 중',
  DELIVERED: '수령 완료',
  CANCELED: '주문 취소',
}

// 실물 상품의 배송 단계 (주문 상세의 진행 표시)
export const ORDER_STEPS = ['PREPARING', 'SHIPPING', 'DELIVERED']

export const PRODUCT_STATUS = { ON_SALE: '판매 중', SOLD_OUT: '품절', HIDDEN: '숨김' }

export const TYPE_LABEL = { PHYSICAL: '배송 상품', COUPON: '쿠폰 상품' }

// 서버 규칙(ShopDtos)과 같은 값
export const MAX_QUANTITY = 99

export function points(n) {
  return `${Number(n).toLocaleString()}P`
}

/** '2026.10.02 14:30' */
export function dateTime(iso) {
  return `${iso.slice(0, 10).replaceAll('-', '.')} ${iso.slice(11, 16)}`
}

/** 결제 버튼을 누를 때마다 새로 만드는 값 (같은 값으로 두 번 보내면 서버가 한 번만 주문한다) */
export function newRequestKey() {
  return crypto.randomUUID()
}

/**
 * 가진 포인트로 total 을 낼 때 출처별로 얼마씩 빠지는지 (서버와 같은 규칙: 보상 포인트 먼저, 모자란 만큼 충전 포인트).
 * 화면에 미리 보여 주는 값이고, 실제 차감은 서버가 지갑을 잠그고 다시 계산한다.
 */
export function splitPayment(total, wallet) {
  const reward = Math.min(wallet.rewardBalance, total)
  const charged = Math.min(wallet.chargedBalance, total - reward)
  return { reward, charged, shortage: total - reward - charged }
}
