import { useEffect, useState } from 'react'
import { verificationApi } from '../api/client.js'

// 한 번 받은 사진은 다시 받지 않는다. 키: "챌린지id:인증id"
const cache = new Map()

function loadPhoto(challengeId, verificationId) {
  const key = `${challengeId}:${verificationId}`
  if (!cache.has(key)) {
    const pending = verificationApi
      .imageBlob(challengeId, verificationId)
      .then((blob) => URL.createObjectURL(blob))
      .catch((err) => {
        cache.delete(key) // 실패하면 다음에 다시 시도
        throw err
      })
    cache.set(key, pending)
  }
  return cache.get(key)
}

/** 인증 사진. 참가자만 받을 수 있어 토큰을 붙여 받은 뒤 보여 준다. */
export function VerifyPhoto({ challengeId, verificationId, alt, className = 'vf-photo' }) {
  const [src, setSrc] = useState(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let cancelled = false
    loadPhoto(challengeId, verificationId)
      .then((url) => !cancelled && setSrc(url))
      .catch(() => !cancelled && setFailed(true))
    return () => {
      cancelled = true
    }
  }, [challengeId, verificationId])

  if (failed) return <span className={`${className} is-failed`}>사진을 불러오지 못했어요</span>
  if (!src) return <span className={`${className} is-loading`} aria-label="사진 불러오는 중" />
  return <img className={className} src={src} alt={alt} />
}
