import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import QuizGenerator from './QuizGenerator.vue'

const { post } = vi.hoisted(() => ({ post: vi.fn() }))

vi.mock('axios', () => ({ default: { post } }))

const quiz = {
  quizId: 101,
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

describe('QuizGenerator', () => {
  beforeEach(() => post.mockReset())

  it('keeps answers hidden until the server returns a grading result', async () => {
    post.mockResolvedValueOnce({ data: quiz }).mockResolvedValueOnce({ data: grading })
    const wrapper = mount(QuizGenerator)

    await wrapper.get('#topic').setValue('Java')
    await wrapper.get('[data-testid="quiz-generate"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('정답:')
    expect(wrapper.find('[data-testid="quiz-submit"]').attributes('disabled')).toBeDefined()

    await wrapper.get('[data-testid="quiz-option-0-0"]').trigger('click')
    expect(wrapper.find('[data-testid="quiz-submit"]').attributes('disabled')).toBeUndefined()

    await wrapper.get('[data-testid="quiz-submit"]').trigger('click')
    await flushPromises()

    expect(post).toHaveBeenLastCalledWith('/api/learning-attempts', {
      quizId: 101,
      answers: [{ questionOrder: 1, selectedAnswer: '바이트코드 실행' }],
    })
    expect(wrapper.get('[data-testid="quiz-answer-0"]').text()).toContain('정답:')
    expect(wrapper.get('[data-testid="quiz-submission-result"]').text()).toContain('풀이 기록 자동 저장됨')
  })
})
