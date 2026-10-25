import { createApp } from 'vue';
import { createPinia } from 'pinia';
import 'ant-design-vue/dist/reset.css';
import './styles.css';
import App from './App.vue';
import router from './router';
import { setUnauthorizedHandler } from './api/http';
import { setEnterpriseUnauthorizedHandler } from './api/enterprise';
import { useAuthStore } from './stores/auth';
import { useEnterpriseAuthStore } from './stores/enterpriseAuth';

const app = createApp(App);
const pinia = createPinia();
app.use(pinia);
app.use(router);

const auth = useAuthStore(pinia);
const enterpriseAuth = useEnterpriseAuthStore(pinia);
setUnauthorizedHandler(() => {
  auth.clear();
  if (router.currentRoute.value.path !== '/login') {
    router.push({
      path: '/login',
      query: { redirect: router.currentRoute.value.fullPath }
    });
  }
});
setEnterpriseUnauthorizedHandler(() => enterpriseAuth.clear());

app.mount('#app');
