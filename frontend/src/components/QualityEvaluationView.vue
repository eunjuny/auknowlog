<script setup>
import { computed, onMounted, ref } from 'vue'
import axios from 'axios'

const summary = ref(null)
const reviews = ref({ duplicateCases: [], objectiveCases: [] })
const roadmapSteps = ref([])
const datasets = ref([])
const loading = ref(false)
const runningDuplicates = ref(false)
const runningObjectives = ref(false)
const creatingDataset = ref(false)
const embeddingDatasetId = ref(null)
const error = ref('')
const message = ref('')
const maxPairs = ref(100)
const selectedRoadmapStepId = ref('')

const humanSampleCount = computed(() => summary.value?.duplicate?.reviewedPairs ?? 0)
const metricsWithSamples = computed(() =>
  (summary.value?.duplicate?.thresholdMetrics ?? []).filter((metric) => metric.sampleSize > 0)
)
const visibleThresholdMetrics = computed(() => {
  if (!metricsWithSamples.value.length) return []
  const recommended = summary.value?.duplicate?.recommendedThreshold
  return [...metricsWithSamples.value]
    .sort((left, right) => {
      if (recommended != null) {
        const leftDistance = Math.abs(left.threshold - recommended)
        const rightDistance = Math.abs(right.threshold - recommended)
        if (leftDistance !== rightDistance) return leftDistance - rightDistance
      }
      return right.f1Percent - left.f1Percent
    })
    .slice(0, 8)
    .sort((left, right) => left.threshold - right.threshold)
})
const standardDataset = computed(() => datasets.value.find((dataset) => dataset.datasetKey === 'backend-korean-v1'))
const standardDatasetAtNinety = computed(() =>
  standardDataset.value?.thresholdMetrics?.find((metric) => metric.threshold === 0.9) ?? null
)

function percent(value) {
  return value == null ? '표본 부족' : `${value}%`
}

function confidence(value) {
  return `${Math.round((value ?? 0) * 100)}%`
}

function label(value) {
  return {
    DUPLICATE: '같은 문제',
    RELATED: '유사하지만 다른 내용',
    DISTINCT: '다른 문제',
    COVERED: '목표 포함',
    PARTIAL: '일부만 일치',
    MISSING: '필수 목표 누락',
    ALIGNED: '목표와 일치',
    MISALIGNED: '목표와 불일치',
    REVIEW_REQUIRED: '검토 필요',
    AUTO_ACCEPTED: '자동 판정'
  }[value] ?? value ?? '-'
}

function runType(value) {
  return value === 'DUPLICATE_THRESHOLD' ? '중복 임계값' : '목표·문항 품질'
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const [summaryResponse, reviewResponse, stepResponse, datasetResponse] = await Promise.all([
      axios.get('/api/quality-evaluations/summary'),
      axios.get('/api/quality-evaluations/reviews', { params: { limit: 30 } }),
      axios.get('/api/quality-evaluations/roadmap-steps'),
      axios.get('/api/quality-evaluations/datasets')
    ])
    summary.value = summaryResponse.data
    reviews.value = reviewResponse.data
    roadmapSteps.value = stepResponse.data
    datasets.value = datasetResponse.data
    if (!selectedRoadmapStepId.value && roadmapSteps.value.length) {
      selectedRoadmapStepId.value = String(roadmapSteps.value[0].roadmapStepId)
    }
  } catch (requestError) {
    error.value = requestError.response?.data?.message || '품질 평가 데이터를 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
}

async function createStandardDataset() {
  creatingDataset.value = true
  error.value = ''
  message.value = ''
  try {
    const response = await axios.post('/api/quality-evaluations/datasets/standard')
    message.value = `${response.data.totalSamples}개 기준 문제 쌍을 별도 데이터셋에 저장했습니다. 아직 OpenAI 호출은 하지 않았습니다.`
    await loadAll()
  } catch (requestError) {
    error.value = requestError.response?.data?.message || '중복 평가 데이터셋을 만들지 못했습니다.'
  } finally {
    creatingDataset.value = false
  }
}

async function embedStandardDataset() {
  const dataset = standardDataset.value
  if (!dataset) return
  const accepted = window.confirm(
    `${dataset.totalSamples}개 문제 쌍의 문장을 임베딩해 실제 pgvector 유사도를 계산합니다. 이 작업은 OpenAI 임베딩 API 비용이 발생할 수 있으며, 학습 문제 이력에는 저장하지 않습니다. 계속할까요?`
  )
  if (!accepted) return

  embeddingDatasetId.value = dataset.datasetId
  error.value = ''
  message.value = ''
  try {
    const response = await axios.post(`/api/quality-evaluations/datasets/${dataset.datasetId}/embeddings`, {
      confirmCost: true
    })
    message.value = `${response.data.embeddedSamples}개 표본의 실제 유사도를 계산했습니다. 임베딩 입력 토큰: ${response.data.embeddingInputTokens ?? 0}`
    await loadAll()
  } catch (requestError) {
    error.value = requestError.response?.data?.message || '표본 임베딩 생성에 실패했습니다.'
  } finally {
    embeddingDatasetId.value = null
  }
}

async function runDuplicateEvaluation() {
  runningDuplicates.value = true
  error.value = ''
  message.value = ''
  try {
    const response = await axios.post('/api/quality-evaluations/duplicate-runs', {
      maxPairs: Number(maxPairs.value)
    })
    message.value = `저장된 임베딩으로 ${response.data.candidateCount}개 문제 쌍을 평가했습니다. OpenAI 호출과 토큰 사용은 없습니다.`
    await loadAll()
  } catch (requestError) {
    error.value = requestError.response?.data?.message || '중복 후보 평가에 실패했습니다.'
  } finally {
    runningDuplicates.value = false
  }
}

async function runObjectiveEvaluation() {
  if (!selectedRoadmapStepId.value) return
  const selected = roadmapSteps.value.find(
    (step) => String(step.roadmapStepId) === String(selectedRoadmapStepId.value)
  )
  const accepted = window.confirm(
    `${selected?.stepTitle ?? '선택한 학습 단위'}의 목표와 최대 30문항을 AI로 평가합니다. 실제 OpenAI 토큰이 사용됩니다. 계속할까요?`
  )
  if (!accepted) return

  runningObjectives.value = true
  error.value = ''
  message.value = ''
  try {
    const response = await axios.post('/api/quality-evaluations/objective-runs', {
      roadmapStepId: Number(selectedRoadmapStepId.value)
    })
    message.value = `목표·문항 ${response.data.candidateCount}건을 평가했고, ${response.data.reviewRequiredCount}건을 검토함에 넣었습니다. 사용 토큰: ${response.data.totalTokens ?? 0}`
    await loadAll()
  } catch (requestError) {
    error.value = requestError.response?.data?.message || 'AI 목표 품질 평가에 실패했습니다.'
  } finally {
    runningObjectives.value = false
  }
}

async function reviewDuplicate(pairId, verdict) {
  await submitReview(`/api/quality-evaluations/duplicate-pairs/${pairId}/review`, verdict)
}

async function reviewObjective(caseId, verdict) {
  await submitReview(`/api/quality-evaluations/objective-cases/${caseId}/review`, verdict)
}

async function submitReview(url, verdict) {
  error.value = ''
  try {
    await axios.put(url, { verdict })
    message.value = '판정을 저장했습니다. 사람 검증 지표에 즉시 반영했습니다.'
    await loadAll()
  } catch (requestError) {
    error.value = requestError.response?.data?.message || '검토 결과를 저장하지 못했습니다.'
  }
}

onMounted(loadAll)
</script>

<template>
  <section class="quality-page" aria-labelledby="quality-title">
    <header class="quality-heading">
      <div>
        <p class="eyebrow">HUMAN-IN-THE-LOOP EVALUATION</p>
        <h2 id="quality-title">AI 품질 평가</h2>
        <p>자동 평가 결과와 사람이 확인한 근거를 분리해 저장하고, 검증된 데이터만 운영 기준 후보로 사용합니다.</p>
      </div>
      <button type="button" class="secondary-button" :disabled="loading" @click="loadAll">
        {{ loading ? '불러오는 중...' : '새로고침' }}
      </button>
    </header>

    <p v-if="error" class="notice error" role="alert">{{ error }}</p>
    <p v-if="message" class="notice success" role="status">{{ message }}</p>

    <template v-if="summary">
      <section class="quality-section dataset-section">
        <div class="section-title">
          <div>
            <p class="section-kicker">CURATED REFERENCE DATASET</p>
            <h3>중복 평가 기준 데이터셋</h3>
            <p>학습 이력과 분리된 백엔드 핵심 개념 문제 쌍입니다. 같은 문제·관련 문제·다른 문제를 균형 있게 담아 임계값을 검증합니다.</p>
          </div>
          <div class="dataset-actions">
            <button v-if="!standardDataset" type="button" :disabled="creatingDataset" @click="createStandardDataset">
              {{ creatingDataset ? '표본 만드는 중...' : '기준 표본 만들기' }}
            </button>
            <button v-else type="button" :disabled="embeddingDatasetId === standardDataset.datasetId || standardDataset.status === 'READY'" @click="embedStandardDataset">
              {{ standardDataset.status === 'READY' ? '임베딩 완료' : embeddingDatasetId === standardDataset.datasetId ? '임베딩 계산 중...' : '실제 유사도 계산' }}
            </button>
          </div>
        </div>

        <div v-if="standardDataset" class="dataset-summary">
          <div class="metric-grid dataset-metrics">
            <article><span>기준 문제 쌍</span><strong>{{ standardDataset.totalSamples }}</strong></article>
            <article><span>실제 유사도 계산</span><strong>{{ standardDataset.embeddedSamples }}/{{ standardDataset.totalSamples }}</strong></article>
            <article><span>참조 라벨</span><strong>{{ standardDataset.duplicateSamples }}/{{ standardDataset.relatedSamples }}/{{ standardDataset.distinctSamples }}</strong><small>같은·관련·다른 문제</small></article>
            <article class="accent"><span>0.90 기준 F1</span><strong>{{ standardDataset.embeddedSamples ? percent(standardDatasetAtNinety?.f1Percent) : '임베딩 필요' }}</strong></article>
          </div>
          <p v-if="standardDataset.status !== 'READY'" class="cost-warning">표본 문장은 저장됐지만 아직 벡터가 없습니다. <b>실제 유사도 계산</b> 버튼을 누르면 OpenAI 임베딩 API를 호출합니다.</p>
          <div v-else class="evidence-card dataset-evidence">
            <div class="card-heading"><div><h4>참조 라벨 기준 임계값 결과</h4><p>초기 참조 라벨 {{ standardDataset.embeddedSamples }}개와 실제 pgvector 코사인 유사도를 비교한 결과입니다.</p></div></div>
            <div class="table-wrap"><table>
              <thead><tr><th>임계값</th><th>정밀도</th><th>재현율</th><th>F1</th><th>TP</th><th>FP</th><th>FN</th><th>TN</th></tr></thead>
              <tbody><tr v-for="metric in standardDataset.thresholdMetrics" :key="metric.threshold" :class="{ recommended: metric.threshold === 0.9 }">
                <td>{{ metric.threshold }}</td><td>{{ percent(metric.precisionPercent) }}</td><td>{{ percent(metric.recallPercent) }}</td><td>{{ percent(metric.f1Percent) }}</td><td>{{ metric.truePositive }}</td><td>{{ metric.falsePositive }}</td><td>{{ metric.falseNegative }}</td><td>{{ metric.trueNegative }}</td>
              </tr></tbody>
            </table></div>
          </div>
        </div>
        <div v-else class="empty-state">기준 표본 만들기를 누르면 42개 문제 쌍과 참조 라벨을 별도 데이터셋에 저장합니다. API 비용은 발생하지 않습니다.</div>
      </section>

      <section class="quality-section">
        <div class="section-title">
          <div>
            <p class="section-kicker">SEMANTIC DUPLICATE</p>
            <h3>pgvector 중복 임계값 평가</h3>
            <p>기존에 저장된 512차원 벡터만 조회하므로 이 평가에는 AI 비용이 들지 않습니다.</p>
          </div>
          <form class="run-controls" @submit.prevent="runDuplicateEvaluation">
            <label>최대 후보 쌍
              <input v-model.number="maxPairs" type="number" min="10" max="500" />
            </label>
            <button type="submit" :disabled="runningDuplicates">
              {{ runningDuplicates ? '평가 중...' : '중복 후보 수집' }}
            </button>
          </form>
        </div>

        <div class="metric-grid">
          <article><span>수집된 문제 쌍</span><strong>{{ summary.duplicate.totalPairs }}</strong></article>
          <article><span>사람 검증 표본</span><strong>{{ summary.duplicate.reviewedPairs }}</strong></article>
          <article><span>검토 대기</span><strong>{{ summary.duplicate.pendingPairs }}</strong></article>
          <article class="accent"><span>추천 임계값</span><strong>{{ summary.duplicate.recommendedThreshold ?? '표본 부족' }}</strong></article>
        </div>

        <div class="policy-strip">
          <span><b>{{ summary.duplicate.automaticDistinctCeiling }} 미만</b> 자동 비중복</span>
          <span><b>{{ summary.duplicate.automaticDistinctCeiling }}~{{ summary.duplicate.automaticDuplicateFloor }}</b> 사람 검토</span>
          <span><b>{{ summary.duplicate.automaticDuplicateFloor }} 이상</b> 자동 중복</span>
        </div>

        <div class="evidence-card">
          <div class="card-heading">
            <div><h4>임계값별 검증 결과</h4><p>사람이 판정한 {{ humanSampleCount }}개 문제 쌍만 계산에 사용합니다.</p></div>
          </div>
          <div v-if="visibleThresholdMetrics.length" class="table-wrap">
            <table>
              <thead><tr><th>임계값</th><th>정밀도</th><th>재현율</th><th>F1</th><th>TP</th><th>FP</th><th>FN</th><th>TN</th></tr></thead>
              <tbody>
                <tr v-for="metric in visibleThresholdMetrics" :key="metric.threshold" :class="{ recommended: metric.threshold === summary.duplicate.recommendedThreshold }">
                  <td>{{ metric.threshold }}</td><td>{{ percent(metric.precisionPercent) }}</td><td>{{ percent(metric.recallPercent) }}</td>
                  <td>{{ percent(metric.f1Percent) }}</td><td>{{ metric.truePositive }}</td><td>{{ metric.falsePositive }}</td>
                  <td>{{ metric.falseNegative }}</td><td>{{ metric.trueNegative }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else class="empty-state">애매한 문제 쌍을 검토하면 정밀도·재현율 근거가 표시됩니다.</div>
        </div>
      </section>

      <section class="quality-section objective-section">
        <div class="section-title">
          <div>
            <p class="section-kicker">OBJECTIVE COVERAGE</p>
            <h3>필수 목표 누락과 문항 일치도</h3>
            <p>AI가 기준 목표 초안과 문항 일치도를 평가하고, 낮은 확신과 부정 판정만 검토함에 보냅니다.</p>
          </div>
          <form class="run-controls objective-controls" @submit.prevent="runObjectiveEvaluation">
            <label>평가할 학습 단위
              <select v-model="selectedRoadmapStepId" :disabled="!roadmapSteps.length">
                <option value="" disabled>학습 단위를 선택하세요</option>
                <option v-for="step in roadmapSteps" :key="step.roadmapStepId" :value="String(step.roadmapStepId)">
                  {{ step.roadmapTitle }} · {{ step.stepTitle }} (목표 {{ step.objectiveCount }}, 문항 {{ step.questionCount }})
                </option>
              </select>
            </label>
            <button type="submit" :disabled="runningObjectives || !selectedRoadmapStepId">
              {{ runningObjectives ? 'AI 평가 중...' : 'AI 품질 평가 실행' }}
            </button>
          </form>
        </div>

        <p class="cost-warning">이 버튼만 실제 OpenAI API를 호출합니다. 선택한 소주제의 목표와 최근 문항 최대 30개를 한 번만 평가하며, 실행 전 확인창을 표시합니다.</p>
        <div class="metric-grid objective-metrics">
          <article><span>AI 평가 항목</span><strong>{{ summary.objective.totalCases }}</strong></article>
          <article><span>자동 확정</span><strong>{{ summary.objective.automaticAcceptedCases }}</strong></article>
          <article><span>검토 대기</span><strong>{{ summary.objective.pendingCases }}</strong></article>
          <article><span>AI 잠정 누락률</span><strong>{{ percent(summary.objective.provisionalOmissionRatePercent) }}</strong></article>
          <article><span>사람 검증 누락률</span><strong>{{ percent(summary.objective.humanVerifiedOmissionRatePercent) }}</strong></article>
          <article class="accent"><span>사람 검증 문항 일치도</span><strong>{{ percent(summary.objective.humanVerifiedAlignmentRatePercent) }}</strong></article>
        </div>
      </section>

      <section class="quality-section review-section">
        <div class="section-title compact">
          <div><p class="section-kicker">REVIEW QUEUE</p><h3>판단이 필요한 항목</h3><p>여기부터는 사람이 최종 의미를 판단합니다. 자동 정책 변경은 하지 않습니다.</p></div>
        </div>

        <div class="review-list">
          <article v-for="item in reviews.duplicateCases" :key="`duplicate-${item.pairId}`" class="review-card">
            <div class="review-meta"><span>중복 문제 검토</span><b>유사도 {{ item.similarity.toFixed(3) }}</b></div>
            <div class="question-pair">
              <div><small>{{ item.questionATopic }}</small><p>{{ item.questionAText }}</p></div>
              <div><small>{{ item.questionBTopic }}</small><p>{{ item.questionBText }}</p></div>
            </div>
            <div class="review-actions">
              <button @click="reviewDuplicate(item.pairId, 'DUPLICATE')">같은 문제</button>
              <button @click="reviewDuplicate(item.pairId, 'RELATED')">유사하지만 다름</button>
              <button @click="reviewDuplicate(item.pairId, 'DISTINCT')">다른 문제</button>
              <button class="ghost" @click="reviewDuplicate(item.pairId, 'SKIPPED')">보류 제외</button>
            </div>
          </article>

          <article v-for="item in reviews.objectiveCases" :key="`objective-${item.caseId}`" class="review-card">
            <div class="review-meta"><span>{{ item.caseType === 'OBJECTIVE_COVERAGE' ? '필수 목표 검토' : '문항-목표 검토' }}</span><b>AI 확신 {{ confidence(item.aiConfidence) }}</b></div>
            <h4>{{ item.referenceTitle || item.learningObjectiveTitle || '연결되지 않은 목표' }}</h4>
            <p v-if="item.learningQuestionText" class="review-question">{{ item.learningQuestionText }}</p>
            <p class="rationale"><b>AI 판정: {{ label(item.aiVerdict) }}</b> · {{ item.aiRationale }}</p>
            <div v-if="item.caseType === 'OBJECTIVE_COVERAGE'" class="review-actions">
              <button @click="reviewObjective(item.caseId, 'COVERED')">포함됨</button>
              <button @click="reviewObjective(item.caseId, 'PARTIAL')">일부 포함</button>
              <button @click="reviewObjective(item.caseId, 'MISSING')">누락됨</button>
              <button class="ghost" @click="reviewObjective(item.caseId, 'SKIPPED')">보류 제외</button>
            </div>
            <div v-else class="review-actions">
              <button @click="reviewObjective(item.caseId, 'ALIGNED')">목표와 일치</button>
              <button @click="reviewObjective(item.caseId, 'PARTIAL')">일부 일치</button>
              <button @click="reviewObjective(item.caseId, 'MISALIGNED')">불일치</button>
              <button class="ghost" @click="reviewObjective(item.caseId, 'SKIPPED')">보류 제외</button>
            </div>
          </article>

          <div v-if="!reviews.duplicateCases.length && !reviews.objectiveCases.length" class="empty-state">
            현재 사람이 확인할 애매한 항목이 없습니다.
          </div>
        </div>
      </section>

      <section class="quality-section history-section">
        <div class="section-title compact"><div><p class="section-kicker">REPRODUCIBILITY</p><h3>최근 평가 실행</h3></div></div>
        <div class="table-wrap">
          <table>
            <thead><tr><th>유형</th><th>대상</th><th>모델·방식</th><th>후보</th><th>검토 필요</th><th>토큰</th><th>상태</th></tr></thead>
            <tbody>
              <tr v-for="run in summary.recentRuns" :key="run.id">
                <td>{{ runType(run.evaluationType) }}</td><td>{{ run.sourceKey || '-' }}</td><td>{{ run.model || '-' }}</td>
                <td>{{ run.candidateCount }}</td><td>{{ run.reviewRequiredCount }}</td><td>{{ run.totalTokens ?? 0 }}</td><td>{{ run.status }}</td>
              </tr>
            </tbody>
          </table>
          <div v-if="!summary.recentRuns.length" class="empty-state">아직 저장된 평가 실행이 없습니다.</div>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.quality-page { padding: 38px 40px 56px; background: var(--surface); }
.quality-heading, .section-title { display: flex; justify-content: space-between; gap: 24px; align-items: flex-start; }
.quality-heading { padding-bottom: 28px; border-bottom: 1px solid var(--line); }
.eyebrow, .section-kicker { margin: 0 0 5px; color: var(--accent); font-size: .76rem; font-weight: 900; letter-spacing: .11em; }
h2, h3, h4, p { margin-top: 0; }
h2 { margin-bottom: 8px; font-size: 2rem; }
h3 { margin-bottom: 5px; font-size: 1.35rem; }
h4 { margin-bottom: 8px; }
.quality-heading p, .section-title p { margin-bottom: 0; color: var(--muted); }
.quality-section { padding: 34px 0; border-bottom: 1px solid var(--line); }
.quality-section:last-child { border-bottom: 0; }
.secondary-button, .run-controls button, .review-actions button { border: 1px solid var(--ink); border-radius: 8px; background: var(--ink); color: #fff; font: inherit; font-weight: 800; cursor: pointer; }
.secondary-button { padding: 10px 14px; background: #fff; color: var(--ink); }
button:disabled { opacity: .5; cursor: wait; }
.run-controls { min-width: 310px; display: flex; gap: 10px; align-items: flex-end; }
.run-controls label { flex: 1; color: var(--muted); font-size: .8rem; font-weight: 800; }
.run-controls input, .run-controls select { width: 100%; margin-top: 5px; padding: 10px 11px; border: 1px solid var(--line-strong); border-radius: 8px; background: #fff; font: inherit; color: var(--ink); }
.run-controls button { padding: 11px 15px; white-space: nowrap; }
.objective-controls { min-width: 470px; }
.dataset-actions { display: flex; gap: 10px; }
.dataset-actions button { padding: 11px 15px; border: 1px solid var(--ink); border-radius: 8px; background: var(--ink); color: #fff; font: inherit; font-weight: 800; cursor: pointer; white-space: nowrap; }
.dataset-metrics { grid-template-columns: repeat(4, 1fr); }
.dataset-metrics small { display: block; margin-top: 4px; color: var(--muted); font-size: .75rem; }
.dataset-evidence { margin-top: 18px; }
.metric-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin: 24px 0 18px; }
.metric-grid article { min-height: 96px; padding: 18px; border: 1px solid var(--line); border-radius: 11px; background: var(--surface-subtle); }
.metric-grid span { display: block; color: var(--muted); font-size: .82rem; font-weight: 700; }
.metric-grid strong { display: block; margin-top: 7px; font-size: 1.55rem; }
.metric-grid .accent { background: var(--accent-soft); border-color: #efc8bc; }
.objective-metrics { grid-template-columns: repeat(3, 1fr); }
.policy-strip { display: grid; grid-template-columns: repeat(3, 1fr); margin-bottom: 18px; border: 1px solid var(--line); border-radius: 10px; overflow: hidden; }
.policy-strip span { padding: 12px 14px; color: var(--muted); text-align: center; border-right: 1px solid var(--line); }
.policy-strip span:last-child { border-right: 0; }
.policy-strip b { color: var(--ink); }
.evidence-card, .review-card { border: 1px solid var(--line); border-radius: 12px; background: #fff; }
.evidence-card { overflow: hidden; }
.card-heading { padding: 18px 20px; border-bottom: 1px solid var(--line); }
.card-heading h4, .card-heading p { margin-bottom: 0; }
.card-heading p { color: var(--muted); font-size: .88rem; }
.table-wrap { width: 100%; overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: .88rem; }
th, td { padding: 12px 13px; border-bottom: 1px solid var(--line); text-align: left; white-space: nowrap; }
th { color: var(--muted); background: var(--surface-subtle); font-size: .78rem; }
tr:last-child td { border-bottom: 0; }
tr.recommended td { background: var(--success-soft); font-weight: 800; }
.cost-warning, .notice { margin: 18px 0 0; padding: 13px 15px; border-radius: 9px; font-size: .88rem; }
.cost-warning { color: #6c4814; background: #fff7e7; border: 1px solid #eed9ab; }
.notice.error { color: var(--danger); background: var(--danger-soft); border: 1px solid #efc4bf; }
.notice.success { color: var(--success); background: var(--success-soft); border: 1px solid #bddcc9; }
.section-title.compact { margin-bottom: 18px; }
.review-list { display: grid; gap: 14px; }
.review-card { padding: 19px; }
.review-meta { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 13px; color: var(--accent); font-size: .8rem; font-weight: 800; }
.question-pair { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.question-pair div { padding: 14px; border-radius: 9px; background: var(--surface-subtle); }
.question-pair small { color: var(--muted); font-weight: 800; }
.question-pair p { margin: 5px 0 0; }
.review-question { padding: 13px 14px; background: var(--surface-subtle); border-radius: 8px; }
.rationale { color: var(--muted); }
.rationale b { color: var(--ink); }
.review-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 14px; }
.review-actions button { padding: 8px 11px; font-size: .82rem; }
.review-actions button.ghost { color: var(--muted); background: #fff; border-color: var(--line-strong); }
.empty-state { padding: 28px; color: var(--muted); text-align: center; background: var(--surface-subtle); }
@media (max-width: 860px) {
  .quality-page { padding: 28px 20px 44px; }
  .quality-heading, .section-title { flex-direction: column; }
  .run-controls, .objective-controls { width: 100%; min-width: 0; }
  .metric-grid, .objective-metrics { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 560px) {
  .run-controls { align-items: stretch; flex-direction: column; }
  .metric-grid, .objective-metrics, .policy-strip, .question-pair { grid-template-columns: 1fr; }
  .policy-strip span { border-right: 0; border-bottom: 1px solid var(--line); }
  .policy-strip span:last-child { border-bottom: 0; }
}
</style>
