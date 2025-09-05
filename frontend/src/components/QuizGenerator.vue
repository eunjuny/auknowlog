<script setup>
import { ref } from 'vue';
import axios from 'axios';

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
    const response = await axios.post('http://localhost:8080/api/quizzes', {
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
  <div class="quiz-container">
    <div class="quiz-input-section">
      <div class="quiz-input-group">
        <label for="topic">주제:</label>
        <input type="text" id="topic" v-model="topic" placeholder="예: 자바스크립트, 인공지능" />
      </div>
      <div class="quiz-input-group">
        <label for="numQuestions">문제 수 (기본 10):</label>
        <input type="number" id="numQuestions" v-model.number="numberOfQuestions" min="1" />
      </div>
      <button @click="generateQuiz" :disabled="loading || !topic">
        {{ loading ? '생성 중...' : '퀴즈 생성' }}
      </button>
    </div>

    <div v-if="error" class="error-message">
      {{ error }}
    </div>

    <div v-if="quizResult" class="quiz-output-section">
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
  </div>
</template>

<style scoped>
.quiz-container {
  max-width: 800px;
  margin: 20px auto;
  padding: 20px;
  background-color: #ffffff;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.quiz-input-group {
  margin-bottom: 15px;
}

.quiz-input-group label {
  display: block;
  margin-bottom: 8px;
  font-weight: 600;
  color: #333;
}

.quiz-input-group input[type="text"],
.quiz-input-group input[type="number"] {
  width: calc(100% - 20px);
  padding: 10px;
  border: 1px solid #e0e0e0;
  border-radius: 5px;
  font-size: 16px;
  transition: border-color 0.3s ease;
}

.quiz-input-group input[type="text"]:focus,
.quiz-input-group input[type="number"]:focus {
  border-color: #667eea;
  outline: none;
}

button {
  background-color: #667eea;
  color: white;
  padding: 12px 25px;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  font-size: 17px;
  font-weight: 600;
  transition: background-color 0.3s ease;
  width: 100%;
}

button:hover:not(:disabled) {
  background-color: #5a67d8;
}

button:disabled {
  background-color: #cccccc;
  cursor: not-allowed;
}

.error-message {
  color: #d32f2f;
  background-color: #ffebee;
  padding: 15px;
  border-radius: 5px;
  margin-top: 20px;
  border: 1px solid #ef9a9a;
}

.quiz-output-section {
  margin-top: 30px;
  padding-top: 20px;
  border-top: 1px solid #eee;
}

.quiz-output-section h2 {
  text-align: center;
  color: #444;
  margin-bottom: 25px;
  font-size: 24px;
}

.question-item {
  background-color: #f9f9f9;
  border: 1px solid #e0e0e0;
  padding: 20px;
  margin-bottom: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.05);
}

.question-item h3 {
  color: #667eea;
  margin-top: 0;
  font-size: 18px;
  margin-bottom: 15px;
}

.question-item ul {
  list-style: none;
  padding: 0;
  margin-bottom: 15px;
}

.question-item li {
  background-color: #f0f2f5;
  padding: 10px;
  margin-bottom: 8px;
  border-radius: 4px;
  font-size: 15px;
  color: #555;
}

.question-item p {
  font-size: 15px;
  color: #444;
  margin-bottom: 5px;
}

.question-item strong {
  color: #333;
}
</style>