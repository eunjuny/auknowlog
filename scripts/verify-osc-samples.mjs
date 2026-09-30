// Exercise actual OIDC login and ownership with the private local sample accounts.
import { readFileSync } from 'node:fs'
import { chromium } from '../frontend/node_modules/@playwright/test/index.mjs'

const credentials = JSON.parse(readFileSync(new URL('../.runtime/osc-sample-accounts.json', import.meta.url), 'utf8'))
const browser = await chromium.launch({ headless: true })
try {
  const histories = []
  for (const learner of credentials.learners) {
    const context = await browser.newContext()
    const page = await context.newPage()
    const initialRequest = page.waitForRequest(request => request.url().includes('/api/daily-learnings/today'), { timeout: 30000 })
    await page.goto('http://127.0.0.1:5173/')
    await page.locator('#username').fill(learner.username)
    await page.locator('#password').fill(learner.password)
    await page.locator('#kc-login').click()
    const request = await initialRequest.catch(async () => {
      const heading = await page.locator('h1').first().textContent().catch(() => 'unavailable')
      throw new Error(`Login did not reach application. Page heading: ${heading}`)
    })
    const authorization = request.headers().authorization
    if (!authorization?.startsWith('Bearer ')) throw new Error('Bearer token missing after login.')
    const headers = { Authorization: authorization }
    const budget = await context.request.get('http://127.0.0.1:8080/api/account/ai-budget', { headers })
    if (budget.status() !== 200 || (await budget.json()).tokenLimit === undefined) throw new Error('Personal AI budget unavailable.')
    const settings = await context.request.get('http://127.0.0.1:8080/api/account/notifications', { headers })
    if (settings.status() !== 200) throw new Error('Personal notification settings unavailable.')
    if (learner.username === 'test1') {
      const flags = await settings.json()
      console.log(`Learning mail configuration: enabled=${flags.deliveryEnabled}, SMTP configured=${flags.smtpConfigured}. No email was requested.`)
    }
    await page.getByRole('button', { name: '계정·알림', exact: true }).click()
    await page.getByRole('heading', { name: '학습 메일 수신 설정', exact: true }).waitFor()
    const me = await context.request.get('http://127.0.0.1:8080/api/auth/me', { headers })
    if (me.status() !== 200 || (await me.json()).username !== learner.username) throw new Error('Current user mismatch.')
    const result = await context.request.get('http://127.0.0.1:8080/api/learning-attempts', { headers })
    const body = await result.json()
    const expectedTopic = learner.username === 'test1' ? 'Kubernetes Pod' : 'PostgreSQL 트랜잭션'
    const items = body.items ?? body.attempts ?? body.content
    if (result.status() !== 200 || !Array.isArray(items) || items.length !== 1 || items[0].topic !== expectedTopic) {
      throw new Error('Sample history ownership mismatch.')
    }
    const roadmapsResponse = await context.request.get('http://127.0.0.1:8080/api/learning-roadmaps', { headers })
    const roadmaps = (await roadmapsResponse.json()).inProgressRoadmaps
    if (roadmapsResponse.status() !== 200 || roadmaps?.length !== 1 || roadmaps[0].topic !== expectedTopic) {
      throw new Error('Sample roadmap ownership mismatch.')
    }
    const reviewsResponse = await context.request.get('http://127.0.0.1:8080/api/reviews', { headers })
    const reviews = (await reviewsResponse.json()).reviews
    if (reviewsResponse.status() !== 200 || reviews?.length !== 1 || reviews[0].topic !== expectedTopic) {
      throw new Error('Sample review ownership mismatch.')
    }
    const admin = await context.request.get('http://127.0.0.1:8080/api/quality-evaluations/summary', { headers })
    if (admin.status() !== 403) throw new Error('USER accessed ADMIN API.')
    const globalAdmin = await context.request.get('http://127.0.0.1:8080/api/admin/summary', { headers })
    if (globalAdmin.status() !== 403) throw new Error('USER accessed global monitoring API.')
    if (await page.getByRole('button', { name: '전체 관리자', exact: true }).count()) {
      throw new Error('USER was shown administrator navigation.')
    }
    histories.push({ context, headers, id: items[0].attemptId ?? items[0].id, username: learner.username })
    console.log(`${learner.username}: actual OIDC login, personal history and USER restriction passed`)
  }
  for (let index = 0; index < histories.length; index++) {
    const current = histories[index]
    const other = histories[1 - index]
    if (!other.id) throw new Error('Sample attempt identifier missing.')
    const response = await current.context.request.get(`http://127.0.0.1:8080/api/learning-attempts/${other.id}`, {
      headers: current.headers
    })
    if (response.status() !== 404) throw new Error('Other user history was not hidden.')
    console.log(`${current.username}: other user history returned 404`)
  }
  const context = await browser.newContext()
  const page = await context.newPage()
  const initial = page.waitForRequest(request => request.url().includes('/api/daily-learnings/today'))
  await page.goto('http://127.0.0.1:5173/')
  await page.locator('#username').fill(credentials.applicationAdmin.username)
  await page.locator('#password').fill(credentials.applicationAdmin.password)
  await page.locator('#kc-login').click()
  const headers = { Authorization: (await initial).headers().authorization }
  // Provision this application administrator before querying global records.
  const me = await context.request.get('http://127.0.0.1:8080/api/auth/me', { headers })
  if (me.status() !== 200) throw new Error('Application administrator provisioning failed.')
  await page.getByRole('button', { name: '전체 관리자', exact: true }).click()
  await page.getByRole('heading', { name: '사용자별 현황', exact: true }).waitFor()
  const users = await context.request.get('http://127.0.0.1:8080/api/admin/users', { headers })
  const body = await users.json()
  if (users.status() !== 200 || !credentials.learners.every(l => body.items.some(u => u.username === l.username))) {
    throw new Error('Administrator cannot see sample users.')
  }
  for (const kind of ['attempts', 'roadmaps', 'reviews', 'review-attempts', 'daily', 'ai', 'audits', 'notifications']) {
    const response = await context.request.get(`http://127.0.0.1:8080/api/admin/records/${kind}`, { headers })
    if (response.status() !== 200) throw new Error(`Administrator ${kind} query failed.`)
  }
  for (const user of body.items.filter(u => credentials.learners.some(l => l.username === u.username))) {
    const response = await context.request.get(`http://127.0.0.1:8080/api/admin/records/attempts?userId=${user.id}`, { headers })
    const records = (await response.json()).items
    if (records.length !== 1 || records[0].username !== user.username) throw new Error('Admin user filter mismatch.')
    const detail = await context.request.get(`http://127.0.0.1:8080/api/admin/attempts/${records[0].id}`, { headers })
    if (detail.status() !== 200 || (await detail.json()).questions.length !== 1) throw new Error('Admin detail failed.')
  }
  const summary = await context.request.get('http://127.0.0.1:8080/api/admin/summary', { headers })
  if (summary.status() !== 200 || !(await summary.json()).ai) throw new Error('Global AI summary failed.')
  const audit = await context.request.get('http://127.0.0.1:8080/api/admin/records/audits?size=100', { headers })
  const auditRows = (await audit.json()).items
  if (!auditRows.some(row => row.status === 403) || !auditRows.some(row => row.status === 200)) {
    throw new Error('Administrator audit outcomes were not persisted.')
  }
  const sample = body.items.find(user => user.username === 'test1')
  const limits = await context.request.get(`http://127.0.0.1:8080/api/admin/users/${sample.id}/ai-budget`, { headers })
  const original = await limits.json()
  const update = await context.request.put(`http://127.0.0.1:8080/api/admin/users/${sample.id}/ai-budget`, {
    headers, data: { tokenLimit: original.tokenLimit, callLimit: original.callLimit }
  })
  if (update.status() !== 200) throw new Error('Administrator budget policy update failed.')
  const anonymous = await context.request.get('http://127.0.0.1:8080/api/admin/summary')
  if (anonymous.status() !== 401) throw new Error('Unauthenticated admin request was accepted.')
  await page.setViewportSize({ width: 390, height: 844 })
  if (await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)) {
    throw new Error('Mobile administrator layout overflows.')
  }
  console.log('ADMIN: actual login, navigation, global monitoring, user filters and cross-user details passed; anonymous 401.')
  console.log('Actual login and sample data isolation verified without OpenAI calls.')
} finally {
  await browser.close()
}
