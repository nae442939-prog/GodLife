// 뱃지 코드 → 그림 파일 (public/badges/{코드}.png, 앱과 같은 그림. 출처는 public/badges/CREDITS.md).
// 프로필 카드 · 뱃지 전체 보기 · 프로필 수정이 같이 쓴다. 서버 ProfileService.badges 의 코드와 맞춘다
export const badgeImage = (code) => `/badges/${code}.png`
