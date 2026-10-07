import { useState } from 'react'
import { shopApi } from '../api/client.js'
import { Field } from '../components/Field.jsx'

const EMPTY = { recipient: '', phone: '', zipcode: '', address1: '', address2: '', isDefault: false }

/**
 * 배송지 추가 · 수정 폼. address 가 있으면 수정, 없으면 새 배송지.
 * 저장하면 onSaved(저장한 배송지 id) 를 부른다.
 */
export function AddressForm({ address, onSaved, onCancel }) {
  const [form, setForm] = useState(address ? { ...address, address2: address.address2 ?? '' } : EMPTY)
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [busy, setBusy] = useState(false)

  const onChange = (e) => {
    const { name, value, type, checked } = e.target
    setForm((f) => ({ ...f, [name]: type === 'checkbox' ? checked : value }))
    setErrors((errs) => ({ ...errs, [name]: undefined }))
  }

  async function submit(e) {
    e.preventDefault()
    e.stopPropagation() // 결제 화면 안에 들어가도 바깥 폼이 같이 제출되지 않게
    setFormError('')
    const body = {
      recipient: form.recipient.trim(),
      phone: form.phone.trim(),
      zipcode: form.zipcode.trim(),
      address1: form.address1.trim(),
      address2: form.address2.trim(),
      isDefault: form.isDefault,
    }
    const found = {}
    if (!body.recipient) found.recipient = '받는 사람을 입력해 주세요.'
    if (!/^01[016789]-?\d{3,4}-?\d{4}$/.test(body.phone)) found.phone = '휴대폰 번호를 다시 확인해 주세요.'
    if (!/^\d{5}$/.test(body.zipcode)) found.zipcode = '우편번호는 숫자 5자리예요.'
    if (!body.address1) found.address1 = '주소를 입력해 주세요.'
    setErrors(found)
    if (Object.keys(found).length > 0) return

    setBusy(true)
    try {
      if (address) {
        await shopApi.updateAddress(address.id, body)
        onSaved(address.id)
      } else {
        const created = await shopApi.addAddress(body)
        onSaved(created.id)
      }
    } catch (err) {
      setErrors(err.fieldErrors ?? {})
      if (Object.keys(err.fieldErrors ?? {}).length === 0) setFormError(err.message)
      setBusy(false)
    }
  }

  return (
    <form className="sh-address-form cf" onSubmit={submit} noValidate>
      <div className="field-pair">
        <Field
          label="받는 사람"
          name="recipient"
          value={form.recipient}
          onChange={onChange}
          maxLength={50}
          error={errors.recipient}
        />
        <Field
          label="연락처"
          name="phone"
          value={form.phone}
          onChange={onChange}
          maxLength={13}
          placeholder="010-1234-5678"
          error={errors.phone}
        />
      </div>
      <Field
        label="우편번호"
        name="zipcode"
        value={form.zipcode}
        onChange={onChange}
        maxLength={5}
        inputMode="numeric"
        placeholder="숫자 5자리"
        error={errors.zipcode}
      />
      <Field
        label="주소"
        name="address1"
        value={form.address1}
        onChange={onChange}
        maxLength={200}
        placeholder="도로명 주소"
        error={errors.address1}
      />
      <Field
        label="상세 주소"
        name="address2"
        value={form.address2}
        onChange={onChange}
        maxLength={200}
        placeholder="동 · 호수 (선택)"
        error={errors.address2}
      />
      <label className="check">
        <input type="checkbox" name="isDefault" checked={form.isDefault} onChange={onChange} />
        <span>기본 배송지로 쓸게요</span>
      </label>
      {formError && <p className="form-error">{formError}</p>}
      <div className="sh-address-foot">
        {onCancel && (
          <button type="button" className="btn btn-outline btn-sm" disabled={busy} onClick={onCancel}>
            취소
          </button>
        )}
        <button type="submit" className="btn btn-dark btn-sm" disabled={busy}>
          {busy ? '저장하는 중…' : '배송지 저장'}
        </button>
      </div>
    </form>
  )
}
