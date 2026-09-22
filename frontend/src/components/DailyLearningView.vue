<script setup>
import { onMounted, ref } from 'vue'
import axios from 'axios'

const emit = defineEmits(['open-quiz', 'completed'])
const daily = ref(null), loading = ref(true), generating = ref(false), quizLoading = ref(false), error = ref(null)
const advancedTopic = ref(''), advancedQuestions = ref(5)
async function load() {
  loading.value = true; error.value = null
  try { const { data } = await axios.get('/api/daily-learnings/today'); daily.value = data || null; if (data?.status === 'COMPLETED') emit('completed') }
  catch (err) { error.value = `데일리 학습을 불러오지 못했습니다: ${err.response?.data?.message || err.message}` }
  finally { loading.value = false }
}
async function generate() {
  generating.value = true; error.value = null
  try { daily.value = (await axios.post('/api/daily-learnings/generate', {})).data }
  catch (err) { error.value = `오늘의 데일리 학습 생성에 실패했습니다: ${err.response?.data?.message || err.message}` }
  finally { generating.value = false }
}
async function start(kind) {
  if (!daily.value || quizLoading.value) return
  quizLoading.value = true; error.value = null
  try {
    const url = kind === 'review' ? `/api/daily-learnings/${daily.value.dailyLearningId}/review-quiz` : `/api/daily-learnings/${daily.value.dailyLearningId}/advanced-quiz`
    const body = kind === 'review' ? undefined : { topic: advancedTopic.value.trim(), numberOfQuestions: Number(advancedQuestions.value) }
    emit('open-quiz', (await axios.post(url, body)).data)
  } catch (err) { error.value = `문제를 준비하지 못했습니다: ${err.response?.data?.message || err.message}` }
  finally { quizLoading.value = false }
}
onMounted(load)
defineExpose({ load })
</script>

<template>
  <section class="daily-learning-view" aria-labelledby="daily-learning-title">
    <div class="heading"><div><p class="eyebrow">TODAY'S TECH BRIEF</p><h2 id="daily-learning-title">데일리 학습</h2><p>기사 맥락을 읽고, 보충 해설을 이해한 뒤, 해당 개념을 복습합니다.</p></div><span v-if="daily" class="status" :class="daily.status.toLowerCase()">{{ daily.status === 'COMPLETED' ? '오늘 학습 완료' : '학습 준비됨' }}</span></div>
    <p v-if="error" class="error">{{ error }}</p>
    <div v-if="loading" class="empty">오늘의 데일리 학습을 확인하고 있습니다.</div>
    <div v-else-if="!daily" class="empty"><h3>아직 오늘의 학습이 준비되지 않았습니다.</h3><p>자동 생성 시간이 지났는데도 항목이 없다면 RSS 기사 한 건을 수집해 해설을 만들 수 있습니다. 이 작업은 AI 토큰 예산을 사용합니다.</p><button class="primary" :disabled="generating" @click="generate">{{ generating ? '생성 중…' : '오늘의 학습 생성' }}</button></div>
    <template v-else>
      <article class="card"><p class="eyebrow">오늘의 기사</p><h3>{{ daily.articleTitle }}</h3><a :href="daily.articleUrl" target="_blank" rel="noopener noreferrer">원문 열기 ↗</a><p class="copy">{{ daily.articleSummary }}</p></article>
      <article class="card"><p class="eyebrow">보충 해설</p><h3>기사에서 한 걸음 더 보기</h3><p class="copy">{{ daily.supplement }}</p><div class="concepts"><div v-for="concept in daily.concepts" :key="concept.title"><strong>{{ concept.title }}</strong><span>{{ concept.explanation }}</span></div></div></article>
      <section class="actions"><div><p class="eyebrow">복습 학습</p><h3>{{ daily.reviewTopic }} · {{ daily.recommendedReviewQuestionCount }}문제</h3><p>보충 해설의 핵심 개념 수와 중요도에 따라 문제 수를 정했습니다. 기존 서버 채점·풀이 기록·오답 복습 흐름으로 저장됩니다.</p><button class="primary" :disabled="quizLoading || daily.status === 'COMPLETED'" @click="start('review')">{{ quizLoading ? '문제 준비 중…' : daily.status === 'COMPLETED' ? '오늘 복습 완료' : daily.reviewQuizId ? '복습 문제 다시 열기' : '복습 문제 시작' }}</button></div>
        <form @submit.prevent="start('advanced')"><p class="eyebrow">선택 심화 학습</p><label>더 깊게 볼 주제<input v-model="advancedTopic" maxlength="255" placeholder="예: AI 보안 자동화" /></label><label>문제 수<input v-model.number="advancedQuestions" type="number" min="1" max="20" /></label><button class="secondary" :disabled="quizLoading || !advancedTopic.trim()">심화 문제 생성</button><small>심화 퀴즈 {{ daily.advancedQuizCount }}회 생성됨 · 기사와 보충 해설을 근거로 출제합니다.</small></form></section>
    </template>
  </section>
</template>

<style scoped>
.daily-learning-view{padding:30px 38px 42px}.heading{display:flex;justify-content:space-between;gap:20px;margin-bottom:24px}.eyebrow{margin:0 0 5px;color:var(--accent);font-size:.76rem;font-weight:800;letter-spacing:.09em;text-transform:uppercase}h2{margin:0;font-size:1.7rem}h3{margin:0 0 8px;font-size:1.12rem}.heading p:not(.eyebrow){margin:7px 0 0;color:var(--muted)}.status{padding:6px 10px;border-radius:99px;background:var(--accent-soft);color:var(--accent-strong);font-size:.82rem;font-weight:800;white-space:nowrap}.status.completed{background:var(--success-soft);color:var(--success)}.card,.actions,.empty{padding:22px;border:1px solid var(--line);border-radius:12px;background:var(--surface);margin-top:16px}.card a{color:var(--accent-strong);font-weight:700;font-size:.9rem}.copy{white-space:pre-line;color:var(--ink-soft);line-height:1.8}.concepts{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:10px}.concepts div{padding:12px;background:var(--surface-subtle);border-radius:8px}.concepts strong,.concepts span{display:block}.concepts span{margin-top:4px;color:var(--muted);font-size:.9rem}.actions{display:grid;grid-template-columns:1.2fr .8fr;gap:24px}.actions p:not(.eyebrow){color:var(--muted)}form{display:grid;gap:10px;align-content:start;padding-left:24px;border-left:1px solid var(--line)}label{display:grid;gap:4px;font-size:.86rem;font-weight:700}input{width:100%;padding:9px;border:1px solid var(--line-strong);border-radius:7px;font:inherit}small{color:var(--muted)}button{padding:10px 14px;border-radius:8px;font:inherit;font-weight:800;cursor:pointer}.primary{border:1px solid var(--ink);color:#fff;background:var(--ink)}.secondary{border:1px solid var(--line-strong);background:var(--surface);color:var(--ink)}button:disabled{opacity:.55;cursor:not-allowed}.error{padding:12px;border-radius:8px;background:var(--danger-soft);color:var(--danger)}@media(max-width:680px){.daily-learning-view{padding:22px 18px}.heading,.actions{display:block}.status{display:inline-block;margin-top:12px}form{margin-top:20px;padding:20px 0 0;border-left:0;border-top:1px solid var(--line)}}
</style>
