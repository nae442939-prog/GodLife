import { pushApi } from './client.js'

// 푸시 알림(웹 푸시): 이 브라우저에서 켜고 끄기.
// 켜면 브라우저가 알림 권한을 묻고, 허용하면 구독(주소 + 키)을 만들어 서버에 남긴다.
// 서버는 알림함에 알림이 생길 때 그 구독으로 같은 내용을 보내고, /sw.js 가 받아서 띄운다.

const supported = () => 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window

/** 서버 공개 키(base64url)를 브라우저가 받는 바이트 배열로 */
function keyBytes(base64url) {
  const base64 = (base64url + '='.repeat((4 - (base64url.length % 4)) % 4)).replace(/-/g, '+').replace(/_/g, '/')
  return Uint8Array.from(atob(base64), (c) => c.charCodeAt(0))
}

async function currentSubscription() {
  const registration = await navigator.serviceWorker.getRegistration('/sw.js')
  return registration ? registration.pushManager.getSubscription() : null
}

/**
 * 이 브라우저의 푸시 상태.
 * 'unsupported' 브라우저가 못 함 · 'unavailable' 서버에 키가 없음 · 'denied' 브라우저에서 알림을 막아 둠 · 'on' · 'off'
 */
export async function pushStatus() {
  if (!supported()) return 'unsupported'
  const config = await pushApi.config()
  if (!config.enabled) return 'unavailable'
  if (Notification.permission === 'denied') return 'denied'
  return (await currentSubscription()) ? 'on' : 'off'
}

/** 푸시 켜기. 권한을 허용하지 않으면 'denied' 를 돌려준다 */
export async function enablePush() {
  const config = await pushApi.config()
  if (!supported() || !config.enabled) return 'unavailable'
  if ((await Notification.requestPermission()) !== 'granted') return 'denied'
  const registration = await navigator.serviceWorker.register('/sw.js')
  await navigator.serviceWorker.ready
  const subscription =
    (await registration.pushManager.getSubscription()) ??
    (await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: keyBytes(config.publicKey),
    }))
  await pushApi.subscribe(subscription.toJSON())
  return 'on'
}

/** 푸시 끄기 (이 브라우저만) */
export async function disablePush() {
  const subscription = await currentSubscription()
  if (subscription) {
    await pushApi.unsubscribe({ endpoint: subscription.endpoint })
    await subscription.unsubscribe()
  }
  return 'off'
}
