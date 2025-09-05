<script setup>
import { ref } from 'vue';
import axios from 'axios'; // Assuming axios is installed

const topic = ref('');
const numberOfQuestions = ref(10); // Default value
const quizResult = ref(null);
const error = ref(null);
const loading = ref(false);

async function generateQuiz() {
  loading.value = true;
  quizResult.value = null;
  error.value = null;

  try {
    const response = await axios.post('/api/quizzes', {
      topic: topic.value,
      numberOfQuestions: numberOfQuestions.value
    });
    quizResult.value = response.data;
  } catch (err) {
    console.error('API call failed:', err);
    error.value = '퀴즈 생성에 실패했습니다: ' + (err.response?.data?.message || err.message);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <header>
    <h1>Auknowlog Quiz Generator</h1>
  </header>

  <main>
    <div class="quiz-input">
      <label for="topic">주제:</label>
      <input type="text" id="topic" v-model="topic" placeholder="예: 자바스크립트, 인공지능" />
    </div>
    <div class="quiz-input">
      <label for="numQuestions">문제 수 (기본 10):</label>
      <input type="number" id="numQuestions" v-model.number="numberOfQuestions" min="1" />
    </div>
    <button @click="generateQuiz" :disabled="loading || !topic">
      {{ loading ? '생성 중...' : '퀴즈 생성' }}
    </button>

    <div v-if="error" class="error-message">
      {{ error }}
    </div>

    <div v-if="quizResult" class="quiz-output">
      <h2>{{ quizResult.quizTitle }}</h2>
      <div v-for="(question, index) in quizResult.questions" :key="index" class="question-item">
        <h3>{{ index + 1 }}. {{ question.questionText }}</h3>
        <ul>
          <li v-for="(option, optIndex) in question.options" :key="optIndex">
            {{ option }}
          </li>
        </ul>
        <p><strong>정답:</strong> {{ question.correctAnswer }}</p>
        <p><strong>설명:</strong> {{ question.explanation }}</p>
      </div>
    </div>
  </main>
</template>

<style>
/* Basic styling for readability */
body {
  font-family: sans-serif;
  margin: 20px;
  background-color: #f4f4f4;
}
header {
  background-color: #333;
  color: white;
  padding: 10px;
  text-align: center;
  margin-bottom: 20px;
}
main {
  max-width: 800px;
  margin: 0 auto;
  background-color: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}
.quiz-input {
  margin-bottom: 15px;
}
.quiz-input label {
  display: block;
  margin-bottom: 5px;
  font-weight: bold;
}
.quiz-input input[type="text"],
.quiz-input input[type="number"] {
  width: calc(100% - 22px); /* Adjust for padding and border */
  padding: 10px;
  border: 1px solid #ddd;
  border-radius: 4px;
}
button {
  background-color: #007bff;
  color: white;
  padding: 10px 20px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 16px;
  margin-top: 10px;
}
button:hover:not(:disabled) {
  background-color: #0056b3;
}
button:disabled {
  background-color: #cccccc;
  cursor: not-allowed;
}
.error-message {
  color: red;
  margin-top: 20px;
  padding: 10px;
  border: 1px solid red;
  background-color: #ffe6e6;
  border-radius: 4px;
}
.quiz-output {
  margin-top: 30px;
  border-top: 1px solid #eee;
  padding-top: 20px;
}
.quiz-output h2 {
  color: #333;
  text-align: center;
  margin-bottom: 20px;
}
.question-item {
  background-color: #f9f9f9;
  border: 1px solid #eee;
  padding: 15px;
  margin-bottom: 15px;
  border-radius: 6px;
}
.question-item h3 {
  color: #007bff;
  margin-top: 0;
}
.question-item ul {
  list-style: none;
  padding: 0;
}
.question-item li {
  background-color: #e9ecef;
  margin-bottom: 5px;
  padding: 8px;
  border-radius: 3px;
}
</style>