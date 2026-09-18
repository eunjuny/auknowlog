<script setup>
import { computed, onMounted, ref } from 'vue';
import axios from 'axios';

const loading = ref(false);
const error = ref(null);
const queue = ref({ dueCount: 0, upcomingCount: 0, reviews: [] });
const selectedAnswers = ref({});
const submitting = ref({});
const submissionIds = ref({});
const results = ref({});

const dueReviews = computed(() => queue.value.reviews.filter((review) => review.due && !results.value[review.reviewScheduleId]));
const completedReviews = computed(() => queue.value.reviews.filter((review) => results.value[review.reviewScheduleId]));
const upcomingReviews = computed(() => queue.value.reviews.filter((review) => !review.due && !results.value[review.reviewScheduleId]));

async function loadQueue() {
  loading.value = true;
  error.value = null;
  try {
    const response = await axios.get('/api/reviews');
    queue.value = response.data;
    selectedAnswers.value = {};
    submitting.value = {};
    submissionIds.value = {};
    results.value = {};
  } catch (err) {
    error.value = '복습 목록을 불러오지 못했습니다: ' + (err.response?.data?.message || err.message);
  } finally {
    loading.value = false;
  }
}

async function submitReview(review) {
  const scheduleId = review.reviewScheduleId;
  const selectedAnswer = selectedAnswers.value[scheduleId];
  if (!selectedAnswer || submitting.value[scheduleId]) {
    return;
  }

  // 응답이 유실돼 다시 눌러도 서버가 같은 채점을 한 번만 반영하도록 UUID를 재사용한다.
  submissionIds.value[scheduleId] ||= crypto.randomUUID();
  submitting.value[scheduleId] = true;
  error.value = null;
  try {
    const response = await axios.post(`/api/reviews/${scheduleId}/answer`, {
      submissionId: submissionIds.value[scheduleId],
      selectedAnswer
    });
    results.value[scheduleId] = response.data;
    queue.value.dueCount = Math.max(0, queue.value.dueCount - 1);
    if (response.data.status === 'PENDING') {
      queue.value.upcomingCount += 1;
    }
  } catch (err) {
    error.value = '복습 답안을 채점하지 못했습니다: ' + (err.response?.data?.message || err.message);
  } finally {
    submitting.value[scheduleId] = false;
  }
}

function formatDateTime(value) {
  if (!value) return '복습 완료';
  return new Intl.DateTimeFormat('ko-KR', {
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value));
}

function intervalText(result) {
  if (result.status === 'COMPLETED') return '이 문항의 간격 반복을 완료했습니다.';
  return `${formatDateTime(result.nextReviewAt)}에 다시 복습합니다. (간격 ${result.intervalDays}일)`;
}

onMounted(loadQueue);
</script>

<template>
  <section class="review-page">
    <div class="review-hero">
      <div>
        <p class="eyebrow">SPACED REPETITION</p>
        <h2>오늘의 복습</h2>
        <p>오답과 직접 선택한 문항을 다시 풀고, 기억 상태에 맞춰 다음 복습일을 조정합니다.</p>
      </div>
      <button type="button" class="refresh-button" :disabled="loading" @click="loadQueue">
        {{ loading ? '불러오는 중...' : '목록 새로고침' }}
      </button>
    </div>

    <div class="review-stats" aria-label="복습 현황">
      <article>
        <strong>{{ queue.dueCount }}</strong>
        <span>오늘 복습</span>
      </article>
      <article>
        <strong>{{ queue.upcomingCount }}</strong>
        <span>예정된 복습</span>
      </article>
    </div>

    <p v-if="error" class="error-message" role="alert">{{ error }}</p>
    <p v-if="loading && !queue.reviews.length" class="empty-message">복습 목록을 불러오고 있습니다.</p>

    <section v-if="dueReviews.length" class="review-section">
      <div class="section-heading">
        <h3>지금 풀 문제</h3>
        <span>{{ dueReviews.length }}개</span>
      </div>
      <article v-for="review in dueReviews" :key="review.reviewScheduleId" class="review-card due-card">
        <div class="card-meta">
          <span class="topic-chip">{{ review.topic }}</span>
          <span v-if="review.lapseCount" class="lapse-chip">반복 오답 {{ review.lapseCount }}회</span>
        </div>
        <h4>{{ review.questionText }}</h4>
        <div class="review-options" role="radiogroup" :aria-label="`${review.topic} 복습 답안`">
          <label v-for="(option, index) in review.options" :key="option" :class="{ selected: selectedAnswers[review.reviewScheduleId] === option }">
            <input v-model="selectedAnswers[review.reviewScheduleId]" type="radio" :name="`review-${review.reviewScheduleId}`" :value="option" />
            <span>{{ String.fromCharCode(65 + index) }}. {{ option }}</span>
          </label>
        </div>
        <button
          type="button"
          class="submit-review-button"
          :disabled="!selectedAnswers[review.reviewScheduleId] || submitting[review.reviewScheduleId]"
          @click="submitReview(review)"
        >
          {{ submitting[review.reviewScheduleId] ? '서버에서 채점 중...' : '복습 답안 제출' }}
        </button>
      </article>
    </section>

    <section v-if="completedReviews.length" class="review-section">
      <div class="section-heading">
        <h3>이번 세션 결과</h3>
        <span>{{ completedReviews.length }}개</span>
      </div>
      <article v-for="review in completedReviews" :key="`result-${review.reviewScheduleId}`" class="review-card result-card">
        <div class="result-title" :class="results[review.reviewScheduleId].correct ? 'correct' : 'wrong'">
          <strong>{{ results[review.reviewScheduleId].correct ? '정답입니다' : '다시 확인해보세요' }}</strong>
          <span>{{ review.topic }}</span>
        </div>
        <h4>{{ review.questionText }}</h4>
        <p><strong>내 답:</strong> {{ results[review.reviewScheduleId].selectedAnswer }}</p>
        <p><strong>정답:</strong> {{ results[review.reviewScheduleId].correctAnswer }}</p>
        <p><strong>해설:</strong> {{ results[review.reviewScheduleId].explanation }}</p>
        <ul v-if="results[review.reviewScheduleId].optionExplanations?.length" class="option-explanation-list">
          <li v-for="(option, optionIndex) in review.options" :key="`${option}-explanation`" :class="option === results[review.reviewScheduleId].correctAnswer ? 'correct-option' : 'wrong-option'">
            <strong>{{ String.fromCharCode(65 + optionIndex) }}. {{ option }}</strong>
            <span>{{ results[review.reviewScheduleId].optionExplanations[optionIndex] || '이 보기에 대한 추가 해설이 없습니다.' }}</span>
          </li>
        </ul>
        <p class="next-review">{{ intervalText(results[review.reviewScheduleId]) }}</p>
      </article>
    </section>

    <section v-if="upcomingReviews.length" class="review-section upcoming-section">
      <div class="section-heading">
        <h3>예정된 복습</h3>
        <span>{{ upcomingReviews.length }}개</span>
      </div>
      <article v-for="review in upcomingReviews" :key="`upcoming-${review.reviewScheduleId}`" class="upcoming-card">
        <div>
          <span class="topic-chip">{{ review.topic }}</span>
          <h4>{{ review.questionText }}</h4>
        </div>
        <div class="upcoming-meta">
          <strong>{{ formatDateTime(review.nextReviewAt) }}</strong>
          <span>간격 {{ review.intervalDays }}일 · 정답 반복 {{ review.repetitionCount }}회 · 오답 {{ review.lapseCount }}회</span>
        </div>
      </article>
    </section>

    <div v-if="!loading && !queue.reviews.length" class="empty-state">
      <strong>예정된 복습이 없습니다.</strong>
      <p>오답은 자동으로, 맞힌 문항은 채점 화면의 ‘이 문제도 복습하기’ 버튼으로 추가할 수 있습니다.</p>
    </div>
  </section>
</template>

<style scoped>
.review-page {
  min-height: 420px;
  padding: 38px 50px 56px;
  background: var(--surface);
  color: var(--ink);
}

.review-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.eyebrow { margin: 0 0 5px; color: var(--accent); font-size: 12px; font-weight: 800; letter-spacing: .1em; }
.review-hero h2 { margin: 0; font-size: 28px; }
.review-hero p:not(.eyebrow) { max-width: 680px; margin: 8px 0 0; color: var(--muted); }
.refresh-button { width: auto; flex: 0 0 auto; padding: 10px 14px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); color: var(--ink); font: inherit; font-weight: 700; cursor: pointer; }
.refresh-button:hover:not(:disabled) { border-color: var(--ink); background: var(--surface-subtle); }

.review-stats { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin: 28px 0; }
.review-stats article { display: flex; flex-direction: column; padding: 18px 20px; border: 1px solid var(--line); border-radius: 12px; background: var(--surface-subtle); }
.review-stats strong { color: var(--ink); font-size: 28px; }
.review-stats span { color: var(--muted); font-size: 14px; }

.review-section { margin-top: 30px; }
.section-heading { display: flex; align-items: center; gap: 9px; margin-bottom: 13px; }
.section-heading h3 { margin: 0; font-size: 19px; }
.section-heading span { padding: 2px 8px; border-radius: 99px; background: var(--accent-soft); color: var(--accent); font-size: 12px; font-weight: 800; }

.review-card { margin-bottom: 16px; padding: 22px; border: 1px solid var(--line); border-radius: 12px; background: var(--surface); }
.due-card { border-left: 4px solid var(--accent); }
.review-card h4, .upcoming-card h4 { margin: 12px 0 16px; color: var(--ink-soft); font-size: 17px; }
.card-meta, .result-title { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.topic-chip, .lapse-chip { display: inline-block; padding: 4px 9px; border-radius: 99px; background: var(--accent-soft); color: var(--accent); font-size: 12px; font-weight: 800; }
.lapse-chip { background: #fff0ef; color: #b33d38; }

.review-options { display: grid; gap: 8px; }
.review-options label { display: flex; gap: 10px; align-items: center; padding: 12px 14px; border: 1px solid var(--line); border-radius: 8px; cursor: pointer; }
.review-options label:hover, .review-options label.selected { border-color: var(--ink); background: var(--surface-subtle); }
.review-options input { accent-color: var(--ink); }
.submit-review-button { width: 100%; margin-top: 14px; padding: 12px; border: 0; border-radius: 8px; background: var(--ink); color: #fff; font: inherit; font-weight: 800; cursor: pointer; }
.submit-review-button:hover:not(:disabled) { background: var(--ink-soft); }
.submit-review-button:disabled, .refresh-button:disabled { opacity: .55; cursor: not-allowed; }

.result-card p { margin: 7px 0; color: var(--ink-soft); }
.option-explanation-list { display: grid; gap: 7px; margin: 13px 0 0; padding: 0; list-style: none; }
.option-explanation-list li { display: grid; gap: 3px; padding: 10px 12px; color: var(--muted); border: 1px solid var(--line); border-radius: 8px; font-size: .86rem; }
.option-explanation-list li strong { color: var(--ink-soft); }
.option-explanation-list .correct-option { border-color: #9ad5b5; background: #effaf3; }
.result-title strong { font-size: 17px; }
.result-title.correct strong { color: #24744d; }
.result-title.wrong strong { color: #be3c35; }
.result-title span { color: var(--muted); font-size: 13px; }
.next-review { margin-top: 15px !important; padding: 11px 13px; border-radius: 8px; background: var(--accent-soft); color: var(--accent-strong) !important; font-weight: 700; }

.upcoming-card { display: flex; justify-content: space-between; gap: 24px; padding: 17px 18px; border-bottom: 1px solid var(--line); }
.upcoming-card h4 { margin: 8px 0 0; }
.upcoming-meta { display: flex; min-width: 250px; flex-direction: column; align-items: flex-end; justify-content: center; text-align: right; }
.upcoming-meta strong { color: var(--accent); font-size: 14px; }
.upcoming-meta span { color: var(--muted); font-size: 12px; }
.empty-state, .empty-message { padding: 34px 20px; border: 1px dashed var(--line-strong); border-radius: 12px; text-align: center; color: var(--muted); }
.empty-state strong { color: var(--ink-soft); font-size: 18px; }
.empty-state p { margin-bottom: 0; }
.error-message { padding: 13px; border: 1px solid #f1b8b4; border-radius: 8px; background: #fff1f0; color: #b5322d; }

@media (max-width: 680px) {
  .review-page { padding: 28px 18px 40px; border-radius: 0; }
  .review-hero, .upcoming-card { flex-direction: column; }
  .refresh-button { width: 100%; }
  .review-stats { grid-template-columns: 1fr 1fr; }
  .review-card { padding: 17px; }
  .upcoming-card { gap: 8px; padding: 16px 4px; }
  .upcoming-meta { min-width: 0; align-items: flex-start; text-align: left; }
}
</style>
