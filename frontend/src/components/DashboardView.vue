<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import axios from 'axios'
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { SVGRenderer } from 'echarts/renderers'

echarts.use([BarChart, GridComponent, LegendComponent, LineChart, SVGRenderer, TooltipComponent])

const emit = defineEmits(['start-recommended-quiz', 'create-learning-roadmap'])

const dashboard = ref(null)
const loading = ref(false)
const error = ref(null)
const learningChartElement = ref(null)
const topicChartElement = ref(null)
const aiChartElement = ref(null)
const duplicateThresholdChartElement = ref(null)
const objectiveQualityChartElement = ref(null)

let learningChart
let topicChart
let aiChart
let duplicateThresholdChart
let objectiveQualityChart

const hasLearningActivity = computed(() =>
  dashboard.value?.learning?.dailyActivity?.some((item) => item.attempts > 0) ?? false
)
const hasTopicData = computed(() => dashboard.value?.learning?.topicAccuracy?.length > 0)
const hasAiActivity = computed(() =>
  dashboard.value?.ai?.dailyActivity?.some((item) => item.calls > 0) ?? false
)
const hasDuplicateThresholdData = computed(() =>
  dashboard.value?.qualityEvaluation?.duplicateThresholdMetrics?.some((item) =>
    item.precisionPercent != null || item.recallPercent != null || item.f1Percent != null
  ) ?? false
)
const hasObjectiveQualityData = computed(() => {
  const metric = dashboard.value?.qualityEvaluation?.objectiveQuality
  return [
    metric?.provisionalOmissionRatePercent,
    metric?.provisionalAlignmentRatePercent,
    metric?.humanVerifiedOmissionRatePercent,
    metric?.humanVerifiedAlignmentRatePercent
  ].some((value) => value != null)
})

function formatNumber(value) {
  return new Intl.NumberFormat('ko-KR').format(value ?? 0)
}

function formatDuration(value) {
  if (!value) return '-'
  if (value < 1_000) return `${formatNumber(value)}ms`
  return `${(value / 1_000).toFixed(1)}초`
}

function dailyLabel(value) {
  return value?.slice(5).replace('-', '/') ?? ''
}

function accuracy(metric) {
  if (!metric?.totalQuestions) return 0
  return Math.round((metric.correctAnswers / metric.totalQuestions) * 100)
}

function recommendationBadge(priority) {
  return {
    WEAKNESS: '우선 보완',
    REINFORCE: '이해 강화',
    REVIEW: '복습 추천'
  }[priority] ?? '학습 추천'
}

function startRecommendedQuiz(recommendation) {
  emit('start-recommended-quiz', recommendation)
}

function createRoadmapFromRecommendation(recommendation) {
  emit('create-learning-roadmap', recommendation)
}

function chartFor(currentChart, element, option) {
  if (!element) return currentChart
  const chart = currentChart ?? echarts.init(element, null, { renderer: 'svg' })
  chart.setOption(option, true)
  return chart
}

function renderCharts() {
  if (!dashboard.value) return

  const learning = dashboard.value.learning
  const ai = dashboard.value.ai
  const labels = learning.dailyActivity.map((item) => dailyLabel(item.date))

  if (hasLearningActivity.value) {
    learningChart = chartFor(learningChart, learningChartElement.value, {
      color: ['#171717', '#c64e32'],
      grid: { top: 42, right: 20, bottom: 28, left: 42 },
      legend: { top: 4, textStyle: { color: '#303030' } },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: labels, axisLabel: { color: '#6b6b66' }, axisLine: { lineStyle: { color: '#deded8' } } },
      yAxis: [
        { type: 'value', name: '풀이 수', minInterval: 1, axisLabel: { color: '#6b6b66' }, splitLine: { lineStyle: { color: '#efefeb' } } },
        { type: 'value', name: '정답률', min: 0, max: 100, axisLabel: { formatter: '{value}%', color: '#6b6b66' }, splitLine: { show: false } }
      ],
      series: [
        { name: '풀이', type: 'bar', data: learning.dailyActivity.map((item) => item.attempts), barMaxWidth: 22, itemStyle: { borderRadius: [5, 5, 0, 0] } },
        { name: '정답률', type: 'line', yAxisIndex: 1, data: learning.dailyActivity.map(accuracy), smooth: true, symbolSize: 6, lineStyle: { width: 3 } }
      ]
    })
  }

  if (hasTopicData.value) {
    topicChart = chartFor(topicChart, topicChartElement.value, {
      color: ['#c64e32'],
      grid: { top: 18, right: 20, bottom: 56, left: 38 },
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: (value) => `${value}%` },
      xAxis: {
        type: 'category',
        data: learning.topicAccuracy.map((item) => item.topic),
        axisLabel: { color: '#6b6b66', interval: 0, rotate: 26, overflow: 'truncate', width: 78 },
        axisLine: { lineStyle: { color: '#deded8' } }
      },
      yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value}%', color: '#6b6b66' }, splitLine: { lineStyle: { color: '#efefeb' } } },
      series: [{ name: '정답률', type: 'bar', data: learning.topicAccuracy.map((item) => item.accuracyPercent), barMaxWidth: 36, itemStyle: { borderRadius: [5, 5, 0, 0] } }]
    })
  }

  if (hasAiActivity.value) {
    aiChart = chartFor(aiChart, aiChartElement.value, {
      color: ['#171717', '#c64e32'],
      grid: { top: 42, right: 42, bottom: 28, left: 50 },
      legend: { top: 4, textStyle: { color: '#303030' } },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: ai.dailyActivity.map((item) => dailyLabel(item.date)), axisLabel: { color: '#6b6b66' }, axisLine: { lineStyle: { color: '#deded8' } } },
      yAxis: [
        { type: 'value', name: '토큰', axisLabel: { color: '#6b6b66', formatter: (value) => formatNumber(value) }, splitLine: { lineStyle: { color: '#efefeb' } } },
        { type: 'value', name: '호출', minInterval: 1, axisLabel: { color: '#6b6b66' }, splitLine: { show: false } }
      ],
      series: [
        { name: '총 토큰', type: 'bar', data: ai.dailyActivity.map((item) => item.totalTokens), barMaxWidth: 22, itemStyle: { borderRadius: [5, 5, 0, 0] } },
        { name: 'API 호출', type: 'line', yAxisIndex: 1, data: ai.dailyActivity.map((item) => item.calls), smooth: true, symbolSize: 6, lineStyle: { width: 3 } }
      ]
    })
  }

  const quality = dashboard.value.qualityEvaluation
  if (hasDuplicateThresholdData.value) {
    duplicateThresholdChart = chartFor(duplicateThresholdChart, duplicateThresholdChartElement.value, {
      color: ['#171717', '#c64e32', '#24744d'],
      grid: { top: 42, right: 20, bottom: 34, left: 42 },
      legend: { top: 4, textStyle: { color: '#303030' } },
      tooltip: { trigger: 'axis', valueFormatter: (value) => `${value}%` },
      xAxis: {
        type: 'category',
        name: '임계값',
        data: quality.duplicateThresholdMetrics.map((item) => item.threshold.toFixed(2)),
        axisLabel: { color: '#6b6b66' },
        axisLine: { lineStyle: { color: '#deded8' } }
      },
      yAxis: {
        type: 'value',
        min: 0,
        max: 100,
        axisLabel: { formatter: '{value}%', color: '#6b6b66' },
        splitLine: { lineStyle: { color: '#efefeb' } }
      },
      series: [
        { name: '정밀도', type: 'line', data: quality.duplicateThresholdMetrics.map((item) => item.precisionPercent), smooth: true, symbolSize: 5, lineStyle: { width: 3 } },
        { name: '재현율', type: 'line', data: quality.duplicateThresholdMetrics.map((item) => item.recallPercent), smooth: true, symbolSize: 5, lineStyle: { width: 3 } },
        { name: 'F1', type: 'line', data: quality.duplicateThresholdMetrics.map((item) => item.f1Percent), smooth: true, symbolSize: 5, lineStyle: { width: 3 } }
      ]
    })
  }

  if (hasObjectiveQualityData.value) {
    objectiveQualityChart = chartFor(objectiveQualityChart, objectiveQualityChartElement.value, {
      color: ['#c64e32', '#24744d'],
      grid: { top: 42, right: 20, bottom: 34, left: 42 },
      legend: { top: 4, textStyle: { color: '#303030' } },
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: (value) => `${value}%` },
      xAxis: {
        type: 'category',
        data: ['목표 누락률', '문항 일치도'],
        axisLabel: { color: '#6b6b66' },
        axisLine: { lineStyle: { color: '#deded8' } }
      },
      yAxis: {
        type: 'value',
        min: 0,
        max: 100,
        axisLabel: { formatter: '{value}%', color: '#6b6b66' },
        splitLine: { lineStyle: { color: '#efefeb' } }
      },
      series: [
        {
          name: 'AI 잠정',
          type: 'bar',
          data: [quality.objectiveQuality.provisionalOmissionRatePercent, quality.objectiveQuality.provisionalAlignmentRatePercent],
          barMaxWidth: 30,
          itemStyle: { borderRadius: [5, 5, 0, 0] }
        },
        {
          name: '사람 검증',
          type: 'bar',
          data: [quality.objectiveQuality.humanVerifiedOmissionRatePercent, quality.objectiveQuality.humanVerifiedAlignmentRatePercent],
          barMaxWidth: 30,
          itemStyle: { borderRadius: [5, 5, 0, 0] }
        }
      ]
    })
  }
}

function resizeCharts() {
  learningChart?.resize()
  topicChart?.resize()
  aiChart?.resize()
  duplicateThresholdChart?.resize()
  objectiveQualityChart?.resize()
}

function disposeCharts() {
  ;[learningChart, topicChart, aiChart, duplicateThresholdChart, objectiveQualityChart].forEach((chart) => chart?.dispose())
  learningChart = undefined
  topicChart = undefined
  aiChart = undefined
  duplicateThresholdChart = undefined
  objectiveQualityChart = undefined
}

async function loadDashboard() {
  loading.value = true
  error.value = null

  try {
    const response = await axios.get('/api/dashboard')
    dashboard.value = response.data
    await nextTick()
    disposeCharts()
    renderCharts()
  } catch (requestError) {
    error.value = requestError.response?.data?.message || '대시보드 데이터를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadDashboard()
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  disposeCharts()
})
</script>

<template>
  <section class="dashboard-page" aria-labelledby="dashboard-title">
    <div class="dashboard-heading">
      <div>
        <p class="eyebrow">UNIFIED DASHBOARD</p>
        <h2 id="dashboard-title">학습과 AI 운영 현황</h2>
        <p>내 학습 성과와 퀴즈 생성 서비스의 운영 신호를 한 화면에서 확인합니다.</p>
      </div>
      <button type="button" class="refresh-button" :disabled="loading" @click="loadDashboard">
        {{ loading ? '불러오는 중...' : '새로고침' }}
      </button>
    </div>

    <p v-if="error" class="message error-message" role="alert">{{ error }}</p>
    <div v-else-if="loading && !dashboard" class="page-loading" aria-live="polite">대시보드 데이터를 불러오는 중입니다.</div>

    <template v-else-if="dashboard">
      <section class="dashboard-section learning-section" aria-labelledby="learning-section-title">
        <div class="section-heading">
          <div>
            <p class="section-kicker">LEARNING</p>
            <h3 id="learning-section-title">학습 현황</h3>
            <p>누적 풀이 기록과 최근 14일의 학습 흐름입니다.</p>
          </div>
          <div class="review-notice" :class="{ due: dashboard.learning.dueReviewCount > 0 }">
            <strong>{{ formatNumber(dashboard.learning.dueReviewCount) }}개</strong>
            <span>오늘 복습할 문제</span>
          </div>
        </div>

        <div class="metric-grid learning-metrics">
          <article class="metric-card">
            <span>누적 풀이</span>
            <strong>{{ formatNumber(dashboard.learning.totalAttempts) }}회</strong>
          </article>
          <article class="metric-card">
            <span>누적 정답률</span>
            <strong>{{ dashboard.learning.accuracyPercent }}%</strong>
          </article>
          <article class="metric-card">
            <span>푼 문항</span>
            <strong>{{ formatNumber(dashboard.learning.totalQuestions) }}문제</strong>
          </article>
          <article class="metric-card accent-card">
            <span>맞힌 문항</span>
            <strong>{{ formatNumber(dashboard.learning.correctAnswers) }}문제</strong>
          </article>
        </div>

        <div class="chart-grid">
          <article class="chart-card" aria-label="최근 14일 학습 활동 차트">
            <div class="chart-title"><h4>최근 14일 학습 활동</h4><span>풀이 수 · 일별 정답률</span></div>
            <div v-if="hasLearningActivity" ref="learningChartElement" class="chart" role="img" aria-label="일별 풀이 수와 정답률 차트"></div>
            <div v-else class="chart-empty"><strong>아직 최근 학습 기록이 없습니다.</strong><span>문제를 풀고 풀이 기록을 저장하면 변화가 나타납니다.</span></div>
          </article>
          <article class="chart-card" aria-label="주제별 정답률 차트">
            <div class="chart-title"><h4>주제별 정답률</h4><span>누적 기록 상위 8개 주제</span></div>
            <div v-if="hasTopicData" ref="topicChartElement" class="chart" role="img" aria-label="주제별 정답률 막대 차트"></div>
            <div v-else class="chart-empty"><strong>표시할 주제가 없습니다.</strong><span>풀이 기록이 쌓이면 주제별 강약점을 비교할 수 있습니다.</span></div>
          </article>
        </div>

        <section class="recommendation-card" aria-labelledby="recommendation-title">
          <div class="chart-title">
            <div>
              <p class="section-kicker">PERSONALIZED NEXT STEP</p>
              <h4 id="recommendation-title">다음 학습 추천</h4>
            </div>
            <span>풀이 이력 기반 · AI 호출 없음</span>
          </div>
          <div v-if="dashboard.learning.recommendations?.length" class="recommendation-list">
            <article v-for="recommendation in dashboard.learning.recommendations" :key="recommendation.topic" class="recommendation-item">
              <div class="recommendation-copy">
                <span class="recommendation-badge" :class="recommendation.priority.toLowerCase()">{{ recommendationBadge(recommendation.priority) }}</span>
                <h5>{{ recommendation.topic }}</h5>
                <p>{{ recommendation.reason }}</p>
                <span class="recommendation-metric">정답률 {{ recommendation.accuracyPercent }}% · 누적 {{ formatNumber(recommendation.totalQuestions) }}문제</span>
              </div>
              <div class="recommendation-actions">
                <button type="button" class="recommendation-action secondary" @click="startRecommendedQuiz(recommendation)">
                  {{ recommendation.recommendedQuestionCount }}문제 준비
                </button>
                <button type="button" class="recommendation-action" @click="createRoadmapFromRecommendation(recommendation)">
                  로드맵 만들기
                </button>
              </div>
            </article>
          </div>
          <div v-else class="recommendation-empty">
            <strong>현재 학습 주제는 안정적으로 유지되고 있습니다.</strong>
            <span>새 주제를 풀거나 시간이 지난 주제를 다시 학습하면 추천이 표시됩니다.</span>
          </div>
        </section>
      </section>

      <section class="dashboard-section ai-section" aria-labelledby="ai-section-title">
        <div class="section-heading">
          <div>
            <p class="section-kicker">AI OPERATIONS</p>
            <h3 id="ai-section-title">AI 운영 현황</h3>
            <p>최근 14일간 이 서비스가 기록한 OpenAI API 호출 원장 기준입니다.</p>
          </div>
          <span class="source-label">로컬 AI 생성 원장</span>
        </div>

        <div class="metric-grid ai-metrics">
          <article class="metric-card"><span>API 호출</span><strong>{{ formatNumber(dashboard.ai.totalCalls) }}회</strong></article>
          <article class="metric-card"><span>성공률</span><strong>{{ dashboard.ai.successRatePercent }}%</strong></article>
          <article class="metric-card"><span>총 토큰</span><strong>{{ formatNumber(dashboard.ai.totalTokens) }}</strong></article>
          <article class="metric-card accent-card">
            <span>오늘 토큰 예산</span>
            <strong>{{ formatNumber(dashboard.ai.todayTokens) }} / {{ formatNumber(dashboard.ai.dailyTokenBudget) }}</strong>
            <small>{{ dashboard.ai.dailyBudgetEnforced ? `${dashboard.ai.dailyBudgetUsedPercent}% 사용` : '표시 전용' }}</small>
          </article>
          <article class="metric-card">
            <span>오늘 남은 예산</span>
            <strong>{{ formatNumber(dashboard.ai.remainingDailyTokens) }}</strong>
            <small>로컬 안전 한도 기준</small>
          </article>
          <article class="metric-card"><span>평균 지연시간</span><strong>{{ formatDuration(dashboard.ai.averageLatencyMs) }}</strong></article>
          <article class="metric-card"><span>P95 지연시간</span><strong>{{ formatDuration(dashboard.ai.p95LatencyMs) }}</strong></article>
          <article class="metric-card accent-card"><span>저장된 문제</span><strong>{{ formatNumber(dashboard.ai.storedQuestionCount) }}개</strong></article>
        </div>

        <div class="operations-layout">
          <article class="chart-card" aria-label="최근 14일 AI 호출 및 토큰 차트">
            <div class="chart-title"><h4>최근 14일 AI 사용량</h4><span>총 토큰 · API 호출</span></div>
            <div v-if="hasAiActivity" ref="aiChartElement" class="chart" role="img" aria-label="일별 AI 토큰 사용량과 API 호출 차트"></div>
            <div v-else class="chart-empty"><strong>최근 14일의 AI 호출 기록이 없습니다.</strong><span>문제를 생성하면 모델·토큰·응답 시간이 자동 기록됩니다.</span></div>
          </article>

          <article class="model-card">
            <div class="chart-title"><h4>모델별 사용 현황</h4><span>최근 14일</span></div>
            <div v-if="dashboard.ai.modelUsage?.length" class="model-table-wrap">
              <table>
                <thead><tr><th scope="col">모델</th><th scope="col">호출</th><th scope="col">토큰</th><th scope="col">성공률</th><th scope="col">평균 지연</th></tr></thead>
                <tbody>
                  <tr v-for="model in dashboard.ai.modelUsage" :key="model.model">
                    <td>{{ model.model }}</td>
                    <td>{{ formatNumber(model.calls) }}</td>
                    <td>{{ formatNumber(model.totalTokens) }}</td>
                    <td>{{ model.successRatePercent }}%</td>
                    <td>{{ formatDuration(model.averageLatencyMs) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-else class="chart-empty compact"><strong>기록된 모델 호출이 없습니다.</strong><span>실제 생성 요청을 성공·실패 여부와 함께 기록합니다.</span></div>
          </article>
        </div>

        <aside class="operation-note">
          <strong>비용·품질 제어 기준</strong>
          <p>오늘 예산은 이 서버가 저장한 실제 토큰 원장과 요청 전 추정치로 검사하는 로컬 안전 한도입니다. OpenAI 청구 한도와는 별개이며, 한도를 넘길 요청은 외부 API 호출 전에 차단됩니다. 자료 기반 생성은 요청 주제와 겹치는 조각만 제한해서 보내며, 별도 임베딩 호출은 하지 않습니다.</p>
        </aside>
      </section>

      <section class="dashboard-section feedback-section" aria-labelledby="feedback-section-title">
        <div class="section-heading">
          <div>
            <p class="section-kicker">QUESTION QUALITY</p>
            <h3 id="feedback-section-title">문제 품질 피드백</h3>
            <p>학습자가 남긴 문제·정답·해설 개선 신호를 확인합니다.</p>
          </div>
          <span class="source-label">문항당 최신 의견</span>
        </div>

        <div class="metric-grid feedback-metrics">
          <article class="metric-card">
            <span>수집된 피드백</span>
            <strong>{{ formatNumber(dashboard.qualityFeedback.totalFeedbackCount) }}건</strong>
          </article>
          <article class="metric-card accent-card">
            <span>확인 필요한 의견</span>
            <strong>{{ formatNumber(dashboard.qualityFeedback.openFeedbackCount) }}건</strong>
          </article>
        </div>

        <div v-if="dashboard.qualityFeedback.typeMetrics?.length" class="feedback-type-list">
          <article v-for="metric in dashboard.qualityFeedback.typeMetrics" :key="metric.type" class="feedback-type-row">
            <span>{{ metric.label }}</span>
            <strong>{{ formatNumber(metric.count) }}건</strong>
          </article>
        </div>
        <div v-else class="feedback-empty">
          <strong>아직 수집된 문제 피드백이 없습니다.</strong>
          <span>문제를 제출한 뒤 각 문항의 품질 피드백을 남길 수 있습니다.</span>
        </div>
      </section>

      <section class="dashboard-section evaluation-section" aria-labelledby="evaluation-section-title">
        <div class="section-heading">
          <div>
            <p class="section-kicker">QUALITY EVIDENCE</p>
            <h3 id="evaluation-section-title">AI 품질 검증 현황</h3>
            <p>AI 자체 판단과 사람이 확정한 근거를 구분해, 중복 방지 정책과 로드맵 문항 품질을 확인합니다.</p>
          </div>
          <span class="source-label">저장된 평가 결과</span>
        </div>

        <div class="metric-grid evaluation-metrics">
          <article class="metric-card">
            <span>중복 사람 검증 표본</span>
            <strong>{{ formatNumber(dashboard.qualityEvaluation.duplicateHumanSampleCount) }}건</strong>
          </article>
          <article class="metric-card">
            <span>중복 검토 대기</span>
            <strong>{{ formatNumber(dashboard.qualityEvaluation.duplicatePendingReviewCount) }}건</strong>
          </article>
          <article class="metric-card accent-card">
            <span>추천 임계값</span>
            <strong>{{ dashboard.qualityEvaluation.recommendedThreshold ?? '표본 부족' }}</strong>
          </article>
          <article class="metric-card">
            <span>목표·문항 사람 검토</span>
            <strong>{{ formatNumber(dashboard.qualityEvaluation.objectiveQuality.humanReviewedCases) }}건</strong>
          </article>
        </div>

        <div class="chart-grid quality-chart-grid">
          <article class="chart-card" aria-label="임계값별 정밀도 재현율 F1 차트">
            <div class="chart-title"><h4>임계값별 중복 판정 성능</h4><span>사람 검증 표본만 사용</span></div>
            <div v-if="hasDuplicateThresholdData" ref="duplicateThresholdChartElement" class="chart" role="img" aria-label="임계값별 정밀도 재현율 F1 선 차트"></div>
            <div v-else class="chart-empty"><strong>아직 사람 검증 표본이 없습니다.</strong><span>품질 평가에서 애매한 문제 쌍을 판정하면 0.75~0.95 구간의 추이가 나타납니다.</span></div>
          </article>
          <article class="chart-card" aria-label="목표 누락률과 문항 일치도 차트">
            <div class="chart-title"><h4>로드맵 목표·문항 품질</h4><span>AI 잠정 · 사람 검증 분리</span></div>
            <div v-if="hasObjectiveQualityData" ref="objectiveQualityChartElement" class="chart" role="img" aria-label="목표 누락률과 문항 일치도 막대 차트"></div>
            <div v-else class="chart-empty"><strong>아직 목표 품질 평가 결과가 없습니다.</strong><span>로드맵 학습 단위를 선택해 평가하면 AI 잠정 지표부터 표시하고, 검토 후 사람 검증 값이 추가됩니다.</span></div>
          </article>
        </div>

        <aside class="operation-note quality-note">
          <strong>차트 읽는 법</strong>
          <p>중복 차트는 같은 임계값에서 정밀도·재현율·F1의 균형을 봅니다. 목표 누락률은 낮을수록, 문항 일치도는 높을수록 좋습니다. 사람 검증 값이 비어 있으면 아직 AI의 잠정 평가만 존재한다는 뜻입니다.</p>
        </aside>
      </section>
    </template>
  </section>
</template>

<style scoped>
.dashboard-page { max-width: 1200px; margin: 0 auto; padding: 36px 40px 52px; background: var(--surface); }
.dashboard-heading, .section-heading, .chart-title { display: flex; gap: 16px; align-items: flex-start; justify-content: space-between; }
.eyebrow, .section-kicker { margin: 0 0 6px; color: var(--accent); font-size: .75rem; font-weight: 800; letter-spacing: .08em; }
h2, h3, h4, p { margin: 0; }
h2 { color: var(--ink); font-size: clamp(1.7rem, 4vw, 2.25rem); }
h3 { color: var(--ink); font-size: 1.32rem; }
h4 { color: var(--ink-soft); font-size: 1rem; }
.dashboard-heading > div > p:not(.eyebrow), .section-heading p:not(.section-kicker) { margin-top: 6px; color: var(--muted); }
button { border: 0; border-radius: 8px; font: inherit; font-weight: 700; cursor: pointer; }
button:focus-visible { outline: 3px solid rgb(198 78 50 / 24%); outline-offset: 3px; }
button:disabled { cursor: not-allowed; opacity: .55; }
.refresh-button { flex: 0 0 auto; padding: 11px 16px; color: #fff; background: var(--ink); }
.message { margin-top: 20px; padding: 14px 16px; border-radius: 8px; }
.error-message { color: #b42318; background: #fef3f2; border: 1px solid #fecdca; }
.page-loading { display: grid; min-height: 320px; place-items: center; color: var(--muted); }
.dashboard-section { margin-top: 34px; padding: 28px; border: 1px solid var(--line); border-radius: 14px; }
.learning-section, .ai-section, .feedback-section, .evaluation-section { background: var(--surface); }
.review-notice, .source-label { flex: 0 0 auto; border-radius: 10px; font-weight: 700; }
.review-notice { display: grid; min-width: 112px; padding: 10px 12px; color: var(--accent-strong); text-align: center; background: var(--accent-soft); }
.review-notice.due { color: #a04910; background: #fff1df; }
.review-notice strong { font-size: 1.1rem; }
.review-notice span { font-size: .74rem; }
.source-label { padding: 8px 10px; color: var(--ink-soft); background: var(--surface-subtle); font-size: .78rem; }
.metric-grid { display: grid; gap: 12px; margin-top: 22px; grid-template-columns: repeat(4, minmax(0, 1fr)); }
.ai-metrics { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.feedback-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); max-width: 560px; }
.evaluation-metrics { grid-template-columns: repeat(4, minmax(0, 1fr)); }
.metric-card { display: grid; gap: 3px; min-width: 0; padding: 18px; border: 1px solid var(--line); border-radius: 10px; background: var(--surface); }
.metric-card span { color: var(--muted); font-size: .82rem; font-weight: 700; }
.metric-card strong { overflow: hidden; color: var(--ink); font-size: clamp(1.15rem, 2.4vw, 1.6rem); text-overflow: ellipsis; white-space: nowrap; }
.metric-card small { color: var(--muted); font-size: .74rem; font-weight: 700; }
.accent-card { border-color: var(--line-strong); background: var(--surface-subtle); }
.chart-grid, .operations-layout { display: grid; gap: 18px; margin-top: 20px; grid-template-columns: repeat(2, minmax(0, 1fr)); }
.operations-layout { grid-template-columns: minmax(0, 1.2fr) minmax(360px, .8fr); }
.chart-card, .model-card { min-width: 0; padding: 20px; border: 1px solid var(--line); border-radius: 12px; background: var(--surface); }
.chart-title { align-items: baseline; }
.chart-title span { flex: 0 0 auto; color: var(--muted); font-size: .75rem; }
.chart { width: 100%; height: 270px; margin-top: 12px; }
.chart-empty { display: grid; min-height: 270px; place-content: center; gap: 6px; color: var(--muted); text-align: center; }
.chart-empty strong { color: var(--ink-soft); }
.chart-empty span { max-width: 280px; font-size: .88rem; }
.chart-empty.compact { min-height: 220px; }
.recommendation-card { margin-top: 20px; padding: 20px; border: 1px solid var(--line); border-radius: 12px; background: var(--surface); }
.recommendation-list { display: grid; gap: 10px; margin-top: 14px; }
.recommendation-item { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 15px; border: 1px solid var(--line); border-radius: 10px; background: var(--surface-subtle); }
.recommendation-copy { min-width: 0; }
.recommendation-copy h5 { display: inline; margin: 0 0 0 8px; color: var(--ink); font-size: 1rem; }
.recommendation-copy p { margin: 7px 0 4px; color: var(--ink-soft); font-size: .88rem; }
.recommendation-metric { color: var(--muted); font-size: .78rem; font-weight: 700; }
.recommendation-badge { display: inline-block; padding: 3px 7px; border-radius: 999px; font-size: .72rem; font-weight: 800; }
.recommendation-badge.weakness { color: #b54708; background: #fff1df; }
.recommendation-badge.reinforce { color: var(--accent-strong); background: var(--accent-soft); }
.recommendation-badge.review { color: #1f6b51; background: #e7f7ef; }
.recommendation-actions { display: grid; flex: 0 0 auto; gap: 7px; }
.recommendation-action { padding: 10px 12px; color: #fff; background: var(--ink); font-size: .82rem; white-space: nowrap; }
.recommendation-action.secondary { color: var(--ink); border: 1px solid var(--line-strong); background: var(--surface); }
.recommendation-empty { display: grid; min-height: 118px; place-content: center; gap: 5px; margin-top: 10px; color: var(--muted); text-align: center; }
.recommendation-empty strong { color: var(--ink-soft); }
.recommendation-empty span { font-size: .88rem; }
.model-table-wrap { overflow-x: auto; margin-top: 14px; }
table { width: 100%; border-collapse: collapse; color: var(--ink-soft); font-size: .82rem; }
th, td { padding: 11px 8px; border-bottom: 1px solid var(--line); text-align: right; white-space: nowrap; }
th { color: var(--muted); font-size: .72rem; }
th:first-child, td:first-child { max-width: 120px; overflow: hidden; text-align: left; text-overflow: ellipsis; }
tbody tr:last-child td { border-bottom: 0; }
.operation-note { margin-top: 18px; padding: 15px 17px; color: var(--ink-soft); border: 1px solid var(--line); border-radius: 10px; background: var(--surface-subtle); font-size: .87rem; }
.operation-note strong { color: var(--ink); }
.operation-note p { margin-top: 4px; }
.feedback-type-list { display: grid; gap: 8px; margin-top: 18px; max-width: 560px; }
.feedback-type-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 14px; color: var(--ink-soft); border: 1px solid #f0dfbc; border-radius: 9px; background: var(--surface); }
.feedback-type-row strong { color: #8a5712; }
.feedback-empty { display: grid; min-height: 112px; place-content: center; gap: 5px; margin-top: 18px; color: var(--muted); text-align: center; }
.feedback-empty strong { color: var(--ink-soft); }
.feedback-empty span { font-size: .88rem; }
.quality-note { margin-top: 20px; }
@media (max-width: 850px) { .dashboard-page { padding: 28px 22px 42px; } .metric-grid, .ai-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); } .operations-layout { grid-template-columns: 1fr; } }
@media (max-width: 680px) { .dashboard-page { padding: 25px 16px 34px; border-radius: 0; } .dashboard-heading, .section-heading, .recommendation-item { flex-direction: column; align-items: flex-start; } .refresh-button, .recommendation-actions, .recommendation-action { width: 100%; } .dashboard-section { margin-top: 23px; padding: 17px; } .review-notice { width: 100%; } .source-label { align-self: flex-start; } .metric-grid, .ai-metrics, .chart-grid { grid-template-columns: 1fr; } .chart-card, .model-card, .recommendation-card { padding: 16px; } .chart-title { align-items: flex-start; flex-direction: column; gap: 3px; } .chart { height: 244px; } }
</style>
