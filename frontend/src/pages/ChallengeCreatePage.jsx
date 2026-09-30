import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { challengeApi } from '../api/client.js'
import { MODE_LABEL, addDays, daysBetween, toIsoDate } from '../challenge/format.js'
import { DatePicker } from '../components/DatePicker.jsx'
import { Field } from '../components/Field.jsx'

// 서버 규칙(ChallengeCreateRequest)과 같은 값
const MAX_DAYS = 90
const MIN_FEE = 100
const MAX_FEE = 100_000

// 만들기 첫 화면: 토글로 종류를 고르면 아래에 그 종류의 설명이 바뀐다.
const MODE_CHOICES = [
  {
    value: 'FREE',
    title: MODE_LABEL.FREE,
    badge: '가볍게 시작 모드',
    body: '부담 없이 습관을 시작해 보세요. 포인트 없이 인증만으로 참여하고, 기록은 갓생기록에 차곡차곡 쌓여요.',
    rows: [
      ['참여 방법', '포인트 없이 바로 참여'],
      ['실패하면', '잃는 포인트가 없어요'],
      ['성공하면', '인증 기록과 연속 달성이 쌓이고 랭킹에 반영돼요'],
    ],
  },
  {
    value: 'BET',
    title: MODE_LABEL.BET,
    badge: '강제 갓생 모드',
    body: '내 포인트를 걸어야 진짜 하게 되잖아요. 참가 포인트를 걸고 도전하고, 성공하면 건 포인트를 그대로 돌려받아요.',
    rows: [
      ['참여 방법', '포인트를 걸고 참여'],
      ['실패하면', '건 포인트를 잃어요'],
      ['성공하면', '건 포인트를 그대로 돌려받고, 포기한 사람 몫은 포인트 상점 보상으로 나눠 받아요'],
    ],
  },
]

const VISIBILITY_CHOICES = [
  { value: 'PUBLIC', label: '공개', hint: '챌린지 목록에 나와서 누구나 참여할 수 있어요.' },
  {
    value: 'PRIVATE',
    label: '비공개 (친구만)',
    hint: '목록에 나오지 않아요. 만든 뒤 초대 링크를 보내면 그 링크를 받은 사람만 참여할 수 있어요.',
  },
]

function ModeIcon({ mode }) {
  if (mode === 'BET') {
    return (
      <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
        <circle cx="12" cy="12" r="8.5" strokeWidth="1.6" />
        <path
          d="M12 8v8M9.4 9.7c0-1.1 1.1-1.9 2.6-1.9s2.6 0.7 2.6 1.7c0 2.4-5.2 1.3-5.2 3.7 0 1 1.1 1.7 2.6 1.7s2.6-0.8 2.6-1.9"
          strokeWidth="1.4"
          strokeLinecap="round"
        />
      </svg>
    )
  }
  return (
    <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
      <circle cx="12" cy="12" r="8.5" strokeWidth="1.6" />
      <path d="M8.3 12.3l2.5 2.5 5-5.3" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

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
    useWindow: false,
    visibility: 'PUBLIC',
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
  const [mode, setMode] = useState('FREE')
  // false = 종류 고르는 첫 화면, true = 입력 폼
  const [picked, setPicked] = useState(false)
  const [form, setForm] = useState(initialForm)
  const [categories, setCategories] = useState([])
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    challengeApi
      .categories()
      .then(setCategories)
      .catch(() => setCategories([]))
  }, [])

  const setField = (name, value) => {
    setForm((f) => {
      const next = { ...f, [name]: value }
      // 시작일을 종료일 뒤로 옮기면 종료일도 같은 날로 따라온다.
      if (name === 'startDate' && next.endDate && next.endDate < value) next.endDate = value
      return next
    })
    setErrors((errs) => ({ ...errs, [name]: undefined }))
  }

  const onChange = (e) => {
    const { name, value, type, checked } = e.target
    setField(name, type === 'checkbox' ? checked : value)
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
        visibility: form.visibility,
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

  if (!picked) {
    const choice = MODE_CHOICES.find((m) => m.value === mode)
    return (
      <div className="container page">
        <div className="ch-head">
          <h1 className="page-title">챌린지 만들기</h1>
          <p className="page-sub">어떤 챌린지를 만들까요?</p>
        </div>

        <div>
          <div className="segment" role="radiogroup" aria-label="챌린지 종류">
            {MODE_CHOICES.map((m) => (
              <button
                key={m.value}
                type="button"
                role="radio"
                aria-checked={mode === m.value}
                className={`segment-item ${mode === m.value ? 'is-active' : ''}`}
                onClick={() => setMode(m.value)}
              >
                {m.title}
              </button>
            ))}
          </div>

          <section className={`mc-detail is-${choice.value}`} aria-live="polite">
            <div className="mc-detail-head">
              <span className="mc-icon">
                <ModeIcon mode={choice.value} />
              </span>
              <div>
                <span className="mc-badge">{choice.badge}</span>
                <h2>{choice.title}</h2>
              </div>
            </div>
            <p className="mc-body">{choice.body}</p>
            <dl className="mc-rows">
              {choice.rows.map(([label, value]) => (
                <div key={label}>
                  <dt>{label}</dt>
                  <dd>{value}</dd>
                </div>
              ))}
            </dl>
          </section>

          <div className="mc-actions">
            <button type="button" className="btn btn-dark" onClick={() => setPicked(true)}>
              다음
            </button>
          </div>
        </div>
      </div>
    )
  }

  const days = form.startDate && form.endDate ? daysBetween(form.startDate, form.endDate) + 1 : 0

  return (
    <div className="container page">
      <div className="ch-head">
        <h1 className="page-title">{MODE_LABEL[mode]} 만들기</h1>
        <p className="page-sub">
          <button type="button" className="back-button" onClick={() => setPicked(false)}>
            <svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" aria-hidden="true">
              <path d="M6.5 1.5L2 5l4.5 3.5" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
            챌린지 종류 다시 고르기
          </button>
        </p>
      </div>

      <form className="card form-card cf" onSubmit={onSubmit} noValidate>
        {/* 넓은 화면: 왼쪽 = 무엇을(카테고리·제목·설명), 오른쪽 = 어떻게(기간·주기·인원·포인트·시간대) */}
        <div className="form-grid">
          <div className="form-col">
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
          </div>

          <div className="form-col">
            <div className="field-pair">
              <DatePicker
                label="시작일"
                value={form.startDate}
                min={toIsoDate(new Date())}
                onChange={(iso) => setField('startDate', iso)}
                error={errors.startDate}
                rangeStart={form.startDate}
                rangeEnd={form.endDate}
              />
              <DatePicker
                label="종료일"
                value={form.endDate}
                min={form.startDate}
                onChange={(iso) => setField('endDate', iso)}
                error={errors.endDate}
                hint={days > 0 ? `총 ${days}일` : undefined}
                rangeStart={form.startDate}
                rangeEnd={form.endDate}
                align="right"
              />
            </div>

            <fieldset className="field">
              <legend className="field-label">인증 주기</legend>
              <div className="freq-row">
                <div className="segment segment-sm">
                  <label className={`segment-item ${form.frequencyType === 'DAILY' ? 'is-active' : ''}`}>
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
                  <label className={`segment-item ${form.frequencyType === 'WEEKLY_N' ? 'is-active' : ''}`}>
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
                </div>
                {form.frequencyType === 'WEEKLY_N' && (
                  <select
                    name="weeklyCount"
                    className="select"
                    aria-label="주 몇 회"
                    value={form.weeklyCount}
                    onChange={onChange}
                  >
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

            <fieldset className="field">
              <legend className="field-label">공개 범위</legend>
              <div className="segment segment-sm">
                {VISIBILITY_CHOICES.map((v) => (
                  <label key={v.value} className={`segment-item ${form.visibility === v.value ? 'is-active' : ''}`}>
                    <input
                      type="radio"
                      name="visibility"
                      value={v.value}
                      checked={form.visibility === v.value}
                      onChange={onChange}
                      className="sr-only"
                    />
                    {v.label}
                  </label>
                ))}
              </div>
              <p className="field-hint">{VISIBILITY_CHOICES.find((v) => v.value === form.visibility).hint}</p>
            </fieldset>

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
                  hint="100P 단위, 100P ~ 100,000P. 인증한 날만큼 돌려받고, 못 한 날 몫은 그날 성공한 사람들이 나눠 가져요. 챌린지가 끝나면 한 번에 정산돼요."
                />
              </>
            )}

            <label className="check">
              <input type="checkbox" name="useWindow" checked={form.useWindow} onChange={onChange} />
              <span>
                인증 가능한 시간대를 정할래요 <span className="muted">(예: 새벽 기상은 05:00 ~ 09:00)</span>
              </span>
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
          </div>
        </div>

        {formError && <p className="form-error">{formError}</p>}
        <button type="submit" className="btn btn-dark btn-block cf-submit" disabled={submitting}>
          {submitting ? '만드는 중…' : '챌린지 만들기'}
        </button>
      </form>
    </div>
  )
}
