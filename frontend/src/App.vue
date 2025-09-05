<script setup>
import { ref } from 'vue';
import axios from 'axios'; // Assuming axios is installed

const topic = ref('');
const numberOfQuestions = ref(10); // Default value
const quizResult = ref(null);
const error = ref(null);
const loading = ref(false);
const selectedAnswers = ref({}); // 선택된 답안을 저장

async function generateQuiz() {
  loading.value = true;
  quizResult.value = null;
  error.value = null;
  selectedAnswers.value = {}; // 선택 상태 초기화

  try {
    // const response = await axios.post('/api/quizzes/create', {
    const response = await axios.post('/api/quizzes/dummy', {
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

function selectOption(questionIndex, optionIndex) {
  selectedAnswers.value[questionIndex] = optionIndex;
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
        <div class="options-container">
          <div 
            v-for="(option, optIndex) in question.options" 
            :key="optIndex"
            class="option-item"
            @click="selectOption(index, optIndex)"
            :class="{ 
              'selected': selectedAnswers[index] === optIndex,
              'correct': selectedAnswers[index] === optIndex && question.correctAnswer === option,
              'incorrect': selectedAnswers[index] === optIndex && question.correctAnswer !== option && selectedAnswers[index] !== null
            }"
          >
            {{ String.fromCharCode(65 + optIndex) }}. {{ option }}
          </div>
        </div>
        <div v-if="selectedAnswers[index] !== null && selectedAnswers[index] !== undefined" class="answer-section">
          <p><strong>정답:</strong> {{ question.correctAnswer }}</p>
          <p><strong>설명:</strong> {{ question.explanation }}</p>
        </div>
      </div>
    </div>
  </main>
</template>

<style>
/* Basic styling for readability */
body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen', 'Ubuntu', 'Cantarell', sans-serif;
  margin: 0;
  padding: 20px;
  background-color: #f5f7fa;
  line-height: 1.6;
}
header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 20px;
  text-align: center;
  margin-bottom: 30px;
  border-radius: 0 0 15px 15px;
  box-shadow: 0 4px 6px rgba(0,0,0,0.1);
}

header h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
}
main {
  max-width: 900px;
  margin: 0 auto;
  background-color: white;
  padding: 30px;
  border-radius: 12px;
  box-shadow: 0 8px 25px rgba(0,0,0,0.1);
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
  color: #2c3e50;
  margin-top: 0;
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 15px;
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

.options-container {
  margin: 15px 0;
}

.option-item {
  background-color: #ffffff;
  border: 2px solid #dee2e6;
  margin-bottom: 10px;
  padding: 15px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 16px;
  font-weight: 500;
  color: #2c3e50;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.option-item:hover {
  background-color: #f8f9fa;
  border-color: #007bff;
  transform: translateY(-1px);
  box-shadow: 0 4px 8px rgba(0,0,0,0.15);
}

.option-item.selected {
  background-color: #e3f2fd;
  border-color: #2196f3;
  font-weight: 600;
  color: #1976d2;
}

.option-item.correct {
  background-color: #e8f5e8;
  border-color: #4caf50;
  color: #2e7d32;
  font-weight: 600;
}

.option-item.incorrect {
  background-color: #ffebee;
  border-color: #f44336;
  color: #c62828;
  font-weight: 600;
}

.answer-section {
  margin-top: 20px;
  padding: 20px;
  background-color: #f8f9fa;
  border-radius: 8px;
  border-left: 4px solid #007bff;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.answer-section p {
  margin: 8px 0;
  font-size: 16px;
  line-height: 1.5;
  color: #2c3e50;
}

.answer-section strong {
  color: #1976d2;
  font-weight: 600;
}
</style>