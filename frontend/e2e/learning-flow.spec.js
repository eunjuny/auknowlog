import { expect, test } from '@playwright/test'

const quiz = {
  quizId: 1001,
  quizTitle: 'Java 퀴즈',
  questions: [{
    questionText: 'JVM의 역할은 무엇인가요?',
    options: ['바이트코드 실행', '이미지 편집', 'DNS 관리', '패킷 암호화'],
    sourceReferences: [],
  }],
}

const grading = {
  correctAnswers: 1,
  totalQuestions: 1,
  reviewScheduledCount: 0,
  questions: [{
    questionOrder: 1,
    selectedAnswer: '바이트코드 실행',
    correctAnswer: '바이트코드 실행',
    correct: true,
    explanation: 'JVM은 Java 바이트코드를 실행합니다.',
    optionExplanations: ['정답입니다.', 'JVM의 역할이 아닙니다.', 'JVM의 역할이 아닙니다.', 'JVM의 역할이 아닙니다.'],
    sourceReferences: [],
  }],
}

const dailyLearning = {
  dailyLearningId: 77,
  learningDate: '2026-09-28',
  articleTitle: '테스트용 기술 기사',
  articleUrl: 'https://example.com/tech-brief',
  articleSummary: '테스트용 기사 요약입니다.',
  supplement: '기사의 개념을 복습하기 위한 테스트용 보충 해설입니다.',
  concepts: [{ title: 'JVM', explanation: 'Java 바이트코드를 실행하는 런타임입니다.' }],
  reviewTopic: 'JVM 기초',
  recommendedReviewQuestionCount: 1,
  focusTier: 'DEVELOPER_CORE',
  status: 'READY',
  reviewQuizId: null,
  advancedQuizCount: 0,
  completedAt: null,
}

const emptyDashboard = {
  generatedAt: '2026-09-28',
  learning: { totalAttempts: 1, totalQuestions: 1, correctAnswers: 1, accuracyPercent: 100, dueReviewCount: 0, dailyActivity: [], topicAccuracy: [], recommendations: [] },
  ai: { totalCalls: 0, successfulCalls: 0, failedCalls: 0, totalTokens: 0, successRatePercent: 0, averageLatencyMs: 0, p95LatencyMs: 0, storedQuestionCount: 0, dailyBudgetEnforced: true, dailyTokenBudget: 10000, todayTokens: 0, remainingDailyTokens: 10000, dailyBudgetUsedPercent: 0, dailyActivity: [], modelUsage: [] },
  qualityFeedback: { totalFeedbackCount: 0, openFeedbackCount: 0, typeMetrics: [] },
  qualityEvaluation: { duplicateHumanSampleCount: 0, duplicatePendingReviewCount: 0, recommendedThreshold: null, duplicateThresholdMetrics: [], objectiveQuality: { totalCases: 0, humanReviewedCases: 0, pendingReviewCases: 0, provisionalOmissionRatePercent: null, provisionalAlignmentRatePercent: null, humanVerifiedOmissionRatePercent: null, humanVerifiedAlignmentRatePercent: null } },
}

async function mockLearningApi(page, { daily = null } = {}) {
  let completed = false
  await page.route('**/api/daily-learnings/today', (route) => route.fulfill({ json: completed && daily ? { ...daily, status: 'COMPLETED', completedAt: '2026-09-28T08:00:00' } : daily }))
  await page.route('**/api/quizzes/dummy', (route) => route.fulfill({ json: quiz }))
  await page.route('**/api/daily-learnings/77/review-quiz', (route) => route.fulfill({ json: quiz }))
  await page.route('**/api/learning-attempts', async (route) => {
    const request = route.request().postDataJSON()
    expect(request).toEqual({ quizId: 1001, answers: [{ questionOrder: 1, selectedAnswer: '바이트코드 실행' }] })
    completed = true
    await route.fulfill({ json: grading })
  })
  await page.route('**/api/dashboard', (route) => route.fulfill({ json: emptyDashboard }))
}

test('브라우저에서 데모 생성, 서버 채점, 자동 저장 결과를 확인한다', async ({ page }) => {
  await mockLearningApi(page)
  await page.goto('/')
  await page.getByRole('button', { name: '문제 생성', exact: true }).click()
  await page.getByLabel('주제:').fill('Java')
  await page.getByTestId('quiz-generate').click()

  await expect(page.getByTestId('quiz-answer-0')).toHaveCount(0)
  await expect(page.getByTestId('quiz-submit')).toBeDisabled()

  await page.getByTestId('quiz-option-0-0').click()
  await expect(page.getByTestId('quiz-submit')).toBeEnabled()
  await page.getByTestId('quiz-submit').click()

  await expect(page.getByTestId('quiz-answer-0')).toContainText('정답:')
  await expect(page.getByTestId('quiz-submission-result')).toContainText('풀이 기록 자동 저장됨')
})

test('데일리 학습의 복습 퀴즈가 완료 뒤 대시보드로 돌아간다', async ({ page }) => {
  await mockLearningApi(page, { daily: dailyLearning })
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '데일리 학습' })).toBeVisible()
  await expect(page.getByText('개발 핵심', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '복습 문제 시작' }).click()
  await page.getByTestId('quiz-option-0-0').click()
  await page.getByTestId('quiz-submit').click()

  await expect(page.getByRole('heading', { name: '학습과 AI 운영 현황' })).toBeVisible()
})

test('모바일 화면에서 주요 메뉴와 퀴즈 생성 화면이 가로로 넘치지 않는다', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 800 })
  await mockLearningApi(page)
  await page.goto('/')
  await page.getByRole('button', { name: '문제 생성', exact: true }).click()
  await expect(page.getByTestId('quiz-generator')).toBeVisible()
  expect(await page.locator('html').evaluate((element) => element.scrollWidth <= window.innerWidth)).toBeTruthy()
})
