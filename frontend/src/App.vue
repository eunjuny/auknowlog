<script setup>
import { defineAsyncComponent, ref } from 'vue'
import QuizGenerator from './components/QuizGenerator.vue'
import LearningHistory from './components/LearningHistory.vue'

const DashboardView = defineAsyncComponent(() => import('./components/DashboardView.vue'))
const RoadmapView = defineAsyncComponent(() => import('./components/RoadmapView.vue'))

const activeView = ref('quiz')
const historyMounted = ref(false)
const roadmapMounted = ref(false)
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

function startRoadmapQuiz(roadmapQuiz) {
  recommendedQuiz.value = {
    topic: roadmapQuiz.topic,
    numberOfQuestions: roadmapQuiz.numberOfQuestions,
    roadmapId: roadmapQuiz.roadmapId,
    roadmapStepId: roadmapQuiz.roadmapStepId,
    priority: 'ROADMAP',
    requestedAt: Date.now()
  }
  activeView.value = 'quiz'
}
</script>

<template>
  <div id="app">
    <header>
      <div class="header-content">
        <div>
          <p class="product-name">Auknowlog</p>
          <h1>기술 학습 퀴즈</h1>
        </div>
        <nav aria-label="주요 메뉴">
          <button type="button" :class="{ active: activeView === 'dashboard' }" :aria-current="activeView === 'dashboard' ? 'page' : undefined" @click="showDashboard">대시보드</button>
          <button type="button" :class="{ active: activeView === 'quiz' }" :aria-current="activeView === 'quiz' ? 'page' : undefined" @click="activeView = 'quiz'">문제 생성</button>
          <button type="button" :class="{ active: activeView === 'roadmap' }" :aria-current="activeView === 'roadmap' ? 'page' : undefined" @click="showRoadmap">학습 로드맵</button>
          <button type="button" :class="{ active: activeView === 'history' }" :aria-current="activeView === 'history' ? 'page' : undefined" @click="showHistory">풀이 기록</button>
        </nav>
      </div>
    </header>

    <main>
      <DashboardView v-if="activeView === 'dashboard'" @start-recommended-quiz="startRecommendedQuiz" @create-learning-roadmap="createRoadmapFromRecommendation" />
      <QuizGenerator v-show="activeView === 'quiz'" :recommended-quiz="recommendedQuiz" />
      <RoadmapView v-if="roadmapMounted" v-show="activeView === 'roadmap'" :initial-roadmap="roadmapDraft" @start-roadmap-quiz="startRoadmapQuiz" />
      <LearningHistory v-if="historyMounted" v-show="activeView === 'history'" />
    </main>
  </div>
</template>

<style>
* {
  box-sizing: border-box;
}

body {
  margin: 0;
  padding: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen', 'Ubuntu', 'Cantarell', sans-serif;
  background-color: #f5f7fa;
  line-height: 1.6;
}

#app {
  display: block;
  width: 100%;
  max-width: 1200px;
  margin: 40px auto;
  padding: 0;
}

header {
  width: 100%;
  margin: 0 auto;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 24px 40px 58px;
  margin-bottom: 0;
  box-shadow: none;
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
  color: rgb(255 255 255 / 72%);
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
  color: rgb(255 255 255 / 80%);
  background: transparent;
  border: 1px solid rgb(255 255 255 / 24%);
  border-radius: 8px;
  font: inherit;
  font-size: .9rem;
  font-weight: 700;
  cursor: pointer;
}
header nav button:hover,
header nav button.active { color: #4c51bf; background: #fff; border-color: #fff; }
header nav button:focus-visible { outline: 3px solid rgb(255 255 255 / 55%); outline-offset: 3px; }

main {
  width: 100%;
  margin: 0 auto;
  padding: 0;
  display: block;
}

@media (max-width: 680px) {
  #app { margin: 0; }
  header { padding: 20px 18px 48px; }
  .header-content { align-items: flex-start; flex-direction: column; }
}
</style>
