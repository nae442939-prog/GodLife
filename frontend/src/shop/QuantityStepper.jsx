/** 수량 고르기 (− 숫자 +). 1 ~ max 사이로만 바뀐다. */
export function QuantityStepper({ value, max, onChange, disabled = false, label = '수량' }) {
  return (
    <span className="sh-qty" role="group" aria-label={label}>
      <button type="button" aria-label="하나 줄이기" disabled={disabled || value <= 1} onClick={() => onChange(value - 1)}>
        −
      </button>
      <output aria-live="polite">{value}</output>
      <button type="button" aria-label="하나 늘리기" disabled={disabled || value >= max} onClick={() => onChange(value + 1)}>
        +
      </button>
    </span>
  )
}
