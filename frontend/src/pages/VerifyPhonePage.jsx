import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { userApi } from '../api/client.js'
import { useAuth } from '../auth/useAuth.js'
import { PhoneVerification } from '../components/PhoneVerification.jsx'

/** 소셜로 가입한 뒤 휴대폰 번호가 없는 회원이 거치는 화면. 인증해야 서비스를 이용할 수 있다. */
export function VerifyPhonePage() {
  const { user, updateUser } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [phoneKey, setPhoneKey] = useState(0)

  if (user.phoneVerified) return <Navigate to="/" replace />

  async function onVerified(phoneProof) {
    setError('')
    try {
      updateUser(await userApi.registerPhone(phoneProof))
      navigate('/', { replace: true })
    } catch (err) {
      setError(err.message)
      setPhoneKey((k) => k + 1)
    }
  }

  return (
    <div className="container auth-wrap">
      <section className="card auth-card">
        <h1>휴대폰 본인인증</h1>
        <p className="sub">
          {user.nickname}님, 갓생살기는 한 사람이 한 계정만 쓸 수 있도록 휴대폰 인증을 받고 있어요. 인증한 번호로
          나중에 아이디도 찾을 수 있어요.
        </p>
        <PhoneVerification key={phoneKey} onVerified={onVerified} />
        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}
      </section>
    </div>
  )
}
