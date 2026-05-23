import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import * as ElementPlusIconsVue from '@element-plus/icons-vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';
import 'element-plus/dist/index.css';
import './styles/global.css';

const app = createApp(App);

// Element Plus 提供基础 UI 组件，zh-cn 用于统一中文交互文案。
app.use(ElementPlus, { locale: zhCn });

// Element Plus 图标全局注册，后续按钮、菜单和状态提示可直接使用图标组件。
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component);
}

// Pinia 管理全局状态；必须在 Router 之前注册，保证路由守卫和页面组件能读取登录态。
app.use(createPinia());

// Router 管理 SPA 路由表与权限守卫。
app.use(router);

// 所有插件注册完成后再挂载根组件。
app.mount('#app');
