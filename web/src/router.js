import { createRouter, createWebHistory } from 'vue-router';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('./views/LoginView.vue') },
    { path: '/', name: 'dashboard', component: () => import('./views/DashboardView.vue') },
    { path: '/practice/setup', name: 'setup', component: () => import('./views/SetupView.vue') },
    { path: '/practice/device-check', name: 'device-check', component: () => import('./views/DeviceCheckView.vue') },
    { path: '/practice/:id/session', name: 'session', component: () => import('./views/SessionView.vue') },
    { path: '/practice/:id/report', name: 'report', component: () => import('./views/ReportView.vue') },
    { path: '/history', name: 'history', component: () => import('./views/HistoryView.vue') },
    { path: '/mistakes', name: 'mistakes', component: () => import('./views/MistakeBookView.vue') },
    { path: '/resumes', name: 'resume-list', component: () => import('./views/ResumeListView.vue') },
    { path: '/resumes/new', name: 'resume-new', component: () => import('./views/ResumeNewView.vue') },
    { path: '/resumes/:id', name: 'resume-detail', component: () => import('./views/ResumeDetailView.vue') },
    { path: '/coding', name: 'coding-setup', component: () => import('./views/CodingSetupView.vue') },
    { path: '/coding/:id', name: 'coding-exam', component: () => import('./views/CodingExamView.vue') },
    { path: '/coding/:id/report', name: 'coding-report', component: () => import('./views/CodingReportView.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
});

export default router;
