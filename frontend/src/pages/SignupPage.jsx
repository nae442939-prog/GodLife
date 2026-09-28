import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth.js'
import { Field } from '../components/Field.jsx'
import { SocialLoginButtons } from '../components/SocialLoginButtons.jsx'

// 서버 검증 규칙(SignupRequest)과 같다. 서버가 최종 판단하고, 여기서는 빠른 안내만 한다.
function validate({ email, password, nickname }) {
  const errors = {}
  if (!/^\S+@\S+\.\S+$/.test(email)) errors.email = '이메일 형식이 올바르지 않습니다.'
  if (password.length < 8 || password.length > 64) {
    errors.password = '비밀번호는 8~64자여야 합니다.'
  } else if (!/^(?=.*[A-Za-z])(?=.*\d)[\x21-\x7E]+$/.test(password)) {
    errors.password = '영문과 숫자를 포함해야 하며 공백/한글은 쓸 수 없습니다.'
  }
  if (nickname.length < 2 || nickname.length > 20) {
    errors.nickname = '닉네임은 2~20자여야 합니다.'
  } else if (!/^[가-힣a-zA-Z0-9_]+$/.test(nickname)) {
    errors.nickname = '한글, 영문, 숫자, _ 만 쓸 수 있습니다.'
  }
  return errors
}

export function SignupPage() {
  const { status, signup, login } = useAuth()
  const navigate = useNavigate()

  const [form, setForm] = useState({ email: '', password: '', nickname: '' })
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  if (status === 'authed') return <Navigate to="/" replace />

  const onChange = (e) => {
    const { name, value } = e.target
    setForm((f) => ({ ...f, [name]: value }))
    setErrors((prev) => ({ ...prev, [name]: undefined }))
  }

  async function onSubmit(e) {
    e.preventDefault()
    setFormError('')
    const payload = { ...form, email: form.email.trim() }
    const clientErrors = validate(payload)
    if (Object.keys(clientErrors).length > 0) {
      setErrors(clientErrors)
      return
    }
    setSubmitting(true)
    try {
      await signup(payload)
      await login({ email: payload.email, password: payload.password })
      navigate('/', { replace: true })
    } catch (err) {
      if (err.code === 'DUPLICATE_EMAIL') setErrors({ email: err.message })
      else if (err.code === 'DUPLICATE_NICKNAME') setErrors({ nickname: err.message })
      else if (Object.keys(err.fieldErrors ?? {}).length > 0) setErrors(err.fieldErrors)
      else setFormError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="container auth-wrap">
      <section className="card auth-card">
        <h1>회원가입</h1>
        <p className="sub">갓생살기와 함께 습관을 만들어 보세요.</p>
        <SocialLoginButtons />
        <div className="divider">또는 이메일로 가입</div>
        <form onSubmit={onSubmit} noValidate>
          <Field
            label="이메일"
            name="email"
            type="email"
            autoComplete="email"
            value={form.email}
            onChange={onChange}
            error={errors.email}
          />
          <Field
            label="비밀번호"
            name="password"
            type="password"
            autoComplete="new-password"
            value={form.password}
            onChange={onChange}
            error={errors.password}
            hint="영문과 숫자를 포함해 8~64자"
          />
          <Field
            label="닉네임"
            name="nickname"
            type="text"
            autoComplete="nickname"
            value={form.nickname}
            onChange={onChange}
            error={errors.nickname}
            hint="2~20자, 한글/영문/숫자/_"
          />
          {formError && (
            <p className="form-error" role="alert">
              {formError}
            </p>
          )}
          <button type="submit" className="btn btn-primary btn-block" disabled={submitting}>
            {submitting ? '가입 중…' : '가입하기'}
          </button>
        </form>
        <p className="switch">
          이미 계정이 있으신가요? <Link to="/login">로그인</Link>
        </p>
      </section>
    </div>
  )
}
