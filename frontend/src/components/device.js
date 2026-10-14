// 폰에서는 앱 화면(/app/)이 기본이고, 웹 화면은 앱에 없는 곳(충전 · 상점 · 설정)만 보여 준다.
// (개발 서버에는 앱 화면이 없어 배포 빌드에서만 폰으로 본다)
export const ON_PHONE = import.meta.env.PROD && /iphone|ipod|android.*mobile|windows phone/i.test(navigator.userAgent)
