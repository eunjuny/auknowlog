<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import axios from 'axios'

const props = defineProps({
  initialRoadmap: { type: Object, default: null }
})
const emit = defineEmits(['start-roadmap-quiz'])

const title = ref('')
const topic = ref('')
const description = ref('')
const durationWeeks = ref(4)
const steps = ref(defaultSteps())
const roadmap = ref(null)
const loading = ref(false)
const saving = ref(false)
const importing = ref(false)
const aiGenerating = ref(false)
const aiStepCount = ref(4)
const selectedFile = ref(null)
const error = ref(null)
const message = ref(null)

function defaultSteps() {
  return [
    { key: 'foundation', title: '핵심 개념 이해', description: '', topic: '', questionTarget: 5, dependsOn: [] },
    { key: 'practice', title: '응용과 실전', description: '', topic: '', questionTarget: 5, dependsOn: ['foundation'] }
  ]
}

const canCreate = computed(() => topic.value.trim()
  && steps.value.length > 0
  && steps.value.every((step) => step.title.trim() && Number(step.questionTarget) >= 1))

function applyInitialRoadmap() {
  if (!props.initialRoadmap?.topic) return
  topic.value = props.initialRoadmap.topic
  if (!title.value) title.value = `${topic.value} 단계별 학습 로드맵`
  steps.value[0].topic = `${topic.value} 기초`
  steps.value[1].topic = `${topic.value} 실전`
  const target = Math.min(20, Math.max(1, props.initialRoadmap.questionsPerWeek || 5))
  steps.value.forEach((step) => { step.questionTarget = target })
  message.value = `추천 주제 “${topic.value}”로 단계 초안을 준비했습니다. 각 단계와 완료 기준을 확인해주세요.`
}

watch(() => props.initialRoadmap?.requestedAt, applyInitialRoadmap)

function formatDate(value) {
  if (!value) return ''
  return new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: 'short', day: 'numeric' })
    .format(new Date(`${value}T00:00:00`))
}

function statusLabel(status) {
  return {
    READY: '시작 가능', IN_PROGRESS: '학습 중', LOCKED: '잠김', COMPLETED: '완료',
    CURRENT: '이번 주', UPCOMING: '예정', OVERDUE: '미달성'
  }[status] ?? status
}

function sourceLabel(sourceType) {
  return { FILE_IMPORT: '.roadmap.json 가져오기', MANUAL: '직접 작성', AI_GENERATED: 'AI 생성', WEEKLY: '기존 주차형' }[sourceType] ?? sourceType
}

async function loadRoadmap() {
  loading.value = true
  error.value = null
  try {
    const response = await axios.get('/api/learning-roadmaps/active')
    roadmap.value = response.data.roadmap
  } catch (requestError) {
    error.value = requestError.response?.data?.message || '학습 로드맵을 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
}

function nextStepKey() {
  let number = steps.value.length + 1
  while (steps.value.some((step) => step.key === `step-${number}`)) number += 1
  return `step-${number}`
}

function addStep() {
  const previous = steps.value.at(-1)
  steps.value.push({
    key: nextStepKey(), title: '', description: '', topic: topic.value,
    questionTarget: 5, dependsOn: previous ? [previous.key] : []
  })
}

function removeStep(index) {
  if (steps.value.length === 1) return
  const removedKey = steps.value[index].key
  steps.value.splice(index, 1)
  steps.value.forEach((step) => {
    step.dependsOn = step.dependsOn.filter((key) => key !== removedKey)
  })
}

function toggleDependency(step, prerequisiteKey) {
  step.dependsOn = step.dependsOn.includes(prerequisiteKey)
    ? step.dependsOn.filter((key) => key !== prerequisiteKey)
    : [...step.dependsOn, prerequisiteKey]
}

function roadmapDefinition() {
  return {
    version: '1.0',
    title: title.value.trim() || `${topic.value.trim()} 단계별 학습 로드맵`,
    topic: topic.value.trim(),
    description: description.value.trim() || null,
    durationWeeks: Number(durationWeeks.value),
    steps: steps.value.map((step) => ({
      key: step.key,
      title: step.title.trim(),
      description: step.description.trim() || null,
      topic: step.topic.trim() || topic.value.trim(),
      questionTarget: Math.min(20, Math.max(1, Number(step.questionTarget))),
      dependsOn: [...step.dependsOn]
    }))
  }
}

async function createRoadmap() {
  saving.value = true
  error.value = null
  message.value = null
  try {
    const response = await axios.post('/api/learning-roadmaps/stages', roadmapDefinition())
    roadmap.value = response.data
    message.value = '단계별 로드맵을 만들었습니다. 시작 가능한 첫 단계부터 학습해보세요.'
  } catch (requestError) {
    error.value = requestError.response?.data?.message || '학습 로드맵을 만들지 못했습니다.'
  } finally {
    saving.value = false
  }
}

function chooseFile(event) {
  selectedFile.value = event.target.files?.[0] || null
  error.value = null
}

async function importRoadmap() {
  if (!selectedFile.value) {
    error.value = '.roadmap.json 파일을 선택해주세요.'
    return
  }
  importing.value = true
  error.value = null
  message.value = null
  try {
    const form = new FormData()
    form.append('file', selectedFile.value)
    const response = await axios.post('/api/learning-roadmaps/import', form)
    roadmap.value = response.data
    message.value = `${selectedFile.value.name}에서 단계별 로드맵을 가져왔습니다.`
  } catch (requestError) {
    error.value = requestError.response?.data?.message || '로드맵 파일을 가져오지 못했습니다.'
  } finally {
    importing.value = false
  }
}

async function createAiRoadmap() {
  if (!topic.value.trim()) {
    error.value = 'AI 로드맵을 만들 학습 주제를 입력해주세요.'
    return
  }
  aiGenerating.value = true
  error.value = null
  message.value = null
  try {
    const response = await axios.post('/api/learning-roadmaps/ai', {
      topic: topic.value.trim(),
      durationWeeks: Number(durationWeeks.value),
      stepCount: Number(aiStepCount.value)
    })
    roadmap.value = response.data
    message.value = 'AI가 만든 단계 로드맵을 v1.0 계약으로 검증해 저장했습니다.'
  } catch (requestError) {
    error.value = requestError.response?.data?.message || 'AI 로드맵을 만들지 못했습니다.'
  } finally {
    aiGenerating.value = false
  }
}

function downloadSample() {
  const sample = {
    version: '1.0',
    title: 'Kubernetes 단계별 학습 로드맵',
    topic: 'Kubernetes',
    description: '기초 개념을 완료한 뒤 네트워크와 운영 단계로 진행합니다.',
    durationWeeks: 4,
    steps: [
      { key: 'core', title: '핵심 개념', description: 'Pod와 Deployment 이해', topic: 'Kubernetes 핵심 개념', questionTarget: 5, dependsOn: [] },
      { key: 'network', title: '네트워크', description: 'Service와 Ingress 이해', topic: 'Kubernetes 네트워크', questionTarget: 5, dependsOn: ['core'] },
      { key: 'operations', title: '운영', description: '관측성과 장애 대응', topic: 'Kubernetes 운영', questionTarget: 5, dependsOn: ['network'] }
    ]
  }
  const url = URL.createObjectURL(new Blob([JSON.stringify(sample, null, 2)], { type: 'application/json' }))
  const link = document.createElement('a')
  link.href = url
  link.download = 'kubernetes.roadmap.json'
  link.click()
  URL.revokeObjectURL(url)
}

function prerequisiteLabel(step) {
  if (!step.prerequisiteKeys?.length) return '선행 단계 없음'
  return `선행: ${step.prerequisiteKeys.map((key) => roadmap.value.steps.find((item) => item.key === key)?.title || key).join(', ')}`
}

function startStepQuiz(step) {
  if (step.status === 'LOCKED' || step.status === 'COMPLETED') return
  emit('start-roadmap-quiz', {
    roadmapId: roadmap.value.roadmapId,
    roadmapStepId: step.stepId,
    topic: step.topic,
    numberOfQuestions: Math.min(20, Math.max(1, step.questionTarget - step.completedQuestions))
  })
}

function startWeekQuiz(week) {
  emit('start-roadmap-quiz', {
    roadmapId: roadmap.value.roadmapId,
    topic: week.topic,
    numberOfQuestions: Math.min(20, Math.max(1, week.plannedQuestions - week.completedQuestions))
  })
}

onMounted(() => {
  applyInitialRoadmap()
  loadRoadmap()
})
</script>

<template>
  <section class="roadmap-page" aria-labelledby="roadmap-title">
    <div class="page-heading">
      <div>
        <p class="eyebrow">LEARNING PATH</p>
        <h2 id="roadmap-title">단계별 학습 로드맵</h2>
        <p>선행 단계를 완료하면 다음 단계가 열리고, 해당 단계에서 푼 문제만 진행률에 반영됩니다.</p>
      </div>
      <button type="button" class="secondary-button" :disabled="loading" @click="loadRoadmap">
        {{ loading ? '불러오는 중...' : '진행률 새로고침' }}
      </button>
    </div>

    <p v-if="error" class="message error-message" role="alert">{{ error }}</p>
    <p v-if="message" class="message success-message">{{ message }}</p>

    <section v-if="roadmap" class="active-roadmap" aria-labelledby="active-roadmap-title">
      <div class="active-heading">
        <div>
          <p class="section-kicker">ACTIVE ROADMAP · {{ sourceLabel(roadmap.sourceType) }}</p>
          <h3 id="active-roadmap-title">{{ roadmap.title }}</h3>
          <p>{{ roadmap.topic }} · {{ roadmap.durationWeeks }}주 · {{ formatDate(roadmap.startDate) }} 시작</p>
          <p v-if="roadmap.description" class="roadmap-description">{{ roadmap.description }}</p>
        </div>
        <span class="completion-label" :class="{ completed: roadmap.completed }">
          {{ roadmap.completed ? '전체 단계 완료' : `진행률 ${roadmap.progressPercent}%` }}
        </span>
      </div>

      <div class="overall-progress" aria-label="전체 학습 로드맵 진행률">
        <div><span :style="{ width: `${roadmap.progressPercent}%` }"></span></div>
        <strong>{{ roadmap.completedQuestions }}/{{ roadmap.totalPlannedQuestions }}문제</strong>
      </div>

      <ol v-if="roadmap.steps?.length" class="step-list">
        <li v-for="(step, index) in roadmap.steps" :key="step.stepId" class="step-item" :class="step.status.toLowerCase()">
          <div class="step-marker"><span>{{ index + 1 }}</span><i v-if="index < roadmap.steps.length - 1"></i></div>
          <div class="step-card">
            <div class="step-title-row">
              <div>
                <p class="dependency">{{ prerequisiteLabel(step) }}</p>
                <h4>{{ step.title }}</h4>
                <p v-if="step.description">{{ step.description }}</p>
                <p class="step-topic">문제 주제: {{ step.topic }}</p>
              </div>
              <span class="status-badge">{{ statusLabel(step.status) }}</span>
            </div>
            <div class="step-progress">
              <div><span :style="{ width: `${step.progressPercent}%` }"></span></div>
              <span>{{ step.completedQuestions }}/{{ step.questionTarget }}문제</span>
            </div>
            <button type="button" class="primary-button step-action" :disabled="step.status === 'LOCKED' || step.status === 'COMPLETED'" @click="startStepQuiz(step)">
              {{ step.status === 'LOCKED' ? '선행 단계 학습 필요' : step.status === 'COMPLETED' ? '단계 완료' : '이 단계 학습 시작' }}
            </button>
          </div>
        </li>
      </ol>

      <ol v-else class="legacy-week-list">
        <li v-for="week in roadmap.weeks" :key="week.weekNumber">
          <div><strong>{{ week.weekNumber }}주차 · {{ week.topic }}</strong><p>{{ week.completedQuestions }}/{{ week.plannedQuestions }}문제 · {{ statusLabel(week.status) }}</p></div>
          <button type="button" class="primary-button" :disabled="week.status === 'UPCOMING' || week.status === 'COMPLETED'" @click="startWeekQuiz(week)">학습 시작</button>
        </li>
      </ol>
    </section>

    <div class="builder-grid">
      <section class="builder-card" aria-labelledby="manual-builder-title">
        <div class="section-heading">
          <div>
            <p class="section-kicker">DIRECT BUILDER</p>
            <h3 id="manual-builder-title">단계 직접 작성</h3>
          </div>
          <span class="cost-badge">AI 비용 없음</span>
        </div>
        <form @submit.prevent="createRoadmap">
          <label>로드맵 이름 <span>(선택)</span><input v-model="title" maxlength="120" placeholder="예: Kubernetes 운영 역량 로드맵" /></label>
          <label>전체 학습 주제<input v-model="topic" maxlength="120" required placeholder="예: Kubernetes" /></label>
          <label>설명 <span>(선택)</span><textarea v-model="description" maxlength="2000" rows="2" placeholder="이 로드맵의 목표를 적어주세요."></textarea></label>
          <label>예상 기간<select v-model.number="durationWeeks"><option :value="2">2주</option><option :value="4">4주</option><option :value="8">8주</option><option :value="12">12주</option></select></label>

          <div class="steps-editor-heading"><strong>학습 단계</strong><button type="button" class="text-button" :disabled="steps.length >= 20" @click="addStep">+ 단계 추가</button></div>
          <article v-for="(step, index) in steps" :key="step.key" class="step-editor">
            <div class="editor-heading"><strong>{{ index + 1 }}단계</strong><button type="button" class="remove-button" :disabled="steps.length === 1" @click="removeStep(index)">삭제</button></div>
            <label>단계 이름<input v-model="step.title" maxlength="120" required placeholder="예: 핵심 개념 이해" /></label>
            <label>문제 생성 주제 <span>(비우면 전체 주제)</span><input v-model="step.topic" maxlength="120" placeholder="예: Kubernetes Pod와 Deployment" /></label>
            <label>학습 내용 <span>(선택)</span><textarea v-model="step.description" maxlength="1000" rows="2" placeholder="이 단계에서 익힐 내용을 적어주세요."></textarea></label>
            <label>완료 기준<input v-model.number="step.questionTarget" type="number" min="1" max="20" required /> 문제 풀이</label>
            <fieldset v-if="index > 0">
              <legend>선행 단계 <span>(복수 선택 가능)</span></legend>
              <label v-for="candidate in steps.slice(0, index)" :key="candidate.key" class="check-label">
                <input type="checkbox" :checked="step.dependsOn.includes(candidate.key)" @change="toggleDependency(step, candidate.key)" /> {{ candidate.title || candidate.key }}
              </label>
            </fieldset>
          </article>
          <button type="submit" class="primary-button create-button" :disabled="saving || !canCreate">{{ saving ? '생성 중...' : '단계별 로드맵 생성' }}</button>
        </form>
      </section>

      <section class="builder-card file-card" aria-labelledby="file-builder-title">
        <div class="section-heading">
          <div><p class="section-kicker">PORTABLE FORMAT</p><h3 id="file-builder-title">.roadmap.json 가져오기</h3></div>
          <span class="format-badge">v1.0</span>
        </div>
        <p>단계, 완료 기준, 선행 관계를 구조적으로 저장합니다. Git에서 변경 이력을 볼 수 있고 서버가 형식을 검증합니다.</p>
        <div class="ai-panel">
          <div><strong>주제로 AI 로드맵 생성</strong><span>OpenAI API 토큰 사용</span></div>
          <p>왼쪽의 전체 학습 주제와 예상 기간을 사용해 같은 v1.0 형식의 단계 계획을 생성합니다. 버튼을 누를 때만 API가 호출됩니다.</p>
          <label>생성할 단계 수<select v-model.number="aiStepCount"><option :value="3">3단계</option><option :value="4">4단계</option><option :value="5">5단계</option><option :value="6">6단계</option></select></label>
          <button type="button" class="ai-button" :disabled="aiGenerating || !topic.trim()" @click="createAiRoadmap">{{ aiGenerating ? 'AI가 단계 구성 중...' : 'AI로 로드맵 생성' }}</button>
        </div>
        <div class="format-comparison"><strong>왜 Mermaid가 아닌가요?</strong><p>Mermaid는 시각화에 좋지만 진행 상태를 저장하는 표준 계약은 아닙니다. JSON을 원본으로 삼고, 향후 Mermaid 화면은 이 데이터에서 자동 생성하는 편이 안전합니다.</p></div>
        <label class="file-input">로드맵 파일<input type="file" accept=".roadmap.json,application/json" @change="chooseFile" /></label>
        <p v-if="selectedFile" class="selected-file">선택: {{ selectedFile.name }}</p>
        <div class="file-actions">
          <button type="button" class="primary-button" :disabled="importing || !selectedFile" @click="importRoadmap">{{ importing ? '검증 및 저장 중...' : '파일 가져오기' }}</button>
          <button type="button" class="secondary-button" @click="downloadSample">샘플 파일 다운로드</button>
        </div>
        <ul class="validation-list">
          <li>최대 128KB, 파일명은 반드시 <code>*.roadmap.json</code></li>
          <li>지원 버전 <code>1.0</code>, 단계 최대 20개</li>
          <li>없는 단계 참조·자기 참조·순환 의존성은 저장 전 차단</li>
          <li>가져오기 자체는 OpenAI API와 토큰을 사용하지 않음</li>
        </ul>
      </section>
    </div>
  </section>
</template>

<style scoped>
.roadmap-page { max-width: 1200px; margin: 0 auto; padding: 36px 40px 52px; color: #334155; background: #fff; border-radius: 0 0 14px 14px; box-shadow: 0 4px 12px rgb(15 23 42 / 8%); }
.page-heading, .active-heading, .section-heading, .step-title-row, .editor-heading, .steps-editor-heading, .file-actions { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.eyebrow, .section-kicker { margin: 0 0 6px; color: #5666d9; font-size: .74rem; font-weight: 800; letter-spacing: .08em; }
h2, h3, h4, p { margin: 0; }
h2 { color: #20294f; font-size: clamp(1.7rem, 4vw, 2.25rem); }
h3 { color: #26325b; font-size: 1.3rem; }
h4 { color: #293657; font-size: 1.04rem; }
.page-heading > div > p:not(.eyebrow), .active-heading p, .builder-card > p { margin-top: 6px; color: #64748b; }
button { border: 0; border-radius: 8px; font: inherit; font-weight: 750; cursor: pointer; }
button:disabled { cursor: not-allowed; opacity: .5; }
button:focus-visible, input:focus-visible, textarea:focus-visible, select:focus-visible { outline: 3px solid rgb(102 126 234 / 32%); outline-offset: 2px; }
.primary-button { padding: 10px 14px; color: #fff; background: #667eea; }
.primary-button:not(:disabled):hover { background: #5361c5; }
.secondary-button { padding: 10px 14px; color: #4c58ad; border: 1px solid #cbd2fa; background: #f7f8ff; }
.message { margin-top: 18px; padding: 13px 15px; border-radius: 8px; font-weight: 700; }
.error-message { color: #b42318; border: 1px solid #fecdca; background: #fef3f2; }
.success-message { color: #1f6b51; border: 1px solid #bde7ce; background: #edfbf2; }
.active-roadmap { margin-top: 28px; padding: 26px; border: 1px solid #dfe5f1; border-radius: 14px; background: linear-gradient(135deg, #f8faff, #f8fcff); }
.completion-label, .cost-badge, .format-badge, .status-badge { flex: 0 0 auto; padding: 6px 9px; border-radius: 999px; font-size: .76rem; font-weight: 800; }
.completion-label, .format-badge { color: #4653a5; background: #eef0ff; }
.completion-label.completed, .cost-badge { color: #1f6b51; background: #e7f7ef; }
.roadmap-description { max-width: 720px; }
.overall-progress, .step-progress { display: flex; align-items: center; gap: 12px; }
.overall-progress { margin-top: 22px; }
.overall-progress > div, .step-progress > div { flex: 1; overflow: hidden; height: 9px; border-radius: 999px; background: #e7ebf3; }
.overall-progress > div span, .step-progress > div span { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, #667eea, #7c5bc4); }
.overall-progress strong { color: #405078; font-size: .86rem; white-space: nowrap; }
.step-list { margin: 26px 0 0; padding: 0; list-style: none; }
.step-item { display: grid; grid-template-columns: 38px minmax(0, 1fr); gap: 10px; }
.step-marker { display: flex; align-items: center; flex-direction: column; }
.step-marker span { display: grid; flex: 0 0 30px; width: 30px; color: #fff; border-radius: 50%; background: #667eea; place-items: center; font-size: .8rem; font-weight: 800; }
.step-marker i { width: 2px; height: 100%; min-height: 24px; background: #d9def2; }
.locked .step-marker span { background: #a8b0bf; }
.completed .step-marker span { background: #2f9b72; }
.step-card { margin-bottom: 12px; padding: 16px; border: 1px solid #e1e6f0; border-radius: 10px; background: #fff; }
.step-title-row p { margin-top: 4px; color: #66758c; font-size: .84rem; }
.step-title-row .dependency { margin: 0 0 3px; color: #7b66c4; font-size: .72rem; font-weight: 800; }
.step-title-row .step-topic { color: #53627c; font-weight: 700; }
.status-badge { color: #4a56a8; background: #eef0ff; }
.locked .status-badge { color: #687385; background: #edf0f4; }
.completed .status-badge { color: #1f6b51; background: #e7f7ef; }
.step-progress { margin-top: 12px; }
.step-progress > div { height: 7px; }
.step-progress > span { color: #6b7890; font-size: .78rem; font-weight: 700; }
.step-action { margin-top: 13px; }
.legacy-week-list { display: grid; gap: 10px; margin: 24px 0 0; padding: 0; list-style: none; }
.legacy-week-list li { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px; border: 1px solid #e2e8f0; border-radius: 9px; background: #fff; }
.legacy-week-list p { margin-top: 3px; color: #718096; font-size: .82rem; }
.builder-grid { display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(300px, .65fr); gap: 18px; margin-top: 26px; align-items: start; }
.builder-card { padding: 24px; border: 1px solid #dfe5f1; border-radius: 14px; background: #fff; }
.builder-card form { display: grid; gap: 12px; margin-top: 18px; }
.builder-card label { display: grid; gap: 6px; color: #43526f; font-size: .84rem; font-weight: 800; }
.builder-card label span, fieldset legend span { color: #8390a5; font-weight: 500; }
.builder-card input, .builder-card textarea, .builder-card select { width: 100%; padding: 10px 11px; color: #334155; border: 1px solid #cfd7e6; border-radius: 7px; background: #fff; font: inherit; }
.steps-editor-heading { align-items: center; margin-top: 8px; }
.text-button, .remove-button { padding: 6px 9px; color: #5666d9; background: #f1f3ff; font-size: .8rem; }
.remove-button { color: #a53d3d; background: #fff1f1; }
.step-editor { display: grid; gap: 10px; padding: 16px; border: 1px solid #e2e7f0; border-radius: 10px; background: #fafbfe; }
.step-editor fieldset { display: flex; flex-wrap: wrap; gap: 7px 14px; margin: 3px 0 0; padding: 10px; border: 1px solid #dde3ed; border-radius: 8px; }
.step-editor legend { color: #43526f; font-size: .8rem; font-weight: 800; }
.step-editor .check-label { display: flex; align-items: center; gap: 5px; font-weight: 600; }
.step-editor .check-label input { width: auto; }
.create-button { width: 100%; margin-top: 4px; }
.file-card { position: sticky; top: 14px; }
.format-comparison { margin-top: 18px; padding: 14px; border-radius: 9px; background: #f4f6fb; }
.format-comparison strong { color: #384765; font-size: .84rem; }
.format-comparison p { margin-top: 5px; color: #66758b; font-size: .82rem; }
.ai-panel { display: grid; gap: 10px; margin-top: 18px; padding: 15px; color: #5b3d08; border: 1px solid #f0d7a1; border-radius: 10px; background: #fffaf0; }
.ai-panel > div { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.ai-panel > div span { padding: 4px 7px; color: #9a5c07; border-radius: 999px; background: #ffedc7; font-size: .7rem; font-weight: 800; }
.ai-panel p { color: #7a633d; font-size: .8rem; }
.ai-button { width: 100%; padding: 10px 12px; color: #fff; background: #b96d0d; }
.ai-button:not(:disabled):hover { background: #985808; }
.file-input { margin-top: 18px; }
.selected-file { margin-top: 8px; color: #4a5ab1; font-size: .82rem; font-weight: 700; overflow-wrap: anywhere; }
.file-actions { margin-top: 13px; }
.file-actions button { flex: 1; }
.validation-list { display: grid; gap: 6px; margin: 18px 0 0; padding-left: 18px; color: #66758b; font-size: .79rem; }
code { padding: 2px 4px; border-radius: 4px; background: #edf0f6; }
@media (max-width: 900px) { .builder-grid { grid-template-columns: 1fr; } .file-card { position: static; } }
@media (max-width: 680px) { .roadmap-page { padding: 28px 18px 42px; border-radius: 0; } .page-heading, .active-heading, .step-title-row, .section-heading, .file-actions { flex-direction: column; } .page-heading button, .step-action, .file-actions button { width: 100%; } .active-roadmap, .builder-card { padding: 18px; } .step-item { grid-template-columns: 30px minmax(0, 1fr); } .legacy-week-list li { align-items: stretch; flex-direction: column; } }
</style>
