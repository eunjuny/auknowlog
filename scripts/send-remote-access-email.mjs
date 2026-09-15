import fs from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
const projectRoot = path.dirname(scriptDirectory)
const runtimeDirectory = path.join(projectRoot, '.runtime', 'remote-access')
const backendUrl = process.env.AUKNOWLOG_BACKEND_URL ?? 'http://127.0.0.1:8080'
const urlFile = path.join(runtimeDirectory, 'public-url')
const credentialsFile = path.join(runtimeDirectory, 'credentials')

function readLifetimeSeconds() {
  const index = process.argv.indexOf('--lifetime-seconds')
  const value = index >= 0 ? process.argv[index + 1] : null
  if (!value || !/^\d+$/.test(value) || Number(value) < 60) {
    throw new Error('invalid lifetime')
  }
  return Number(value)
}

function parseCredentials(contents) {
  const match = /^사용자명:\r?\n([^\r\n]+)\r?\n비밀번호:\r?\n([^\r\n]+)\r?\n?$/u.exec(contents)
  if (!match) throw new Error('invalid credential file')
  return { username: match[1], password: match[2] }
}

function publicError() {
  return '원격 접속 정보 메일 전송에 실패했습니다. 로컬 메일 설정과 Gmail 앱 비밀번호를 확인하세요.'
}

try {
  const lifetimeSeconds = readLifetimeSeconds()
  const [publicUrl, credentials] = await Promise.all([
    fs.readFile(urlFile, 'utf8'),
    fs.readFile(credentialsFile, 'utf8'),
  ])
  const normalizedUrl = publicUrl.trim()
  if (!/^https:\/\/[a-z0-9-]+\.trycloudflare\.com\/?$/u.test(normalizedUrl)) {
    throw new Error('invalid quick tunnel url')
  }

  const response = await fetch(`${backendUrl.replace(/\/$/u, '')}/api/notifications/remote-access/email`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      publicUrl: normalizedUrl,
      ...parseCredentials(credentials),
      expiresAt: new Date(Date.now() + lifetimeSeconds * 1000).toISOString(),
    }),
    signal: AbortSignal.timeout(15_000),
  })
  if (!response.ok) throw new Error(`mail endpoint returned ${response.status}`)

  // URL, account name, recipient, and password are deliberately not written to stdout or logs.
  console.log('원격 접속 정보를 설정된 수신 메일로 전송했습니다.')
} catch {
  console.error(publicError())
  process.exitCode = 1
}
