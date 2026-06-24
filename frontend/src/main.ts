import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import JobsListPage from './pages/JobsListPage.vue'
import JobDetailsPage from './pages/JobDetailsPage.vue'
import SearchCriteriaPage from './pages/SearchCriteriaPage.vue'
import './styles.css'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/jobs' },
    { path: '/jobs', component: JobsListPage },
    { path: '/jobs/:id', component: JobDetailsPage, props: true },
    { path: '/search-criteria', component: SearchCriteriaPage }
  ]
})

createApp(App).use(router).mount('#app')
