// 액세스 토큰은 메모리에만 둔다. (localStorage 에 두면 XSS 로 탈취될 수 있다)
// 리프레시 토큰은 서버가 HttpOnly 쿠키로 내려주므로 JS 는 볼 수도 없고 다룰 필요도 없다.
let accessToken = null

export function setAccessToken(token) {
  accessToken = token
}

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message ?? '요청을 처리하지 못했습니다.')
    this.status = status
    this.code = body?.code
    this.fieldErrors = body?.fieldErrors ?? {}
  }
}

async function parse(res) {
  if (res.status === 204) return null
  try {
    return await res.json()
  } catch {
    return null
  }
}

async function request(path, { method = 'GET', body, auth = true } = {}) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (auth && accessToken) headers.Authorization = `Bearer ${accessToken}`

  const res = await fetch(path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
    credentials: 'same-origin',
  })
  const data = await parse(res)
  if (!res.ok) throw new ApiError(res.status, data)
  return data
}

// 리프레시 토큰은 1회용(회전)이라 동시에 두 번 보내면 한쪽이 실패한다.
// 진행 중인 요청을 공유(single-flight)해서 한 번만 보낸다. (React StrictMode 의 이중 실행 포함)
let refreshing = null

export function refreshAccessToken() {
  if (!refreshing) {
    refreshing = request('/api/auth/refresh', { method: 'POST', auth: false })
      .then((data) => {
        accessToken = data.accessToken
        return accessToken
      })
      .catch((err) => {
        accessToken = null
        throw err
      })
      .finally(() => {
        refreshing = null
      })
  }
  return refreshing
}

/** 인증이 필요한 API 호출. 액세스 토큰이 만료돼 401 이 오면 한 번만 재발급 후 재시도한다. */
export async function apiFetch(path, options = {}) {
  try {
    return await request(path, options)
  } catch (err) {
    if (err instanceof ApiError && err.status === 401 && options.auth !== false) {
      await refreshAccessToken()
      return request(path, options)
    }
    throw err
  }
}

export const authApi = {
  signup: (payload) => request('/api/auth/signup', { method: 'POST', body: payload, auth: false }),
  async login(payload) {
    const data = await request('/api/auth/login', { method: 'POST', body: payload, auth: false })
    accessToken = data.accessToken
    return data
  },
  async logout() {
    try {
      await request('/api/auth/logout', { method: 'POST', auth: false })
    } finally {
      accessToken = null
    }
  },
}

export const userApi = {
  me: () => apiFetch('/api/users/me'),
  registerPhone: (phoneProof) => apiFetch('/api/users/me/phone', { method: 'POST', body: { phoneProof } }),
}

// 휴대폰 인증: 인증번호 발송 → 확인하면 1회용 증표(phoneProof)를 받아 가입/아이디 찾기/번호 등록에 제출한다.
export const phoneApi = {
  send: (phone) => request('/api/phone-verifications', { method: 'POST', body: { phone }, auth: false }),
  confirm: (phone, code) =>
    request('/api/phone-verifications/confirm', { method: 'POST', body: { phone, code }, auth: false }),
}

export const accountApi = {
  findId: (phoneProof) => request('/api/account/find-id', { method: 'POST', body: { phoneProof }, auth: false }),
  requestPasswordReset: (email) =>
    request('/api/account/password-reset', { method: 'POST', body: { email }, auth: false }),
  confirmPasswordReset: (email, code) =>
    request('/api/account/password-reset/confirm', { method: 'POST', body: { email, code }, auth: false }),
  completePasswordReset: (resetToken, newPassword) =>
    request('/api/account/password-reset/complete', {
      method: 'POST',
      body: { resetToken, newPassword },
      auth: false,
    }),
}

// 챌린지: 목록/상세/카테고리는 비로그인도 볼 수 있다. (로그인 상태면 토큰을 같이 보내 참여 여부를 받는다)
export const challengeApi = {
  categories: () => apiFetch('/api/categories'),
  list: ({ categoryId, mode, q, sort, page } = {}) => {
    const params = new URLSearchParams()
    if (categoryId) params.set('categoryId', categoryId)
    if (mode) params.set('mode', mode)
    if (q) params.set('q', q)
    if (sort) params.set('sort', sort)
    if (page) params.set('page', page)
    const qs = params.toString()
    return apiFetch(`/api/challenges${qs ? `?${qs}` : ''}`)
  },
  get: (id) => apiFetch(`/api/challenges/${id}`),
  create: (payload) => apiFetch('/api/challenges', { method: 'POST', body: payload }),
  join: (id) => apiFetch(`/api/challenges/${id}/participants`, { method: 'POST' }),
  leave: (id) => apiFetch(`/api/challenges/${id}/participants/me`, { method: 'DELETE' }),
  // 초대 링크(/challenges/join/{code}): 비공개 챌린지는 이 코드로만 보고 참여한다.
  getByInvite: (code) => apiFetch(`/api/challenges/invite/${encodeURIComponent(code)}`),
  joinByInvite: (code) =>
    apiFetch(`/api/challenges/invite/${encodeURIComponent(code)}/participants`, { method: 'POST' }),
  regenerateInvite: (id) => apiFetch(`/api/challenges/${id}/invite-code`, { method: 'POST' }),
  // 개설자만, 시작일 전날까지
  remove: (id) => apiFetch(`/api/challenges/${id}`, { method: 'DELETE' }),
}

// 챌린지 오픈채팅 (개설자·참가자만). 3초마다 after 로 새 메시지를 가져온다.
export const chatApi = {
  list: (challengeId, { after, before } = {}) => {
    const params = new URLSearchParams()
    if (after) params.set('after', after)
    if (before) params.set('before', before)
    const qs = params.toString()
    return apiFetch(`/api/challenges/${challengeId}/messages${qs ? `?${qs}` : ''}`)
  },
  send: (challengeId, content) =>
    apiFetch(`/api/challenges/${challengeId}/messages`, { method: 'POST', body: { content } }),
  // 신고: reason = ABUSE | SPAM | INAPPROPRIATE | OTHER
  report: (challengeId, messageId, reason, detail) =>
    apiFetch(`/api/challenges/${challengeId}/messages/${messageId}/reports`, {
      method: 'POST',
      body: { reason, detail },
    }),
  // 방장 전용: 공지(빈 내용이면 내림) · 내보내기 · 신고 누적 알림
  setNotice: (challengeId, content) =>
    apiFetch(`/api/challenges/${challengeId}/notice`, { method: 'PUT', body: { content } }),
  kick: (challengeId, userId) =>
    apiFetch(`/api/challenges/${challengeId}/participants/${userId}/kick`, { method: 'POST' }),
  reportAlerts: (challengeId) => apiFetch(`/api/challenges/${challengeId}/report-alerts`),
  dismissAlert: (challengeId, userId) =>
    apiFetch(`/api/challenges/${challengeId}/report-alerts/${userId}/dismiss`, { method: 'POST' }),
}

// 차단: 나에게만 적용 (차단한 사람의 채팅이 내 화면에서 안 보인다)
export const blockApi = {
  list: () => apiFetch('/api/users/me/blocks'),
  block: (userId) => apiFetch(`/api/users/me/blocks/${userId}`, { method: 'PUT' }),
  unblock: (userId) => apiFetch(`/api/users/me/blocks/${userId}`, { method: 'DELETE' }),
}
