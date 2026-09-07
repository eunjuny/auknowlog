import crypto from 'node:crypto'
import http from 'node:http'
import net from 'node:net'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const LOGIN_PATH = '/__auknowlog_tunnel/login'
const LOGOUT_PATH = '/__auknowlog_tunnel/logout'
const SESSION_COOKIE = 'auknowlog_tunnel_session'
const MAX_BODY_BYTES = 8 * 1024

function secureEqual(left, right) {
  const leftBuffer = Buffer.from(String(left))
  const rightBuffer = Buffer.from(String(right))
  return leftBuffer.length === rightBuffer.length && crypto.timingSafeEqual(leftBuffer, rightBuffer)
}

function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

function safeReturnPath(value) {
  if (!value || !value.startsWith('/') || value.startsWith('//')) {
    return '/'
  }
  return value
}

function parseCookies(cookieHeader = '') {
  return cookieHeader.split(';').reduce((cookies, item) => {
    const separatorIndex = item.indexOf('=')
    if (separatorIndex < 0) return cookies
    const name = item.slice(0, separatorIndex).trim()
    const value = item.slice(separatorIndex + 1).trim()
    if (name) cookies[name] = value
    return cookies
  }, {})
}

function withoutTunnelCookie(cookieHeader = '') {
  return cookieHeader
    .split(';')
    .map((item) => item.trim())
    .filter((item) => item && !item.startsWith(`${SESSION_COOKIE}=`))
    .join('; ')
}

function securityHeaders() {
  return {
    'Cache-Control': 'no-store',
    'Content-Security-Policy': "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'",
    'Referrer-Policy': 'no-referrer',
    'X-Content-Type-Options': 'nosniff',
    'X-Frame-Options': 'DENY',
  }
}

function sendHtml(response, statusCode, html, extraHeaders = {}) {
  response.writeHead(statusCode, {
    ...securityHeaders(),
    ...extraHeaders,
    'Content-Type': 'text/html; charset=utf-8',
  })
  response.end(html)
}

function loginPage(returnPath, errorMessage = '') {
  const safePath = safeReturnPath(returnPath)
  const error = errorMessage
    ? `<p class="error" role="alert">${escapeHtml(errorMessage)}</p>`
    : ''

  return `<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Auknowlog 임시 접속</title>
  <style>
    :root { color-scheme: light dark; font-family: system-ui, -apple-system, sans-serif; }
    body { min-height: 100vh; margin: 0; display: grid; place-items: center; background: Canvas; color: CanvasText; }
    main { width: min(88vw, 360px); padding: 28px; border: 1px solid color-mix(in srgb, CanvasText 18%, transparent); border-radius: 16px; }
    h1 { margin: 0 0 8px; font-size: 1.4rem; }
    p { margin: 0 0 20px; color: color-mix(in srgb, CanvasText 70%, transparent); }
    label { display: grid; gap: 6px; margin-top: 14px; }
    input, button { box-sizing: border-box; width: 100%; min-height: 44px; padding: 10px 12px; font: inherit; border-radius: 9px; }
    input { border: 1px solid color-mix(in srgb, CanvasText 28%, transparent); background: Canvas; color: CanvasText; }
    button { margin-top: 20px; border: 0; background: #2563eb; color: white; font-weight: 600; cursor: pointer; }
    .error { color: #dc2626; }
  </style>
</head>
<body>
  <main>
    <h1>Auknowlog 임시 접속</h1>
    <p>외부 접속용 임시 인증 정보를 입력하세요.</p>
    ${error}
    <form method="post" action="${LOGIN_PATH}">
      <input type="hidden" name="return" value="${escapeHtml(safePath)}">
      <label>사용자명<input name="username" autocomplete="username" required></label>
      <label>비밀번호<input name="password" type="password" autocomplete="current-password" required></label>
      <button type="submit">접속</button>
    </form>
  </main>
</body>
</html>`
}

function readFormBody(request) {
  return new Promise((resolve, reject) => {
    const chunks = []
    let size = 0

    request.on('data', (chunk) => {
      size += chunk.length
      if (size > MAX_BODY_BYTES) {
        reject(new Error('request body too large'))
        request.destroy()
        return
      }
      chunks.push(chunk)
    })
    request.on('end', () => resolve(new URLSearchParams(Buffer.concat(chunks).toString('utf8'))))
    request.on('error', reject)
  })
}

function clientIdentifier(request) {
  const cloudflareIp = request.headers['cf-connecting-ip']
  const forwardedFor = request.headers['x-forwarded-for']
  return String(cloudflareIp || forwardedFor || request.socket.remoteAddress || 'unknown').split(',')[0].trim()
}

function sanitizedProxyHeaders(request, upstreamHost, upstreamPort) {
  const headers = { ...request.headers }
  headers.host = `${upstreamHost}:${upstreamPort}`
  delete headers.authorization
  delete headers['proxy-authorization']

  const applicationCookies = withoutTunnelCookie(headers.cookie)
  if (applicationCookies) headers.cookie = applicationCookies
  else delete headers.cookie

  return headers
}

export function createAuthProxy({
  username,
  password,
  sessionToken,
  upstreamHost = '127.0.0.1',
  upstreamPort = 5173,
  sessionMaxAgeSeconds = 8 * 60 * 60,
  maxFailedAttempts = 5,
  failedAttemptWindowMs = 5 * 60 * 1000,
} = {}) {
  if (!username || !password || !sessionToken) {
    throw new Error('username, password, and sessionToken are required')
  }

  const failedAttempts = new Map()

  function hasValidSession(request) {
    const token = parseCookies(request.headers.cookie)[SESSION_COOKIE]
    return Boolean(token && secureEqual(token, sessionToken))
  }

  function isRateLimited(identifier) {
    const entry = failedAttempts.get(identifier)
    if (!entry) return false
    if (Date.now() >= entry.resetAt) {
      failedAttempts.delete(identifier)
      return false
    }
    return entry.count >= maxFailedAttempts
  }

  function recordFailure(identifier) {
    const now = Date.now()
    const existing = failedAttempts.get(identifier)
    if (!existing || now >= existing.resetAt) {
      failedAttempts.set(identifier, { count: 1, resetAt: now + failedAttemptWindowMs })
      return
    }
    existing.count += 1
  }

  function proxyHttp(request, response) {
    const proxyRequest = http.request({
      host: upstreamHost,
      port: upstreamPort,
      method: request.method,
      path: request.url,
      headers: sanitizedProxyHeaders(request, upstreamHost, upstreamPort),
      timeout: 30_000,
    }, (proxyResponse) => {
      response.writeHead(proxyResponse.statusCode || 502, proxyResponse.headers)
      proxyResponse.pipe(response)
    })

    proxyRequest.on('timeout', () => proxyRequest.destroy(new Error('upstream timeout')))
    proxyRequest.on('error', () => {
      if (!response.headersSent) response.writeHead(502, securityHeaders())
      response.end('Upstream unavailable')
    })
    request.pipe(proxyRequest)
  }

  const server = http.createServer(async (request, response) => {
    const requestUrl = new URL(request.url || '/', 'http://127.0.0.1')

    if (requestUrl.pathname === LOGOUT_PATH) {
      response.writeHead(303, {
        ...securityHeaders(),
        'Set-Cookie': `${SESSION_COOKIE}=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=Strict`,
        Location: LOGIN_PATH,
      })
      response.end()
      return
    }

    if (requestUrl.pathname === LOGIN_PATH && request.method === 'GET') {
      sendHtml(response, 200, loginPage(requestUrl.searchParams.get('return') || '/'))
      return
    }

    if (requestUrl.pathname === LOGIN_PATH && request.method === 'POST') {
      const identifier = clientIdentifier(request)
      if (isRateLimited(identifier)) {
        sendHtml(response, 429, loginPage('/', '로그인 시도가 너무 많습니다. 잠시 후 다시 시도하세요.'), {
          'Retry-After': String(Math.ceil(failedAttemptWindowMs / 1000)),
        })
        return
      }

      try {
        const form = await readFormBody(request)
        const returnPath = safeReturnPath(form.get('return'))
        const submittedUsername = form.get('username') || ''
        const submittedPassword = form.get('password') || ''
        const usernameMatches = secureEqual(submittedUsername, username)
        const passwordMatches = secureEqual(submittedPassword, password)
        const valid = usernameMatches && passwordMatches

        if (!valid) {
          recordFailure(identifier)
          // Do not log credentials, client identifiers, or which field matched.
          console.warn(JSON.stringify({ event: 'tunnel_login_rejected' }))
          sendHtml(response, 403, loginPage(returnPath, '사용자명 또는 비밀번호가 올바르지 않습니다.'))
          return
        }

        failedAttempts.delete(identifier)
        response.writeHead(303, {
          ...securityHeaders(),
          'Set-Cookie': `${SESSION_COOKIE}=${sessionToken}; Path=/; Max-Age=${sessionMaxAgeSeconds}; HttpOnly; Secure; SameSite=Strict`,
          Location: returnPath,
        })
        response.end()
      } catch {
        sendHtml(response, 400, loginPage('/', '요청을 처리할 수 없습니다.'))
      }
      return
    }

    if (requestUrl.pathname.startsWith('/__auknowlog_tunnel/')) {
      response.writeHead(404, securityHeaders())
      response.end('Not found')
      return
    }

    if (!hasValidSession(request)) {
      const returnPath = safeReturnPath(request.url || '/')
      response.writeHead(302, {
        ...securityHeaders(),
        Location: `${LOGIN_PATH}?return=${encodeURIComponent(returnPath)}`,
      })
      response.end()
      return
    }

    proxyHttp(request, response)
  })

  server.on('upgrade', (request, socket, head) => {
    if (!hasValidSession(request)) {
      socket.end('HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n')
      return
    }

    const upstreamSocket = net.connect(upstreamPort, upstreamHost, () => {
      const headers = sanitizedProxyHeaders(request, upstreamHost, upstreamPort)
      const headerLines = Object.entries(headers).flatMap(([name, value]) => {
        const values = Array.isArray(value) ? value : [value]
        return values.filter((item) => item !== undefined).map((item) => `${name}: ${item}`)
      })
      upstreamSocket.write(`${request.method} ${request.url} HTTP/${request.httpVersion}\r\n${headerLines.join('\r\n')}\r\n\r\n`)
      if (head.length) upstreamSocket.write(head)
      socket.pipe(upstreamSocket).pipe(socket)
    })

    upstreamSocket.on('error', () => socket.destroy())
    socket.on('error', () => upstreamSocket.destroy())
  })

  return server
}

function startFromEnvironment() {
  const listenHost = process.env.AUKNOWLOG_TUNNEL_LISTEN_HOST || '127.0.0.1'
  const listenPort = Number(process.env.AUKNOWLOG_TUNNEL_LISTEN_PORT || '4180')
  const upstreamUrl = new URL(process.env.AUKNOWLOG_TUNNEL_UPSTREAM || 'http://127.0.0.1:5173')

  const server = createAuthProxy({
    username: process.env.AUKNOWLOG_TUNNEL_USERNAME,
    password: process.env.AUKNOWLOG_TUNNEL_PASSWORD,
    sessionToken: process.env.AUKNOWLOG_TUNNEL_SESSION_TOKEN,
    upstreamHost: upstreamUrl.hostname,
    upstreamPort: Number(upstreamUrl.port || '80'),
  })

  server.listen(listenPort, listenHost, () => {
    console.log(`Auknowlog tunnel auth proxy listening on http://${listenHost}:${listenPort}`)
  })
}

const currentFile = fileURLToPath(import.meta.url)
if (process.argv[1] && path.resolve(process.argv[1]) === currentFile) {
  startFromEnvironment()
}
