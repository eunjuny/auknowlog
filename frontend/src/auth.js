import { reactive } from 'vue'
import axios from 'axios'
import Keycloak from 'keycloak-js'

const enabled = import.meta.env.VITE_AUTH_ENABLED === 'true'
let keycloak = null

export const authSession = reactive({
  enabled,
  authenticated: !enabled,
  username: enabled ? '' : 'eunjuny',
  displayName: enabled ? '' : 'Local learner',
  roles: enabled ? [] : ['USER', 'ADMIN']
})

export async function initializeAuth() {
  if (enabled) {
    keycloak = new Keycloak({
      url: import.meta.env.VITE_KEYCLOAK_URL || 'http://127.0.0.1:8180',
      realm: import.meta.env.VITE_KEYCLOAK_REALM || 'auknowlog',
      clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'auknowlog-web'
    })
    authSession.authenticated = await keycloak.init({
      onLoad: 'login-required',
      pkceMethod: 'S256',
      checkLoginIframe: false
    })
    authSession.username = keycloak.tokenParsed?.preferred_username || ''
    authSession.displayName = keycloak.tokenParsed?.name || authSession.username
    authSession.roles = keycloak.realmAccess?.roles || []
  }

  axios.interceptors.request.use(async config => {
    if (keycloak?.authenticated) {
      await keycloak.updateToken(30)
      config.headers.Authorization = `Bearer ${keycloak.token}`
    }
    return config
  })
}

export function hasRole(role) {
  return authSession.roles.includes(role)
}

export function logout() {
  if (keycloak) keycloak.logout({ redirectUri: window.location.origin })
}
