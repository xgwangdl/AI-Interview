/**
 * 「从零创建」的分步采集：步骤定义、素材转换、本地草稿。
 *
 * 放在 service 层而不是组件里，是为了让这些规则能被单测覆盖
 * （项目里没有 @vue/test-utils，组件测试跑不了）。
 */

/** 采集步骤；kind 决定这一步长什么样。 */
export const WIZARD_STEPS = [
  { key: 'targetRole', label: '目标岗位', kind: 'text' },
  { key: 'basics', label: '基本信息', kind: 'basics' },
  { key: 'education', label: '教育背景', kind: 'entries' },
  { key: 'work', label: '工作经历', kind: 'entries' },
  { key: 'projects', label: '项目经历', kind: 'entries' },
  { key: 'skills', label: '技能与证书', kind: 'long' },
  { key: 'other', label: '其他', kind: 'long' },
];

/** 每段最多这么多条：再多就不是简历，是流水账。 */
export const ENTRY_LIMIT = 12;

export const BASICS_FIELDS = [
  { key: 'name', label: '姓名', example: '例如：张三' },
  { key: 'phone', label: '电话', example: '例如：13800000000' },
  { key: 'email', label: '邮箱', example: '例如：zhangsan@example.com' },
  { key: 'city', label: '城市', example: '例如：上海' },
  { key: 'extra', label: '其他想说的', example: '例如：3 年经验、在职看机会' },
];

/** 三个"经历类"步骤共用同一张卡片，只是字段名与示例不同。 */
export const ENTRY_STEPS = {
  education: {
    entryLabel: '学校',
    addLabel: '+ 再加一段学历',
    fields: [
      { key: 'title', label: '学校', example: '例如：某某大学' },
      { key: 'role', label: '专业 / 学历', example: '例如：计算机科学与技术 / 本科' },
      { key: 'period', label: '时间', example: '例如：2016.09 - 2020.06' },
      { key: 'detail', label: '主修课程 / 在校经历', example: '例如：数据结构、数据库系统原理；担任班级学习委员', rows: 2 },
      { key: 'result', label: '成绩 / 奖项', example: '例如：GPA 3.7/4.0，两次校级奖学金', rows: 2 },
    ],
  },
  work: {
    entryLabel: '公司',
    addLabel: '+ 再加一段工作',
    fields: [
      { key: 'title', label: '公司', example: '例如：某某科技有限公司' },
      { key: 'role', label: '职位', example: '例如：后端开发工程师' },
      { key: 'period', label: '时间', example: '例如：2020.07 - 至今' },
      { key: 'detail', label: '负责什么', example: '例如：负责订单系统的接口开发与维护，参与需求评审', rows: 3 },
      { key: 'result', label: '结果 / 成绩', example: '例如：把订单查询从 3 次库调用改成 1 次，平均耗时 800ms → 120ms', rows: 2 },
    ],
  },
  projects: {
    entryLabel: '项目',
    addLabel: '+ 再加一个项目',
    fields: [
      { key: 'title', label: '项目名称', example: '例如：订单中心改造' },
      { key: 'role', label: '你的角色', example: '例如：后端主力开发（3 人团队）' },
      { key: 'period', label: '时间', example: '例如：2021.03 - 2021.08' },
      { key: 'detail', label: '做了什么', example: '例如：拆订单表、引入缓存、把同步调用改成消息队列', rows: 3 },
      { key: 'result', label: '结果 / 成绩', example: '例如：峰值 QPS 提升 3 倍，超时投诉降到 0', rows: 2 },
    ],
  },
};

/** 自由文本框的示例：不会写的人照着改一句就行，别给他讲道理。 */
export const EXAMPLES = {
  targetRole: '例如：Java 后端工程师 / 测试开发 / 前端工程师',
  skills: '例如：Java、Spring Boot、MySQL、Redis；CET-6',
  other: '例如：2021 年公司最佳新人；业余维护过一个 200 star 的开源项目',
};

const STORAGE_KEY = 'light-interview-resume-guide';

export function emptyState() {
  return {
    targetRole: '',
    basics: { name: '', phone: '', email: '', city: '', extra: '' },
    education: [],
    work: [],
    projects: [],
    skills: '',
    other: '',
  };
}

export function emptyEntry() {
  return { title: '', role: '', period: '', detail: '', result: '' };
}

function text(value) {
  return typeof value === 'string' ? value.trim() : '';
}

function cleanEntry(entry) {
  return {
    title: text(entry?.title),
    role: text(entry?.role),
    period: text(entry?.period),
    detail: text(entry?.detail),
    result: text(entry?.result),
  };
}

/** 只保留填了内容的条目：空卡片不该占模型上下文，也不该算进"已填几项"。 */
function cleanEntries(list) {
  return (list || [])
    .map(cleanEntry)
    .filter((entry) => Object.values(entry).some(Boolean))
    .slice(0, ENTRY_LIMIT);
}

/** 采集状态 → 接口入参。 */
export function toMaterials(state) {
  const basics = state?.basics || {};
  return {
    targetRole: text(state?.targetRole),
    basics: {
      name: text(basics.name),
      phone: text(basics.phone),
      email: text(basics.email),
      city: text(basics.city),
      extra: text(basics.extra),
    },
    education: cleanEntries(state?.education),
    work: cleanEntries(state?.work),
    projects: cleanEntries(state?.projects),
    skills: text(state?.skills),
    other: text(state?.other),
  };
}

/** 用户一共填了几项 —— 界面上用来说"你填了 5 项，没填的会留成待补充"。 */
export function countFilled(state) {
  const materials = toMaterials(state);
  let count = Object.values(materials.basics).filter(Boolean).length;
  if (materials.targetRole) count++;
  count += materials.education.length + materials.work.length + materials.projects.length;
  if (materials.skills) count++;
  if (materials.other) count++;
  return count;
}

/** 保存前默认标题：`<姓名>的简历`，没写名字就退回通用标题。 */
export function defaultTitle(state) {
  const name = text(state?.basics?.name);
  return name ? `${name}的简历` : '我的简历';
}

export function saveLocalDraft(state) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(toMaterials(state)));
  } catch {
    // 隐私模式等场景写不进去：草稿丢了不影响主流程，静默跳过
  }
}

/** 读回本地草稿，并**只按已知字段还原**（旧结构或脏数据不能让界面炸掉）。 */
export function loadLocalDraft() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    const state = emptyState();
    state.targetRole = text(parsed?.targetRole);
    BASICS_FIELDS.forEach((field) => {
      state.basics[field.key] = text(parsed?.basics?.[field.key]);
    });
    Object.keys(ENTRY_STEPS).forEach((key) => {
      state[key] = (Array.isArray(parsed?.[key]) ? parsed[key] : []).map(cleanEntry);
    });
    state.skills = text(parsed?.skills);
    state.other = text(parsed?.other);
    return countFilled(state) > 0 ? state : null;
  } catch {
    return null;
  }
}

export function clearLocalDraft() {
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    // 同上：清理失败无妨
  }
}
