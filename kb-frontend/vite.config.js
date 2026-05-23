import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import AutoImport from 'unplugin-auto-import/vite';
import Components from 'unplugin-vue-components/vite';
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers';

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      // 自动导入 Vue API，减少后续页面中的重复 import。
      imports: ['vue', 'vue-router', 'pinia'],
      resolvers: [ElementPlusResolver()],
      dts: false
    }),
    Components({
      // Element Plus 组件按需自动导入，避免全量引入组件代码。
      resolvers: [ElementPlusResolver()],
      dts: false
    })
  ],
  server: {
    proxy: {
      // 开发环境前端由 Vite 提供，后端接口走 Gateway；代理 /api 用于解决浏览器跨域问题。
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist'
  }
});
