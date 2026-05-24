import { createRouter, createWebHistory } from 'vue-router';

const ROLE_NAME_MAP = {
  0: 'SUPER_ADMIN',
  1: 'TENANT_ADMIN',
  2: 'USER'
};

const normalizeRoleName = (role) => {
  if (role === null || role === undefined || role === '') {
    return '';
  }

  if (ROLE_NAME_MAP[role] !== undefined) {
    return ROLE_NAME_MAP[role];
  }

  return String(role);
};

const readStoredRole = () => {
  const directRole = localStorage.getItem('role');

  if (directRole) {
    return normalizeRoleName(directRole);
  }

  try {
    const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}');
    return normalizeRoleName(userInfo.role);
  } catch {
    return '';
  }
};

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/LoginView.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    name: 'MainLayout',
    component: () => import('../views/MainLayout.vue'),
    redirect: '/chat',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('../views/ChatView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'docs',
        name: 'Documents',
        component: () => import('../views/DocumentView.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'admin/users',
        name: 'UserManage',
        component: () => import('../views/UserManageView.vue'),
        meta: { requiresAuth: true, roles: ['TENANT_ADMIN', 'SUPER_ADMIN'] }
      },
      {
        path: 'admin/tenants',
        name: 'TenantManage',
        component: () => import('../views/TenantManageView.vue'),
        meta: { requiresAuth: true, roles: ['SUPER_ADMIN'] }
      },
      {
        path: 'eval',
        name: 'Eval',
        component: () => import('../views/EvalView.vue'),
        meta: { requiresAuth: true, roles: ['TENANT_ADMIN', 'SUPER_ADMIN'] }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/login'
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach((to) => {
  const token = localStorage.getItem('accessToken') || localStorage.getItem('token');
  const role = readStoredRole();
  const isPublicRoute = to.matched.some((record) => record.meta.public);
  const requiredRoles = to.matched.flatMap((record) => record.meta.roles || []);

  if (!token && !isPublicRoute) {
    return '/login';
  }

  if (token && to.path === '/login') {
    return '/chat';
  }

  if (requiredRoles.length && !requiredRoles.includes(role)) {
    return '/chat';
  }

  return true;
});

export default router;
