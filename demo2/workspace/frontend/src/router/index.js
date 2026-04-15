import { createRouter, createWebHistory } from 'vue-router'

const Login = () => import('../views/auth/Login.vue')
const AdminDashboard = () => import('../views/admin/Dashboard.vue')
const DeveloperDashboard = () => import('../views/developer/Dashboard.vue')
const BusinessDashboard = () => import('../views/business/Dashboard.vue')
const PromptComposer = () => import('../views/business/PromptComposer.vue')

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: Login,
    meta: { requiresAuth: false }
  },
  {
    path: '/admin',
    name: 'Admin',
    component: AdminDashboard,
    meta: { requiresAuth: true, role: 'ADMIN' }
  },
  {
    path: '/developer',
    name: 'Developer',
    component: DeveloperDashboard,
    meta: { requiresAuth: true, role: 'DEVELOPER' }
  },
  {
    path: '/business',
    name: 'Business',
    component: BusinessDashboard,
    meta: { requiresAuth: true, role: 'BUSINESS' }
  },
  {
    path: '/business/psus/:psuId/composer',
    name: 'PromptComposer',
    component: PromptComposer,
    meta: { requiresAuth: true, role: 'BUSINESS' }
  },
  {
    path: '/',
    redirect: '/login'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  
  if (to.meta.requiresAuth !== false && !token) {
    next('/login')
  } else if (to.meta.role) {
    // 角色权限检查（后续实现）
    const userRole = localStorage.getItem('userRole')
    if (userRole === to.meta.role) {
      next()
    } else {
      // 重定向到对应角色的首页
      if (userRole === 'ADMIN') {
        next('/admin')
      } else if (userRole === 'DEVELOPER') {
        next('/developer')
      } else if (userRole === 'BUSINESS') {
        next('/business')
      } else {
        next('/login')
      }
    }
  } else {
    next()
  }
})

export default router