<script setup>
import { ref } from 'vue'
import QuizGenerator from './components/QuizGenerator.vue'
import LearningHistory from './components/LearningHistory.vue'

const activeView = ref('quiz')
const historyMounted = ref(false)

function showHistory() {
  historyMounted.value = true
  activeView.value = 'history'
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
          <button type="button" :class="{ active: activeView === 'quiz' }" :aria-current="activeView === 'quiz' ? 'page' : undefined" @click="activeView = 'quiz'">문제 생성</button>
          <button type="button" :class="{ active: activeView === 'history' }" :aria-current="activeView === 'history' ? 'page' : undefined" @click="showHistory">풀이 기록</button>
        </nav>
      </div>
    </header>

    <main>
      <QuizGenerator v-show="activeView === 'quiz'" />
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
