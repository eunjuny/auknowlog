// Disposable, real Keycloak + PostgreSQL + browser verification. Never print credentials or tokens.
import { randomBytes } from 'node:crypto'
import { spawnSync } from 'node:child_process'
import { existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { createServer } from 'node:net'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const runtime = join(root, '.runtime')
const project = 'auknowlog-auth-smoke'
const composeFiles = [
  'docker-compose.yml', 'docker-compose.app.yml', 'docker-compose.auth.yml',
  'docker-compose.app-auth.yml', 'docker-compose.smoke.yml', 'docker-compose.auth-smoke.yml'
]
const composeArgs = ['compose', '-p', project, ...composeFiles.flatMap(file => ['-f', file])]

function command(name, args, options = {}) {
  const result = spawnSync(name, args, {
    cwd: root, encoding: 'utf8', timeout: 20 * 60 * 1000,
    stdio: 'inherit', ...options
  })
  if (result.status !== 0) throw new Error(`${name} step failed without exposing credentials`)
  return result
}

function capture(name, args) {
  const result = spawnSync(name, args, { cwd: root, encoding: 'utf8', timeout: 10000 })
  if (result.status !== 0) throw new Error(`${name} preflight failed`)
  return result.stdout.trim()
}

const existingContainers = capture('docker', [...composeArgs, 'ps', '-a', '--format', 'json'])
const existingVolumes = capture('docker', ['volume', 'ls', '-q', '--filter', `label=com.docker.compose.project=${project}`])
if (existingContainers || existingVolumes) {
  throw new Error('An auth smoke project already exists. Inspect it before running a destructive isolated test.')
}
async function ensurePortFree(port) {
  await new Promise((done, reject) => {
    const server = createServer()
    server.once('error', () => reject(new Error(`Auth smoke port ${port} is already in use`)))
    server.listen(port, '127.0.0.1', () => server.close(done))
  })
}
for (const port of [15433, 18080, 15173, 18180]) await ensurePortFree(port)

mkdirSync(runtime, { recursive: true, mode: 0o700 })
const privateDirectory = mkdtempSync(join(runtime, 'auth-smoke-'))
const credentialPath = join(privateDirectory, 'accounts.json')
const password = () => randomBytes(24).toString('base64url')
const credentials = {
  administrator: { username: 'osc-smoke-admin', password: password() },
  learners: [{ username: 'test1', password: password() }, { username: 'test2', password: password() }],
  applicationAdmin: { username: 'app-admin', password: password() }
}
writeFileSync(credentialPath, JSON.stringify(credentials), { mode: 0o600 })

const env = {
  ...process.env,
  COMPOSE_PROJECT_NAME: project,
  POSTGRES_PORT: '15433', BACKEND_PORT: '18080', FRONTEND_PORT: '15173', KEYCLOAK_PORT: '18180',
  AUKNOWLOG_CONTAINER_OPENAI_API_KEY: '',
  AUKNOWLOG_CONTAINER_MAIL_USERNAME: '',
  AUKNOWLOG_CONTAINER_MAIL_APP_PASSWORD: '',
  AUKNOWLOG_CONTAINER_MAIL_RECIPIENT: '',
  AUKNOWLOG_CONTAINER_DAILY_LEARNING_ENABLED: 'false',
  AUKNOWLOG_CONTAINER_LEARNING_MAIL_ENABLED: 'false',
  AUKNOWLOG_CONTAINER_EMBEDDINGS_ENABLED: 'false',
  KEYCLOAK_ADMIN_USERNAME: credentials.administrator.username,
  KEYCLOAK_ADMIN_PASSWORD: credentials.administrator.password,
  AUKNOWLOG_OSC_CREDENTIAL_FILE: credentialPath,
  AUKNOWLOG_OSC_SKIP_COMPOSE_START: 'true',
  AUKNOWLOG_OSC_KEYCLOAK_URL: 'http://127.0.0.1:18180',
  AUKNOWLOG_OSC_FRONTEND_URL: 'http://127.0.0.1:15173',
  AUKNOWLOG_OSC_API_URL: 'http://127.0.0.1:15173',
  AUKNOWLOG_OSC_DB_CONTAINER: 'auknowlog-auth-smoke-postgres'
}
let started = false
try {
  started = true
  command('docker', [...composeArgs, 'up', '--build', '-d', '--wait'], { env })
  command('node', ['scripts/setup-osc-sample-accounts.mjs'], { env })
  command('node', ['scripts/verify-osc-samples.mjs'], { env })
  console.log('Isolated container OIDC login, ownership and RBAC verification passed without AI or SMTP calls.')
} finally {
  let cleanupFailed = false
  if (started) {
    const cleanup = spawnSync('docker', [...composeArgs, 'down', '-v'], {
      cwd: root, env, encoding: 'utf8', stdio: 'inherit', timeout: 120000
    })
    cleanupFailed = cleanup.status !== 0
  }
  if (existsSync(privateDirectory)) rmSync(privateDirectory, { recursive: true, force: true })
  if (cleanupFailed) throw new Error('Auth smoke cleanup failed; inspect the isolated project manually.')
}
