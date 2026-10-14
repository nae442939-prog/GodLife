import { useState } from 'react'
import { ON_PHONE } from '../components/device.js'
import { Navigate } from 'react-router-dom'
import { userApi } from '../api/client.js'
import { useAuth } from '../auth/useAuth.js'
import { PhoneVerification } from '../components/PhoneVerification.jsx'

/** 소셜로 가입한 뒤 휴대폰 번호가 없는 회원이 거치는 화면. 인증해야 서비스를 이용할 수 있다. */
export function VerifyPhonePage() {
  const { user, updateUser } = useAuth()
  const [error, setError] = useState('')
  const [phoneKey, setPhoneKey] = useState(0)

  // 방금 인증을 마쳤는지 (= 소셜 가입을 마침). 번호가 등록되는 순간 아래 줄이 홈으로 보내는데, 그때 환영 창을 띄우라고 알린다
  const [justJoined, setJustJoined] = useState(false)

  if (user.phoneVerified) {
    if (!justJoined) return <Navigate to="/" replace />
    // 폰이면 앱 화면(/app/)이 환영 창을 띄운다 (주소를 바꾸는 동안 아무것도 그리지 않는다)
    return ON_PHONE ? null : <Navigate to="/" replace state={{ welcome: true }} />
  }

  async function onVerified(phoneProof) {
    setError('')
    try {
      const updated = await userApi.registerPhone(phoneProof)
      setJustJoined(true)
      updateUser(updated)
      if (ON_PHONE) window.location.replace('/app/?welcome=1')
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
