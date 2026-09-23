export function installAuthGuard(router) {
  let verifiedToken;
  router.beforeEach((to) => {
    const token = localStorage.getItem('token');
    if (to.name !== 'login' && !token) return { name: 'login', query: { redirect: to.fullPath } };
    if (to.name === 'login' && token) return { name: 'dashboard' };
    if (to.name !== 'login' && token !== verifiedToken) {
      return fetch('/auth/verify-token', { method: 'POST', headers: { token } })
        .then(async (response) => {
          const result = await response.json();
          if (!response.ok || result.code !== 200) throw new Error('Token expired');
          verifiedToken = token;
          if (result.data) localStorage.setItem('userName', result.data);
          return true;
        })
        .catch(() => {
          ['token', 'userId', 'userName', 'username'].forEach((key) => localStorage.removeItem(key));
          verifiedToken = undefined;
          return { name: 'login', query: { redirect: to.fullPath } };
        });
    }
    return true;
  });
}
