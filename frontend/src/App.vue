<script setup>
import { defineAsyncComponent, ref } from 'vue'
import QuizGenerator from './components/QuizGenerator.vue'
import LearningHistory from './components/LearningHistory.vue'

const DashboardView = defineAsyncComponent(() => import('./components/DashboardView.vue'))
const RoadmapView = defineAsyncComponent(() => import('./components/RoadmapView.vue'))
const ReviewQueue = defineAsyncComponent(() => import('./components/ReviewQueue.vue'))
const SourceLibrary = defineAsyncComponent(() => import('./components/SourceLibrary.vue'))
const QualityEvaluationView = defineAsyncComponent(() => import('./components/QualityEvaluationView.vue'))

const activeView = ref('quiz')
const historyMounted = ref(false)
const roadmapMounted = ref(false)
const reviewMounted = ref(false)
const sourceMounted = ref(false)
const qualityMounted = ref(false)
const recommendedQuiz = ref(null)
const roadmapDraft = ref(null)

function showDashboard() {
  activeView.value = 'dashboard'
}

function showHistory() {
  historyMounted.value = true
  activeView.value = 'history'
}

function showRoadmap() {
  roadmapMounted.value = true
  activeView.value = 'roadmap'
}

function showReview() {
  reviewMounted.value = true
  activeView.value = 'review'
}

function showSources() {
  sourceMounted.value = true
  activeView.value = 'sources'
}

function showQuality() {
  qualityMounted.value = true
  activeView.value = 'quality'
}

function startRecommendedQuiz(recommendation) {
  // 추천은 입력값만 채운다. 사용자가 데모/AI 생성 방식을 선택한 뒤 명시적으로 생성해야 비용이 발생한다.
  recommendedQuiz.value = {
    topic: recommendation.topic,
    numberOfQuestions: recommendation.recommendedQuestionCount,
    priority: recommendation.priority,
    requestedAt: Date.now()
  }
  activeView.value = 'quiz'
}

function createRoadmapFromRecommendation(recommendation) {
  roadmapDraft.value = {
    topic: recommendation.topic,
    questionsPerWeek: Math.max(5, recommendation.recommendedQuestionCount),
    requestedAt: Date.now()
  }
  showRoadmap()
}

function createRoadmapFromSource(source) {
  roadmapDraft.value = {
    topic: source.title,
    sourceId: source.sourceId,
    sourceUri: source.sourceUri,
    requestedAt: Date.now()
  }
  showRoadmap()
}

function startRoadmapQuiz(roadmapQuiz) {
  recommendedQuiz.value = {
    topic: roadmapQuiz.topic,
    numberOfQuestions: roadmapQuiz.numberOfQuestions,
    roadmapId: roadmapQuiz.roadmapId,
    roadmapStepId: roadmapQuiz.roadmapStepId,
    sourceId: roadmapQuiz.sourceId || null,
    additionalPractice: roadmapQuiz.additionalPractice === true,
    priority: 'ROADMAP',
    requestedAt: Date.now()
  }
  activeView.value = 'quiz'
}
</script>

<template>
  <div class="app-shell">
    <header>
      <div class="header-content">
        <div>
          <p class="product-name">Auknowlog</p>
          <h1>기술 학습 퀴즈</h1>
        </div>
        <nav aria-label="주요 메뉴">
          <button type="button" :class="{ active: activeView === 'dashboard' }" :aria-current="activeView === 'dashboard' ? 'page' : undefined" @click="showDashboard">대시보드</button>
          <button type="button" :class="{ active: activeView === 'quiz' }" :aria-current="activeView === 'quiz' ? 'page' : undefined" @click="activeView = 'quiz'">문제 생성</button>
          <button type="button" :class="{ active: activeView === 'review' }" :aria-current="activeView === 'review' ? 'page' : undefined" @click="showReview">오늘의 복습</button>
          <button type="button" :class="{ active: activeView === 'roadmap' }" :aria-current="activeView === 'roadmap' ? 'page' : undefined" @click="showRoadmap">학습 로드맵</button>
          <button type="button" :class="{ active: activeView === 'sources' }" :aria-current="activeView === 'sources' ? 'page' : undefined" @click="showSources">학습 자료</button>
          <button type="button" :class="{ active: activeView === 'history' }" :aria-current="activeView === 'history' ? 'page' : undefined" @click="showHistory">풀이 기록</button>
          <button type="button" :class="{ active: activeView === 'quality' }" :aria-current="activeView === 'quality' ? 'page' : undefined" @click="showQuality">품질 평가</button>
        </nav>
      </div>
    </header>

    <main>
      <DashboardView v-if="activeView === 'dashboard'" @start-recommended-quiz="startRecommendedQuiz" @create-learning-roadmap="createRoadmapFromRecommendation" />
      <QuizGenerator v-show="activeView === 'quiz'" :recommended-quiz="recommendedQuiz" @open-roadmap="showRoadmap" />
      <ReviewQueue v-if="reviewMounted" v-show="activeView === 'review'" />
      <RoadmapView v-if="roadmapMounted" v-show="activeView === 'roadmap'" :initial-roadmap="roadmapDraft" :visible="activeView === 'roadmap'" @start-roadmap-quiz="startRoadmapQuiz" />
      <SourceLibrary v-if="sourceMounted" v-show="activeView === 'sources'" @create-roadmap="createRoadmapFromSource" />
      <LearningHistory v-if="historyMounted" v-show="activeView === 'history'" />
      <QualityEvaluationView v-if="qualityMounted" v-show="activeView === 'quality'" />
    </main>
  </div>
</template>

<style>
:root {
  --ink: #171717;
  --ink-soft: #303030;
  --muted: #6b6b66;
  --line: #deded8;
  --line-strong: #c9c9c1;
  --surface: #ffffff;
  --surface-subtle: #f7f7f4;
  --page: #efefeb;
  --accent: #c64e32;
  --accent-strong: #9f3823;
  --accent-soft: #fff1ec;
  --success: #24744d;
  --success-soft: #edf8f1;
  --danger: #b83a32;
  --danger-soft: #fff1ef;
}

* {
  box-sizing: border-box;
}

body {
  margin: 0;
  padding: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen', 'Ubuntu', 'Cantarell', sans-serif;
  color: var(--ink);
  background-color: var(--page);
  line-height: 1.6;
}

.app-shell {
  display: block;
  width: 100%;
  max-width: 1200px;
  margin: 24px auto;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--surface);
  box-shadow: 0 18px 50px rgb(23 23 23 / 7%);
}

header {
  width: 100%;
  margin: 0 auto;
  background: var(--surface);
  color: var(--ink);
  padding: 25px 40px;
  margin-bottom: 0;
  border-bottom: 1px solid var(--line);
}

.header-content {
  display: flex;
  gap: 24px;
  align-items: center;
  justify-content: space-between;
  max-width: 1100px;
  margin: 0 auto;
}

.product-name {
  margin: 0 0 3px;
  color: var(--accent);
  font-size: .78rem;
  font-weight: 800;
  letter-spacing: .1em;
  text-transform: uppercase;
}

header h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
}

header nav { display: flex; gap: 8px; }
header nav button {
  padding: 9px 12px;
  color: var(--muted);
  background: var(--surface);
  border: 1px solid transparent;
  border-radius: 8px;
  font: inherit;
  font-size: .9rem;
  font-weight: 700;
  cursor: pointer;
}
header nav button:hover,
header nav button.active { color: #fff; background: var(--ink); border-color: var(--ink); }
header nav button:focus-visible { outline: 3px solid rgb(198 78 50 / 24%); outline-offset: 3px; }

main {
  width: 100%;
  margin: 0 auto;
  padding: 0;
  display: block;
}

@media (max-width: 980px) {
  .header-content { align-items: flex-start; flex-direction: column; }
  header nav { width: 100%; overflow-x: auto; padding-bottom: 3px; }
  header nav button { flex: 0 0 auto; white-space: nowrap; }
}

@media (max-width: 680px) {
  .app-shell { margin: 0; border: 0; border-radius: 0; }
  header { padding: 20px 18px; }
}
</style>
