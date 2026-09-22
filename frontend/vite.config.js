import { fileURLToPath, URL } from 'node:url'
import { readFileSync } from 'node:fs'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

function dailyTechLearningSamplePlugin() {
  const samplePath = fileURLToPath(new URL('../docs/DAILY_TECH_LEARNING_SAMPLE.html', import.meta.url))

  return {
    name: 'daily-tech-learning-sample',
    configureServer(server) {
      // 데일리 학습 화면 제안서는 개발 Vite와 Quick Tunnel에서만 한 파일로 제한해 제공한다.
      // docs 전체를 정적 디렉터리로 열지 않아 로컬 운영 문서가 외부에 노출되지 않는다.
      server.middlewares.use((req, res, next) => {
        if (req.url?.split('?')[0] !== '/daily-tech-learning-sample.html') {
          return next()
        }
        if (req.method !== 'GET' && req.method !== 'HEAD') {
          res.statusCode = 405
          return res.end()
        }
        res.setHeader('Content-Type', 'text/html; charset=utf-8')
        if (req.method === 'HEAD') {
          return res.end()
        }
        return res.end(readFileSync(samplePath))
      })
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
    dailyTechLearningSamplePlugin(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
        // Quick Tunnel 접속자는 Vite를 통과한다. 원격 URL·인증 정보를 메일로 보내는
        // API는 로컬 스크립트만 localhost:8080으로 직접 호출할 수 있게 분리한다.
        bypass(req) {
          if (req.url?.startsWith('/api/notifications/remote-access/email')) {
            return '/__auknowlog_local_only'
          }
        },
      }
    }
  }
})
