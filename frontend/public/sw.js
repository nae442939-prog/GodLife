// 푸시 알림용 서비스 워커. 화면이 닫혀 있어도 브라우저가 이 파일을 깨워 알림을 띄운다.
// 서버가 보내는 내용: { title, body, link } (알림함에 쌓이는 알림과 같은 내용)
// 캐시 · 오프라인 기능은 넣지 않았다 — 푸시를 받고 눌렀을 때 그 화면을 여는 일만 한다.

self.addEventListener('push', (event) => {
  let data
  try {
    data = event.data ? event.data.json() : {}
  } catch {
    data = {}
  }
  event.waitUntil(
    self.registration.showNotification(data.title || '갓생살기', {
      body: data.body || '',
      icon: '/favicon.svg',
      data: { link: typeof data.link === 'string' && data.link.startsWith('/') ? data.link : '/' },
    }),
  )
})

// 알림을 누르면: 열려 있는 갓생살기 탭이 있으면 그 탭을 그 화면으로 옮기고, 없으면 새로 연다
self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const link = event.notification.data?.link || '/'
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((tabs) => {
      const tab = tabs.find((t) => 'focus' in t)
      if (tab) {
        return tab.navigate(link).then((t) => (t ?? tab).focus())
      }
      return self.clients.openWindow(link)
    }),
  )
})
