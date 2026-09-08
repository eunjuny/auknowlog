<script setup>
import { ref, watch } from 'vue';
import axios from 'axios';

const props = defineProps({
  recommendedQuiz: {
    type: Object,
    default: null
  }
});

const topic = ref('');
const numberOfQuestions = ref(5); // Default value
const quizResult = ref(null);
const error = ref(null);
const loading = ref(false);
const selectedAnswers = ref({});
const showNextQuizForm = ref(false);
const nextQuizTopic = ref('');
const nextQuizQuestions = ref(5);
const saveMessage = ref(null);
const sourceTitle = ref('');
const sourceContent = ref('');
const sourceMessage = ref(null);
const demoMode = ref(true);
const attemptMessage = ref(null);
const attemptSaved = ref(false);
const attemptSaving = ref(false);
const quizSubmitted = ref(false);
const submissionMessage = ref(null);
const feedbackForms = ref({});
const recommendationMessage = ref(null);
const roadmapContext = ref(null);

const feedbackTypes = [
  { value: 'INCORRECT_CONTENT', label: '정답 또는 내용이 부정확해요' },
  { value: 'AMBIGUOUS', label: '질문이 모호해요' },
  { value: 'EXPLANATION_INSUFFICIENT', label: '해설이 부족해요' },
  { value: 'DIFFICULTY_TOO_LOW', label: '난이도가 너무 낮아요' },
  { value: 'DIFFICULTY_TOO_HIGH', label: '난이도가 너무 높아요' },
  { value: 'OTHER', label: '기타 의견' }
];

// 노션 저장은 서버 기본 설정을 사용합니다. (별도 입력 필드 제거)

// 입력값을 1~20 범위로 강제 클램프
watch(numberOfQuestions, (v) => {
  const n = Number(v);
  if (Number.isNaN(n)) return;
  if (n > 20) numberOfQuestions.value = 20;
  else if (n < 1) numberOfQuestions.value = 1;
});

watch(nextQuizQuestions, (v) => {
  const n = Number(v);
  if (Number.isNaN(n)) return;
  if (n > 20) nextQuizQuestions.value = 20;
  else if (n < 1) nextQuizQuestions.value = 1;
});

watch(() => props.recommendedQuiz?.requestedAt, () => {
  if (!props.recommendedQuiz?.topic) {
    return;
  }
  topic.value = props.recommendedQuiz.topic;
  numberOfQuestions.value = props.recommendedQuiz.numberOfQuestions || 5;
  roadmapContext.value = props.recommendedQuiz.roadmapId
    ? {
        roadmapId: props.recommendedQuiz.roadmapId,
        roadmapStepId: props.recommendedQuiz.roadmapStepId || null,
        topic: props.recommendedQuiz.topic
      }
    : null;
  recommendationMessage.value = `학습 추천에 따라 “${props.recommendedQuiz.topic}” ${numberOfQuestions.value}문제를 준비했습니다. 생성 방식을 선택한 뒤 시작해주세요.`;
});

async function generateQuiz() {
  loading.value = true;
  quizResult.value = null;
  error.value = null;
  selectedAnswers.value = {}; // Reset selected answers
  saveMessage.value = null; // Clear save message on new quiz generation
  attemptMessage.value = null;
  attemptSaved.value = false;
  attemptSaving.value = false;
  quizSubmitted.value = false;
  submissionMessage.value = null;
  sourceMessage.value = null;
  feedbackForms.value = {};
  recommendationMessage.value = null;

  try {
    let sourceId = null;
    if (sourceContent.value.trim()) {
      const sourceResponse = await axios.post('/api/sources', {
        title: sourceTitle.value.trim() || `${topic.value} 학습 자료`,
        content: sourceContent.value.trim()
      });
      sourceId = sourceResponse.data.sourceId;
      sourceMessage.value = `학습 자료를 ${sourceResponse.data.chunkCount}개 청크로 저장했습니다.`;
    }

    const endpoint = demoMode.value ? '/api/quizzes/dummy' : '/api/quizzes/create';
    const response = await axios.post(endpoint, {
      topic: topic.value,
      numberOfQuestions: numberOfQuestions.value,
      sourceId,
      roadmapId: roadmapContext.value?.topic === topic.value.trim() ? roadmapContext.value.roadmapId : null,
      roadmapStepId: roadmapContext.value?.topic === topic.value.trim() ? roadmapContext.value.roadmapStepId : null
    });
    quizResult.value = response.data;
  } catch (err) {
    console.error('API call failed:', err);
    error.value = '퀴즈 생성에 실패했습니다: ' + (err.response?.data?.message || err.message);
  } finally {
    loading.value = false;
  }
}

async function saveLearningAttempt() {
  if (!quizSubmitted.value || !quizResult.value?.quizId || attemptSaved.value || attemptSaving.value) {
    return;
  }

  attemptSaving.value = true;
  attemptMessage.value = null;
  const answers = quizResult.value.questions.map((question, index) => ({
    questionOrder: index + 1,
    selectedAnswer: question.options[selectedAnswers.value[index]]
  }));

  try {
    // 응답이 유실된 경우에도 백엔드가 quizId 기준으로 기존 기록을 돌려주므로, 한 번 자동 재시도해도 중복 기록이 생기지 않는다.
    for (let retry = 0; retry < 2; retry += 1) {
      try {
        const response = await axios.post('/api/learning-attempts', {
          quizId: quizResult.value.quizId,
          answers
        });
        const result = response.data;
        attemptSaved.value = true;
        attemptMessage.value = null;
        submissionMessage.value = `채점 완료 · ${result.correctAnswers}/${result.totalQuestions} 정답 · 풀이 기록 자동 저장됨` +
          (result.reviewScheduledCount > 0 ? ` · 오답 ${result.reviewScheduledCount}개는 내일 복습으로 예약됐습니다.` : '');
        return;
      } catch (err) {
        if (retry === 0) {
          attemptMessage.value = '풀이 기록을 자동 저장하지 못해 재시도 중입니다.';
          await new Promise((resolve) => window.setTimeout(resolve, 500));
          continue;
        }
        attemptMessage.value = '풀이 기록 자동 저장 실패: ' + (err.response?.data?.message || err.message);
        submissionMessage.value = '채점은 완료됐지만 풀이 기록을 저장하지 못했습니다.';
      }
    }
  } finally {
    attemptSaving.value = false;
  }
}

function selectOption(questionIndex, optionIndex) {
  if (quizSubmitted.value) {
    return;
  }

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

function getUnansweredQuestionCount() {
  if (!quizResult.value?.questions) return 0;
  return quizResult.value.questions.filter((_, index) => selectedAnswers.value[index] === null || selectedAnswers.value[index] === undefined).length;
}

function getCorrectAnswerCount() {
  if (!quizResult.value?.questions) return 0;
  return quizResult.value.questions.filter((question, index) =>
    question.options[selectedAnswers.value[index]] === question.correctAnswer
  ).length;
}

async function submitQuiz() {
  if (!isAllQuestionsAnswered()) {
    submissionMessage.value = `미응답 문제가 ${getUnansweredQuestionCount()}개 남아 있습니다.`;
    return;
  }

  quizSubmitted.value = true;
  const correctAnswers = getCorrectAnswerCount();
  submissionMessage.value = `채점 완료 · ${correctAnswers}/${quizResult.value.questions.length} 정답 · 풀이 기록 저장 중...`;
  await saveLearningAttempt();
}

function feedbackFormFor(questionIndex) {
  if (!feedbackForms.value[questionIndex]) {
    feedbackForms.value[questionIndex] = {
      open: false,
      feedbackType: '',
      comment: '',
      saving: false,
      saved: false,
      message: null,
      error: null
    };
  }
  return feedbackForms.value[questionIndex];
}

function toggleQuestionFeedback(questionIndex) {
  const form = feedbackFormFor(questionIndex);
  form.open = !form.open;
  form.error = null;
}

async function saveQuestionFeedback(questionIndex) {
  const form = feedbackFormFor(questionIndex);
  if (!form.feedbackType || form.saving) {
    return;
  }
  if (form.feedbackType === 'OTHER' && !form.comment.trim()) {
    form.error = '기타 의견을 선택한 경우 내용을 입력해주세요.';
    return;
  }

  form.saving = true;
  form.error = null;
  try {
    const response = await axios.put('/api/question-feedback', {
      quizId: quizResult.value.quizId,
      questionOrder: questionIndex + 1,
      feedbackType: form.feedbackType,
      comment: form.comment
    });
    form.saved = true;
    form.message = response.data.updated ? '피드백을 수정했습니다. 감사합니다.' : '피드백을 저장했습니다. 감사합니다.';
  } catch (err) {
    form.error = '피드백 저장 실패: ' + (err.response?.data?.message || err.message);
  } finally {
    form.saving = false;
  }
}

async function saveQuizAsMarkdown() {
  if (!quizSubmitted.value) {
    saveMessage.value = '답안을 제출한 후 저장할 수 있습니다.';
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

    // 노션 관련 파라미터는 마크다운 저장에서는 사용하지 않습니다.

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
  if (!quizSubmitted.value) {
    saveMessage.value = '답안을 제출한 후 저장할 수 있습니다.';
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

async function saveQuizToGit() {
  if (!quizSubmitted.value) {
    saveMessage.value = '답안을 제출한 후 저장할 수 있습니다.';
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

    const response = await axios.post('/api/documents/save-quiz-git', payload);
    saveMessage.value = response.data;
  } catch (err) {
    console.error('Git Save API call failed:', err);
    saveMessage.value = 'Git 저장 실패: ' + (err.response?.data || err.message);
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
        <label for="numQuestions">문제 수 (기본 5, 최대 20):</label>
        <input type="number" id="numQuestions" v-model.number="numberOfQuestions" min="1" max="20" />
      </div>
      <div class="quiz-input-group">
        <label for="sourceTitle">학습 자료 제목 (선택):</label>
        <input type="text" id="sourceTitle" v-model="sourceTitle" placeholder="예: JVM 실행 구조" />
      </div>
      <div class="quiz-input-group">
        <label for="sourceContent">학습 자료 내용 (선택):</label>
        <textarea id="sourceContent" v-model="sourceContent" rows="6"
          placeholder="Markdown 또는 기술 문서 내용을 붙여넣으면 퀴즈와 함께 저장합니다."></textarea>
      </div>
      <label class="demo-mode-toggle">
        <input type="checkbox" v-model="demoMode" />
        비용 없는 데모 퀴즈로 생성 (해제 시 OpenAI API 호출)
      </label>
      <p v-if="recommendationMessage" class="recommendation-message">{{ recommendationMessage }}</p>
      <button @click="generateQuiz" :disabled="loading || !topic">
        {{ loading ? '생성 중...' : demoMode ? '데모 퀴즈 생성' : 'AI 퀴즈 생성' }}
      </button>
      <p v-if="sourceMessage" class="source-message">{{ sourceMessage }}</p>
    </div>

    <div v-if="error" class="error-message">
      {{ error }}
    </div>

    <div v-if="quizResult" class="quiz-output-section">
      <h2>{{ quizResult.quizTitle }}</h2>
      <div v-for="(question, index) in quizResult.questions" :key="index" class="question-item">
        <h3>{{ index + 1 }}. {{ question.questionText }}</h3>
        <div class="options-container">
          <button
            v-for="(option, optIndex) in question.options" 
            :key="optIndex"
            type="button"
            class="option-item"
            :disabled="quizSubmitted"
            :aria-pressed="selectedAnswers[index] === optIndex"
            :class="{
              'selected': !quizSubmitted && selectedAnswers[index] === optIndex,
              'correct-answer': quizSubmitted && option === question.correctAnswer,
              'wrong-answer': quizSubmitted && selectedAnswers[index] === optIndex && option !== question.correctAnswer,
              'not-selected': quizSubmitted && selectedAnswers[index] !== optIndex && option !== question.correctAnswer
            }"
            @click="selectOption(index, optIndex)"
          >
            {{ String.fromCharCode(65 + optIndex) }}. {{ option }}
          </button>
        </div>
        
          <div v-if="quizSubmitted"
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
          <p v-if="quizSubmitted && question.sourceReferences?.length" class="source-reference">
            <strong>근거:</strong> {{ question.sourceReferences.join(', ') }}
          </p>
          <section v-if="quizSubmitted" class="question-feedback" :aria-label="`${index + 1}번 문제 품질 피드백`">
            <button
              type="button"
              class="feedback-toggle"
              :aria-expanded="feedbackForms[index]?.open ?? false"
              @click="toggleQuestionFeedback(index)"
            >
              {{ feedbackForms[index]?.saved ? '문제 피드백 수정' : '문제 품질 피드백' }}
            </button>
            <div v-if="feedbackForms[index]?.open" class="feedback-form">
              <p>문제나 해설에 개선이 필요한 부분이 있나요? 의견은 생성 품질을 개선하는 데 사용됩니다.</p>
              <label :for="`feedback-type-${index}`">피드백 유형</label>
              <select :id="`feedback-type-${index}`" v-model="feedbackForms[index].feedbackType">
                <option value="">유형을 선택해주세요</option>
                <option v-for="feedbackType in feedbackTypes" :key="feedbackType.value" :value="feedbackType.value">
                  {{ feedbackType.label }}
                </option>
              </select>
              <label :for="`feedback-comment-${index}`">의견 <span>(선택, 기타는 필수)</span></label>
              <textarea
                :id="`feedback-comment-${index}`"
                v-model="feedbackForms[index].comment"
                maxlength="500"
                rows="3"
                placeholder="예: 정답은 B도 가능해 보입니다. 판단 근거를 보완해주세요."
              ></textarea>
              <div class="feedback-form-footer">
                <span>{{ feedbackForms[index].comment.length }}/500</span>
                <button
                  type="button"
                  class="feedback-submit"
                  :disabled="feedbackForms[index].saving || !feedbackForms[index].feedbackType"
                  @click="saveQuestionFeedback(index)"
                >
                  {{ feedbackForms[index].saving ? '저장 중...' : feedbackForms[index].saved ? '피드백 수정 저장' : '피드백 보내기' }}
                </button>
              </div>
              <p v-if="feedbackForms[index].message" class="feedback-message success">{{ feedbackForms[index].message }}</p>
              <p v-if="feedbackForms[index].error" class="feedback-message error">{{ feedbackForms[index].error }}</p>
            </div>
          </section>
      </div>
      
      <div class="next-quiz-section">
        <div v-if="submissionMessage" class="result-summary">
          {{ submissionMessage }}
        </div>
        <div class="quiz-actions">
          <button @click="submitQuiz" :disabled="loading || attemptSaving || quizSubmitted || !isAllQuestionsAnswered()" class="submit-button">
            {{ attemptSaving ? '풀이 기록 저장 중...' : quizSubmitted ? (attemptSaved ? '채점 및 기록 저장 완료' : '채점 완료') : isAllQuestionsAnswered() ? '답안 제출' : `미응답 ${getUnansweredQuestionCount()}개` }}
          </button>
          <button @click="showNextQuizOptions" :disabled="attemptSaving" class="next-quiz-button">
            다음 문제 생성
          </button>
          <button @click="saveQuizToGit" :disabled="loading || attemptSaving || !quizSubmitted" class="save-button" style="background-color:#f05033;" title="풀이 결과를 Markdown으로 저장한 뒤 notes 원격 저장소에 commit·push합니다.">
            {{ loading ? '저장 중...' : quizSubmitted ? 'Git에 저장' : '채점 후 Git 저장' }}
          </button>
        </div>

        <div v-if="attemptMessage" class="save-message" :class="{ 'error-message': attemptMessage.includes('실패') || attemptMessage.includes('없습니다') }">
          {{ attemptMessage }}
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
            <label for="nextQuestions">문제 수 (최대 20):</label>
            <input 
              type="number" 
              id="nextQuestions" 
              v-model.number="nextQuizQuestions" 
              min="1" max="20"
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
  max-width: 1200px;
  min-width: 0;
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
.quiz-input-group input[type="number"],
.quiz-input-group textarea {
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
.quiz-input-group input[type="number"]:focus,
.quiz-input-group textarea:focus {
  border-color: #667eea;
  outline: none;
}

.quiz-input-group textarea {
  resize: vertical;
  font-family: inherit;
}

.demo-mode-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #444;
  font-size: 14px;
}

.source-message,
.source-reference {
  color: #356a48;
  font-size: 14px;
}

.recommendation-message {
  margin: 12px 0 0;
  padding: 11px 13px;
  border: 1px solid #cdd5ff;
  border-radius: 7px;
  background: #f4f5ff;
  color: #4653a5;
  font-size: 14px;
  font-weight: 600;
}

.question-feedback {
  margin-top: 16px;
  border-top: 1px dashed #d8deea;
  padding-top: 14px;
}

.feedback-toggle {
  width: auto;
  margin: 0;
  padding: 9px 12px;
  border: 1px solid #c8d0e8;
  border-radius: 7px;
  background: #fff;
  color: #465575;
  font-size: 13px;
  font-weight: 700;
}

.feedback-toggle:hover:not(:disabled) {
  border-color: #667eea;
  background: #f5f6ff;
}

.feedback-form {
  display: grid;
  gap: 8px;
  margin-top: 12px;
  padding: 14px;
  border: 1px solid #dce4f2;
  border-radius: 8px;
  background: #fff;
  text-align: left;
}

.feedback-form p {
  margin: 0 0 2px;
  color: #596780;
  font-size: 13px;
  line-height: 1.5;
}

.feedback-form label {
  color: #43526f;
  font-size: 13px;
  font-weight: 700;
}

.feedback-form label span,
.feedback-form-footer span {
  color: #7c879d;
  font-size: 12px;
  font-weight: 500;
}

.feedback-form select,
.feedback-form textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #cdd6e6;
  border-radius: 6px;
  padding: 10px;
  color: #334155;
  font: inherit;
}

.feedback-form textarea {
  resize: vertical;
}

.feedback-form select:focus,
.feedback-form textarea:focus {
  outline: 2px solid rgb(102 126 234 / 28%);
  border-color: #667eea;
}

.feedback-form-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.feedback-submit {
  width: auto;
  margin: 0;
  padding: 9px 12px;
  font-size: 13px;
}

.feedback-message {
  margin: 0 !important;
  font-weight: 700;
}

.feedback-message.success {
  color: #24744d;
}

.feedback-message.error {
  color: #be3c35;
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
  text-align: left;
  font-family: inherit;
}

.option-item:hover:not(:disabled) {
  background-color: #e8f0fe;
  border-color: #667eea;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.15);
}

.option-item:disabled {
  cursor: default;
  opacity: 1;
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

.result-summary {
  max-width: 560px;
  margin: 0 auto 20px;
  padding: 16px 20px;
  border: 1px solid #b8c2ff;
  border-radius: 8px;
  background-color: #eef0ff;
  color: #3f4a9a;
  font-size: 18px;
  font-weight: 700;
}

.quiz-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 15px;
  justify-content: center;
  margin-bottom: 20px;
}

.submit-button {
  background-color: #5a67d8;
  color: white;
  padding: 15px 30px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 16px;
  font-weight: 600;
  transition: all 0.3s ease;
  box-shadow: 0 4px 12px rgba(90, 103, 216, 0.3);
}

.submit-button:hover:not(:disabled) {
  background-color: #4c51bf;
  transform: translateY(-2px);
}

.submit-button:disabled {
  background-color: #6c757d;
  cursor: not-allowed;
  box-shadow: none;
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

button:focus-visible,
.quiz-input-group input:focus-visible,
.quiz-input-group textarea:focus-visible,
.form-group input:focus-visible {
  outline: 3px solid rgba(102, 126, 234, 0.35);
  outline-offset: 2px;
}

@media (max-width: 680px) {
  .quiz-container {
    padding: 26px 18px 36px;
    border-radius: 0;
  }

  .quiz-input-group input[type="text"],
  .quiz-input-group input[type="number"],
  .quiz-input-group textarea {
    padding: 13px 14px;
  }

  .question-item,
  .answer-section,
  .next-quiz-form {
    padding: 16px;
  }

  .form-buttons { flex-direction: column; }
  .form-buttons button { width: 100%; }
}
</style>
