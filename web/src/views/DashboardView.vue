<script setup>
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { usePracticeStore } from '../stores/practice';

const router = useRouter();
const practice = usePracticeStore();
const latest = computed(() => practice.sessions.slice(0, 4));
const completedMinutes = computed(() => practice.completed.reduce((sum, item) => sum + (item.durationMinutes || 0), 0));
const dateLabel = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }).format(new Date());

function openSession(session) {
  router.push(session.status === 'completed' ? `/practice/${session.id}/report` : `/practice/${session.id}/session`);
}

function timeLabel(value) {
  return new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric' }).format(new Date(value));
}
</script>

<template>
  <section class="dashboard-page">
    <div class="page-heading dashboard-heading">
      <div><div class="eyebrow">{{ dateLabel }}</div><h1>每一次练习，都让表达更清晰</h1><p>从一个真实岗位开始，循序完成练习、录制与复盘。</p></div>
      <el-button class="primary-button" @click="router.push('/practice/setup')"><el-icon><Plus /></el-icon> 创建练习</el-button>
    </div>

    <div class="overview-grid">
      <article class="overview-card panel">
        <div class="overview-card-label"><span>完成练习</span><i class="icon-green"><el-icon><Checked /></el-icon></i></div>
        <div class="overview-number">{{ practice.completed.length }}<small> 次</small></div>
        <div class="overview-foot">每一次回答都值得复盘</div>
      </article>
      <article class="overview-card panel">
        <div class="overview-card-label"><span>练习时长</span><i class="icon-blue"><el-icon><Timer /></el-icon></i></div>
        <div class="overview-number">{{ completedMinutes }}<small> 分钟</small></div>
        <div class="overview-foot">累计录制与练习时间</div>
      </article>
      <article class="overview-card panel next-practice-card">
        <div class="overview-card-label"><span>下一步</span><i class="icon-coral"><el-icon><Microphone /></el-icon></i></div>
        <div class="next-practice-copy">用视频模拟一次<br />真实面试现场</div>
        <button class="text-action" @click="router.push('/practice/setup')">配置岗位与题目 <el-icon><ArrowRight /></el-icon></button>
      </article>
    </div>

    <div class="dashboard-lower">
      <section class="recent-section">
        <div class="section-title"><span>最近练习</span><button class="link-button" @click="router.push('/history')">全部记录 <el-icon><ArrowRight /></el-icon></button></div>
        <div v-if="latest.length" class="recent-list panel">
          <button v-for="session in latest" :key="session.id" class="recent-row" @click="openSession(session)">
            <div class="recent-symbol" :class="session.status"><el-icon><VideoCamera /></el-icon></div>
            <div class="recent-main"><strong>{{ session.role }}</strong><span>{{ session.company || '自主练习' }} · {{ session.questionCount }} 道题</span></div>
            <el-tag v-if="session.status === 'completed'" class="status-done" size="small">已完成</el-tag>
            <el-tag v-else class="status-progress" size="small">进行中</el-tag>
            <span class="recent-date">{{ timeLabel(session.createdAt) }}</span><el-icon class="recent-arrow"><ArrowRight /></el-icon>
          </button>
        </div>
        <div v-else class="empty-recent panel">
          <div class="empty-state-icon"><el-icon><VideoCamera /></el-icon></div>
          <strong>还没有练习记录</strong><p>从目标岗位和面试类型开始，完成第一段模拟练习。</p>
          <el-button class="soft-button" @click="router.push('/practice/setup')">开始第一次练习</el-button>
        </div>
      </section>

      <aside class="guidance-section">
        <div class="section-title">练习准备</div>
        <div class="guidance-panel panel">
          <div class="guidance-art"><div class="art-person"><span class="person-head"></span><span class="person-body"></span><span class="person-neck"></span></div><div class="art-window"><i></i><i></i><i></i></div><div class="art-spark spark-a">✦</div><div class="art-spark spark-b">✦</div></div>
          <strong>让练习尽量接近现场</strong>
          <p>准备岗位描述和简历，开启摄像头与麦克风后，每道题都可以单独录制与重来。</p>
          <div class="guidance-meta"><span><el-icon><Clock /></el-icon>建议 15–30 分钟</span><span><el-icon><Lock /></el-icon>录制保留 7 天</span></div>
        </div>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.overview-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 15px; margin-bottom: 28px; }
.overview-card { min-height: 143px; padding: 17px 19px 14px; box-shadow: none; }
.overview-card-label { display: flex; align-items: center; justify-content: space-between; color: #7c8782; font-size: 11px; }
.overview-card-label i { display: grid; width: 29px; height: 29px; place-items: center; border-radius: 7px; font-style: normal; font-size: 15px; }
.icon-green { background: var(--green-soft); color: var(--green); }.icon-blue { background: var(--blue-soft); color: var(--blue); }.icon-coral { background: var(--coral-soft); color: var(--coral); }
.overview-number { margin-top: 8px; color: var(--ink); font-size: 29px; font-weight: 650; line-height: 1.2; }.overview-number small { color: #8b9691; font-size: 12px; font-weight: 500; }
.overview-foot { margin-top: 7px; color: #9aa39f; font-size: 10px; }
.next-practice-copy { margin-top: 6px; color: var(--ink); font-size: 15px; font-weight: 600; line-height: 1.45; }
.text-action, .link-button { display: inline-flex; align-items: center; gap: 5px; border: 0; background: transparent; color: var(--green); font-size: 10px; font-weight: 600; }
.text-action { margin-top: 9px; padding: 0; }
.dashboard-lower { display: grid; grid-template-columns: minmax(0, 1.55fr) minmax(250px, .85fr); gap: 21px; }
.section-title { margin-bottom: 13px; }
.recent-list { overflow: hidden; box-shadow: none; }
.recent-row { display: flex; width: 100%; min-height: 69px; align-items: center; gap: 12px; padding: 0 15px; border: 0; border-bottom: 1px solid #eef1ee; background: white; text-align: left; }
.recent-row:last-child { border-bottom: 0; }.recent-row:hover { background: #fafcfb; }
.recent-symbol { display: grid; width: 34px; height: 34px; flex: none; place-items: center; border-radius: 7px; background: var(--blue-soft); color: var(--blue); }.recent-symbol.completed { background: var(--green-soft); color: var(--green); }
.recent-main { display: grid; min-width: 0; flex: 1; gap: 5px; }.recent-main strong { overflow: hidden; color: #34403a; font-size: 12px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }.recent-main span { overflow: hidden; color: #929c97; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.status-done.el-tag { border-color: #d9eee2; background: #edf7f0; color: #4a8b62; }.status-progress.el-tag { border-color: #f4e7c9; background: #fbf5e9; color: #ad8844; }
.recent-date { width: 37px; flex: none; color: #98a19d; font-size: 10px; text-align: right; }.recent-arrow { color: #abb4af; font-size: 12px; }
.empty-recent { display: flex; min-height: 250px; align-items: center; flex-direction: column; justify-content: center; padding: 20px; box-shadow: none; }.empty-recent strong { color: #46524c; font-size: 12px; }.empty-recent p { max-width: 280px; color: #8d9792; font-size: 10px; line-height: 1.7; text-align: center; }
.guidance-panel { overflow: hidden; padding: 0 17px 16px; box-shadow: none; }.guidance-art { position: relative; display: flex; height: 116px; align-items: center; justify-content: center; margin: 0 -17px 15px; overflow: hidden; background: #eff6f2; }.art-window { position: absolute; top: 20px; right: 26px; display: flex; width: 97px; height: 67px; gap: 5px; align-items: flex-start; padding: 10px; border: 1px solid #dce8e1; border-radius: 6px; background: rgba(255,255,255,.72); transform: rotate(5deg); }.art-window i { width: 5px; height: 5px; border-radius: 50%; background: #df9b7c; }.art-window i:nth-child(2) { background: #e5c26c; }.art-window i:nth-child(3) { background: #78ad8b; }
.art-person { position: absolute; z-index: 2; bottom: -6px; left: calc(50% - 21px); width: 70px; height: 96px; }.person-head { position: absolute; top: 8px; left: 20px; width: 35px; height: 38px; border: 1px solid #d59c7d; border-radius: 46% 46% 43% 43%; background: #e7b08e; }.person-head::after { position: absolute; top: -4px; left: 0; width: 36px; height: 15px; border-radius: 50% 50% 35% 35%; background: #483c36; content: ''; }.person-neck { position: absolute; top: 42px; left: 31px; width: 14px; height: 16px; background: #dba282; }.person-body { position: absolute; bottom: 0; left: 5px; width: 65px; height: 48px; border-radius: 24px 24px 0 0; background: #4a826e; }.art-spark { position: absolute; color: var(--coral); }.spark-a { top: 21px; left: 31%; font-size: 16px; }.spark-b { right: 23%; bottom: 19px; color: #d7a845; font-size: 12px; }
.guidance-panel > strong { color: #36423c; font-size: 12px; }.guidance-panel > p { margin: 8px 0 12px; color: #87928c; font-size: 10px; line-height: 1.75; }.guidance-meta { display: flex; flex-wrap: wrap; gap: 12px; padding-top: 11px; border-top: 1px solid #edf0ed; color: #7c8782; font-size: 9px; }.guidance-meta span { display: inline-flex; align-items: center; gap: 5px; }
@media(max-width: 920px) { .dashboard-lower { grid-template-columns: 1fr; }.guidance-section { display: none; } }
@media(max-width: 680px) { .overview-grid { grid-template-columns: repeat(2, minmax(0,1fr)); gap: 9px; }.overview-card { min-height: 130px; padding: 13px; }.overview-card:last-child { grid-column: 1 / -1; min-height: 133px; }.overview-number { font-size: 25px; }.recent-row { gap: 8px; padding: 0 10px; }.recent-date, .recent-arrow { display: none; }.recent-main span { max-width: 160px; } }
</style>
