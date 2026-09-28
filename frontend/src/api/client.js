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
}
