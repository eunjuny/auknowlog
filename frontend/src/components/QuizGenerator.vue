<script setup>
import { ref } from 'vue';
import axios from 'axios';

const topic = ref('');
const numberOfQuestions = ref(10); // Default value
const quizResult = ref(null);
const error = ref(null);
const loading = ref(false);
const selectedAnswers = ref({});
const showNextQuizForm = ref(false);
const nextQuizTopic = ref('');
const nextQuizQuestions = ref(10);
const saveMessage = ref(null);

async function generateQuiz() {
  loading.value = true;
  quizResult.value = null;
  error.value = null;
  selectedAnswers.value = {}; // Reset selected answers
  saveMessage.value = null; // Clear save message on new quiz generation

  try {
    // const response = await axios.post('/api/quizzes/dummy', {
    const response = await axios.post('/api/quizzes/create', {
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
  // 이미 같은 옵션을 선택했다면 선택 해제, 아니면 새로 선택
  if (selectedAnswers.value[questionIndex] === optionIndex) {
    selectedAnswers.value[questionIndex] = null;
  } else {
    selectedAnswers.value[questionIndex] = optionIndex;
  }
}

function showNextQuizOptions() {
  nextQuizTopic.value = topic.value;
  nextQuizQuestions.value = numberOfQuestions.value;
  showNextQuizForm.value = true;
}

async function generateNextQuiz() {
  topic.value = nextQuizTopic.value;
  numberOfQuestions.value = nextQuizQuestions.value;
  showNextQuizForm.value = false;
  await generateQuiz();
}

function isAllQuestionsAnswered() {
  if (!quizResult.value || !quizResult.value.questions) return false;
  return quizResult.value.questions.every((_, index) => selectedAnswers.value[index] !== null && selectedAnswers.value[index] !== undefined);
}

async function saveQuizAsMarkdown() {
  if (!isAllQuestionsAnswered()) {
    saveMessage.value = '모든 문제를 풀어야 저장할 수 있습니다.';
    return;
  }

  loading.value = true;
  saveMessage.value = null;
  error.value = null;

  try {
    const questionsWithUser = quizResult.value.questions.map((question, index) => {
      const selectedIndex = selectedAnswers.value[index];
      const userSelectedAnswer = question.options[selectedIndex];
      const isCorrect = userSelectedAnswer === question.correctAnswer;
      return {
        ...question,
        userSelectedIndex: selectedIndex,
        userSelectedAnswer,
        isCorrect,
      };
    });

    const numCorrect = questionsWithUser.filter(q => q.isCorrect).length;
    const payload = {
      ...quizResult.value,
      userAnswers: selectedAnswers.value,
      questions: questionsWithUser,
      stats: {
        total: questionsWithUser.length,
        correct: numCorrect,
        wrong: questionsWithUser.length - numCorrect,
      },
    };

    const response = await axios.post('/api/documents/save-quiz-markdown-raw', payload);
    saveMessage.value = response.data;
  } catch (err) {
    console.error('Save API call failed:', err);
    saveMessage.value = '저장 실패: ' + (err.response?.data || err.message);
  } finally {
    loading.value = false;
  }
}

async function saveQuizToNotion() {
  if (!isAllQuestionsAnswered()) {
    saveMessage.value = '모든 문제를 풀어야 저장할 수 있습니다.';
    return;
  }

  loading.value = true;
  saveMessage.value = null;
  error.value = null;

  try {
    const questionsWithUser = quizResult.value.questions.map((question, index) => {
      const selectedIndex = selectedAnswers.value[index];
      const userSelectedAnswer = question.options[selectedIndex];
      const isCorrect = userSelectedAnswer === question.correctAnswer;
      return {
        ...question,
        userSelectedIndex: selectedIndex,
        userSelectedAnswer,
        isCorrect,
      };
    });

    const numCorrect = questionsWithUser.filter(q => q.isCorrect).length;
    const payload = {
      ...quizResult.value,
      userAnswers: selectedAnswers.value,
      questions: questionsWithUser,
      stats: {
        total: questionsWithUser.length,
        correct: numCorrect,
        wrong: questionsWithUser.length - numCorrect,
      },
    };

    const response = await axios.post('/api/documents/save-quiz-notion', payload);
    saveMessage.value = response.data;
  } catch (err) {
    console.error('Notion Save API call failed:', err);
    saveMessage.value = '노션 저장 실패: ' + (err.response?.data || err.message);
  } finally {
    loading.value = false;
  }
}

function cancelNextQuiz() {
  showNextQuizForm.value = false;
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
        <div class="options-container">
          <div 
            v-for="(option, optIndex) in question.options" 
            :key="optIndex"
            class="option-item"
            :class="{
              'selected': selectedAnswers[index] === optIndex,
              'correct-answer': selectedAnswers[index] !== null && selectedAnswers[index] === optIndex && option === question.correctAnswer,
              'wrong-answer': selectedAnswers[index] !== null && selectedAnswers[index] === optIndex && option !== question.correctAnswer,
              'not-selected': selectedAnswers[index] !== null && selectedAnswers[index] !== optIndex
            }"
            @click="selectOption(index, optIndex)"
          >
            {{ String.fromCharCode(65 + optIndex) }}. {{ option }}
          </div>
        </div>
        
        <div v-if="selectedAnswers[index] !== null && selectedAnswers[index] !== undefined" 
             class="answer-section"
             :class="{
               'correct-result': question.options[selectedAnswers[index]] === question.correctAnswer,
               'incorrect-result': question.options[selectedAnswers[index]] !== question.correctAnswer
             }">
          <div class="result-indicator">
            <span v-if="question.options[selectedAnswers[index]] === question.correctAnswer" class="correct-icon">✓</span>
            <span v-else class="incorrect-icon">✗</span>
            <strong v-if="question.options[selectedAnswers[index]] === question.correctAnswer">정답입니다!</strong>
            <strong v-else>틀렸습니다.</strong>
          </div>
          <p><strong>정답:</strong> {{ question.correctAnswer }}</p>
          <p><strong>설명:</strong> {{ question.explanation }}</p>
        </div>
      </div>
      
      <div class="next-quiz-section">
        <div class="quiz-actions">
          <button @click="showNextQuizOptions" class="next-quiz-button">
            다음 문제 생성
          </button>
          <button @click="saveQuizAsMarkdown" :disabled="loading || !isAllQuestionsAnswered()" class="save-button">
            {{ loading ? '저장 중...' : isAllQuestionsAnswered() ? 'Markdown으로 저장' : '모든 문제를 풀어주세요' }}
          </button>
          <button @click="saveQuizToNotion" :disabled="loading || !isAllQuestionsAnswered()" class="save-button" style="background-color:#222;">
            {{ loading ? '저장 중...' : '노션에 저장(임시)' }}
          </button>
        </div>
        
        <div v-if="saveMessage" class="save-message" :class="{ 'error-message': saveMessage.includes('실패') || saveMessage.includes('풀어야') }">
          {{ saveMessage }}
        </div>
        
        <div v-if="showNextQuizForm" class="next-quiz-form">
          <h3>다음 퀴즈 설정</h3>
          <div class="form-group">
            <label for="nextTopic">주제:</label>
            <input 
              type="text" 
              id="nextTopic" 
              v-model="nextQuizTopic" 
              placeholder="예: 자바스크립트, 인공지능" 
            />
          </div>
          <div class="form-group">
            <label for="nextQuestions">문제 수:</label>
            <input 
              type="number" 
              id="nextQuestions" 
              v-model.number="nextQuizQuestions" 
              min="1" 
            />
          </div>
          <div class="form-buttons">
            <button @click="generateNextQuiz" class="confirm-button">
              퀴즈 생성
            </button>
            <button @click="cancelNextQuiz" class="cancel-button">
              취소
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.quiz-container {
  max-width: 1600px;
  min-width: 800px;
  margin: 0 auto;
  padding: 40px 50px;
  background-color: #ffffff;
  border-radius: 0 0 8px 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  min-height: 120px;
  margin-top: 0;
}

.quiz-input-section {
  margin-top: 20px;
  margin-bottom: 20px;
}

.quiz-input-group {
  margin-bottom: 25px;
}

.quiz-input-group label {
  display: block;
  margin-bottom: 8px;
  font-weight: 600;
  color: #333;
}

.quiz-input-group input[type="text"],
.quiz-input-group input[type="number"] {
  width: 100%;
  max-width: 900px;
  padding: 15px 20px;
  border: 1px solid #e0e0e0;
  border-radius: 5px;
  font-size: 16px;
  transition: border-color 0.3s ease;
  box-sizing: border-box;
}

.quiz-input-group input[type="text"]:focus,
.quiz-input-group input[type="number"]:focus {
  border-color: #667eea;
  outline: none;
}

button {
  background-color: #667eea;
  color: white;
  padding: 18px 25px;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  font-size: 17px;
  font-weight: 600;
  transition: background-color 0.3s ease;
  width: 100%;
  margin-top: 10px;
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
  margin-top: 40px;
  margin-bottom: 40px;
  padding-top: 30px;
  padding-bottom: 30px;
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

.options-container {
  margin-bottom: 15px;
}

.option-item {
  background-color: #f0f2f5;
  padding: 18px 20px;
  margin-bottom: 8px;
  border-radius: 8px;
  font-size: 15px;
  color: #555;
  cursor: pointer;
  transition: all 0.3s ease;
  border: 2px solid transparent;
  width: 100%;
  box-sizing: border-box;
}

.option-item:hover {
  background-color: #e8f0fe;
  border-color: #667eea;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.15);
}

.option-item.selected {
  background-color: #667eea;
  color: white;
  border-color: #5a67d8;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.option-item.correct-answer {
  background-color: #4caf50;
  color: white;
  border-color: #45a049;
  box-shadow: 0 4px 12px rgba(76, 175, 80, 0.3);
  font-weight: 600;
}

.option-item.wrong-answer {
  background-color: #f44336;
  color: white;
  border-color: #d32f2f;
  box-shadow: 0 4px 12px rgba(244, 67, 54, 0.3);
  font-weight: 600;
}

.option-item.not-selected {
  background-color: #f5f5f5;
  color: #666;
  border-color: #e0e0e0;
}

.answer-section {
  background-color: #f8f9fa;
  padding: 20px;
  border-radius: 8px;
  margin-top: 15px;
  border-left: 4px solid #667eea;
  width: 100%;
  box-sizing: border-box;
  transition: all 0.3s ease;
}

.answer-section.correct-result {
  background-color: #e8f5e8;
  border-left-color: #4caf50;
}

.answer-section.incorrect-result {
  background-color: #ffeaea;
  border-left-color: #f44336;
}

.result-indicator {
  display: flex;
  align-items: center;
  margin-bottom: 15px;
  font-size: 16px;
}

.correct-icon {
  color: #4caf50;
  font-size: 20px;
  font-weight: bold;
  margin-right: 8px;
}

.incorrect-icon {
  color: #f44336;
  font-size: 20px;
  font-weight: bold;
  margin-right: 8px;
}

.result-indicator strong {
  font-size: 16px;
}

.answer-section p {
  font-size: 15px;
  color: #444;
  margin-bottom: 5px;
}

.answer-section strong {
  color: #333;
}

.next-quiz-section {
  text-align: center;
  margin-top: 40px;
  padding-top: 30px;
  border-top: 2px solid #e0e0e0;
}

.quiz-actions {
  display: flex;
  gap: 15px;
  justify-content: center;
  margin-bottom: 20px;
}

.save-button {
  background-color: #28a745;
  color: white;
  padding: 15px 30px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 16px;
  font-weight: 600;
  transition: all 0.3s ease;
  box-shadow: 0 4px 12px rgba(40, 167, 69, 0.3);
}

.save-button:hover:not(:disabled) {
  background-color: #218838;
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(40, 167, 69, 0.4);
}

.save-button:disabled {
  background-color: #6c757d;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.save-message {
  margin-top: 15px;
  padding: 12px 20px;
  background-color: #d4edda;
  border: 1px solid #c3e6cb;
  color: #155724;
  border-radius: 5px;
  text-align: center;
  font-weight: 500;
}

.save-message.error-message {
  background-color: #f8d7da;
  border-color: #f5c6cb;
  color: #721c24;
}

.next-quiz-button {
  background-color: #667eea;
  color: white;
  padding: 15px 30px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 16px;
  font-weight: 600;
  transition: all 0.3s ease;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.next-quiz-button:hover {
  background-color: #5a67d8;
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(102, 126, 234, 0.4);
}

.next-quiz-button:active {
  transform: translateY(0);
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
}

.next-quiz-form {
  background-color: #f8f9fa;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 25px;
  margin-top: 20px;
  max-width: 500px;
  margin-left: auto;
  margin-right: auto;
}

.next-quiz-form h3 {
  margin: 0 0 20px 0;
  color: #333;
  text-align: center;
  font-size: 18px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-weight: 600;
  color: #333;
}

.form-group input {
  width: 100%;
  padding: 12px 15px;
  border: 1px solid #ddd;
  border-radius: 5px;
  font-size: 14px;
  transition: border-color 0.3s ease;
  box-sizing: border-box;
}

.form-group input:focus {
  border-color: #667eea;
  outline: none;
}

.form-buttons {
  display: flex;
  gap: 15px;
  justify-content: center;
  margin-top: 25px;
}

.confirm-button {
  background-color: #667eea;
  color: white;
  padding: 12px 25px;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.3s ease;
}

.confirm-button:hover {
  background-color: #5a67d8;
}

.cancel-button {
  background-color: #6c757d;
  color: white;
  padding: 12px 25px;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.3s ease;
}

.cancel-button:hover {
  background-color: #5a6268;
}
</style>