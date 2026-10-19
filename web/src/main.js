import { createApp } from 'vue';
import { createPinia } from 'pinia';
import ElementPlus from 'element-plus';
import * as ElementPlusIconsVue from '@element-plus/icons-vue';
import 'element-plus/dist/index.css';
import './styles.css';
import App from './App.vue';
import router from './router';
import { installAuthGuard } from './authGuard';
import { bootstrapTheme } from './services/theme';
import { bootstrapLocale, i18n } from './i18n';

bootstrapTheme();
bootstrapLocale();
const app = createApp(App);
Object.entries(ElementPlusIconsVue).forEach(([name, component]) => app.component(name, component));
installAuthGuard(router);
app.use(createPinia()).use(i18n).use(router).use(ElementPlus, { size: 'default' }).mount('#app');
