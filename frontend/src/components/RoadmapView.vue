<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import axios from 'axios'

const props = defineProps({
  initialRoadmap: { type: Object, default: null },
  visible: { type: Boolean, default: false }
})
const emit = defineEmits(['start-roadmap-quiz'])

const title = ref('')
const topic = ref('')
const description = ref('')
const durationWeeks = ref(4)
const steps = ref(defaultSteps())
const inProgressRoadmaps = ref([])
const completedRoadmaps = ref([])
const expandedRoadmapId = ref(null)
const loading = ref(false)
const saving = ref(false)
const importing = ref(false)
const aiGenerating = ref(false)
const learningSources = ref([])
const sourcesLoading = ref(false)
const sourceError = ref(null)
const selectedSourceId = ref('')
const builderEditor = ref(null)
const aiPreviewMode = ref(false)
const aiPreviewSource = ref(null)
const aiPreviewRequestTopic = ref('')
const selectedFile = ref(null)
const error = ref(null)
const message = ref(null)

function defaultSteps() {
  return [
    { key: 'foundation', title: '기초', description: '', topic: '', questionTarget: 5, dependsOn: [], subtopics: [] },
    { key: 'operations', title: '운영', description: '', topic: '', questionTarget: 5, dependsOn: ['foundation'], subtopics: [] }
  ]
}

const canCreate = computed(() => topic.value.trim()
  && steps.value.length > 0
  && steps.value.every((step) => step.title.trim()
    && (step.subtopics.length > 0 || (Number(step.questionTarget) >= 1 && Number(step.questionTarget) <= 20))
    && step.subtopics.every((subtopic) => subtopic.title.trim()
      && Number(subtopic.questionTarget) >= 1 && Number(subtopic.questionTarget) <= 20)))

const selectedSource = computed(() => learningSources.value
  .find((source) => source.sourceId === Number(selectedSourceId.value)) || null)

const roadmapGroups = computed(() => [
  {
    key: 'in-progress',
    title: '진행 중인 로드맵',
    description: '여러 학습 로드맵을 동시에 진행할 수 있습니다.',
    emptyMessage: '진행 중인 로드맵이 없습니다.',
    items: inProgressRoadmaps.value
  },
  {
    key: 'completed',
    title: '완료한 로드맵',
    description: '전체 학습 목표를 달성한 로드맵을 따로 보관합니다.',
    emptyMessage: '완료한 로드맵이 아직 없습니다.',
    items: completedRoadmaps.value
  }
])

function applyInitialRoadmap() {
  if (!props.initialRoadmap?.topic) return
  topic.value = props.initialRoadmap.topic
  if (props.initialRoadmap.sourceId) selectedSourceId.value = Number(props.initialRoadmap.sourceId)
  if (!title.value) title.value = `${topic.value} 단계별 학습 로드맵`
  steps.value[0].title = `${topic.value} 기초`
  steps.value[0].topic = `${topic.value} 기초`
  steps.value[1].title = `${topic.value} 운영`
  steps.value[1].topic = `${topic.value} 운영`
  const target = Math.min(20, Math.max(1, props.initialRoadmap.questionsPerWeek || 5))
  steps.value.forEach((step) => { step.questionTarget = target })
  message.value = `추천 주제 “${topic.value}”로 단계 초안을 준비했습니다. 각 단계와 완료 기준을 확인해주세요.`
}

watch(() => props.initialRoadmap?.requestedAt, applyInitialRoadmap)
watch(() => props.visible, (visible, previousVisible) => {
  if (visible && previousVisible === false) loadRoadmaps()
})

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

function sourceLabel(sourceType, sourceDocumentId = null) {
  if (sourceType === 'AI_GENERATED' && sourceDocumentId) return '학습 자료 기반 AI 생성'
  return { FILE_IMPORT: '.roadmap.json 가져오기', MANUAL: '직접 작성', AI_GENERATED: 'AI 생성', WEEKLY: '기존 주차형' }[sourceType] ?? sourceType
}

async function loadSources() {
  sourcesLoading.value = true
  sourceError.value = null
  try {
    const response = await axios.get('/api/sources')
    learningSources.value = response.data || []
  } catch (requestError) {
    sourceError.value = requestError.response?.data?.message || '저장된 학습 자료를 불러오지 못했습니다.'
  } finally {
    sourcesLoading.value = false
  }
}

function applySelectedSource() {
  if (!selectedSource.value) return
  topic.value = selectedSource.value.title.slice(0, 120)
  if (!title.value.trim()) title.value = `${topic.value} 학습 로드맵`
}

async function loadRoadmaps(expandRoadmapId = null) {
  loading.value = true
  error.value = null
  try {
    const response = await axios.get('/api/learning-roadmaps')
    inProgressRoadmaps.value = response.data.inProgressRoadmaps || []
    completedRoadmaps.value = response.data.completedRoadmaps || []
    if (expandRoadmapId != null) expandedRoadmapId.value = expandRoadmapId
    const stillVisible = [...inProgressRoadmaps.value, ...completedRoadmaps.value]
      .some((item) => item.roadmapId === expandedRoadmapId.value)
    if (!stillVisible) expandedRoadmapId.value = null
  } catch (requestError) {
    error.value = requestError.response?.data?.message || '학습 로드맵 목록을 불러오지 못했습니다.'
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
    questionTarget: 5, dependsOn: previous ? [previous.key] : [], subtopics: []
  })
}

function nextSubtopicKey(step) {
  let number = step.subtopics.length + 1
  while (step.subtopics.some((subtopic) => subtopic.key === `subtopic-${number}`)) number += 1
  return `subtopic-${number}`
}

function addSubtopic(step) {
  step.subtopics.push({
    key: nextSubtopicKey(step), title: '', description: '', topic: '', questionTarget: 5
  })
}

function removeSubtopic(step, index) {
  step.subtopics.splice(index, 1)
}

function removeStep(index) {
  if (steps.value.length === 1) return
  const removedKey = steps.value[index].key
  steps.value.splice(index, 1)
  steps.value.forEach((step) => {
    step.dependsOn = step.dependsOn.filter((key) => key !== removedKey)
  })
}

function moveStep(index, offset) {
  const destination = index + offset
  if (destination < 0 || destination >= steps.value.length) return
  const [moved] = steps.value.splice(index, 1)
  steps.value.splice(destination, 0, moved)
  const earlierKeys = new Set()
  steps.value.forEach((step) => {
    step.dependsOn = step.dependsOn.filter((key) => earlierKeys.has(key))
    earlierKeys.add(step.key)
  })
}

function moveSubtopic(step, index, offset) {
  const destination = index + offset
  if (destination < 0 || destination >= step.subtopics.length) return
  const [moved] = step.subtopics.splice(index, 1)
  step.subtopics.splice(destination, 0, moved)
}

function toggleDependency(step, prerequisiteKey) {
  step.dependsOn = step.dependsOn.includes(prerequisiteKey)
    ? step.dependsOn.filter((key) => key !== prerequisiteKey)
    : [...step.dependsOn, prerequisiteKey]
}

function roadmapDefinition() {
  return {
    version: '1.1',
    title: title.value.trim() || `${topic.value.trim()} 단계별 학습 로드맵`,
    topic: topic.value.trim(),
    description: description.value.trim() || null,
    durationWeeks: Number(durationWeeks.value),
    steps: steps.value.map((step) => ({
      key: step.key,
      title: step.title.trim(),
      description: step.description.trim() || null,
      topic: step.topic.trim() || topic.value.trim(),
      questionTarget: step.subtopics.length
        ? step.subtopics.reduce((sum, subtopic) => sum + Math.max(1, Math.min(20, Number(subtopic.questionTarget))), 0)
        : Math.max(1, Math.min(20, Number(step.questionTarget))),
      dependsOn: [...step.dependsOn],
      subtopics: step.subtopics.map((subtopic) => ({
        key: subtopic.key,
        title: subtopic.title.trim(),
        description: subtopic.description.trim() || null,
        topic: subtopic.topic.trim() || null,
        questionTarget: Math.max(1, Math.min(20, Number(subtopic.questionTarget)))
      }))
    }))
  }
}

async function createRoadmap() {
  saving.value = true
  error.value = null
  message.value = null
  const confirmingAiPreview = aiPreviewMode.value
  try {
    const response = confirmingAiPreview
      ? await axios.post('/api/learning-roadmaps/ai/confirm', {
          definition: roadmapDefinition(),
          sourceId: aiPreviewSource.value?.sourceDocumentId || null
        })
      : await axios.post('/api/learning-roadmaps/stages', roadmapDefinition())
    await loadRoadmaps(response.data.roadmapId)
    message.value = confirmingAiPreview
      ? '검토하고 수정한 AI 로드맵을 최종 저장했습니다. 확인 단계에서는 OpenAI를 다시 호출하지 않았습니다.'
      : '대주제와 소주제 로드맵을 만들었습니다. 시작 가능한 첫 학습 단위부터 진행해보세요.'
    aiPreviewMode.value = false
    aiPreviewSource.value = null
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
    await loadRoadmaps(response.data.roadmapId)
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
    aiPreviewRequestTopic.value = topic.value.trim()
    const response = await axios.post('/api/learning-roadmaps/ai/previews', {
      topic: topic.value.trim(),
      durationWeeks: Number(durationWeeks.value),
      sourceId: selectedSource.value?.sourceId || null
    })
    applyAiPreview(response.data)
    message.value = 'AI 로드맵 미리보기를 만들었습니다. 아직 DB에 저장되지 않았습니다. 왼쪽에서 내용과 순서를 검토한 뒤 최종 저장해주세요.'
    await nextTick()
    builderEditor.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } catch (requestError) {
    error.value = requestError.response?.data?.message || 'AI 로드맵을 만들지 못했습니다.'
  } finally {
    aiGenerating.value = false
  }
}

function applyAiPreview(preview) {
  const definition = preview.definition
  title.value = definition.title
  topic.value = definition.topic
  description.value = definition.description || ''
  durationWeeks.value = definition.durationWeeks
  steps.value = (definition.steps || []).map((step) => ({
    key: step.key,
    title: step.title,
    description: step.description || '',
    topic: step.topic || '',
    questionTarget: Number(step.questionTarget),
    dependsOn: [...(step.dependsOn || [])],
    subtopics: (step.subtopics || []).map((subtopic) => ({
      key: subtopic.key,
      title: subtopic.title,
      description: subtopic.description || '',
      topic: subtopic.topic || '',
      questionTarget: Number(subtopic.questionTarget)
    }))
  }))
  aiPreviewSource.value = {
    sourceDocumentId: preview.sourceDocumentId,
    sourceDocumentTitle: preview.sourceDocumentTitle,
    sourceDocumentUri: preview.sourceDocumentUri
  }
  aiPreviewMode.value = true
}

function cancelAiPreview() {
  const requestedTopic = aiPreviewRequestTopic.value
  aiPreviewMode.value = false
  aiPreviewSource.value = null
  title.value = ''
  topic.value = requestedTopic
  description.value = ''
  steps.value = defaultSteps()
  message.value = 'AI 미리보기를 취소했습니다. 저장된 로드맵 데이터는 없습니다.'
}

function downloadSample() {
  const sample = {
    version: '1.1',
    title: 'Kubernetes 단계별 학습 로드맵',
    topic: 'Kubernetes',
    description: '기초 개념을 완료한 뒤 네트워크와 운영 단계로 진행합니다.',
    durationWeeks: 4,
    steps: [
      {
        key: 'foundation', title: 'Kubernetes 기초', description: '핵심 오브젝트 이해', topic: 'Kubernetes 기초', questionTarget: 15, dependsOn: [],
        subtopics: [
          { key: 'pod', title: 'Pod', description: '컨테이너 실행 단위', topic: 'Kubernetes Pod', questionTarget: 5 },
          { key: 'service', title: 'Service', description: '안정적인 네트워크 접근', topic: 'Kubernetes Service', questionTarget: 5 },
          { key: 'deployment', title: 'Deployment', description: '선언적 배포와 복제본 관리', topic: 'Kubernetes Deployment', questionTarget: 5 }
        ]
      },
      {
        key: 'operations', title: 'Kubernetes 운영', description: '운영과 장애 대응', topic: 'Kubernetes 운영', questionTarget: 5, dependsOn: ['foundation'], subtopics: []
      }
    ]
  }
  const url = URL.createObjectURL(new Blob([JSON.stringify(sample, null, 2)], { type: 'application/json' }))
  const link = document.createElement('a')
  link.href = url
  link.download = 'kubernetes.roadmap.json'
  link.click()
  URL.revokeObjectURL(url)
}

function majorTopicsFor(roadmap) {
  if (roadmap.majorTopics?.length) return roadmap.majorTopics
  return (roadmap.steps || []).map((step) => ({ ...step, stepId: step.stepId, subtopics: [] }))
}

function prerequisiteLabel(roadmap, majorTopic) {
  if (!majorTopic.prerequisiteKeys?.length) return '선행 대주제 없음'
  const majorTopics = majorTopicsFor(roadmap)
  return `선행 대주제: ${majorTopic.prerequisiteKeys.map((key) => majorTopics.find((item) => item.key === key)?.title || key).join(', ')}`
}

function toggleRoadmap(roadmapId) {
  expandedRoadmapId.value = expandedRoadmapId.value === roadmapId ? null : roadmapId
}

function startLearningUnit(roadmap, unit) {
  if (unit.status === 'LOCKED' || unit.status === 'COMPLETED' || !unit.stepId) return
  emit('start-roadmap-quiz', {
    roadmapId: roadmap.roadmapId,
    roadmapStepId: unit.stepId,
    topic: unit.topic,
    numberOfQuestions: Math.min(20, Math.max(1, unit.questionTarget - unit.completedQuestions))
  })
}

function startWeekQuiz(roadmap, week) {
  emit('start-roadmap-quiz', {
    roadmapId: roadmap.roadmapId,
    topic: week.topic,
    numberOfQuestions: Math.min(20, Math.max(1, week.plannedQuestions - week.completedQuestions))
  })
}

onMounted(() => {
  applyInitialRoadmap()
  loadRoadmaps()
  loadSources()
})
</script>

<template>
  <section class="roadmap-page" aria-labelledby="roadmap-title">
    <div class="page-heading">
      <div>
        <p class="eyebrow">LEARNING PATH</p>
        <h2 id="roadmap-title">단계별 학습 로드맵</h2>
        <p>대주제 안의 소주제를 순서대로 학습합니다. 소주제가 없으면 대주제 자체가 하나의 학습 단계가 됩니다.</p>
      </div>
      <button type="button" class="secondary-button" :disabled="loading" @click="loadRoadmaps()">
        {{ loading ? '불러오는 중...' : '진행률 새로고침' }}
      </button>
    </div>

    <p v-if="error" class="message error-message" role="alert">{{ error }}</p>
    <p v-if="message" class="message success-message">{{ message }}</p>

    <section class="roadmap-library" aria-label="학습 로드맵 목록">
      <section v-for="group in roadmapGroups" :key="group.key" class="roadmap-group" :class="`${group.key}-group`">
        <div class="roadmap-group-heading">
          <div>
            <p class="section-kicker">{{ group.key === 'in-progress' ? 'IN PROGRESS' : 'COMPLETED' }}</p>
            <h3>{{ group.title }}</h3>
            <p>{{ group.description }}</p>
          </div>
          <span class="roadmap-count">{{ group.items.length }}</span>
        </div>

        <p v-if="!group.items.length" class="empty-roadmap-list">{{ group.emptyMessage }}</p>
        <article v-for="roadmap in group.items" :key="roadmap.roadmapId" class="roadmap-accordion" :class="{ expanded: expandedRoadmapId === roadmap.roadmapId }">
          <button
            type="button"
            class="roadmap-summary-button"
            :aria-expanded="expandedRoadmapId === roadmap.roadmapId"
            :aria-controls="`roadmap-detail-${roadmap.roadmapId}`"
            @click="toggleRoadmap(roadmap.roadmapId)"
          >
            <span class="roadmap-summary-copy">
              <span class="roadmap-summary-state">{{ roadmap.completed ? '완료' : sourceLabel(roadmap.sourceType, roadmap.sourceDocumentId) }}</span>
              <strong>{{ roadmap.title }}</strong>
              <small>{{ roadmap.topic }} · {{ roadmap.durationWeeks }}주 · {{ roadmap.completedQuestions }}/{{ roadmap.totalPlannedQuestions }}문제</small>
            </span>
            <span class="roadmap-summary-progress">
              <span>{{ roadmap.progressPercent }}%</span>
              <i aria-hidden="true">{{ expandedRoadmapId === roadmap.roadmapId ? '−' : '+' }}</i>
            </span>
          </button>

          <div v-if="expandedRoadmapId === roadmap.roadmapId" :id="`roadmap-detail-${roadmap.roadmapId}`" class="roadmap-details">
      <div class="active-heading">
        <div>
          <p class="section-kicker">{{ roadmap.completed ? 'COMPLETED ROADMAP' : 'ACTIVE ROADMAP' }} · {{ sourceLabel(roadmap.sourceType, roadmap.sourceDocumentId) }}</p>
          <h3>{{ roadmap.title }}</h3>
          <p>{{ roadmap.topic }} · {{ roadmap.durationWeeks }}주 · {{ formatDate(roadmap.startDate) }} 시작</p>
          <p v-if="roadmap.description" class="roadmap-description">{{ roadmap.description }}</p>
          <p v-if="roadmap.sourceDocumentId" class="roadmap-source">
            근거 자료 #{{ roadmap.sourceDocumentId }}
            <a v-if="roadmap.sourceDocumentUri" :href="roadmap.sourceDocumentUri" target="_blank" rel="noopener noreferrer">{{ roadmap.sourceDocumentTitle }}</a>
            <span v-else>{{ roadmap.sourceDocumentTitle }}</span>
          </p>
        </div>
        <span class="completion-label" :class="{ completed: roadmap.completed }">
          {{ roadmap.completed ? '전체 단계 완료' : `진행률 ${roadmap.progressPercent}%` }}
        </span>
      </div>

      <div class="overall-progress" aria-label="전체 학습 로드맵 진행률">
        <div><span :style="{ width: `${roadmap.progressPercent}%` }"></span></div>
        <strong>{{ roadmap.completedQuestions }}/{{ roadmap.totalPlannedQuestions }}문제</strong>
      </div>

      <ol v-if="majorTopicsFor(roadmap).length" class="major-topic-list">
        <li v-for="(majorTopic, index) in majorTopicsFor(roadmap)" :key="majorTopic.key" class="major-topic-item" :class="majorTopic.status.toLowerCase()">
          <div class="step-marker"><span>{{ index + 1 }}</span><i v-if="index < majorTopicsFor(roadmap).length - 1"></i></div>
          <div class="major-topic-card">
            <div class="major-topic-heading">
              <div>
                <p class="dependency">{{ prerequisiteLabel(roadmap, majorTopic) }}</p>
                <p class="topic-level">대주제 {{ index + 1 }}</p>
                <h4>{{ majorTopic.title }}</h4>
                <p v-if="majorTopic.description">{{ majorTopic.description }}</p>
              </div>
              <span class="status-badge">{{ statusLabel(majorTopic.status) }}</span>
            </div>
            <div class="step-progress">
              <div><span :style="{ width: `${majorTopic.progressPercent}%` }"></span></div>
              <span>{{ majorTopic.completedQuestions }}/{{ majorTopic.questionTarget }}문제</span>
            </div>

            <ol v-if="majorTopic.subtopics?.length" class="subtopic-list">
              <li v-for="(subtopic, subtopicIndex) in majorTopic.subtopics" :key="subtopic.stepId" class="subtopic-card" :class="subtopic.status.toLowerCase()">
                <div class="subtopic-heading">
                  <span class="subtopic-number">{{ index + 1 }}.{{ subtopicIndex + 1 }}</span>
                  <div>
                    <p class="topic-level">소주제</p>
                    <h5>{{ subtopic.title }}</h5>
                    <p v-if="subtopic.description">{{ subtopic.description }}</p>
                    <p class="step-topic">문제 주제: {{ subtopic.topic }}</p>
                  </div>
                  <span class="status-badge">{{ statusLabel(subtopic.status) }}</span>
                </div>
                <div class="step-progress compact-progress">
                  <div><span :style="{ width: `${subtopic.progressPercent}%` }"></span></div>
                  <span>{{ subtopic.completedQuestions }}/{{ subtopic.questionTarget }}문제</span>
                </div>
                <button v-if="!roadmap.completed" type="button" class="primary-button step-action" :disabled="subtopic.status === 'LOCKED' || subtopic.status === 'COMPLETED'" @click="startLearningUnit(roadmap, subtopic)">
                  {{ subtopic.status === 'LOCKED' ? '앞 소주제 학습 필요' : subtopic.status === 'COMPLETED' ? '소주제 완료' : '이 소주제 학습 시작' }}
                </button>
              </li>
            </ol>

            <button v-else-if="!roadmap.completed" type="button" class="primary-button step-action major-action" :disabled="majorTopic.status === 'LOCKED' || majorTopic.status === 'COMPLETED'" @click="startLearningUnit(roadmap, majorTopic)">
              {{ majorTopic.status === 'LOCKED' ? '선행 대주제 학습 필요' : majorTopic.status === 'COMPLETED' ? '대주제 완료' : '이 대주제 학습 시작' }}
            </button>
          </div>
        </li>
      </ol>

      <ol v-else class="legacy-week-list">
        <li v-for="week in roadmap.weeks" :key="week.weekNumber">
          <div><strong>{{ week.weekNumber }}주차 · {{ week.topic }}</strong><p>{{ week.completedQuestions }}/{{ week.plannedQuestions }}문제 · {{ statusLabel(week.status) }}</p></div>
          <button v-if="!roadmap.completed" type="button" class="primary-button" :disabled="week.status === 'UPCOMING' || week.status === 'COMPLETED'" @click="startWeekQuiz(roadmap, week)">학습 시작</button>
        </li>
      </ol>
          </div>
        </article>
      </section>
    </section>

    <div class="builder-grid">
      <section ref="builderEditor" class="builder-card" :class="{ 'ai-preview-editor': aiPreviewMode }" aria-labelledby="manual-builder-title">
        <div class="section-heading">
          <div>
            <p class="section-kicker">{{ aiPreviewMode ? 'AI PREVIEW EDITOR' : 'DIRECT BUILDER' }}</p>
            <h3 id="manual-builder-title">{{ aiPreviewMode ? 'AI 생성 결과 검토·수정' : '대주제·소주제 직접 작성' }}</h3>
          </div>
          <span class="cost-badge">{{ aiPreviewMode ? '아직 저장 안 됨' : 'AI 비용 없음' }}</span>
        </div>
        <div v-if="aiPreviewMode" class="ai-preview-banner">
          <strong>편집 가능한 미리보기</strong>
          <p>이름·설명·순서·목표 문제 수를 수정할 수 있습니다. 최종 저장은 OpenAI를 다시 호출하지 않습니다.</p>
          <p v-if="aiPreviewSource?.sourceDocumentId">
            근거 자료 #{{ aiPreviewSource.sourceDocumentId }}
            <a v-if="aiPreviewSource.sourceDocumentUri" :href="aiPreviewSource.sourceDocumentUri" target="_blank" rel="noopener noreferrer">{{ aiPreviewSource.sourceDocumentTitle }}</a>
            <span v-else>{{ aiPreviewSource.sourceDocumentTitle }}</span>
          </p>
        </div>
        <form @submit.prevent="createRoadmap">
          <label>로드맵 이름 <span>(선택)</span><input v-model="title" maxlength="120" placeholder="예: Kubernetes 운영 역량 로드맵" /></label>
          <label>전체 학습 주제<input v-model="topic" maxlength="120" required placeholder="예: Kubernetes" /></label>
          <label>설명 <span>(선택)</span><textarea v-model="description" maxlength="2000" rows="2" placeholder="이 로드맵의 목표를 적어주세요."></textarea></label>
          <label>예상 기간<select v-model.number="durationWeeks"><option :value="2">2주</option><option :value="4">4주</option><option :value="8">8주</option><option :value="12">12주</option></select></label>

          <div class="steps-editor-heading"><strong>대주제 <span>최대 10개</span></strong><button type="button" class="text-button" :disabled="steps.length >= 10" @click="addStep">+ 대주제 추가</button></div>
          <article v-for="(step, index) in steps" :key="step.key" class="step-editor">
            <div class="editor-heading">
              <strong>대주제 {{ index + 1 }}</strong>
              <div class="editor-actions">
                <button type="button" class="order-button" :disabled="index === 0" :aria-label="`${step.title || `대주제 ${index + 1}`} 위로 이동`" @click="moveStep(index, -1)">↑</button>
                <button type="button" class="order-button" :disabled="index === steps.length - 1" :aria-label="`${step.title || `대주제 ${index + 1}`} 아래로 이동`" @click="moveStep(index, 1)">↓</button>
                <button type="button" class="remove-button" :disabled="steps.length === 1" @click="removeStep(index)">삭제</button>
              </div>
            </div>
            <label>대주제 이름<input v-model="step.title" maxlength="120" required placeholder="예: Kubernetes 기초" /></label>
            <label>대주제 문제 생성 주제 <span>(소주제가 없을 때 사용)</span><input v-model="step.topic" maxlength="120" placeholder="예: Kubernetes 기초" /></label>
            <label>학습 내용 <span>(선택)</span><textarea v-model="step.description" maxlength="1000" rows="2" placeholder="이 대주제에서 익힐 내용을 적어주세요."></textarea></label>
            <label v-if="!step.subtopics.length">대주제 완료 기준<input v-model.number="step.questionTarget" type="number" min="1" max="20" required /> 문제 풀이</label>
            <fieldset v-if="index > 0">
              <legend>선행 대주제 <span>(복수 선택 가능)</span></legend>
              <label v-for="candidate in steps.slice(0, index)" :key="candidate.key" class="check-label">
                <input type="checkbox" :checked="step.dependsOn.includes(candidate.key)" @change="toggleDependency(step, candidate.key)" /> {{ candidate.title || candidate.key }}
              </label>
            </fieldset>

            <div class="subtopic-editor-heading">
              <div><strong>소주제</strong><span>선택 사항 · 등록 순서대로 진행 · 최대 10개</span></div>
              <button type="button" class="text-button" :disabled="step.subtopics.length >= 10" @click="addSubtopic(step)">+ 소주제 추가</button>
            </div>
            <p v-if="!step.subtopics.length" class="no-subtopic-hint">소주제가 없으므로 이 대주제 자체를 하나의 학습 단계로 진행합니다.</p>
            <article v-for="(subtopic, subtopicIndex) in step.subtopics" :key="subtopic.key" class="subtopic-editor">
              <div class="editor-heading">
                <strong>{{ index + 1 }}.{{ subtopicIndex + 1 }} 소주제</strong>
                <div class="editor-actions">
                  <button type="button" class="order-button" :disabled="subtopicIndex === 0" :aria-label="`${subtopic.title || '소주제'} 위로 이동`" @click="moveSubtopic(step, subtopicIndex, -1)">↑</button>
                  <button type="button" class="order-button" :disabled="subtopicIndex === step.subtopics.length - 1" :aria-label="`${subtopic.title || '소주제'} 아래로 이동`" @click="moveSubtopic(step, subtopicIndex, 1)">↓</button>
                  <button type="button" class="remove-button" @click="removeSubtopic(step, subtopicIndex)">삭제</button>
                </div>
              </div>
              <label>소주제 이름<input v-model="subtopic.title" maxlength="120" required placeholder="예: Pod" /></label>
              <label>문제 생성 주제 <span>(비우면 대주제와 소주제 이름 조합)</span><input v-model="subtopic.topic" maxlength="120" placeholder="예: Kubernetes Pod" /></label>
              <label>학습 내용 <span>(선택)</span><textarea v-model="subtopic.description" maxlength="1000" rows="2" placeholder="이 소주제에서 익힐 내용을 적어주세요."></textarea></label>
              <label>완료 기준<input v-model.number="subtopic.questionTarget" type="number" min="1" max="20" required /> 문제 풀이</label>
            </article>
          </article>
          <div class="builder-save-actions">
            <button type="submit" class="primary-button create-button" :disabled="saving || !canCreate">{{ saving ? '저장 중...' : aiPreviewMode ? '검토한 AI 로드맵 최종 저장' : '대주제·소주제 로드맵 생성' }}</button>
            <button v-if="aiPreviewMode" type="button" class="secondary-button" :disabled="saving" @click="cancelAiPreview">미리보기 취소</button>
          </div>
        </form>
      </section>

      <section class="builder-card file-card" aria-labelledby="file-builder-title">
        <div class="section-heading">
          <div><p class="section-kicker">PORTABLE FORMAT</p><h3 id="file-builder-title">.roadmap.json 가져오기</h3></div>
          <span class="format-badge">v1.1</span>
        </div>
        <p>단계, 완료 기준, 선행 관계를 구조적으로 저장합니다. Git에서 변경 이력을 볼 수 있고 서버가 형식을 검증합니다.</p>
        <div class="ai-panel">
          <div><strong>주제로 AI 로드맵 생성</strong><span>OpenAI API 토큰 사용</span></div>
          <p>AI가 주제의 범위·난이도·예상 기간을 분석해 필요한 대주제, 소주제와 목표 문제 수를 직접 결정합니다. 정해진 개수를 채우기 위해 내용을 억지로 늘리거나 줄이지 않습니다.</p>
          <label>근거 학습 자료
            <select v-model="selectedSourceId" :disabled="sourcesLoading" @change="applySelectedSource">
              <option value="">자료 없이 주제만 사용</option>
              <option v-for="source in learningSources" :key="source.sourceId" :value="source.sourceId">#{{ source.sourceId }} {{ source.title }}</option>
            </select>
          </label>
          <p v-if="sourceError" class="source-load-error">{{ sourceError }}</p>
          <div v-if="selectedSource" class="selected-source-summary">
            <strong>{{ selectedSource.title }}</strong>
            <span>{{ selectedSource.contentLength.toLocaleString() }}자 · {{ selectedSource.chunkCount }}개 조각</span>
            <a v-if="selectedSource.sourceUri" :href="selectedSource.sourceUri" target="_blank" rel="noopener noreferrer">원문 확인</a>
          </div>
          <p v-if="selectedSource" class="source-disclosure">생성 시 이 자료의 앞부분 최대 8개 조각(약 9,600자)이 OpenAI로 전송됩니다. 자료 안의 명령문은 실행하지 않고 학습 내용으로만 취급합니다.</p>
          <button type="button" class="ai-button" :disabled="aiGenerating || !topic.trim()" @click="createAiRoadmap">{{ aiGenerating ? 'AI가 단계 구성 중...' : selectedSource ? '선택 자료로 AI 미리보기 생성' : 'AI 로드맵 미리보기 생성' }}</button>
          <p class="adaptive-generation-note">목표 문제 수는 전체 학습량이며 한 번에 모두 생성하지 않습니다. 학습 시작 시 최대 20문제씩 나누어 생성하므로 API 비용과 풀이 부담을 제어할 수 있습니다.</p>
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
          <li>지원 버전 <code>1.0</code>·<code>1.1</code>, 대주제와 대주제별 소주제 각각 최대 10개</li>
          <li>소주제는 등록 순서대로 진행하며, 없는 대주제는 단독 단계로 진행</li>
          <li>없는 대주제 참조·자기 참조·순환 의존성은 저장 전 차단</li>
          <li>가져오기 자체는 OpenAI API와 토큰을 사용하지 않음</li>
        </ul>
      </section>
    </div>
  </section>
</template>

<style scoped>
.roadmap-page { max-width: 1200px; margin: 0 auto; padding: 36px 40px 52px; color: var(--ink-soft); background: var(--surface); }
.page-heading, .active-heading, .section-heading, .major-topic-heading, .editor-heading, .steps-editor-heading, .subtopic-editor-heading, .file-actions { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.eyebrow, .section-kicker { margin: 0 0 6px; color: var(--accent); font-size: .74rem; font-weight: 800; letter-spacing: .08em; }
h2, h3, h4, p { margin: 0; }
h2 { color: var(--ink); font-size: clamp(1.7rem, 4vw, 2.25rem); }
h3 { color: var(--ink); font-size: 1.3rem; }
h4 { color: var(--ink); font-size: 1.15rem; }
h5 { margin: 0; color: var(--ink-soft); font-size: 1rem; }
.page-heading > div > p:not(.eyebrow), .active-heading p, .builder-card > p { margin-top: 6px; color: var(--muted); }
button { border: 0; border-radius: 8px; font: inherit; font-weight: 750; cursor: pointer; }
button:disabled { cursor: not-allowed; opacity: .5; }
button:focus-visible, input:focus-visible, textarea:focus-visible, select:focus-visible { outline: 3px solid rgb(198 78 50 / 24%); outline-offset: 2px; }
.primary-button { padding: 10px 14px; color: #fff; background: var(--ink); }
.primary-button:not(:disabled):hover { background: var(--ink-soft); }
.secondary-button { padding: 10px 14px; color: var(--ink); border: 1px solid var(--line-strong); background: var(--surface); }
.message { margin-top: 18px; padding: 13px 15px; border-radius: 8px; font-weight: 700; }
.error-message { color: #b42318; border: 1px solid #fecdca; background: #fef3f2; }
.success-message { color: #1f6b51; border: 1px solid #bde7ce; background: #edfbf2; }
.roadmap-library { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; margin-top: 28px; align-items: start; }
.roadmap-group { min-width: 0; padding: 22px; border: 1px solid var(--line); border-radius: 14px; background: var(--surface-subtle); }
.roadmap-group-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 14px; }
.roadmap-group-heading > div > p:not(.section-kicker) { margin-top: 5px; color: var(--muted); font-size: .84rem; }
.roadmap-count { display: grid; min-width: 30px; height: 30px; padding: 0 8px; color: var(--ink-soft); border: 1px solid var(--line); border-radius: 999px; background: var(--surface); place-items: center; font-size: .8rem; font-weight: 800; }
.empty-roadmap-list { padding: 18px; color: var(--muted); border: 1px dashed var(--line-strong); border-radius: 10px; background: var(--surface); text-align: center; font-size: .84rem; }
.roadmap-accordion { overflow: hidden; margin-top: 10px; border: 1px solid var(--line); border-radius: 11px; background: var(--surface); }
.roadmap-accordion.expanded { border-color: var(--line-strong); box-shadow: 0 8px 24px rgb(17 24 39 / 6%); }
.roadmap-summary-button { display: flex; width: 100%; align-items: center; justify-content: space-between; gap: 14px; padding: 16px; color: var(--ink); border-radius: 0; background: var(--surface); text-align: left; }
.roadmap-summary-button:hover { background: var(--surface-subtle); }
.roadmap-summary-copy { display: grid; min-width: 0; gap: 4px; }
.roadmap-summary-copy strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.roadmap-summary-copy small { overflow: hidden; color: var(--muted); font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.roadmap-summary-state { color: var(--accent-strong); font-size: .68rem; font-weight: 800; letter-spacing: .04em; }
.completed-group .roadmap-summary-state { color: #1f6b51; }
.roadmap-summary-progress { display: flex; flex: 0 0 auto; align-items: center; gap: 10px; color: var(--muted); font-size: .8rem; }
.roadmap-summary-progress i { display: grid; width: 28px; height: 28px; color: var(--ink-soft); border: 1px solid var(--line); border-radius: 50%; background: var(--surface); place-items: center; font-size: 1rem; font-style: normal; }
.roadmap-details { padding: 22px; border-top: 1px solid var(--line); background: var(--surface-subtle); }
.completion-label, .cost-badge, .format-badge, .status-badge { flex: 0 0 auto; padding: 6px 9px; border-radius: 999px; font-size: .76rem; font-weight: 800; }
.completion-label, .format-badge { color: var(--accent-strong); background: var(--accent-soft); }
.completion-label.completed, .cost-badge { color: #1f6b51; background: #e7f7ef; }
.roadmap-description { max-width: 720px; }
.roadmap-source { overflow-wrap: anywhere; }
.roadmap-source a { color: var(--ink); font-weight: 750; }
.overall-progress, .step-progress { display: flex; align-items: center; gap: 12px; }
.overall-progress { margin-top: 22px; }
.overall-progress > div, .step-progress > div { flex: 1; overflow: hidden; height: 9px; border-radius: 999px; background: #e8e8e3; }
.overall-progress > div span, .step-progress > div span { display: block; height: 100%; border-radius: inherit; background: var(--accent); }
.overall-progress strong { color: var(--ink-soft); font-size: .86rem; white-space: nowrap; }
.major-topic-list { margin: 26px 0 0; padding: 0; list-style: none; }
.major-topic-item { display: grid; grid-template-columns: 38px minmax(0, 1fr); gap: 10px; }
.step-marker { display: flex; align-items: center; flex-direction: column; }
.step-marker span { display: grid; flex: 0 0 30px; width: 30px; color: #fff; border-radius: 50%; background: var(--ink); place-items: center; font-size: .8rem; font-weight: 800; }
.step-marker i { width: 2px; height: 100%; min-height: 24px; background: var(--line); }
.locked .step-marker span { background: var(--muted); }
.completed .step-marker span { background: #2f9b72; }
.major-topic-card { margin-bottom: 14px; padding: 19px; border: 1px solid var(--line); border-radius: 11px; background: var(--surface); }
.major-topic-heading p, .subtopic-heading p { margin-top: 4px; color: var(--muted); font-size: .84rem; }
.major-topic-heading .dependency { margin: 0 0 4px; color: var(--accent); font-size: .72rem; font-weight: 800; }
.topic-level { color: var(--muted) !important; font-size: .68rem !important; font-weight: 800; letter-spacing: .08em; text-transform: uppercase; }
.subtopic-heading .step-topic { color: var(--ink-soft); font-weight: 700; }
.status-badge { color: var(--accent-strong); background: var(--accent-soft); }
.locked .status-badge { color: var(--muted); background: var(--surface-subtle); }
.completed .status-badge { color: #1f6b51; background: #e7f7ef; }
.step-progress { margin-top: 12px; }
.step-progress > div { height: 7px; }
.step-progress > span { color: var(--muted); font-size: .78rem; font-weight: 700; }
.step-action { margin-top: 13px; }
.major-action { width: 100%; }
.subtopic-list { display: grid; gap: 10px; margin: 18px 0 0; padding: 14px 0 0; border-top: 1px solid var(--line); list-style: none; }
.subtopic-card { padding: 14px; border: 1px solid var(--line); border-radius: 9px; background: var(--surface-subtle); }
.subtopic-heading { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: start; gap: 11px; }
.subtopic-number { display: grid; min-width: 38px; height: 28px; padding: 0 7px; color: var(--accent-strong); border-radius: 999px; background: var(--accent-soft); place-items: center; font-size: .74rem; font-weight: 800; }
.compact-progress { margin-left: 49px; }
.subtopic-card .step-action { width: calc(100% - 49px); margin-left: 49px; }
.legacy-week-list { display: grid; gap: 10px; margin: 24px 0 0; padding: 0; list-style: none; }
.legacy-week-list li { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px; border: 1px solid var(--line); border-radius: 9px; background: var(--surface); }
.legacy-week-list p { margin-top: 3px; color: var(--muted); font-size: .82rem; }
.builder-grid { display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(300px, .65fr); gap: 18px; margin-top: 26px; align-items: start; }
.builder-card { padding: 24px; border: 1px solid var(--line); border-radius: 14px; background: var(--surface); }
.builder-card.ai-preview-editor { border-color: #d6b66f; box-shadow: 0 12px 30px rgb(122 99 61 / 10%); }
.ai-preview-banner { margin-top: 16px; padding: 14px; border: 1px solid #ecd29b; border-radius: 9px; background: #fffaf0; }
.ai-preview-banner strong { color: #7a4a08; }
.ai-preview-banner p { margin-top: 5px; color: #7a633d; font-size: .82rem; }
.ai-preview-banner a { color: #754507; font-weight: 750; overflow-wrap: anywhere; }
.builder-card form { display: grid; gap: 12px; margin-top: 18px; }
.builder-card label { display: grid; gap: 6px; color: var(--ink-soft); font-size: .84rem; font-weight: 800; }
.builder-card label span, fieldset legend span { color: var(--muted); font-weight: 500; }
.builder-card input, .builder-card textarea, .builder-card select { width: 100%; padding: 10px 11px; color: var(--ink); border: 1px solid var(--line-strong); border-radius: 7px; background: var(--surface); font: inherit; }
.steps-editor-heading { align-items: center; margin-top: 8px; }
.subtopic-editor-heading { align-items: center; margin-top: 6px; padding-top: 12px; border-top: 1px solid var(--line); }
.subtopic-editor-heading > div { display: grid; gap: 2px; }
.subtopic-editor-heading span { color: var(--muted); font-size: .75rem; }
.text-button, .remove-button { padding: 6px 9px; color: var(--accent-strong); background: var(--accent-soft); font-size: .8rem; }
.remove-button { color: #a53d3d; background: #fff1f1; }
.editor-actions { display: flex; align-items: center; gap: 5px; }
.order-button { min-width: 30px; padding: 6px 8px; color: var(--ink-soft); border: 1px solid var(--line); background: var(--surface); font-size: .8rem; }
.step-editor { display: grid; gap: 10px; padding: 16px; border: 1px solid var(--line); border-radius: 10px; background: var(--surface-subtle); }
.subtopic-editor { display: grid; gap: 9px; margin-left: 18px; padding: 14px; border: 1px solid var(--line); border-left: 3px solid var(--accent); border-radius: 9px; background: var(--surface); }
.no-subtopic-hint { padding: 10px 12px; color: var(--muted); border: 1px dashed var(--line-strong); border-radius: 8px; background: var(--surface); font-size: .78rem; }
.step-editor fieldset { display: flex; flex-wrap: wrap; gap: 7px 14px; margin: 3px 0 0; padding: 10px; border: 1px solid var(--line); border-radius: 8px; }
.step-editor legend { color: var(--ink-soft); font-size: .8rem; font-weight: 800; }
.step-editor .check-label { display: flex; align-items: center; gap: 5px; font-weight: 600; }
.step-editor .check-label input { width: auto; }
.create-button { width: 100%; margin-top: 4px; }
.builder-save-actions { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: stretch; gap: 8px; }
.builder-save-actions .secondary-button { margin-top: 4px; }
.file-card { position: sticky; top: 14px; }
.format-comparison { margin-top: 18px; padding: 14px; border-radius: 9px; background: var(--surface-subtle); }
.format-comparison strong { color: var(--ink-soft); font-size: .84rem; }
.format-comparison p { margin-top: 5px; color: var(--muted); font-size: .82rem; }
.ai-panel { display: grid; gap: 10px; margin-top: 18px; padding: 15px; color: #5b3d08; border: 1px solid #f0d7a1; border-radius: 10px; background: #fffaf0; }
.ai-panel > div { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.ai-panel > div span { padding: 4px 7px; color: #9a5c07; border-radius: 999px; background: #ffedc7; font-size: .7rem; font-weight: 800; }
.ai-panel p { color: #7a633d; font-size: .8rem; }
.ai-panel .selected-source-summary { display: grid; align-items: start; justify-content: stretch; gap: 4px; padding: 10px; border: 1px solid #ecd29b; border-radius: 8px; background: #fff; }
.selected-source-summary strong { color: var(--ink-soft); font-size: .82rem; }
.selected-source-summary span, .selected-source-summary a { color: var(--muted); font-size: .75rem; }
.selected-source-summary a { color: #8a5107; font-weight: 750; }
.ai-panel .source-disclosure { padding: 9px 10px; border-left: 3px solid #b96d0d; background: #fff5df; }
.ai-panel .adaptive-generation-note { padding-top: 8px; border-top: 1px solid #ecd29b; }
.ai-panel .source-load-error { color: var(--danger); }
.ai-button { width: 100%; padding: 10px 12px; color: #fff; background: #b96d0d; }
.ai-button:not(:disabled):hover { background: #985808; }
.file-input { margin-top: 18px; }
.selected-file { margin-top: 8px; color: var(--accent); font-size: .82rem; font-weight: 700; overflow-wrap: anywhere; }
.file-actions { margin-top: 13px; }
.file-actions button { flex: 1; }
.validation-list { display: grid; gap: 6px; margin: 18px 0 0; padding-left: 18px; color: var(--muted); font-size: .79rem; }
code { padding: 2px 4px; border-radius: 4px; background: var(--surface-subtle); }
@media (max-width: 900px) { .roadmap-library, .builder-grid { grid-template-columns: 1fr; } .file-card { position: static; } }
@media (max-width: 680px) { .roadmap-page { padding: 28px 18px 42px; border-radius: 0; } .page-heading, .active-heading, .major-topic-heading, .section-heading, .file-actions { flex-direction: column; } .page-heading button, .step-action, .file-actions button { width: 100%; } .roadmap-group, .builder-card { padding: 18px; } .roadmap-details { padding: 16px; } .roadmap-summary-button { align-items: flex-start; } .roadmap-summary-copy small { white-space: normal; } .major-topic-item { grid-template-columns: 30px minmax(0, 1fr); } .major-topic-card { padding: 14px; } .subtopic-heading { grid-template-columns: auto minmax(0, 1fr); } .subtopic-heading .status-badge { grid-column: 2; } .compact-progress, .subtopic-card .step-action { width: 100%; margin-left: 0; } .subtopic-editor { margin-left: 0; } .legacy-week-list li { align-items: stretch; flex-direction: column; } .builder-save-actions { grid-template-columns: 1fr; } .builder-save-actions .secondary-button { width: 100%; } }
</style>
