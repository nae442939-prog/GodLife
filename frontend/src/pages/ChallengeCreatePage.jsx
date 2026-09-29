import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { challengeApi } from '../api/client.js'
import { MODE_LABEL, addDays, daysBetween, toIsoDate } from '../challenge/format.js'
import { Field } from '../components/Field.jsx'

// 서버 규칙(ChallengeCreateRequest)과 같은 값
const MAX_DAYS = 90
const MIN_FEE = 100
const MAX_FEE = 100_000

const MODE_CHOICES = [
  {
    value: 'FREE',
    title: MODE_LABEL.FREE,
    body: '포인트 없이 인증만 해요. 가볍게 습관을 시작하고 싶을 때 좋아요.',
  },
  {
    value: 'BET',
    title: MODE_LABEL.BET,
    body: '참가 포인트를 걸고 도전해요. 성공하면 건 포인트를 그대로 돌려받고, 포기한 사람의 포인트는 보상 포인트로 나눠 받아요.',
  },
]

function initialForm() {
  const tomorrow = addDays(toIsoDate(new Date()), 1)
  return {
    categoryId: '',
    title: '',
    description: '',
    startDate: tomorrow,
    endDate: addDays(tomorrow, 13),
    frequencyType: 'DAILY',
    weeklyCount: '3',
    maxParticipants: '20',
    entryFee: '1000',
    partialRefund: false,
    useWindow: false,
    verifyFrom: '05:00',
    verifyUntil: '09:00',
  }
}

function validate(mode, f) {
  const errors = {}
  const today = toIsoDate(new Date())
  if (!f.categoryId) errors.categoryId = '카테고리를 골라 주세요.'
  if (!f.title.trim()) errors.title = '제목을 입력해 주세요.'
  else if (f.title.trim().length > 50) errors.title = '제목은 50자 이하로 입력해 주세요.'
  if (!f.description.trim()) errors.description = '설명을 입력해 주세요.'
  else if (f.description.trim().length > 1000) errors.description = '설명은 1000자 이하로 입력해 주세요.'
  if (!f.startDate) errors.startDate = '시작일을 골라 주세요.'
  else if (f.startDate < today) errors.startDate = '시작일은 오늘 이후로 정해 주세요.'
  if (!f.endDate) errors.endDate = '종료일을 골라 주세요.'
  else if (f.startDate) {
    const days = daysBetween(f.startDate, f.endDate) + 1
    if (days < 1 || days > MAX_DAYS) errors.endDate = '기간은 1일 이상 90일 이하로 정해 주세요.'
  }
  const max = Number(f.maxParticipants)
  if (!Number.isInteger(max) || max < 2 || max > 100) errors.maxParticipants = '최대 인원은 2 ~ 100명이에요.'
  if (mode === 'BET') {
    const fee = Number(f.entryFee)
    if (!Number.isInteger(fee) || fee < MIN_FEE || fee > MAX_FEE || fee % 100 !== 0) {
      errors.entryFee = '참가 포인트는 100P 단위로 100P ~ 100,000P 사이여야 합니다.'
    }
  }
  if (f.useWindow && !(f.verifyFrom && f.verifyUntil && f.verifyFrom < f.verifyUntil)) {
    errors.verifyUntil = '인증 시작 시각이 끝 시각보다 빨라야 합니다.'
  }
  return errors
}

// 서버의 여러 값 규칙 오류(xxxValid)를 화면의 칸에 붙인다.
const SERVER_FIELD = {
  endDateValid: 'endDate',
  weeklyCountValid: 'weeklyCount',
  entryFeeValid: 'entryFee',
  verifyWindowValid: 'verifyUntil',
}

export function ChallengeCreatePage() {
  const navigate = useNavigate()
  const [mode, setMode] = useState(null)
  const [form, setForm] = useState(initialForm)
  const [categories, setCategories] = useState([])
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    challengeApi.categories().then(setCategories).catch(() => setCategories([]))
  }, [])

  const onChange = (e) => {
    const { name, value, type, checked } = e.target
    setForm((f) => ({ ...f, [name]: type === 'checkbox' ? checked : value }))
    setErrors((errs) => ({ ...errs, [name]: undefined }))
  }

  async function onSubmit(e) {
    e.preventDefault()
    setFormError('')
    const found = validate(mode, form)
    setErrors(found)
    if (Object.keys(found).length > 0) return

    setSubmitting(true)
    try {
      const created = await challengeApi.create({
        categoryId: Number(form.categoryId),
        title: form.title.trim(),
        description: form.description.trim(),
        mode,
        startDate: form.startDate,
        endDate: form.endDate,
        frequencyType: form.frequencyType,
        weeklyCount: form.frequencyType === 'WEEKLY_N' ? Number(form.weeklyCount) : null,
        entryFee: mode === 'BET' ? Number(form.entryFee) : null,
        maxParticipants: Number(form.maxParticipants),
        verifyFrom: form.useWindow ? form.verifyFrom : null,
        verifyUntil: form.useWindow ? form.verifyUntil : null,
        partialRefund: mode === 'BET' && form.partialRefund,
      })
      navigate(`/challenges/${created.id}`, { replace: true })
    } catch (err) {
      const fieldErrors = Object.fromEntries(
        Object.entries(err.fieldErrors ?? {}).map(([k, v]) => [SERVER_FIELD[k] ?? k, v]),
      )
      setErrors(fieldErrors)
      if (Object.keys(fieldErrors).length === 0) setFormError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  if (!mode) {
    return (
      <div className="container page">
        <h1 className="page-title">챌린지 만들기</h1>
        <p className="page-sub">어떤 챌린지를 만들까요?</p>
        <div className="mode-choices">
          {MODE_CHOICES.map((m) => (
            <button key={m.value} type="button" className={`mode-choice is-${m.value}`} onClick={() => setMode(m.value)}>
              <strong>{m.title}</strong>
              <span>{m.body}</span>
            </button>
          ))}
        </div>
      </div>
    )
  }

  const days = form.startDate && form.endDate ? daysBetween(form.startDate, form.endDate) + 1 : 0

  return (
    <div className="container page narrow">
      <h1 className="page-title">{MODE_LABEL[mode]} 만들기</h1>
      <p className="page-sub">
        <button type="button" className="link-button" onClick={() => setMode(null)}>
          ← 챌린지 종류 다시 고르기
        </button>
      </p>

      <form className="card form-card" onSubmit={onSubmit} noValidate>
        <fieldset className="field">
          <legend className="field-label">카테고리</legend>
          <div className="chips">
            {categories.map((c) => (
              <label key={c.id} className={`chip ${form.categoryId === String(c.id) ? 'is-active' : ''}`}>
                <input
                  type="radio"
                  name="categoryId"
                  value={c.id}
                  checked={form.categoryId === String(c.id)}
                  onChange={onChange}
                  className="sr-only"
                />
                {c.name}
              </label>
            ))}
          </div>
          {errors.categoryId && <p className="field-error">{errors.categoryId}</p>}
        </fieldset>

        <Field
          label="제목"
          name="title"
          value={form.title}
          onChange={onChange}
          maxLength={50}
          placeholder="예) 아침 6시 기상 러닝"
          error={errors.title}
        />

        <div className="field">
          <label htmlFor="ch-description">설명</label>
          <textarea
            id="ch-description"
            name="description"
            rows={5}
            maxLength={1000}
            value={form.description}
            onChange={onChange}
            placeholder="어떤 사진으로 인증하면 되는지 적어 주세요. 예) 러닝 앱 기록 화면이나 운동화가 보이게 찍어 주세요."
            aria-invalid={errors.description ? 'true' : undefined}
          />
          {errors.description && <p className="field-error">{errors.description}</p>}
        </div>

        <div className="field-pair">
          <Field
            label="시작일"
            name="startDate"
            type="date"
            value={form.startDate}
            min={toIsoDate(new Date())}
            onChange={onChange}
            error={errors.startDate}
          />
          <Field
            label="종료일"
            name="endDate"
            type="date"
            value={form.endDate}
            min={form.startDate}
            onChange={onChange}
            error={errors.endDate}
            hint={days > 0 ? `총 ${days}일` : undefined}
          />
        </div>

        <fieldset className="field">
          <legend className="field-label">인증 주기</legend>
          <div className="chips">
            <label className={`chip ${form.frequencyType === 'DAILY' ? 'is-active' : ''}`}>
              <input
                type="radio"
                name="frequencyType"
                value="DAILY"
                checked={form.frequencyType === 'DAILY'}
                onChange={onChange}
                className="sr-only"
              />
              매일
            </label>
            <label className={`chip ${form.frequencyType === 'WEEKLY_N' ? 'is-active' : ''}`}>
              <input
                type="radio"
                name="frequencyType"
                value="WEEKLY_N"
                checked={form.frequencyType === 'WEEKLY_N'}
                onChange={onChange}
                className="sr-only"
              />
              주 N회
            </label>
            {form.frequencyType === 'WEEKLY_N' && (
              <select name="weeklyCount" className="select" aria-label="주 몇 회" value={form.weeklyCount} onChange={onChange}>
                {[1, 2, 3, 4, 5, 6].map((n) => (
                  <option key={n} value={n}>
                    주 {n}회
                  </option>
                ))}
              </select>
            )}
          </div>
          {errors.weeklyCount && <p className="field-error">{errors.weeklyCount}</p>}
        </fieldset>

        <Field
          label="최대 인원"
          name="maxParticipants"
          type="number"
          min={2}
          max={100}
          value={form.maxParticipants}
          onChange={onChange}
          error={errors.maxParticipants}
          hint="2 ~ 100명"
        />

        {mode === 'BET' && (
          <>
            <Field
              label="참가 포인트"
              name="entryFee"
              type="number"
              min={MIN_FEE}
              max={MAX_FEE}
              step={100}
              value={form.entryFee}
              onChange={onChange}
              error={errors.entryFee}
              hint="100P 단위, 100P ~ 100,000P. 성공하면 건 포인트를 그대로 돌려받아요."
            />
            <label className="check">
              <input type="checkbox" name="partialRefund" checked={form.partialRefund} onChange={onChange} />
              부분 성공도 인정해요 (인증한 날만큼 비례해서 돌려받기)
            </label>
          </>
        )}

        <label className="check">
          <input type="checkbox" name="useWindow" checked={form.useWindow} onChange={onChange} />
          인증 가능한 시간대를 정할래요 (예: 새벽 기상은 05:00 ~ 09:00)
        </label>
        {form.useWindow && (
          <div className="field-pair">
            <Field label="인증 시작" name="verifyFrom" type="time" value={form.verifyFrom} onChange={onChange} />
            <Field
              label="인증 끝"
              name="verifyUntil"
              type="time"
              value={form.verifyUntil}
              onChange={onChange}
              error={errors.verifyUntil}
            />
          </div>
        )}

        {formError && <p className="form-error">{formError}</p>}
        <button type="submit" className="btn btn-primary btn-block" disabled={submitting}>
          {submitting ? '만드는 중…' : '챌린지 만들기'}
        </button>
      </form>
    </div>
  )
}
