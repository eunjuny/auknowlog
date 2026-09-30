<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
const budget = ref(null), settings = ref(null), history = ref([]), total = ref(0), page = ref(0)
const code = ref(''), error = ref(''), message = ref(''), busy = ref(false)
async function load() {
  const [b, s, h] = await Promise.all([axios.get('/api/account/ai-budget'), axios.get('/api/account/notifications'), axios.get('/api/account/notifications/history', { params: { page: page.value } })])
  budget.value = b.data; settings.value = s.data; history.value = h.data.items; total.value = h.data.total
}
async function action(task, success) {
  if (busy.value) return
  busy.value = true; error.value = ''; message.value = ''
  try { await task(); await load(); message.value = success }
  catch (e) { error.value = e.response?.data?.message || '요청을 처리하지 못했습니다. 설정과 서버 상태를 확인해주세요.' }
  finally { busy.value = false }
}
const save = () => action(() => axios.put('/api/account/notifications', {
  email: settings.value.email || '', reviewEnabled: settings.value.reviewEnabled,
  dailyEnabled: settings.value.dailyEnabled, sendHour: Number(settings.value.sendHour)
}), '이메일·알림 설정을 저장했습니다. 이메일을 변경했다면 다시 인증해주세요.')
const requestVerification = () => action(() => axios.post('/api/account/notifications/verification'), '인증 메일을 요청했습니다. 받은 8자리 코드를 입력해주세요.')
const verify = () => action(() => axios.post('/api/account/notifications/verify', { code: code.value.trim() }), '이메일 인증을 완료했습니다.')
const sendNow = () => action(async () => {
  const { data } = await axios.post('/api/account/notifications/send-now')
  if (!data.queued) throw new Error('empty')
}, '학습 알림을 대기열에 등록했습니다. 같은 날에는 1건만 생성됩니다.')
const retry = id => action(() => axios.post(`/api/account/notifications/${id}/retry`), '재시도를 예약했습니다.')
async function move(delta) { page.value += delta; await action(load, '') }
onMounted(() => action(load, ''))
</script>

<template>
  <section class="account-settings">
    <h2>계정·알림</h2>
    <p>본인의 이메일과 학습 알림을 설정합니다. 발신 SMTP 계정은 서버에서 공통으로 관리합니다.</p>
    <p v-if="error" role="alert">{{ error }}</p><p v-if="message" role="status">{{ message }}</p>
    <article v-if="budget"><h3>오늘의 AI 사용 한도</h3>
      <div class="metrics"><div>집계/안전 차감 토큰<strong>{{ Number(budget.usedTokens).toLocaleString() }}</strong></div><div>처리 중 예약 토큰<strong>{{ Number(budget.reservedTokens).toLocaleString() }}</strong></div><div>일일 토큰 한도<strong>{{ Number(budget.tokenLimit).toLocaleString() }}</strong></div><div>API 시도 횟수<strong>{{ budget.startedCalls }} / {{ budget.callLimit }}</strong></div></div>
      <p>문제 생성·로드맵·임베딩 등 개별 API 시도를 합산합니다. 매일 한도가 초기화되며 관리자만 한도를 바꿀 수 있습니다. 타임아웃 등 사용량을 확인할 수 없는 호출은 안전하게 추정 차감하므로 청구서와 다를 수 있습니다.</p>
    </article>
    <article v-if="settings"><h3>학습 메일 수신 설정</h3>
      <p>발송 기능: {{ settings.deliveryEnabled ? '켜짐' : '꺼짐' }} · SMTP: {{ settings.smtpConfigured ? '설정됨' : '미설정' }}</p>
      <form @submit.prevent="save"><label>수신 이메일<input v-model="settings.email" type="email" maxlength="254" autocomplete="email" placeholder="본인 이메일" /></label>
        <p>이메일 인증: {{ settings.verified ? '완료' : '필요' }}</p>
        <label class="check"><input v-model="settings.reviewEnabled" type="checkbox" /> 오늘 복습할 문제 알림</label>
        <label class="check"><input v-model="settings.dailyEnabled" type="checkbox" /> 아직 완료하지 않은 데일리 학습 알림</label>
        <label>매일 발송 시간 (한국 시간)<select v-model="settings.sendHour"><option v-for="hour in 24" :key="hour" :value="hour - 1">{{ hour - 1 }}시</option></select></label>
        <button :disabled="busy" type="submit">설정 저장</button>
      </form>
      <div class="verification"><button :disabled="busy || !settings.email || !settings.deliveryEnabled || !settings.smtpConfigured" @click="requestVerification">인증 메일 요청</button>
        <label>인증 코드<input v-model="code" inputmode="numeric" maxlength="8" autocomplete="one-time-code" placeholder="8자리 코드" /></label><button :disabled="busy || code.length !== 8" @click="verify">인증 확인</button></div>
      <p>이메일을 먼저 저장한 뒤 인증해주세요. 인증 메일은 5분 간격·하루 3회, 코드는 30분 동안 유효합니다. 알림을 끄면 대기 중인 발송은 취소됩니다.</p>
      <button :disabled="busy || !settings.verified || !settings.deliveryEnabled" @click="sendNow">현재 학습 알림 발송 예약</button>
      <p>알림은 복습 또는 미완료 데일리 학습이 있을 때만 생성됩니다. 학습 메일에는 원격 접속 비밀번호를 포함하지 않습니다.</p>
    </article>
    <article><h3>학습 메일 발송 이력</h3>
      <div class="table-scroll"><table><thead><tr><th>생성 시각</th><th>상태</th><th>시도</th><th>오류 분류</th><th>재시도</th></tr></thead><tbody><tr v-for="item in history" :key="item.id"><td>{{ new Date(item.createdAt).toLocaleString() }}</td><td>{{ item.status }}</td><td>{{ item.attempts }}</td><td>{{ item.failureType || '—' }}</td><td><button v-if="item.status === 'FAILED' && item.manualRetries === 0" :disabled="busy" @click="retry(item.id)">다시 보내기</button></td></tr></tbody></table></div>
      <p v-if="!history.length">발송 이력이 없습니다.</p><div class="pager"><button :disabled="page === 0 || busy" @click="move(-1)">이전</button><span>{{ page + 1 }} / {{ Math.max(1, Math.ceil(total / 20)) }}</span><button :disabled="(page + 1) * 20 >= total || busy" @click="move(1)">다음</button></div>
      <p>자동 재시도는 최대 3회이며 최종 실패 후 수동 재시도는 1회까지 가능합니다. SENT는 SMTP 접수 성공이며 실제 받은편지함 도착을 보장하지 않습니다.</p>
    </article>
  </section>
</template>
<style scoped>
.account-settings { max-width:1050px; margin:auto; padding:24px; } article { border:1px solid #ddd; border-radius:12px; padding:20px; margin:18px 0; }
.metrics { display:grid; grid-template-columns:repeat(auto-fit,minmax(150px,1fr)); gap:12px; } strong { display:block; font-size:24px; margin:8px 0; }
label { display:flex; flex-direction:column; gap:8px; margin:14px 0; } .check { flex-direction:row; align-items:center; }
input:not([type=checkbox]),select { padding:10px; border:1px solid #ccc; border-radius:6px; max-width:450px; }
button { padding:10px 14px; border:1px solid #ccc; border-radius:6px; background:white; cursor:pointer; } button:disabled { opacity:.5; cursor:default; }
.verification,.pager { display:flex; gap:12px; flex-wrap:wrap; align-items:center; margin-top:16px; } .table-scroll { overflow:auto; } table { width:100%; border-collapse:collapse; } td,th { padding:10px; text-align:left; border-bottom:1px solid #ddd; }
@media(max-width:600px) { .account-settings { padding:12px; } article { padding:12px; } }
</style>
