import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // /api 요청을 백엔드로 넘긴다. 브라우저 입장에서는 같은 출처라서
    // HttpOnly 리프레시 쿠키(SameSite=Strict)가 그대로 동작하고 CORS 설정이 필요 없다.
    proxy: {
      '/api': 'http://localhost:8080',
      // 소셜 로그인 시작(/oauth2/authorization/*)과 제공자 콜백(/login/oauth2/code/*)도 백엔드가 처리한다.
      // Host 를 바꾸지 않으므로 콜백에서 심는 리프레시 쿠키가 5173 출처에 저장된다.
      '/oauth2': 'http://localhost:8080',
      '/login/oauth2': 'http://localhost:8080',
    },
  },
})
