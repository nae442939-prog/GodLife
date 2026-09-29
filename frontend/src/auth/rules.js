// 서버 검증 규칙(SignupRequest, InputRules)과 같다. 서버가 최종 판단하고, 여기서는 원인을 구체적으로 빨리 알려 준다.

export const PASSWORD_HINT = '영문과 숫자를 포함해 8~20자 · 특수문자 사용 가능'
export const NICKNAME_HINT = '2~20자 · 한글, 영문, 숫자만 (특수문자·공백 불가)'

/** 문제가 없으면 빈 문자열. */
export function passwordError(password) {
  if (password.length < 8) return '비밀번호는 8자 이상이어야 합니다.'
  if (password.length > 20) return '비밀번호는 20자 이하여야 합니다.'
  if (/\s/.test(password)) return '비밀번호에 공백은 쓸 수 없습니다.'
  if (/[^\x21-\x7E]/.test(password)) return '비밀번호에는 영문, 숫자, 특수문자만 쓸 수 있습니다. (한글 불가)'
  if (!/[A-Za-z]/.test(password) || !/\d/.test(password)) return '비밀번호는 영문과 숫자를 모두 포함해야 합니다.'
  return ''
}

/** 문제가 없으면 빈 문자열. 입력하는 동안에도 부를 수 있게 길이 부족은 따로 알려 준다(checkLength). */
export function nicknameError(nickname, { checkLength = true } = {}) {
  if (/\s/.test(nickname)) return '닉네임에 공백은 쓸 수 없습니다.'
  if (/[ㄱ-ㅎㅏ-ㅣ]/.test(nickname)) return '닉네임은 완성된 글자만 쓸 수 있습니다. (ㅋ, ㅏ 같은 낱자 불가)'
  if (/[^가-힣a-zA-Z0-9]/.test(nickname)) return '특수문자는 닉네임에 사용할 수 없습니다.'
  if (nickname.length > 20) return '닉네임은 20자 이하여야 합니다.'
  if (checkLength && nickname.length < 2) return '닉네임은 2자 이상이어야 합니다.'
  return ''
}
