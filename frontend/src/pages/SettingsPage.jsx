import { useEffect, useState } from 'react'
import { blockApi } from '../api/client.js'
import { Avatar } from '../components/UserMenu.jsx'

// 설정 화면. 기능이 만들어지면 항목별로 채워 넣는다.
const ITEMS = [
  { title: '프로필', body: '닉네임과 프로필 사진 변경' },
  { title: '계정', body: '비밀번호 변경, 연결된 소셜 계정' },
  { title: '알림', body: '인증 마감, 정산 결과 알림 설정' },
]

export function SettingsPage() {
  return (
    <div className="container page">
      <div className="ch-head">
        <h1 className="page-title">설정</h1>
        <p className="page-sub">계정과 알림을 관리합니다.</p>
      </div>

      <BlockedUsers />

      <ul className="hub">
        {ITEMS.map((item) => (
          <li key={item.title} className="hub-card">
            <h3>{item.title}</h3>
            <p>{item.body}</p>
            <span className="badge">준비 중</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

/** 차단한 사용자. 풀면 그 사람의 채팅이 다시 보인다. */
function BlockedUsers() {
  const [users, setUsers] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    blockApi
      .list()
      .then(setUsers)
      .catch((err) => setError(err.message))
  }, [])

  async function unblock(userId) {
    try {
      await blockApi.unblock(userId)
      setUsers((cur) => cur.filter((u) => u.userId !== userId))
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <section className="card settings-block">
      <h2>차단한 사용자</h2>
      <p className="settings-help">차단한 사람의 오픈채팅 메시지는 내 화면에서만 보이지 않아요. 상대에게는 알리지 않아요.</p>
      {error && <p className="form-error">{error}</p>}
      {users === null ? (
        <p className="muted">불러오는 중…</p>
      ) : users.length === 0 ? (
        <p className="muted">차단한 사용자가 없어요.</p>
      ) : (
        <ul className="blocked-list">
          {users.map((u) => (
            <li key={u.userId}>
              <Avatar src={u.profileImageUrl} size={32} />
              <span className="blocked-name">{u.nickname}</span>
              <button type="button" className="btn btn-outline btn-sm" onClick={() => unblock(u.userId)}>
                차단 풀기
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
