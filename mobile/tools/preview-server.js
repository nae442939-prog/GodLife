// 내 PC 에서 배포 서버(deploy/nginx-godlife.conf)처럼 한 주소로 본다.
//   /app/…                       → 앱 웹 빌드(mobile/build/web)
//   /api, /oauth2, /login/oauth2 → 백엔드(8080), /ws → 백엔드 소켓
//   그 밖                         → 웹사이트 개발 서버(Vite)
// 폰(또는 크롬의 폰 모양)으로 웹 화면에 들어오면 배포 서버와 같은 규칙으로 /app/ 으로 보낸다.
const http = require('http');
const net = require('net');
const fs = require('fs');
const path = require('path');

const root = process.argv[2];
const port = Number(process.argv[3] || 5173);
const vitePort = Number(process.argv[4] || 5175);
const backendPort = 8080;
const toBackend = /^\/(api|oauth2|login\/oauth2)\//;
const webOnly = /^\/(wallet|shop|signup|find-id|find-password|settings)(\/|$)/;
const mobile = /iphone|ipod|android.*mobile|windows phone/i;
const types = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.mjs': 'text/javascript', '.json': 'application/json',
  '.css': 'text/css', '.png': 'image/png', '.jpg': 'image/jpeg', '.svg': 'image/svg+xml', '.ico': 'image/x-icon',
  '.wasm': 'application/wasm', '.ttf': 'font/ttf', '.otf': 'font/otf', '.woff2': 'font/woff2', '.bin': 'application/octet-stream',
};

function proxy(req, res, targetPort, extraHeaders) {
  const out = http.request({ host: 'localhost', port: targetPort, path: req.url, method: req.method, headers: req.headers }, (r) => {
    res.writeHead(r.statusCode, { ...r.headers, ...extraHeaders });
    r.pipe(res);
  });
  out.on('error', () => { res.writeHead(502); res.end(`localhost:${targetPort} is not running`); });
  req.pipe(out);
}

const server = http.createServer((req, res) => {
  const [url, query = ''] = req.url.split('?');
  if (toBackend.test(url)) return proxy(req, res, backendPort);

  if (url === '/app') { res.writeHead(301, { Location: '/app/' }); return res.end(); }
  if (url.startsWith('/app/')) {
    let file = path.normalize(path.join(root, decodeURIComponent(url.slice('/app/'.length))));
    if (!file.startsWith(root)) { res.writeHead(403); return res.end(); }
    if (!fs.existsSync(file) || fs.statSync(file).isDirectory()) file = path.join(root, 'index.html');
    res.writeHead(200, { 'Content-Type': types[path.extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-cache' });
    return fs.createReadStream(file).pipe(res);
  }

  // 웹 화면을 여는 요청(HTML)만 배포 서버의 규칙을 따른다
  const page = req.method === 'GET' && (req.headers.accept || '').includes('text/html');
  const web = new URLSearchParams(query).get('web') === '1';
  if (page) {
    const stayOnWeb = (req.headers.cookie || '').split('; ').includes('gl_view=web');
    if (mobile.test(req.headers['user-agent'] || '') && !stayOnWeb && !web && !webOnly.test(url)) {
      res.writeHead(302, { Location: '/app/' });
      return res.end();
    }
  }
  proxy(req, res, vitePort, page && web ? { 'Set-Cookie': 'gl_view=web; Path=/; Max-Age=3600; SameSite=Lax' } : {});
});

// 소켓: /ws 는 백엔드(오픈채팅 알림), 나머지는 Vite(고친 내용 바로 반영)
server.on('upgrade', (req, socket, head) => {
  const target = net.connect(req.url.startsWith('/ws') ? backendPort : vitePort, 'localhost', () => {
    const headers = Object.entries(req.headers).map(([k, v]) => `${k}: ${v}`).join('\r\n');
    target.write(`${req.method} ${req.url} HTTP/1.1\r\n${headers}\r\n\r\n`);
    target.write(head);
    socket.pipe(target).pipe(socket);
  });
  target.on('error', () => socket.destroy());
  socket.on('error', () => target.destroy());
});

server.listen(port, () => console.log(`local preview: http://localhost:${port}/  (app: /app/)`));
