<script setup>
import { computed, ref } from 'vue'
import axios from 'axios'

const history = ref(null)
const selectedAttempt = ref(null)
const loadingHistory = ref(false)
const loadingDetail = ref(false)
const historyError = ref(null)
const detailError = ref(null)

const hasPreviousPage = computed(() => history.value?.page > 0)
const hasNextPage = computed(() => history.value?.hasNext ?? false)

function formatDate(value) {
  if (!value) return '-'
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(value))
}

function scoreLabel(attempt) {
  if (!attempt?.totalQuestions) return '0%'
  return `${Math.round((attempt.correctAnswers / attempt.totalQuestions) * 100)}%`
}

async function loadHistory(page = 0) {
  loadingHistory.value = true
  historyError.value = null

  try {
    const response = await axios.get('/api/learning-attempts', {
      params: { page, size: 20 }
    })
    history.value = response.data
  } catch (err) {
    historyError.value = err.response?.data?.message || '풀이 기록을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.'
  } finally {
    loadingHistory.value = false
  }
}

async function selectAttempt(attemptId) {
  loadingDetail.value = true
  detailError.value = null

  try {
    const response = await axios.get(`/api/learning-attempts/${attemptId}`)
    selectedAttempt.value = response.data
  } catch (err) {
    detailError.value = err.response?.data?.message || '풀이 상세 결과를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.'
  } finally {
    loadingDetail.value = false
  }
}

loadHistory()
</script>

<template>
  <section class="history-page" aria-labelledby="history-title">
    <div class="history-heading">
      <div>
        <p class="eyebrow">LEARNING HISTORY</p>
        <h2 id="history-title">풀이 기록</h2>
        <p>저장한 퀴즈의 점수와 문항별 답안·해설을 다시 확인할 수 있습니다.</p>
      </div>
      <button type="button" class="refresh-button" :disabled="loadingHistory" @click="loadHistory(history?.page ?? 0)">
        {{ loadingHistory ? '불러오는 중...' : '새로고침' }}
      </button>
    </div>

    <p v-if="historyError" class="message error-message" role="alert">
      {{ historyError }}
    </p>

    <div v-else class="history-layout">
      <aside class="attempt-list-panel" aria-label="풀이 기록 목록">
        <div class="list-summary">
          <strong>총 {{ history?.totalElements ?? 0 }}개</strong>
          <span>최근 풀이부터 표시</span>
        </div>

        <div v-if="loadingHistory" class="empty-state" aria-live="polite">풀이 기록을 불러오는 중입니다.</div>
        <div v-else-if="!history?.attempts?.length" class="empty-state">
          <strong>아직 저장된 풀이 기록이 없습니다.</strong>
          <p>퀴즈를 풀고 <em>풀이 기록 저장</em>을 누르면 이곳에 결과가 쌓입니다.</p>
        </div>
        <ul v-else class="attempt-list">
          <li v-for="attempt in history.attempts" :key="attempt.attemptId">
            <button
              type="button"
              class="attempt-card"
              :class="{ selected: selectedAttempt?.attemptId === attempt.attemptId }"
              :aria-pressed="selectedAttempt?.attemptId === attempt.attemptId"
              @click="selectAttempt(attempt.attemptId)"
            >
              <span class="attempt-topic">{{ attempt.topic }}</span>
              <strong>{{ attempt.quizTitle }}</strong>
              <span class="attempt-meta">{{ formatDate(attempt.submittedAt) }}</span>
              <span class="score-badge">{{ attempt.correctAnswers }}/{{ attempt.totalQuestions }} · {{ scoreLabel(attempt) }}</span>
            </button>
          </li>
        </ul>

        <div v-if="history && history.totalPages > 1" class="pagination" aria-label="풀이 기록 페이지">
          <button type="button" :disabled="!hasPreviousPage || loadingHistory" @click="loadHistory(history.page - 1)">이전</button>
          <span>{{ history.page + 1 }} / {{ history.totalPages }}</span>
          <button type="button" :disabled="!hasNextPage || loadingHistory" @click="loadHistory(history.page + 1)">다음</button>
        </div>
      </aside>

      <section class="attempt-detail-panel" aria-live="polite" aria-label="선택한 풀이 기록 상세">
        <div v-if="loadingDetail" class="empty-state">상세 결과를 불러오는 중입니다.</div>
        <p v-else-if="detailError" class="message error-message" role="alert">{{ detailError }}</p>
        <div v-else-if="!selectedAttempt" class="empty-state detail-empty">
          <strong>기록을 선택해 상세 결과를 확인하세요.</strong>
          <p>문항별 선택 답안, 정답, 해설과 출처를 보여드립니다.</p>
        </div>
        <template v-else>
          <header class="detail-heading">
            <div>
              <p class="eyebrow">{{ selectedAttempt.topic }}</p>
              <h3>{{ selectedAttempt.quizTitle }}</h3>
              <p>{{ formatDate(selectedAttempt.submittedAt) }}</p>
            </div>
            <div class="detail-score">
              <strong>{{ selectedAttempt.correctAnswers }}/{{ selectedAttempt.totalQuestions }}</strong>
              <span>{{ scoreLabel(selectedAttempt) }} 정답</span>
            </div>
          </header>

          <ol class="answer-list">
            <li v-for="question in selectedAttempt.questions" :key="question.questionOrder" class="answer-card" :class="question.correct ? 'correct' : 'incorrect'">
              <div class="answer-status">
                <span>{{ question.correct ? '정답' : '오답' }}</span>
                <strong>{{ question.questionOrder }}번</strong>
              </div>
              <h4>{{ question.questionText }}</h4>
              <p><b>내 답:</b> {{ question.selectedAnswer }}</p>
              <p><b>정답:</b> {{ question.correctAnswer }}</p>
              <p class="explanation"><b>해설:</b> {{ question.explanation }}</p>
              <p v-if="question.sourceReferences?.length" class="reference"><b>근거:</b> {{ question.sourceReferences.join(', ') }}</p>
            </li>
          </ol>
        </template>
      </section>
    </div>
  </section>
</template>

<style scoped>
.history-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 36px 40px 52px;
  background: var(--surface);
}

.history-heading,
.detail-heading,
.list-summary,
.pagination {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
}

.eyebrow {
  margin: 0 0 6px;
  color: var(--accent);
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.08em;
}

h2, h3, h4, p { margin: 0; }
h2 { color: var(--ink); font-size: clamp(1.7rem, 4vw, 2.25rem); }
.history-heading p:not(.eyebrow), .detail-heading p { margin-top: 6px; color: var(--muted); }

button {
  border: 0;
  border-radius: 8px;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}

button:focus-visible { outline: 3px solid rgb(198 78 50 / 24%); outline-offset: 3px; }
button:disabled { cursor: not-allowed; opacity: 0.55; }

.refresh-button {
  padding: 11px 16px;
  color: #fff;
  background: var(--ink);
}
.refresh-button:hover:not(:disabled) { background: var(--ink-soft); }

.history-layout {
  display: grid;
  grid-template-columns: minmax(270px, .75fr) minmax(0, 1.55fr);
  gap: 24px;
  margin-top: 28px;
}

.attempt-list-panel,
.attempt-detail-panel {
  min-width: 0;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface-subtle);
}

.attempt-list-panel { padding: 18px; }
.attempt-detail-panel { padding: 26px; background: var(--surface); }
.list-summary { margin-bottom: 14px; color: var(--ink-soft); }
.list-summary span { color: var(--muted); font-size: .82rem; }

.attempt-list { display: grid; gap: 10px; margin: 0; padding: 0; list-style: none; }
.attempt-card {
  display: grid;
  width: 100%;
  gap: 4px;
  padding: 15px;
  color: var(--ink-soft);
  text-align: left;
  background: var(--surface);
  border: 1px solid var(--line);
}
.attempt-card:hover, .attempt-card.selected { border-color: var(--accent); background: var(--accent-soft); }
.attempt-card strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.attempt-topic { color: var(--accent); font-size: .78rem; font-weight: 800; }
.attempt-meta { color: var(--muted); font-size: .78rem; }
.score-badge { color: var(--ink-soft); font-size: .82rem; font-weight: 700; }

.empty-state {
  display: grid;
  min-height: 160px;
  place-content: center;
  gap: 8px;
  padding: 24px;
  color: var(--muted);
  text-align: center;
}
.empty-state strong { color: var(--ink-soft); }
.empty-state em { color: var(--accent); font-style: normal; font-weight: 700; }
.detail-empty { min-height: 420px; }

.message { margin-top: 20px; padding: 14px 16px; border-radius: 8px; }
.error-message { color: #b42318; background: #fef3f2; border: 1px solid #fecdca; }

.detail-heading { align-items: flex-start; padding-bottom: 20px; border-bottom: 1px solid var(--line); }
.detail-heading h3 { color: var(--ink); font-size: 1.3rem; }
.detail-score { display: grid; min-width: 94px; padding: 12px; color: var(--ink); text-align: center; background: var(--surface-subtle); border: 1px solid var(--line); border-radius: 10px; }
.detail-score strong { font-size: 1.3rem; }
.detail-score span { font-size: .78rem; font-weight: 700; }

.answer-list { display: grid; gap: 14px; margin: 22px 0 0; padding: 0; list-style: none; }
.answer-card { padding: 18px; border: 1px solid var(--line); border-left: 4px solid var(--line-strong); border-radius: 10px; }
.answer-card.correct { border-left-color: var(--success); background: var(--success-soft); }
.answer-card.incorrect { border-left-color: var(--danger); background: var(--danger-soft); }
.answer-status { display: flex; gap: 8px; align-items: center; margin-bottom: 9px; color: var(--muted); font-size: .84rem; }
.answer-status span { padding: 3px 7px; color: #fff; background: var(--muted); border-radius: 999px; font-weight: 800; }
.correct .answer-status span { background: #168358; }
.incorrect .answer-status span { background: #c9473a; }
.answer-card h4 { margin-bottom: 12px; color: var(--ink); font-size: 1rem; }
.answer-card p { margin-top: 7px; color: var(--ink-soft); }
.answer-card b { color: var(--ink); }
.answer-card .explanation { padding-top: 9px; border-top: 1px solid rgb(148 163 184 / 30%); }
.reference { color: #356a48 !important; font-size: .86rem; }

.pagination { margin-top: 16px; }
.pagination button { padding: 8px 11px; color: var(--ink); background: var(--surface); border: 1px solid var(--line); }
.pagination button:hover:not(:disabled) { border-color: var(--ink); background: var(--surface-subtle); }
.pagination span { color: var(--muted); font-size: .84rem; }

@media (max-width: 760px) {
  .history-page { padding: 26px 18px 36px; }
  .history-heading { align-items: flex-start; flex-direction: column; }
  .history-layout { grid-template-columns: minmax(0, 1fr); }
  .detail-heading { gap: 12px; align-items: flex-start; flex-direction: column; }
  .detail-score { min-width: 0; width: 100%; grid-template-columns: auto auto; align-items: center; }
}
</style>
