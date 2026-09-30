// Local-only fixture setup. Never print credentials or tokens.
import { randomBytes } from 'node:crypto'
import { mkdirSync, readFileSync, writeFileSync, existsSync, chmodSync } from 'node:fs'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { resolve, dirname } from 'node:path'
import { homedir } from 'node:os'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const runtime = resolve(root, '.runtime')
const credentialPath = resolve(runtime, 'osc-sample-accounts.json')
mkdirSync(runtime, { recursive: true, mode: 0o700 })
const password = () => randomBytes(24).toString('base64url')
const credentials = existsSync(credentialPath)
  ? JSON.parse(readFileSync(credentialPath, 'utf8'))
  : { administrator: { username: 'osc-local-admin', password: password() },
      learners: [{ username: 'test1', password: password() }, { username: 'test2', password: password() }] }
credentials.applicationAdmin ??= { username: 'app-admin', password: password() }
writeFileSync(credentialPath, JSON.stringify(credentials, null, 2), { mode: 0o600 })
chmodSync(credentialPath, 0o600)

// Public image pulls do not need a Keychain credential helper. An isolated config
// avoids changing the user's Docker configuration or waiting on desktop prompts.
const dockerConfig = resolve(runtime, 'osc-docker-config')
mkdirSync(dockerConfig, { recursive: true, mode: 0o700 })
writeFileSync(resolve(dockerConfig, 'config.json'), JSON.stringify({ cliPluginsExtraDirs: [
  resolve(homedir(), '.docker', 'cli-plugins'), '/Applications/Docker.app/Contents/Resources/cli-plugins'
] }), { mode: 0o600 })
const context = spawnSync('docker', ['context', 'inspect', '--format', '{{.Endpoints.docker.Host}}'],
  { encoding: 'utf8', timeout: 10000 })
if (context.status !== 0) throw new Error('Cannot resolve the current local Docker engine.')
const compose = spawnSync('docker', ['--config', dockerConfig, 'compose', '-f', 'docker-compose.yml', '-f', 'docker-compose.auth.yml',
  'up', '-d', 'keycloak'], {
  cwd: root, encoding: 'utf8', timeout: 180000,
  env: { ...process.env, DOCKER_HOST: context.stdout.trim(), KEYCLOAK_ADMIN_USERNAME: credentials.administrator.username,
    KEYCLOAK_ADMIN_PASSWORD: credentials.administrator.password }
})
if (compose.status !== 0) {
  console.error('Keycloak startup failed; account credentials remain in the private runtime file. Check Docker network/image availability.')
  process.exit(1)
}

const base = 'http://127.0.0.1:8180'
let ready = false
for (let attempt = 0; attempt < 90; attempt++) {
  try {
    const response = await fetch(`${base}/realms/auknowlog/.well-known/openid-configuration`,
      { signal: AbortSignal.timeout(2000) })
    if (response.ok) { ready = true; break }
  } catch {}
  await new Promise(done => setTimeout(done, 1000))
}
if (!ready) throw new Error('Keycloak realm readiness timed out.')

const tokenResponse = await fetch(`${base}/realms/master/protocol/openid-connect/token`, {
  method: 'POST', body: new URLSearchParams({ grant_type: 'password', client_id: 'admin-cli',
    username: credentials.administrator.username, password: credentials.administrator.password }),
  signal: AbortSignal.timeout(10000)
})
if (!tokenResponse.ok) throw new Error('Local bootstrap administrator authentication failed.')
const token = (await tokenResponse.json()).access_token
async function admin(path, method = 'GET', body) {
  const response = await fetch(`${base}/admin/realms/auknowlog${path}`, {
    method, headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(10000)
  })
  if (!response.ok) throw new Error(`Keycloak admin operation failed (${response.status}).`)
  return response.status === 204 || response.status === 201 ? null : response.json()
}
const role = await admin('/roles/USER')
for (const learner of [...credentials.learners, credentials.applicationAdmin]) {
  let users = await admin(`/users?username=${encodeURIComponent(learner.username)}&exact=true`)
  if (!users.length) {
    await admin('/users', 'POST', { username: learner.username, enabled: true,
      firstName: learner.username, requiredActions: [],
      credentials: [{ type: 'password', value: learner.password, temporary: false }] })
    users = await admin(`/users?username=${encodeURIComponent(learner.username)}&exact=true`)
  }
  if (users.length !== 1) throw new Error('Sample username is ambiguous.')
  await admin(`/users/${users[0].id}`, 'PUT', { firstName: learner.username, lastName: 'Sample',
    email: `${learner.username}@example.invalid`, emailVerified: true, requiredActions: [] })
  await admin(`/users/${users[0].id}/role-mappings/realm`, 'POST', [role])
  if (learner.username === credentials.applicationAdmin.username) {
    await admin(`/users/${users[0].id}/role-mappings/realm`, 'POST', [await admin('/roles/ADMIN')])
  }
  console.log(`${learner.username}: application account ready`)
}
const fixture = spawnSync('docker', ['exec', '-i', 'auknowlog-postgres', 'psql', '-U', 'auknowlog',
  '-d', 'auknowlog', '-v', 'ON_ERROR_STOP=1'], {
  cwd: root, input: readFileSync(resolve(root, 'scripts/seed-osc-samples.sql')), encoding: 'utf8'
})
if (fixture.status !== 0) throw new Error('Sample database setup failed.')
console.log('Separate sample learning data ready. Credentials are stored only in .runtime/osc-sample-accounts.json (mode 600).')
