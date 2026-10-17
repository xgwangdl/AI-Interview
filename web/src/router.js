import { createRouter, createWebHistory } from 'vue-router';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('./views/LoginView.vue') },
    { path: '/', name: 'dashboard', component: () => import('./views/DashboardView.vue') },
    { path: '/today', name: 'today', component: () => import('./views/TodayView.vue') },
    { path: '/progress/weekly', name: 'weekly-report', component: () => import('./views/WeeklyReportView.vue') },
    { path: '/settings/notifications', name: 'notifications', component: () => import('./views/NotificationSettingsView.vue') },
    { path: '/goals', name: 'goals', component: () => import('./views/GoalListView.vue') },
    { path: '/goals/new', name: 'goal-new', component: () => import('./views/GoalFormView.vue') },
    { path: '/goals/:id/edit', name: 'goal-edit', component: () => import('./views/GoalFormView.vue') },
    { path: '/goals/:id', name: 'goal-detail', component: () => import('./views/GoalDetailView.vue') },
    { path: '/cohorts', name: 'cohorts', component: () => import('./views/CohortListView.vue') },
    { path: '/cohorts/:id', name: 'cohort-detail', component: () => import('./views/CohortDetailView.vue') },
    { path: '/practice/setup', name: 'setup', component: () => import('./views/SetupView.vue') },
    { path: '/practice/device-check', name: 'device-check', component: () => import('./views/DeviceCheckView.vue') },
    { path: '/practice/:id/retake/:questionIndex', name: 'retake', component: () => import('./views/RetakeView.vue') },
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
    { path: '/language', name: 'language', component: () => import('./views/LanguageSetupView.vue') },
    { path: '/language/:id', name: 'language-session', component: () => import('./views/LanguageSessionView.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
});

export default router;
