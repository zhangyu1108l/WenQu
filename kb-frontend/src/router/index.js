import { createRouter, createWebHistory } from 'vue-router';

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/LoginView.vue'),
    meta: { public: true } // 公开路由：未登录用户可以访问登录页。
  },
  {
    path: '/',
    name: 'MainLayout',
    component: () => import('../views/MainLayout.vue'),
    redirect: '/chat',
    meta: { requiresAuth: true }, // 需要登录：主布局承载业务页面。
    children: [
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('../views/ChatView.vue'),
        meta: { requiresAuth: true } // 需要登录：普通用户、租户管理员、超级管理员均可访问对话。
      },
      {
        path: 'docs',
        name: 'Documents',
        component: () => import('../views/DocumentView.vue'),
        meta: { requiresAuth: true } // 需要登录：登录用户可查看文档列表。
      },
      {
        path: 'admin/users',
        name: 'UserManage',
        component: () => import('../views/UserManageView.vue'),
        meta: { requiresAuth: true, roles: ['TENANT_ADMIN', 'SUPER_ADMIN'] } // 需要 TENANT_ADMIN：租户管理员及超级管理员可管理用户。
      },
      {
        path: 'admin/tenants',
        name: 'TenantManage',
        component: () => import('../views/TenantManageView.vue'),
        meta: { requiresAuth: true, roles: ['SUPER_ADMIN'] } // 需要 SUPER_ADMIN：仅超级管理员可管理租户。
      },
      {
        path: 'eval',
        name: 'Eval',
        component: () => import('../views/EvalView.vue'),
        meta: { requiresAuth: true, roles: ['TENANT_ADMIN', 'SUPER_ADMIN'] } // 需要 TENANT_ADMIN：租户管理员及超级管理员可运行评估。
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/login' // 404 路由：未知路径统一回到登录页。
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach((to) => {
  // 路由守卫按顺序校验：先判断登录态，再处理登录页跳转，最后校验角色权限。
  const token = localStorage.getItem('token');
  // role 从 localStorage 中读取，由登录成功后写入。
  const role = localStorage.getItem('role');
  const isPublicRoute = to.matched.some((record) => record.meta.public);

  // 无 token 且访问非公开路由，说明用户未登录，跳转到登录页。
  if (!token && !isPublicRoute) {
    return '/login';
  }

  // 已有 token 又访问登录页，说明用户已登录，直接进入默认对话页。
  if (token && to.path === '/login') {
    return '/chat';
  }

  // 访问租户管理但不是超级管理员，阻止越权并回到对话页。
  if (to.path === '/admin/tenants' && role !== 'SUPER_ADMIN') {
    return '/chat';
  }

  // 访问用户管理或评估页面但角色为普通用户，阻止越权并回到对话页。
  if ((to.path === '/admin/users' || to.path === '/eval') && role === 'USER') {
    return '/chat';
  }

  return true;
});

export default router;
