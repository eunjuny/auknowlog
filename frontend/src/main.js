import './assets/main.css'

import { createApp } from 'vue'
import App from './App.vue'
import { initializeAuth } from './auth'

await initializeAuth()
createApp(App).mount('#app')
