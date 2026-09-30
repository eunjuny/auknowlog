import { mount, flushPromises } from '@vue/test-utils'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import AccountSettingsView from './AccountSettingsView.vue'
const { get, put, post } = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn(), post: vi.fn() }))
vi.mock('axios', () => ({ default: { get, put, post } }))
describe('AccountSettingsView', () => {
  beforeEach(() => { get.mockReset(); put.mockReset(); post.mockReset() })
  it('shows own quota, saves only recipient settings and disables sending before verification', async () => {
    get.mockImplementation(async url => ({ data: url.endsWith('ai-budget')
      ? { usedTokens: 200, reservedTokens: 1000, tokenLimit: 20000, startedCalls: 1, callLimit: 100 }
      : url.endsWith('history') ? { items: [], total: 0 }
      : { email: '', verified: false, reviewEnabled: false, dailyEnabled: false, sendHour: 8, deliveryEnabled: false, smtpConfigured: true } }))
    put.mockResolvedValue({ data: {} })
    const view = mount(AccountSettingsView); await flushPromises()
    expect(view.text()).toContain('20,000')
    expect(view.findAll('button').find(b => b.text() === '인증 메일 요청').attributes('disabled')).toBeDefined()
    await view.get('input[type=email]').setValue('recipient@example.invalid')
    await view.get('input[type=checkbox]').setValue(true)
    await view.get('form').trigger('submit'); await flushPromises()
    expect(put).toHaveBeenCalledWith('/api/account/notifications', {
      email: 'recipient@example.invalid', reviewEnabled: true, dailyEnabled: false, sendHour: 8
    })
    expect(post).not.toHaveBeenCalled()
  })
  it('does not expose SMTP exception details when initial settings are unavailable', async () => {
    get.mockRejectedValue(new Error('fixture SMTP secret detail'))
    const view = mount(AccountSettingsView); await flushPromises()
    expect(view.get('[role=alert]').text()).not.toContain('secret')
  })
})
