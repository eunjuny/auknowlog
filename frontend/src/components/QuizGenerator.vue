<script setup>
import { ref, watch } from 'vue';
import axios from 'axios';

const props = defineProps({
  recommendedQuiz: {
    type: Object,
    default: null
  }
});
const emit = defineEmits(['open-roadmap'])

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
const gradingResults = ref({});
const feedbackForms = ref({});
const recommendationMessage = ref(null);
const roadmapContext = ref(null);
const roadmapDecision = ref(null);
const roadmapDecisionLoading = ref(false);
const nextRoadmapUnit = ref(null);
const reviewRegistrations = ref({});

const feedbackTypes = [
  { value: 'INCORRECT_CONTENT', label: '정답 또는 내용이 부정확해요' },
  { value: 'AMBIGUOUS', label: '질문이 모호해요' },
  { value: 'EXPLANATION_INSUFFICIENT', label: '해설이 부족해요' },
  { value: 'TOO_SIMILAR', label: '비슷한 문제가 자주 나와요' },
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
        sourceId: props.recommendedQuiz.sourceId || null,
        topic: props.recommendedQuiz.topic,
        additionalPractice: props.recommendedQuiz.additionalPractice === true
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
  gradingResults.value = {};
  sourceMessage.value = null;
  feedbackForms.value = {};
  reviewRegistrations.value = {};
  roadmapDecision.value = null;
  nextRoadmapUnit.value = null;
  recommendationMessage.value = null;

  try {
    let sourceId = roadmapContext.value?.topic === topic.value.trim()
      ? roadmapContext.value.sourceId
      : null;
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
      roadmapStepId: roadmapContext.value?.topic === topic.value.trim() ? roadmapContext.value.roadmapStepId : null,
      additionalPractice: roadmapContext.value?.topic === topic.value.trim()
        ? roadmapContext.value.additionalPractice === true
        : false
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
        gradingResults.value = Object.fromEntries(
          (result.questions || []).map((question) => [question.questionOrder - 1, question])
        );
        attemptSaved.value = true;
        attemptMessage.value = null;
        submissionMessage.value = `채점 완료 · ${result.correctAnswers}/${result.totalQuestions} 정답 · 풀이 기록 자동 저장됨` +
          (result.reviewScheduledCount > 0 ? ` · 오답 ${result.reviewScheduledCount}개는 내일 복습으로 예약됐습니다.` : '');
        await loadRoadmapDecision();
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

async function loadRoadmapDecision() {
  if (!roadmapContext.value?.roadmapId || !roadmapContext.value?.roadmapStepId) {
    roadmapDecision.value = null;
    return;
  }
  try {
    const response = await axios.get(`/api/learning-roadmaps/${roadmapContext.value.roadmapId}`);
    const step = (response.data.steps || []).find((item) => item.stepId === roadmapContext.value.roadmapStepId);
    roadmapDecision.value = step?.awaitingDecision ? step : null;
  } catch (err) {
    console.warn('로드맵 다음 단계 선택 상태를 불러오지 못했습니다.', err);
  }
}

async function continueRoadmapSubtopic() {
  if (!roadmapContext.value || roadmapDecisionLoading.value) return;
  roadmapDecisionLoading.value = true;
  roadmapContext.value = { ...roadmapContext.value, additionalPractice: true };
  numberOfQuestions.value = 5;
  try {
    await generateQuiz();
  } finally {
    roadmapDecisionLoading.value = false;
  }
}

async function advanceRoadmapStep() {
  if (!roadmapContext.value || roadmapDecisionLoading.value) return;
  roadmapDecisionLoading.value = true;
  try {
    const response = await axios.post(`/api/learning-roadmaps/${roadmapContext.value.roadmapId}/steps/${roadmapContext.value.roadmapStepId}/advance`);
    roadmapDecision.value = null;
    nextRoadmapUnit.value = findNextRoadmapUnit(response.data);
    submissionMessage.value = nextRoadmapUnit.value
      ? '다음 학습 단위를 준비했습니다. 이 화면에서 바로 이어서 시작할 수 있습니다.'
      : '이 로드맵의 모든 학습 단위를 완료했습니다.';
  } catch (err) {
    submissionMessage.value = '다음 단계 진행 처리 실패: ' + (err.response?.data?.message || err.message);
  } finally {
    roadmapDecisionLoading.value = false;
  }
}

function findNextRoadmapUnit(roadmap) {
  const nextStep = (roadmap?.steps || []).find((step) => step.status === 'READY' || step.status === 'IN_PROGRESS');
  if (!nextStep) return null;

  const majorTopic = (roadmap.majorTopics || []).find((major) =>
    major.stepId === nextStep.stepId || (major.subtopics || []).some((subtopic) => subtopic.stepId === nextStep.stepId)
  );
  const isSubtopic = Boolean(majorTopic?.subtopics?.some((subtopic) => subtopic.stepId === nextStep.stepId));
  const previousMajorTopic = (roadmap.majorTopics || []).find((major) =>
    major.stepId === roadmapContext.value?.roadmapStepId || (major.subtopics || []).some((subtopic) => subtopic.stepId === roadmapContext.value?.roadmapStepId)
  );
  const unitType = isSubtopic && previousMajorTopic?.key === majorTopic?.key
    ? '새 소주제'
    : isSubtopic
      ? '새 대주제의 첫 소주제'
      : '새 대주제';
  const remainingQuestions = Math.min(20, Math.max(1, nextStep.questionTarget - nextStep.completedQuestions));

  return {
    roadmapId: roadmap.roadmapId,
    roadmapStepId: nextStep.stepId,
    sourceId: roadmap.sourceDocumentId || null,
    topic: nextStep.topic,
    title: nextStep.title,
    majorTitle: majorTopic?.title || nextStep.title,
    unitType,
    questionCount: remainingQuestions,
    totalQuestionTarget: nextStep.questionTarget,
    description: nextStep.description
  };
}

async function startNextRoadmapUnit() {
  if (!nextRoadmapUnit.value || roadmapDecisionLoading.value) return;
  roadmapDecisionLoading.value = true;
  const nextUnit = nextRoadmapUnit.value;
  roadmapContext.value = {
    roadmapId: nextUnit.roadmapId,
    roadmapStepId: nextUnit.roadmapStepId,
    sourceId: nextUnit.sourceId,
    topic: nextUnit.topic,
    additionalPractice: false
  };
  topic.value = nextUnit.topic;
  numberOfQuestions.value = nextUnit.questionCount;
  try {
    await generateQuiz();
  } finally {
    roadmapDecisionLoading.value = false;
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

async function submitQuiz() {
  if (quizSubmitted.value && !attemptSaved.value) {
    submissionMessage.value = '서버 채점 및 풀이 기록 저장을 다시 시도합니다.';
    await saveLearningAttempt();
    return;
  }
  if (!isAllQuestionsAnswered()) {
    submissionMessage.value = `미응답 문제가 ${getUnansweredQuestionCount()}개 남아 있습니다.`;
    return;
  }

  quizSubmitted.value = true;
  submissionMessage.value = '서버에서 채점하고 풀이 기록과 복습 일정을 저장하는 중입니다...';
  await saveLearningAttempt();
}

function isQuestionCorrect(questionIndex) {
  return gradingResults.value[questionIndex]?.correct === true;
}

function buildGradedQuizPayload() {
  const questions = quizResult.value.questions.map((question, index) => {
    const grade = gradingResults.value[index];
    return {
      ...question,
      userSelectedIndex: selectedAnswers.value[index],
      userSelectedAnswer: grade.selectedAnswer,
      correctAnswer: grade.correctAnswer,
      explanation: grade.explanation,
      sourceReferences: grade.sourceReferences,
      isCorrect: grade.correct
    };
  });
  const correct = questions.filter((question) => question.isCorrect).length;
  return {
    ...quizResult.value,
    userAnswers: selectedAnswers.value,
    questions,
    stats: { total: questions.length, correct, wrong: questions.length - correct }
  };
}

async function registerQuestionForReview(questionIndex) {
  if (!attemptSaved.value || !isQuestionCorrect(questionIndex)) {
    return;
  }

  const state = reviewRegistrations.value[questionIndex] || {
    saving: false,
    registered: false,
    message: null,
    error: null
  };
  reviewRegistrations.value[questionIndex] = state;
  if (state.saving || state.registered) {
    return;
  }

  state.saving = true;
  state.error = null;
  try {
    const response = await axios.post('/api/reviews', {
      quizId: quizResult.value.quizId,
      questionOrder: questionIndex + 1
    });
    state.registered = true;
    state.message = response.data.created
      ? '내일 복습 대상으로 추가했습니다.'
      : '이미 복습 대상으로 등록된 문항입니다.';
  } catch (err) {
    state.error = '복습 등록 실패: ' + (err.response?.data?.message || err.message);
  } finally {
    state.saving = false;
  }
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
  if (!attemptSaved.value) {
    saveMessage.value = '서버 채점과 풀이 저장이 끝난 후 저장할 수 있습니다.';
    return;
  }

  loading.value = true;
  saveMessage.value = null;
  error.value = null;

  try {
    const payload = buildGradedQuizPayload();

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
  if (!attemptSaved.value) {
    saveMessage.value = '서버 채점과 풀이 저장이 끝난 후 저장할 수 있습니다.';
    return;
  }

  loading.value = true;
  saveMessage.value = null;
  error.value = null;

  try {
    const payload = buildGradedQuizPayload();

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
  if (!attemptSaved.value) {
    saveMessage.value = '서버 채점과 풀이 저장이 끝난 후 저장할 수 있습니다.';
    return;
  }

  loading.value = true;
  saveMessage.value = null;
  error.value = null;

  try {
    const payload = buildGradedQuizPayload();

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
    <div class="quiz-heading">
      <p class="eyebrow">QUIZ</p>
      <h2>새 퀴즈 만들기</h2>
      <p>학습할 주제와 문제 수를 정하고, 필요하면 참고 자료를 함께 입력하세요.</p>
    </div>
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
              'selected': selectedAnswers[index] === optIndex && (!quizSubmitted || !gradingResults[index]),
              'correct-answer': gradingResults[index] && option === gradingResults[index].correctAnswer,
              'wrong-answer': gradingResults[index] && selectedAnswers[index] === optIndex && option !== gradingResults[index].correctAnswer,
              'not-selected': gradingResults[index] && selectedAnswers[index] !== optIndex && option !== gradingResults[index].correctAnswer
            }"
            @click="selectOption(index, optIndex)"
          >
            {{ String.fromCharCode(65 + optIndex) }}. {{ option }}
          </button>
        </div>
        
          <div v-if="gradingResults[index]"
             class="answer-section"
             :class="{
               'correct-result': gradingResults[index].correct,
               'incorrect-result': !gradingResults[index].correct
             }">
          <div class="result-indicator">
            <span v-if="gradingResults[index].correct" class="correct-icon">✓</span>
            <span v-else class="incorrect-icon">✗</span>
            <strong v-if="gradingResults[index].correct">정답입니다!</strong>
            <strong v-else>틀렸습니다.</strong>
          </div>
          <p><strong>정답:</strong> {{ gradingResults[index].correctAnswer }}</p>
            <p><strong>설명:</strong> {{ gradingResults[index].explanation }}</p>
          </div>
          <p v-if="quizSubmitted && question.sourceReferences?.length" class="source-reference">
            <strong>근거:</strong> {{ question.sourceReferences.join(', ') }}
          </p>
          <section v-if="attemptSaved" class="review-registration" :aria-label="`${index + 1}번 문제 복습 등록`">
            <template v-if="isQuestionCorrect(index)">
              <button
                type="button"
                class="review-add-button"
                :disabled="!attemptSaved || reviewRegistrations[index]?.saving || reviewRegistrations[index]?.registered"
                @click="registerQuestionForReview(index)"
              >
                {{ reviewRegistrations[index]?.saving ? '복습에 추가 중...' : reviewRegistrations[index]?.registered ? '복습 추가 완료' : '이 문제도 복습하기' }}
              </button>
            </template>
            <span v-else class="review-auto-label">
              오답은 내일 복습 대상으로 자동 등록됐습니다.
            </span>
            <p v-if="reviewRegistrations[index]?.message" class="review-message success">{{ reviewRegistrations[index].message }}</p>
            <p v-if="reviewRegistrations[index]?.error" class="review-message error">{{ reviewRegistrations[index].error }}</p>
          </section>
          <section v-if="attemptSaved" class="question-feedback" :aria-label="`${index + 1}번 문제 품질 피드백`">
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
          <button @click="submitQuiz" :disabled="loading || attemptSaving || attemptSaved || (!quizSubmitted && !isAllQuestionsAnswered())" class="submit-button">
            {{ attemptSaving ? '서버 채점 및 저장 중...' : attemptSaved ? '채점 및 기록 저장 완료' : quizSubmitted ? '채점·저장 다시 시도' : isAllQuestionsAnswered() ? '답안 제출' : `미응답 ${getUnansweredQuestionCount()}개` }}
          </button>
          <button @click="showNextQuizOptions" :disabled="attemptSaving" class="next-quiz-button">
            다음 문제 생성
          </button>
          <button @click="saveQuizToGit" :disabled="loading || attemptSaving || !attemptSaved" class="save-button" title="풀이 결과를 Markdown으로 저장한 뒤 notes 원격 저장소에 commit·push합니다.">
            {{ loading ? '저장 중...' : attemptSaved ? 'Git에 저장' : '채점 후 Git 저장' }}
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
      <section v-if="roadmapDecision && attemptSaved" class="roadmap-decision-card" aria-live="polite">
        <p class="eyebrow">ROADMAP CHECKPOINT</p>
        <h3>“{{ roadmapDecision.title }}”의 필수 학습 목표를 모두 다뤘습니다.</h3>
        <p>같은 소주제를 더 연습하거나 완료를 확정하세요. 확정하면 이 화면에서 다음 학습 단위를 확인하고 바로 이어서 시작할 수 있습니다.</p>
        <div class="roadmap-decision-actions">
          <button type="button" class="secondary-button" :disabled="roadmapDecisionLoading" @click="continueRoadmapSubtopic">같은 소주제 추가 학습</button>
          <button type="button" class="primary-button" :disabled="roadmapDecisionLoading" @click="advanceRoadmapStep">다음 단계로 진행</button>
          <button type="button" class="text-button" :disabled="roadmapDecisionLoading" @click="emit('open-roadmap')">로드맵 보기</button>
        </div>
      </section>
      <section v-else-if="nextRoadmapUnit && attemptSaved" class="roadmap-next-card" aria-live="polite">
        <p class="eyebrow">NEXT LEARNING UNIT</p>
        <div class="roadmap-next-heading">
          <div>
            <span class="roadmap-next-type">{{ nextRoadmapUnit.unitType }}</span>
            <h3>{{ nextRoadmapUnit.majorTitle }} <template v-if="nextRoadmapUnit.majorTitle !== nextRoadmapUnit.title">· {{ nextRoadmapUnit.title }}</template></h3>
          </div>
          <strong>{{ nextRoadmapUnit.questionCount }}문제</strong>
        </div>
        <p>{{ nextRoadmapUnit.description || '남은 필수 학습 목표를 기준으로 문제를 준비합니다.' }}</p>
        <p class="roadmap-next-meta">이 학습 단위의 완료 기준은 총 {{ nextRoadmapUnit.totalQuestionTarget }}문제입니다. 한 번에 최대 20문제씩 생성합니다.</p>
        <div class="roadmap-decision-actions">
          <button type="button" class="primary-button" :disabled="roadmapDecisionLoading" @click="startNextRoadmapUnit">
            {{ roadmapDecisionLoading ? '다음 문제 준비 중...' : `“${nextRoadmapUnit.title}” 바로 시작` }}
          </button>
          <button type="button" class="text-button" :disabled="roadmapDecisionLoading" @click="emit('open-roadmap')">로드맵에서 전체 보기</button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.quiz-container {
  max-width: 1200px;
  min-width: 0;
  margin: 0 auto;
  padding: 40px 50px;
  background-color: var(--surface);
  min-height: 120px;
}

.quiz-heading h2,
.quiz-heading p {
  margin: 0;
}

.quiz-heading .eyebrow {
  color: var(--accent);
  font-size: .75rem;
  font-weight: 800;
  letter-spacing: .08em;
}

.quiz-heading h2 {
  color: var(--ink);
  font-size: clamp(1.7rem, 4vw, 2.25rem);
}

.quiz-heading p:not(.eyebrow) {
  margin-top: 6px;
  color: var(--muted);
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
  color: var(--ink);
}

.quiz-input-group input[type="text"],
.quiz-input-group input[type="number"],
.quiz-input-group textarea {
  width: 100%;
  max-width: 900px;
  padding: 15px 20px;
  border: 1px solid var(--line);
  border-radius: 8px;
  font-size: 16px;
  transition: border-color 0.3s ease;
  box-sizing: border-box;
}

.quiz-input-group input[type="text"]:focus,
.quiz-input-group input[type="number"]:focus,
.quiz-input-group textarea:focus {
  border-color: var(--accent);
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
  color: var(--ink-soft);
  font-size: 14px;
}

.demo-mode-toggle input {
  accent-color: var(--accent);
}

.source-message,
.source-reference {
  color: #356a48;
  font-size: 14px;
}

.recommendation-message {
  margin: 12px 0 0;
  padding: 11px 13px;
  border: 1px solid #f0c9bd;
  border-radius: 7px;
  background: var(--accent-soft);
  color: var(--accent-strong);
  font-size: 14px;
  font-weight: 600;
}

.question-feedback {
  margin-top: 16px;
  border-top: 1px dashed var(--line-strong);
  padding-top: 14px;
}

.review-registration {
  display: flex;
  flex-wrap: wrap;
  gap: 9px;
  align-items: center;
  margin-top: 14px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface-subtle);
}

.review-add-button {
  width: auto;
  margin: 0;
  padding: 9px 13px;
  border: 1px solid var(--ink);
  background: var(--surface);
  color: var(--ink);
  font-size: 13px;
}

.review-add-button:hover:not(:disabled) {
  background: var(--ink);
  color: #fff;
}

.review-auto-label,
.review-hint,
.review-message {
  color: var(--muted);
  font-size: 13px;
}

.review-message {
  flex-basis: 100%;
  margin: 0;
  font-weight: 700;
}

.review-message.success { color: #24744d; }
.review-message.error { color: #be3c35; }

.feedback-toggle {
  width: auto;
  margin: 0;
  padding: 9px 12px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: var(--surface);
  color: var(--ink-soft);
  font-size: 13px;
  font-weight: 700;
}

.feedback-toggle:hover:not(:disabled) {
  border-color: var(--ink);
  background: var(--surface-subtle);
}

.feedback-form {
  display: grid;
  gap: 8px;
  margin-top: 12px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
  text-align: left;
}

.feedback-form p {
  margin: 0 0 2px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.5;
}

.feedback-form label {
  color: var(--ink-soft);
  font-size: 13px;
  font-weight: 700;
}

.feedback-form label span,
.feedback-form-footer span {
  color: var(--muted);
  font-size: 12px;
  font-weight: 500;
}

.feedback-form select,
.feedback-form textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid var(--line);
  border-radius: 6px;
  padding: 10px;
  color: var(--ink-soft);
  font: inherit;
}

.feedback-form textarea {
  resize: vertical;
}

.feedback-form select:focus,
.feedback-form textarea:focus {
  outline: 2px solid rgb(198 78 50 / 18%);
  border-color: var(--accent);
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
  background-color: var(--ink);
  color: white;
  padding: 18px 25px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 17px;
  font-weight: 600;
  transition: background-color 0.3s ease;
  width: 100%;
  margin-top: 10px;
}

button:hover:not(:disabled) {
  background-color: var(--ink-soft);
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
  border-top: 1px solid var(--line);
}

.quiz-output-section h2 {
  text-align: center;
  color: var(--ink);
  margin-bottom: 25px;
  font-size: 24px;
}

.question-item {
  background-color: var(--surface-subtle);
  border: 1px solid var(--line);
  padding: 20px;
  margin-bottom: 20px;
  border-radius: 8px;
}

.question-item h3 {
  color: var(--ink);
  margin-top: 0;
  font-size: 18px;
  margin-bottom: 15px;
}

.options-container {
  margin-bottom: 15px;
}

.option-item {
  background-color: var(--surface);
  padding: 18px 20px;
  margin-bottom: 8px;
  border-radius: 8px;
  font-size: 15px;
  color: var(--ink-soft);
  cursor: pointer;
  transition: all 0.3s ease;
  border: 1px solid var(--line);
  width: 100%;
  box-sizing: border-box;
  text-align: left;
  font-family: inherit;
}

.option-item:hover:not(:disabled) {
  background-color: var(--surface-subtle);
  border-color: var(--ink);
  transform: translateY(-1px);
}

.option-item:disabled {
  cursor: default;
  opacity: 1;
}

.option-item.selected {
  background-color: #2563eb;
  color: white;
  border-color: #1d4ed8;
}

.option-item.correct-answer {
  background-color: #4caf50;
  color: white;
  border-color: #45a049;
  font-weight: 600;
}

.option-item.wrong-answer {
  background-color: #f44336;
  color: white;
  border-color: #d32f2f;
  font-weight: 600;
}

.option-item.not-selected {
  background-color: #f5f5f5;
  color: #666;
  border-color: #e0e0e0;
}

.answer-section {
  background-color: var(--surface);
  padding: 20px;
  border-radius: 8px;
  margin-top: 15px;
  border: 1px solid var(--line);
  border-left: 4px solid var(--accent);
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
  border-top: 1px solid var(--line);
}

.result-summary {
  max-width: 560px;
  margin: 0 auto 20px;
  padding: 16px 20px;
  border: 1px solid #f0c9bd;
  border-radius: 8px;
  background-color: var(--accent-soft);
  color: var(--accent-strong);
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

.roadmap-decision-card {
  display: grid;
  gap: 9px;
  margin-top: 18px;
  padding: 18px;
  border: 1px solid #ebc6b8;
  border-radius: 10px;
  background: var(--accent-soft);
}

.roadmap-next-card {
  display: grid;
  gap: 9px;
  margin-top: 18px;
  padding: 18px;
  border: 1px solid var(--ink);
  border-radius: 10px;
  background: var(--surface);
}

.roadmap-next-card .eyebrow { margin: 0; color: var(--accent-strong); font-size: .72rem; font-weight: 800; letter-spacing: .08em; }
.roadmap-next-card h3, .roadmap-next-card p { margin: 0; }
.roadmap-next-card p { color: var(--ink-soft); font-size: .88rem; }
.roadmap-next-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; }
.roadmap-next-heading h3 { margin-top: 5px; }
.roadmap-next-heading > strong { flex: 0 0 auto; padding: 5px 8px; border-radius: 999px; background: var(--accent-soft); color: var(--accent-strong); font-size: .8rem; }
.roadmap-next-type { color: var(--accent-strong); font-size: .76rem; font-weight: 800; }
.roadmap-next-meta { color: var(--ink-muted, var(--ink-soft)) !important; font-size: .8rem !important; }

.roadmap-decision-card .eyebrow { margin: 0; color: var(--accent-strong); font-size: .72rem; font-weight: 800; letter-spacing: .08em; }
.roadmap-decision-card h3, .roadmap-decision-card p:not(.eyebrow) { margin: 0; }
.roadmap-decision-card p:not(.eyebrow) { color: var(--ink-soft); font-size: .88rem; }
.roadmap-decision-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.roadmap-decision-actions button { width: auto; margin: 0; padding: 9px 12px; font-size: .82rem; }
.roadmap-decision-actions .primary-button { color: #fff; border: 1px solid var(--ink); border-radius: 7px; background: var(--ink); font-weight: 750; }
.roadmap-decision-actions .secondary-button { color: var(--ink); border: 1px solid var(--ink); border-radius: 7px; background: var(--surface); font-weight: 750; }
.roadmap-decision-actions .text-button { padding-left: 2px; color: var(--accent-strong); border: 0; background: transparent; font-weight: 750; text-decoration: underline; }

.submit-button {
  background-color: var(--ink);
  color: white;
  padding: 15px 30px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 16px;
  font-weight: 600;
  transition: all 0.3s ease;
}

.submit-button:hover:not(:disabled) {
  background-color: var(--ink-soft);
  transform: translateY(-2px);
}

.submit-button:disabled {
  background-color: #6c757d;
  cursor: not-allowed;
  box-shadow: none;
}

.save-button {
  background-color: var(--accent);
  color: white;
  padding: 15px 30px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 16px;
  font-weight: 600;
  transition: all 0.3s ease;
}

.save-button:hover:not(:disabled) {
  background-color: var(--accent-strong);
  transform: translateY(-2px);
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
  background-color: var(--surface);
  color: var(--ink);
  padding: 15px 30px;
  border: 1px solid var(--line-strong);
  border-radius: 8px;
  cursor: pointer;
  font-size: 16px;
  font-weight: 600;
  transition: all 0.3s ease;
}

.next-quiz-button:hover {
  background-color: var(--surface-subtle);
  border-color: var(--ink);
  transform: translateY(-2px);
}

.next-quiz-button:active {
  transform: translateY(0);
}

.next-quiz-form {
  background-color: var(--surface-subtle);
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 25px;
  margin-top: 20px;
  max-width: 500px;
  margin-left: auto;
  margin-right: auto;
}

.next-quiz-form h3 {
  margin: 0 0 20px 0;
  color: var(--ink);
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
  color: var(--ink);
}

.form-group input {
  width: 100%;
  padding: 12px 15px;
  border: 1px solid var(--line);
  border-radius: 5px;
  font-size: 14px;
  transition: border-color 0.3s ease;
  box-sizing: border-box;
}

.form-group input:focus {
  border-color: var(--accent);
  outline: none;
}

.form-buttons {
  display: flex;
  gap: 15px;
  justify-content: center;
  margin-top: 25px;
}

.confirm-button {
  background-color: var(--ink);
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
  background-color: var(--ink-soft);
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
  outline: 3px solid rgb(198 78 50 / 24%);
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
