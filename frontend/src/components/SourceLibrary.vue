<script setup>
import { computed, ref } from 'vue'
import axios from 'axios'

const emit = defineEmits(['create-roadmap'])

const activeType = ref('URL')
const sourceUrl = ref('')
const selectedFile = ref(null)
const textTitle = ref('')
const textContent = ref('')
const preview = ref(null)
const loading = ref(false)
const saving = ref(false)
const error = ref(null)
const message = ref(null)
const savedSource = ref(null)

const typeLabel = computed(() => ({ URL: '웹 URL', FILE: '파일', TEXT: '직접 입력' })[activeType.value])
const currentContentLength = computed(() => preview.value?.content?.length || 0)

function changeType(type) {
  activeType.value = type
  preview.value = null
  error.value = null
  message.value = null
  savedSource.value = null
}

function selectFile(event) {
  selectedFile.value = event.target.files?.[0] || null
  preview.value = null
  error.value = null
  message.value = null
  savedSource.value = null
}

async function createPreview() {
  loading.value = true
  preview.value = null
  error.value = null
  message.value = null

  try {
    let response
    if (activeType.value === 'URL') {
      response = await axios.post('/api/sources/previews/url', { url: sourceUrl.value.trim() })
    } else if (activeType.value === 'FILE') {
      const form = new FormData()
      form.append('file', selectedFile.value)
      response = await axios.post('/api/sources/previews/file', form)
    } else {
      response = await axios.post('/api/sources/previews/text', {
        title: textTitle.value.trim(),
        content: textContent.value
      })
    }
    preview.value = response.data
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '학습 자료를 미리 볼 수 없습니다.'
  } finally {
    loading.value = false
  }
}

async function saveSource() {
  if (!preview.value || saving.value) return
  saving.value = true
  error.value = null
  message.value = null
  try {
    const response = await axios.post('/api/sources', {
      title: preview.value.title,
      content: preview.value.content,
      sourceType: preview.value.sourceType,
      sourceUri: preview.value.sourceUri,
      originalName: preview.value.originalName,
      mimeType: preview.value.mimeType
    })
    const result = response.data
    savedSource.value = {
      sourceId: result.sourceId,
      title: preview.value.title,
      sourceUri: preview.value.sourceUri
    }
    message.value = result.reused
      ? `같은 본문이 이미 저장되어 있어 기존 자료 #${result.sourceId}를 사용합니다.`
      : `학습 자료 #${result.sourceId}를 ${result.chunkCount}개 조각으로 저장했습니다.`
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '학습 자료를 저장하지 못했습니다.'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="source-library">
    <section class="source-heading">
      <p class="eyebrow">LEARNING SOURCE</p>
      <h2>학습 자료 가져오기</h2>
      <p>파일, 공개 URL 또는 직접 입력한 글에서 텍스트를 추출합니다. 저장 전에 반드시 내용을 확인할 수 있습니다.</p>
    </section>

    <section class="security-note" aria-label="보안 안내">
      <strong>안전한 가져오기 범위</strong>
      <p>TXT·Markdown·PDF와 공개 HTML만 지원합니다. 파일은 10MB, URL 응답은 5MB로 제한하며 내부망 주소와 비표준 포트는 차단합니다. 로그인, 쿠키, 웹 검색은 사용하지 않습니다.</p>
    </section>

    <section class="source-card input-card">
      <div class="source-tabs" role="tablist" aria-label="자료 입력 방식">
        <button v-for="type in ['URL', 'FILE', 'TEXT']" :key="type" type="button" role="tab"
          :aria-selected="activeType === type" :class="{ active: activeType === type }" @click="changeType(type)">
          {{ ({ URL: 'URL', FILE: '파일', TEXT: '직접 입력' })[type] }}
        </button>
      </div>

      <div v-if="activeType === 'URL'" class="source-form">
        <label for="source-url">공개 문서 URL</label>
        <input id="source-url" v-model="sourceUrl" type="url" maxlength="2048" placeholder="https://example.com/article" @keyup.enter="createPreview" />
        <p>지정한 주소 한 곳만 가져옵니다. 링크를 따라 크롤링하거나 검색하지 않습니다.</p>
      </div>

      <div v-else-if="activeType === 'FILE'" class="source-form">
        <label for="source-file">문서 파일</label>
        <input id="source-file" type="file" accept=".txt,.md,.markdown,.pdf,text/plain,text/markdown,application/pdf" @change="selectFile" />
        <p v-if="selectedFile">선택: {{ selectedFile.name }} · {{ Math.ceil(selectedFile.size / 1024).toLocaleString() }}KB</p>
        <p v-else>TXT, Markdown(.md), PDF · 최대 10MB</p>
      </div>

      <div v-else class="source-form manual-form">
        <label for="source-text-title">자료 제목</label>
        <input id="source-text-title" v-model="textTitle" maxlength="255" placeholder="예: Kubernetes Service 정리" />
        <label for="source-text-content">자료 내용</label>
        <textarea id="source-text-content" v-model="textContent" maxlength="50000" rows="9" placeholder="학습할 내용을 붙여 넣으세요."></textarea>
        <p>{{ textContent.length.toLocaleString() }}/50,000자</p>
      </div>

      <button type="button" class="primary-action"
        :disabled="loading || (activeType === 'URL' && !sourceUrl.trim()) || (activeType === 'FILE' && !selectedFile) || (activeType === 'TEXT' && (!textTitle.trim() || !textContent.trim()))"
        @click="createPreview">
        {{ loading ? '안전하게 가져오는 중...' : `${typeLabel} 미리보기` }}
      </button>
    </section>

    <p v-if="error" class="status-message error" role="alert">{{ error }}</p>
    <p v-if="message" class="status-message success" role="status">{{ message }}</p>
    <button v-if="savedSource" type="button" class="roadmap-action" @click="emit('create-roadmap', savedSource)">
      이 자료로 AI 로드맵 만들기
    </button>

    <section v-if="preview" class="source-card preview-card">
      <div class="preview-title-row">
        <div>
          <p class="eyebrow">PREVIEW</p>
          <h3>추출 결과 확인</h3>
        </div>
        <span class="source-type-badge">{{ preview.sourceType }}</span>
      </div>

      <div class="preview-stats">
        <span><strong>{{ currentContentLength.toLocaleString() }}</strong>자</span>
        <span><strong>{{ preview.estimatedChunkCount }}</strong>개 예상 조각</span>
        <span><strong>{{ preview.mimeType }}</strong></span>
      </div>
      <p v-if="preview.duplicate" class="duplicate-note">같은 추출 본문이 자료 #{{ preview.existingSourceId }}에 이미 있습니다. 내용을 그대로 저장하면 기존 자료를 재사용합니다.</p>
      <p v-if="preview.sourceUri" class="provenance">출처: <a :href="preview.sourceUri" target="_blank" rel="noopener noreferrer">{{ preview.sourceUri }}</a></p>
      <p v-if="preview.originalName" class="provenance">원본 파일: {{ preview.originalName }}</p>

      <label for="preview-title">저장할 제목</label>
      <input id="preview-title" v-model="preview.title" maxlength="255" />
      <label for="preview-content">저장할 본문</label>
      <textarea id="preview-content" v-model="preview.content" maxlength="50000" rows="18"></textarea>
      <div class="preview-footer">
        <p>추출 결과는 신뢰할 수 없는 외부 입력으로 취급됩니다. 내용이 올바른지 확인한 후 저장하세요.</p>
        <button type="button" class="primary-action" :disabled="saving || !preview.title.trim() || !preview.content.trim()" @click="saveSource">
          {{ saving ? '저장 중...' : '확인한 내용 저장' }}
        </button>
      </div>
    </section>

    <section class="next-phase-note">
      <strong>자료 기반 로드맵 연결</strong>
      <p>저장한 자료는 학습 로드맵 메뉴에서 선택할 수 있습니다. AI 생성 버튼을 누르면 제한된 본문 조각만 전송해 대주제와 소주제를 구성합니다.</p>
    </section>
  </div>
</template>

<style scoped>
.source-library { max-width: 1100px; margin: 0 auto; padding: 40px 50px 56px; }
.source-heading h2, .source-heading p { margin: 0; }
.source-heading h2 { font-size: 1.75rem; }
.source-heading > p:last-child { margin-top: 8px; color: var(--muted); }
.eyebrow { margin: 0 0 6px; color: var(--accent); font-size: .76rem; font-weight: 800; letter-spacing: .12em; }
.security-note, .next-phase-note { margin-top: 24px; padding: 16px 18px; border: 1px solid var(--line); border-radius: 12px; background: var(--surface-subtle); }
.security-note p, .next-phase-note p { margin: 4px 0 0; color: var(--muted); font-size: .92rem; }
.source-card { margin-top: 22px; padding: 24px; border: 1px solid var(--line); border-radius: 14px; background: var(--surface); }
.source-tabs { display: flex; gap: 6px; padding-bottom: 20px; }
.source-tabs button { padding: 8px 14px; border: 1px solid var(--line); border-radius: 8px; color: var(--muted); background: var(--surface); cursor: pointer; font-weight: 700; }
.source-tabs button.active { color: #fff; border-color: var(--ink); background: var(--ink); }
.source-form { display: grid; gap: 8px; }
.source-form label, .preview-card label { color: var(--ink-soft); font-weight: 750; }
.source-form p, .preview-footer p { margin: 0; color: var(--muted); font-size: .87rem; }
.manual-form label:not(:first-child) { margin-top: 8px; }
input, textarea { width: 100%; padding: 11px 12px; border: 1px solid var(--line-strong); border-radius: 8px; color: var(--ink); background: #fff; }
textarea { resize: vertical; line-height: 1.55; }
input:focus, textarea:focus { outline: 3px solid rgb(198 78 50 / 16%); border-color: var(--accent); }
.primary-action { margin-top: 18px; padding: 11px 17px; border: 0; border-radius: 8px; color: #fff; background: var(--ink); cursor: pointer; font-weight: 750; }
.primary-action:disabled { opacity: .45; cursor: not-allowed; }
.status-message { margin: 18px 0 0; padding: 12px 15px; border-radius: 9px; font-weight: 650; }
.status-message.error { color: var(--danger); background: var(--danger-soft); }
.status-message.success { color: var(--success); background: var(--success-soft); }
.roadmap-action { width: 100%; margin-top: 10px; padding: 11px 17px; border: 1px solid var(--ink); border-radius: 8px; color: var(--ink); background: var(--surface); cursor: pointer; font-weight: 800; }
.roadmap-action:hover { color: #fff; background: var(--ink); }
.preview-title-row, .preview-footer { display: flex; align-items: center; justify-content: space-between; gap: 18px; }
.preview-title-row h3 { margin: 0; font-size: 1.25rem; }
.source-type-badge { padding: 4px 9px; border: 1px solid var(--line); border-radius: 999px; color: var(--muted); font-size: .75rem; font-weight: 800; }
.preview-stats { display: flex; flex-wrap: wrap; gap: 10px; margin: 18px 0; }
.preview-stats span { padding: 7px 10px; border-radius: 7px; background: var(--surface-subtle); color: var(--muted); font-size: .83rem; }
.preview-stats strong { color: var(--ink); }
.duplicate-note { padding: 11px 12px; border-left: 3px solid var(--accent); background: var(--accent-soft); color: var(--accent-strong); }
.provenance { overflow-wrap: anywhere; color: var(--muted); font-size: .87rem; }
.provenance a { color: var(--ink); }
.preview-card > label { display: block; margin: 15px 0 6px; }
.preview-footer { margin-top: 14px; }
.preview-footer .primary-action { flex: 0 0 auto; margin: 0; }
.next-phase-note { border-style: dashed; }
@media (max-width: 680px) {
  .source-library { padding: 28px 18px 40px; }
  .source-card { padding: 18px; }
  .preview-title-row, .preview-footer { align-items: stretch; flex-direction: column; }
  .preview-footer .primary-action { width: 100%; }
}
</style>
