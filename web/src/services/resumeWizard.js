/**
 * 「从零创建」的分步采集：步骤定义、素材转换、本地草稿。
 *
 * 放在 service 层而不是组件里，是为了让这些规则能被单测覆盖
 * （项目里没有 @vue/test-utils，组件测试跑不了）。
 */

/** 采集步骤；kind 决定这一步长什么样。 */
export const WIZARD_STEPS = [
  { key: 'targetRole', label: '目标岗位', labelKey: 'resume.wizard.steps.targetRole', kind: 'text' },
  { key: 'basics', label: '基本信息', labelKey: 'resume.wizard.steps.basics', kind: 'basics' },
  { key: 'education', label: '教育背景', labelKey: 'resume.wizard.steps.education', kind: 'entries' },
  { key: 'work', label: '工作经历', labelKey: 'resume.wizard.steps.work', kind: 'entries' },
  { key: 'projects', label: '项目经历', labelKey: 'resume.wizard.steps.projects', kind: 'entries' },
  { key: 'skills', label: '技能与证书', labelKey: 'resume.wizard.steps.skills', kind: 'long' },
  { key: 'other', label: '其他', labelKey: 'resume.wizard.steps.other', kind: 'long' },
];

/** 每段最多这么多条：再多就不是简历，是流水账。 */
export const ENTRY_LIMIT = 12;

export const BASICS_FIELDS = [
  { key: 'name', label: '姓名', labelKey: 'resume.wizard.basics.name', example: '例如：张三', exampleKey: 'resume.wizard.examples.name' },
  { key: 'phone', label: '电话', labelKey: 'resume.wizard.basics.phone', example: '例如：13800000000', exampleKey: 'resume.wizard.examples.phone' },
  { key: 'email', label: '邮箱', labelKey: 'resume.wizard.basics.email', example: '例如：zhangsan@example.com', exampleKey: 'resume.wizard.examples.email' },
  { key: 'city', label: '城市', labelKey: 'resume.wizard.basics.city', example: '例如：上海', exampleKey: 'resume.wizard.examples.city' },
  { key: 'extra', label: '其他想说的', labelKey: 'resume.wizard.basics.extra', example: '例如：3 年经验、在职看机会', exampleKey: 'resume.wizard.examples.extra' },
];

/** 三个"经历类"步骤共用同一张卡片，只是字段名与示例不同。 */
export const ENTRY_STEPS = {
  education: {
    entryLabel: '学校',
    entryLabelKey: 'resume.wizard.entries.education.entry',
    addLabel: '+ 再加一段学历',
    addLabelKey: 'resume.wizard.entries.education.add',
    fields: [
      { key: 'title', label: '学校', labelKey: 'resume.wizard.entries.education.title', example: '例如：某某大学', exampleKey: 'resume.wizard.entries.education.titleExample' },
      { key: 'role', label: '专业 / 学历', labelKey: 'resume.wizard.fields.roleEducation', example: '例如：计算机科学与技术 / 本科', exampleKey: 'resume.wizard.entries.education.roleExample' },
      { key: 'period', label: '时间', labelKey: 'resume.wizard.fields.period', example: '例如：2016.09 - 2020.06', exampleKey: 'resume.wizard.entries.education.periodExample' },
      { key: 'detail', label: '主修课程 / 在校经历', labelKey: 'resume.wizard.entries.education.detail', example: '例如：数据结构、数据库系统原理；担任班级学习委员', exampleKey: 'resume.wizard.entries.education.detailExample', rows: 2 },
      { key: 'result', label: '成绩 / 奖项', labelKey: 'resume.wizard.fields.result', example: '例如：GPA 3.7/4.0，两次校级奖学金', exampleKey: 'resume.wizard.entries.education.resultExample', rows: 2 },
    ],
  },
  work: {
    entryLabel: '公司',
    entryLabelKey: 'resume.wizard.entries.work.entry',
    addLabel: '+ 再加一段工作',
    addLabelKey: 'resume.wizard.entries.work.add',
    fields: [
      { key: 'title', label: '公司', labelKey: 'resume.wizard.entries.work.title', example: '例如：某某科技有限公司', exampleKey: 'resume.wizard.entries.work.titleExample' },
      { key: 'role', label: '职位', labelKey: 'resume.wizard.entries.work.role', example: '例如：后端开发工程师', exampleKey: 'resume.wizard.entries.work.roleExample' },
      { key: 'period', label: '时间', labelKey: 'resume.wizard.fields.period', example: '例如：2020.07 - 至今', exampleKey: 'resume.wizard.entries.work.periodExample' },
      { key: 'detail', label: '负责什么', labelKey: 'resume.wizard.entries.work.detail', example: '例如：负责订单系统的接口开发与维护，参与需求评审', exampleKey: 'resume.wizard.entries.work.detailExample', rows: 3 },
      { key: 'result', label: '结果 / 成绩', labelKey: 'resume.wizard.fields.result', example: '例如：把订单查询从 3 次库调用改成 1 次，平均耗时 800ms → 120ms', exampleKey: 'resume.wizard.entries.work.resultExample', rows: 2 },
    ],
  },
  projects: {
    entryLabel: '项目',
    entryLabelKey: 'resume.wizard.entries.projects.entry',
    addLabel: '+ 再加一个项目',
    addLabelKey: 'resume.wizard.entries.projects.add',
    fields: [
      { key: 'title', label: '项目名称', labelKey: 'resume.wizard.entries.projects.title', example: '例如：订单中心改造', exampleKey: 'resume.wizard.entries.projects.titleExample' },
      { key: 'role', label: '你的角色', labelKey: 'resume.wizard.entries.projects.role', example: '例如：后端主力开发（3 人团队）', exampleKey: 'resume.wizard.entries.projects.roleExample' },
      { key: 'period', label: '时间', labelKey: 'resume.wizard.fields.period', example: '例如：2021.03 - 2021.08', exampleKey: 'resume.wizard.entries.projects.periodExample' },
      { key: 'detail', label: '做了什么', labelKey: 'resume.wizard.entries.projects.detail', example: '例如：拆订单表、引入缓存、把同步调用改成消息队列', exampleKey: 'resume.wizard.entries.projects.detailExample', rows: 3 },
      { key: 'result', label: '结果 / 成绩', labelKey: 'resume.wizard.fields.result', example: '例如：峰值 QPS 提升 3 倍，超时投诉降到 0', exampleKey: 'resume.wizard.entries.projects.resultExample', rows: 2 },
    ],
  },
};

/** 自由文本框的示例：不会写的人照着改一句就行，别给他讲道理。 */
export const EXAMPLES = {
  targetRole: '例如：Java 后端工程师 / 测试开发 / 前端工程师',
  skills: '例如：Java、Spring Boot、MySQL、Redis；CET-6',
  other: '例如：2021 年公司最佳新人；业余维护过一个 200 star 的开源项目',
};
export const EXAMPLE_KEYS = {
  targetRole: 'resume.wizard.examples.targetRole',
  skills: 'resume.wizard.examples.skills',
  other: 'resume.wizard.examples.other',
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
export function defaultTitle(state, t = null) {
  const name = text(state?.basics?.name);
  if (t) return name ? t('resume.defaultTitle', { name }) : t('resume.defaultTitleFallback');
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
