import { mount, flushPromises } from '@vue/test-utils'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import AdminMonitoringView from './AdminMonitoringView.vue'

const { get } = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('axios', () => ({ default: { get } }))

describe('AdminMonitoringView', () => {
  beforeEach(() => { get.mockReset() })
  it('renders global summary and uses an explicit administrator user filter', async () => {
    get.mockImplementation(async url => ({ data: url.endsWith('/summary')
      ? { learning: { users: 2, attempts: 2, roadmaps: 2, pendingReviews: 2 },
          ai: { calls: 0, failures: 0, tokens: 0, averageLatencyMs: 0, unattributedCalls: 0 },
          budget: { todayTokens: 0, dailyTokenBudget: 50000, enforcementEnabled: true }, dailyAi: [] }
      : url.endsWith('/users') ? { total: 1, items: [{ id: 2, username: 'test1', attempts: 1, roadmaps: 1, tokens: 0 }] }
      : { total: 0, items: [] } }))
    const view = mount(AdminMonitoringView)
    await flushPromises()
    expect(view.text()).toContain('읽기 전용')
    expect(view.text()).toContain('test1')
    const button = view.findAll('button').find(b => b.text() === '기록 보기')
    await button.trigger('click'); await flushPromises()
    expect(get).toHaveBeenLastCalledWith('/api/admin/records/attempts', { params: { page: 0, size: 20, userId: 2 } })
  })
  it('shows an error instead of data on denied access', async () => {
    get.mockRejectedValue({ response: { status: 403 } })
    const view = mount(AdminMonitoringView)
    await flushPromises()
    expect(view.get('[role="alert"]').text()).toContain('전체 관리자 권한')
    expect(view.findAll('tbody tr')).toHaveLength(0)
  })
})
