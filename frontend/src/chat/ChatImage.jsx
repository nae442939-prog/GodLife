import { useEffect, useState } from 'react'
import { chatApi } from '../api/client.js'
import { Dialog } from './ChatParts.jsx'

// 한 번 받은 사진은 다시 받지 않게 (폴링으로 화면이 다시 그려져도). 키: "챌린지id:메시지id"
const cache = new Map()

function loadImage(challengeId, messageId) {
  const key = `${challengeId}:${messageId}`
  if (!cache.has(key)) {
    const pending = chatApi
      .imageBlob(challengeId, messageId)
      .then((blob) => URL.createObjectURL(blob))
      .catch((err) => {
        cache.delete(key) // 실패하면 다음에 다시 시도
        throw err
      })
    cache.set(key, pending)
  }
  return cache.get(key)
}

/** 채팅 사진. 누르면 크게 본다. 다 불러오면 onLoad 로 알려서 채팅 화면이 맨 아래를 다시 맞춘다. */
export function ChatImage({ challengeId, messageId, onLoad }) {
  const [src, setSrc] = useState(null)
  const [failed, setFailed] = useState(false)
  const [zoom, setZoom] = useState(false)

  useEffect(() => {
    let cancelled = false
    loadImage(challengeId, messageId)
      .then((url) => !cancelled && setSrc(url))
      .catch(() => !cancelled && setFailed(true))
    return () => {
      cancelled = true
    }
  }, [challengeId, messageId])

  if (failed) return <span className="chat-image is-failed">사진을 불러오지 못했어요</span>
  if (!src) return <span className="chat-image is-loading" aria-label="사진 불러오는 중" />

  return (
    <>
      <button type="button" className="chat-image-btn" onClick={() => setZoom(true)} aria-label="사진 크게 보기">
        <img className="chat-image" src={src} alt="채팅 사진" onLoad={onLoad} />
      </button>
      {zoom && (
        <Dialog title="사진" onClose={() => setZoom(false)}>
          <img className="chat-image-full" src={src} alt="채팅 사진 크게 보기" />
        </Dialog>
      )}
    </>
  )
}
