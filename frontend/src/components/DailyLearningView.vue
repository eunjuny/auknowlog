<script setup>
const emit = defineEmits(['complete-today'])

function completeToday() {
  emit('complete-today')
}
</script>

<template>
  <section class="daily-learning-view" aria-labelledby="daily-learning-title">
    <div class="daily-heading">
      <div>
        <p class="eyebrow">TODAY'S TECH BRIEF</p>
        <h2 id="daily-learning-title">데일리 학습</h2>
        <p>기사 맥락, 보충 해설, 복습 문제와 선택형 심화 학습을 한 흐름으로 확인합니다.</p>
      </div>
      <button type="button" class="complete-button" @click="completeToday">오늘 학습 완료</button>
    </div>
    <p class="status-note">현재는 데일리 학습 화면의 기능 흐름을 검증하는 샘플입니다. 실제 기사 수집·AI 생성·서버 채점 연동 전에는 완료 상태를 이 버튼으로 표시합니다.</p>
    <iframe
      class="daily-sample"
      title="데일리 기술 학습 샘플"
      src="/daily-tech-learning-sample.html"
    />
  </section>
</template>

<style scoped>
.daily-learning-view { padding: 30px 38px 42px; }
.daily-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }
.eyebrow { margin: 0 0 5px; color: var(--accent); font-size: .76rem; font-weight: 800; letter-spacing: .09em; }
h2 { margin: 0; font-size: 1.7rem; }
.daily-heading p:not(.eyebrow) { margin: 7px 0 0; color: var(--muted); }
.complete-button { flex: 0 0 auto; padding: 10px 14px; border: 1px solid var(--ink); border-radius: 8px; color: #fff; background: var(--ink); font: inherit; font-size: .88rem; font-weight: 800; cursor: pointer; }
.status-note { margin: 22px 0 14px; padding: 12px 14px; border: 1px solid var(--line); border-radius: 10px; color: var(--muted); background: var(--surface-subtle); font-size: .88rem; }
.daily-sample { width: 100%; min-height: 1460px; border: 1px solid var(--line); border-radius: 12px; background: #fff; }
@media (max-width: 680px) { .daily-learning-view { padding: 22px 18px; } .daily-heading { flex-direction: column; } .daily-sample { min-height: 1680px; } }
</style>
