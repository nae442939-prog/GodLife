// 설정 화면. 기능이 만들어지면 항목별로 채워 넣는다.
const ITEMS = [
  { title: '프로필', body: '닉네임과 프로필 사진 변경' },
  { title: '계정', body: '비밀번호 변경, 연결된 소셜 계정' },
  { title: '알림', body: '인증 마감, 정산 결과 알림 설정' },
]

export function SettingsPage() {
  return (
    <div className="container page">
      <h1 className="page-title">설정</h1>
      <p className="page-sub">계정과 알림을 관리합니다.</p>
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
