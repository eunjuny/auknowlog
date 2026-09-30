<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'

const summary = ref(null), users = ref([]), items = ref([]), total = ref(0)
const globalBudget = ref(null)
const kind = ref('attempts'), userId = ref(''), page = ref(0), userPage = ref(0), userTotal = ref(0)
const detail = ref(null), error = ref(''), loading = ref(false)
const labels = { attempts: '풀이 기록', roadmaps: '로드맵', reviews: '복습 일정', 'review-attempts': '복습 풀이', daily: '데일리 진행', ai: 'AI 호출 원장', audits: '관리자 감사 로그', notifications: '학습 메일 이력' }
const userBudget = ref(null), budgetUser = ref(null), budgetMessage = ref('')
async function editBudget(user) {
  budgetMessage.value = ''; budgetUser.value = user
  try { userBudget.value = (await axios.get(`/api/admin/users/${user.id}/ai-budget`)).data }
  catch { error.value = '사용자 한도를 조회하지 못했습니다.' }
}
async function saveBudget() {
  try {
    userBudget.value = (await axios.put(`/api/admin/users/${budgetUser.value.id}/ai-budget`, {
      tokenLimit: Number(userBudget.value.tokenLimit), callLimit: Number(userBudget.value.callLimit)
    })).data
    budgetMessage.value = '사용자 한도를 변경했습니다. 기존 사용량과 처리 중 예약은 유지됩니다.'
  } catch { error.value = '한도 변경에 실패했습니다. 입력값과 권한을 확인해주세요.' }
}
const parse = value => { try { return JSON.parse(value || '[]') } catch { return [] } }
const time = value => value ? new Date(value).toLocaleString() : '—'

async function loadUsers() {
  const { data } = await axios.get('/api/admin/users', { params: { page: userPage.value, size: 20 } })
  users.value = data.items; userTotal.value = data.total
}
async function load() {
  loading.value = true; error.value = ''; detail.value = null
  try {
    const { data } = await axios.get(`/api/admin/records/${kind.value}`, {
      params: { page: page.value, size: 20, ...(userId.value ? { userId: userId.value } : {}) }
    })
    items.value = data.items; total.value = data.total
  } catch { items.value = []; error.value = '관리자 조회에 실패했습니다. 로그인 권한과 서버 상태를 확인해주세요.' }
  finally { loading.value = false }
}
async function refresh() {
  try {
    summary.value = (await axios.get('/api/admin/summary')).data
    globalBudget.value = (await axios.get('/api/admin/ai-budget')).data
    await loadUsers(); await load()
  } catch { error.value = '전체 관리자 권한이 필요하거나 서버에 연결할 수 없습니다.' }
}
async function selectUser(id) { userId.value = id; page.value = 0; await load() }
async function changeKind(value) { kind.value = value; page.value = 0; await load() }
async function move(delta) { page.value += delta; await load() }
async function moveUsers(delta) {
  userPage.value += delta
  try { await loadUsers() } catch { error.value = '사용자 조회에 실패했습니다.' }
}
async function showDetail(id) {
  try { detail.value = (await axios.get(`/api/admin/attempts/${id}`)).data }
  catch { error.value = '풀이 상세를 조회할 수 없습니다.' }
}
onMounted(refresh)
</script>

<template>
  <section class="admin-monitor">
    <header><div><h2>전체 관리자</h2><p>전체 사용자 학습 · AI 운영 현황 / 읽기 전용</p></div><button @click="refresh">새로고침</button></header>
    <p class="notice">다른 사용자의 개인 학습 기록을 조회하는 관리자 화면입니다. AI 토큰은 측정된 사용량이며 청구 금액이 아닙니다. 기존·시스템 호출은 미귀속으로 표시됩니다.</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <div v-if="summary" class="metrics">
      <article>사용자<strong>{{ summary.learning.users }}</strong></article>
      <article>전체 풀이<strong>{{ summary.learning.attempts }}</strong></article>
      <article>로드맵<strong>{{ summary.learning.roadmaps }}</strong></article>
      <article>복습 대기<strong>{{ summary.learning.pendingReviews }}</strong></article>
      <article>AI 호출 / 실패<strong>{{ summary.ai.calls }} / {{ summary.ai.failures }}</strong></article>
      <article>측정 토큰<strong>{{ Number(summary.ai.tokens).toLocaleString() }}</strong></article>
      <article>평균 AI 지연<strong>{{ summary.ai.averageLatencyMs }} ms</strong></article>
      <article>미귀속 호출<strong>{{ summary.ai.unattributedCalls }}</strong></article>
    </div>
    <p v-if="summary">오늘의 공유 토큰 예산: {{ summary.budget.todayTokens?.toLocaleString() }} / {{ summary.budget.dailyTokenBudget?.toLocaleString() }} · 예산 적용 {{ summary.budget.enforcementEnabled ? '켜짐' : '꺼짐' }}</p>
    <p v-if="globalBudget">공유 안전 차감 {{ globalBudget.usedTokens }} · 처리 중 예약 {{ globalBudget.reservedTokens }} 토큰 · API 시도 {{ globalBudget.startedCalls }}회</p>
    <h3>최근 14일 AI 사용량</h3>
    <div v-if="summary" class="days">
      <p v-if="!summary.dailyAi.length">기록이 없습니다.</p>
      <div v-for="day in summary.dailyAi" :key="day.day" class="day">
        <span>{{ day.day }}</span><meter :value="Number(day.tokens)" :max="Math.max(1, ...summary.dailyAi.map(d => Number(d.tokens)))" />
        <span>{{ Number(day.tokens).toLocaleString() }} 토큰 · {{ day.calls }}회 · 실패 {{ day.failures }}</span>
      </div>
    </div>
    <h3>사용자별 현황</h3>
    <div class="table-scroll"><table><thead><tr><th>계정</th><th>풀이</th><th>로드맵</th><th>측정 토큰</th><th>조회 / 정책</th></tr></thead>
      <tbody><tr v-for="user in users" :key="user.id"><td>{{ user.username }}</td><td>{{ user.attempts }}</td><td>{{ user.roadmaps }}</td><td>{{ Number(user.tokens).toLocaleString() }}</td><td><button @click="selectUser(user.id)">기록 보기</button><button @click="editBudget(user)">AI 한도</button></td></tr></tbody>
    </table></div>
    <div class="pager"><button :disabled="userPage === 0" @click="moveUsers(-1)">이전 사용자</button><span>{{ userPage + 1 }} / {{ Math.max(1, Math.ceil(userTotal / 20)) }}</span><button :disabled="(userPage + 1) * 20 >= userTotal" @click="moveUsers(1)">다음 사용자</button></div>
    <article v-if="userBudget"><h3>{{ budgetUser.username }} AI 예산</h3><p>사용 {{ userBudget.usedTokens }} · 예약 {{ userBudget.reservedTokens }} 토큰 · API 시도 {{ userBudget.startedCalls }}회</p>
      <form @submit.prevent="saveBudget"><label>일일 토큰 한도 <input v-model="userBudget.tokenLimit" type="number" min="0" max="1000000" required /></label><label>일일 API 시도 한도 <input v-model="userBudget.callLimit" type="number" min="0" max="1000" required /></label><button>한도 저장</button><button type="button" @click="userBudget = null">닫기</button></form><p role="status">{{ budgetMessage }}</p>
    </article>
    <h3>전체 기록 <small v-if="userId">(사용자 ID {{ userId }})</small></h3>
    <div class="tabs"><button @click="selectUser('')">전체 사용자</button><button v-for="(label, key) in labels" :key="key" :aria-pressed="kind === key" @click="changeKind(key)">{{ label }}</button></div>
    <p v-if="loading" role="status">불러오는 중…</p>
    <div class="table-scroll"><table><thead><tr><th>계정</th><th>주제 / 작업</th><th>결과</th><th>시각</th><th>상세</th></tr></thead><tbody>
      <tr v-for="item in items" :key="item.id"><td>{{ item.username }}</td><td>{{ item.topic || item.operation }}<div>{{ item.title || item.model }}</div></td><td>
        <span v-if="kind === 'attempts'">{{ item.correctAnswers }} / {{ item.totalQuestions }} 정답</span>
        <span v-else>{{ item.status }}</span>
        <span v-if="kind === 'review-attempts'"> · {{ item.correct ? '정답' : '오답' }} · 다음 간격 {{ item.intervalDays }}일</span>
        <div v-if="kind === 'ai'">{{ item.tokens ?? '미측정' }} 토큰 · {{ item.latencyMs }} ms<div>{{ item.failureType }}</div></div>
        <div v-if="kind === 'audits'">대상 사용자 ID: {{ item.targetUserId ?? '전체 / 미지정' }}</div>
        <div v-if="kind === 'notifications'">{{ item.attempts }}회 · {{ item.failureType || '오류 없음' }}</div>
      </td><td>{{ time(item.time) }}</td><td><button v-if="kind === 'attempts'" @click="showDetail(item.id)">문항 보기</button><span v-else>—</span></td></tr>
    </tbody></table></div>
    <p v-if="!loading && !items.length">조회할 기록이 없습니다.</p>
    <div class="pager"><button :disabled="page === 0 || loading" @click="move(-1)">이전</button><span>{{ page + 1 }} / {{ Math.max(1, Math.ceil(total / 20)) }} · {{ total }}건</span><button :disabled="(page + 1) * 20 >= total || loading" @click="move(1)">다음</button></div>
    <section v-if="detail" class="details"><header><h3>{{ detail.attempt.username }} · {{ detail.attempt.title }}</h3><button @click="detail = null">닫기</button></header>
      <article v-for="(q, i) in detail.questions" :key="i"><h4>{{ i + 1 }}. {{ q.question }}</h4><p>{{ q.correct ? '정답' : '오답' }} · 선택: {{ q.selectedAnswer }} · 정답: {{ q.correctAnswer }}</p>
        <ol><li v-for="(option, index) in parse(q.options)" :key="index">{{ option }}<p>{{ parse(q.optionExplanations)[index] }}</p></li></ol><p>{{ q.explanation }}</p>
      </article>
    </section>
  </section>
</template>

<style scoped>
.admin-monitor { max-width: 1200px; margin: auto; padding: 24px; color: #18181b; }
header,.pager,.tabs { display:flex; gap:12px; justify-content:space-between; align-items:center; flex-wrap:wrap; }
.tabs { justify-content:flex-start; margin-bottom:16px; }
.metrics { display:grid; grid-template-columns:repeat(auto-fit,minmax(160px,1fr)); gap:12px; margin:20px 0; }
.metrics article,.details article { padding:16px; border:1px solid #ddd; border-radius:10px; }
strong { display:block; font-size:24px; margin-top:8px; }
button { padding:8px 12px; border:1px solid #ccc; border-radius:6px; background:white; cursor:pointer; }
button[aria-pressed=true] { background:#18181b; color:white; }
button:disabled { opacity:.4; cursor:default; }
.notice { padding:12px; background:#f4f4f5; border-radius:8px; }
.table-scroll { overflow-x:auto; } table { border-collapse:collapse; width:100%; } th,td { text-align:left; padding:12px; border-bottom:1px solid #ddd; }
td div { margin-top:4px; color:#555; } .pager { margin:16px 0; } .day { display:flex; gap:12px; flex-wrap:wrap; margin:8px 0; } meter { flex:1; min-width:100px; } h3 { margin-top:28px; }
@media(max-width:600px) { .admin-monitor { padding:12px; } th,td { padding:8px; } }
</style>
