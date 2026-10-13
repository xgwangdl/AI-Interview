import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

const apiServer = process.env.API_SERVER_URL || 'http://localhost:8088';

// /login 与 /auth 既是后端的 POST 接口，也是前端路由。
// 整段代理会让浏览器直接访问 /login 时被转发到后端，拿到 405 Whitelabel 页面，
// 于是「在登录页刷新」必然失败。这里只代理非 GET 请求，GET 交回 SPA。
const spaBypass = (req) => (req.method === 'GET' ? '/index.html' : undefined);

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    strictPort: false,
    proxy: {
      '/api': apiServer,
      '/login': { target: apiServer, bypass: spaBypass },
      '/register': { target: apiServer, bypass: spaBypass },
      '/auth': { target: apiServer, bypass: spaBypass },
    },
  },
});
