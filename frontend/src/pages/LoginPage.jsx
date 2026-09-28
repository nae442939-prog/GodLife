import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/useAuth.js'
import { Field } from '../components/Field.jsx'
import { SocialLoginButtons } from '../components/SocialLoginButtons.jsx'

// 소셜 로그인이 실패하면 백엔드가 /login?error=코드 로 돌려보낸다.
const SOCIAL_ERRORS = {
  ACCOUNT_SUSPENDED: '이용이 정지된 계정입니다.',
  SOCIAL_LOGIN_FAILED: '소셜 로그인에 실패했습니다. 다시 시도해 주세요.',
}

export function LoginPage() {
  const { status, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = location.state?.from ?? '/'
  const [searchParams] = useSearchParams()
  const socialError = SOCIAL_ERRORS[searchParams.get('error')] ?? ''

  const [form, setForm] = useState({ email: '', password: '' })
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  if (status === 'authed') return <Navigate to={from} replace />

  const onChange = (e) => setForm((f) => ({ ...f, [e.target.name]: e.target.value }))

  async function onSubmit(e) {
    e.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      await login({ email: form.email.trim(), password: form.password })
      navigate(from, { replace: true })
    } catch (err) {
      setError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="container auth-wrap">
      <section className="card auth-card">
        <h1>로그인</h1>
        <p className="sub">소셜 계정이나 이메일로 로그인하세요.</p>
        {socialError && !error && (
          <p className="form-error" role="alert">
            {socialError}
          </p>
        )}
        <SocialLoginButtons />
        <div className="divider">또는 이메일로 로그인</div>
        <form onSubmit={onSubmit} noValidate>
          <Field
            label="이메일"
            name="email"
            type="email"
            autoComplete="email"
            value={form.email}
            onChange={onChange}
            required
          />
          <Field
            label="비밀번호"
            name="password"
            type="password"
            autoComplete="current-password"
            value={form.password}
            onChange={onChange}
            required
          />
          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}
          <button type="submit" className="btn btn-primary btn-block" disabled={submitting || !form.email || !form.password}>
            {submitting ? '로그인 중…' : '로그인'}
          </button>
        </form>
        <p className="switch">
          계정이 없으신가요? <Link to="/signup">회원가입</Link>
        </p>
      </section>
    </div>
  )
}
