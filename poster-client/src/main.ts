import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { router } from './router'
import App from './App.vue'
import './styles/base.css'
import { useSessionStore } from './stores/session'

const pinia = createPinia()
router.beforeEach(async (to) => {
  if (!to.meta.requiresAuth) return true
  const session = useSessionStore(pinia)
  if (session.isAuthenticated || await session.restore()) return true
  return { path: '/', query: { returnTo: to.fullPath } }
})

void useSessionStore(pinia).restore()

createApp(App).use(pinia).use(router).mount('#app')
