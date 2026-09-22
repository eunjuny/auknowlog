import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
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
