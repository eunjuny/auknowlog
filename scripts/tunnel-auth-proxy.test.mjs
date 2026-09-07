import assert from 'node:assert/strict'
import http from 'node:http'

import { createAuthProxy } from './tunnel-auth-proxy.mjs'

function listen(server) {
  return new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
}

function close(server) {
  return new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()))
}

const upstreamServer = http.createServer((req, response) => {
  response.setHeader('Content-Type', 'application/json')
  response.setHeader('Connection', 'close')
  response.end(JSON.stringify({
    url: req.url,
    authorization: req.headers.authorization || null,
    cookie: req.headers.cookie || null,
  }))
})
await listen(upstreamServer)

const proxyServer = createAuthProxy({
  username: 'tester',
  password: 'a-secure-test-password',
  sessionToken: 'test-session-token',
  upstreamPort: upstreamServer.address().port,
  maxFailedAttempts: 2,
})
await listen(proxyServer)
const proxyPort = proxyServer.address().port

function request({ method = 'GET', path = '/', headers = {}, body = '' } = {}) {
  return new Promise((resolve, reject) => {
    const req = http.request({
      host: '127.0.0.1',
      port: proxyPort,
      method,
      path,
      agent: false,
      headers: body
        ? { Connection: 'close', 'Content-Type': 'application/x-www-form-urlencoded', 'Content-Length': Buffer.byteLength(body), ...headers }
        : { Connection: 'close', ...headers },
    }, (response) => {
      const chunks = []
      response.on('data', (chunk) => chunks.push(chunk))
      response.on('end', () => resolve({
        status: response.statusCode,
        headers: response.headers,
        body: Buffer.concat(chunks).toString('utf8'),
      }))
    })
    req.on('error', reject)
    if (body) req.write(body)
    req.end()
  })
}

try {
  const unauthenticated = await request({ path: '/quiz?topic=java' })
  assert.equal(unauthenticated.status, 302)
  assert.match(unauthenticated.headers.location, /^\/__auknowlog_tunnel\/login\?return=/)
  assert.equal(unauthenticated.headers['cache-control'], 'no-store')
  console.log('PASS unauthenticated request redirects to login')

  const loginPage = await request({ path: '/__auknowlog_tunnel/login?return=%2Fquiz' })
  assert.equal(loginPage.status, 200)
  assert.match(loginPage.body, /Auknowlog 임시 접속/)
  assert.doesNotMatch(loginPage.body, /a-secure-test-password/)
  console.log('PASS login form does not expose credentials')

  const loginBody = new URLSearchParams({
    username: 'tester',
    password: 'a-secure-test-password',
    return: '/quiz?topic=java',
  }).toString()
  const validLogin = await request({ method: 'POST', path: '/__auknowlog_tunnel/login', body: loginBody })
  assert.equal(validLogin.status, 303)
  assert.equal(validLogin.headers.location, '/quiz?topic=java')
  assert.match(validLogin.headers['set-cookie'][0], /HttpOnly/)
  assert.match(validLogin.headers['set-cookie'][0], /Secure/)
  assert.match(validLogin.headers['set-cookie'][0], /SameSite=Strict/)
  console.log('PASS valid login creates a secure session cookie')

  const authenticated = await request({
    path: '/quiz?topic=java',
    headers: {
      Authorization: 'Bearer must-not-leak',
      Cookie: 'application_cookie=keep; auknowlog_tunnel_session=test-session-token',
    },
  })
  assert.equal(authenticated.status, 200)
  assert.deepEqual(JSON.parse(authenticated.body), {
    url: '/quiz?topic=java',
    authorization: null,
    cookie: 'application_cookie=keep',
  })
  console.log('PASS tunnel credentials are removed before proxying')

  const invalidLoginBody = new URLSearchParams({ username: 'tester', password: 'wrong' }).toString()
  assert.equal((await request({ method: 'POST', path: '/__auknowlog_tunnel/login', body: invalidLoginBody })).status, 403)
  assert.equal((await request({ method: 'POST', path: '/__auknowlog_tunnel/login', body: invalidLoginBody })).status, 403)
  assert.equal((await request({ method: 'POST', path: '/__auknowlog_tunnel/login', body: invalidLoginBody })).status, 429)
  console.log('PASS repeated invalid logins are rate limited')
} finally {
  await close(proxyServer)
  await close(upstreamServer)
}
