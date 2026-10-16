import { beforeEach, describe, expect, it } from 'vitest';
import {
  ENTRY_LIMIT, WIZARD_STEPS, clearLocalDraft, countFilled, defaultTitle, emptyEntry, emptyState,
  loadLocalDraft, saveLocalDraft, toMaterials,
} from './resumeWizard';

function stateWith(overrides) {
  return Object.assign(emptyState(), overrides);
}

describe('resumeWizard', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('空状态也能转成合法入参：后端靠它生成骨架', () => {
    const materials = toMaterials(emptyState());

    expect(materials).toEqual({
      targetRole: '',
      basics: { name: '', phone: '', email: '', city: '', extra: '' },
      education: [],
      work: [],
      projects: [],
      skills: '',
      other: '',
    });
    expect(countFilled(emptyState())).toBe(0);
  });

  it('去掉空卡片与首尾空格，最多保留 12 段', () => {
    const work = Array.from({ length: ENTRY_LIMIT + 3 }, () => ({ ...emptyEntry(), title: ' 某公司 ' }));
    work.unshift(emptyEntry());

    const materials = toMaterials(stateWith({ work, targetRole: ' Java 后端工程师 ' }));

    expect(materials.work).toHaveLength(ENTRY_LIMIT);
    expect(materials.work[0].title).toBe('某公司');
    expect(materials.targetRole).toBe('Java 后端工程师');
  });

  it('已填项数只算真的有内容的字段', () => {
    const state = stateWith({
      targetRole: 'Java 后端',
      basics: { name: '张三', phone: '', email: '', city: '', extra: '' },
      work: [{ ...emptyEntry(), title: '某公司' }],
      education: [emptyEntry()],
    });

    expect(countFilled(state)).toBe(3);
  });

  it('默认标题取姓名，没有名字时退回通用标题', () => {
    expect(defaultTitle(stateWith({ basics: { name: '张三' } }))).toBe('张三的简历');
    expect(defaultTitle(emptyState())).toBe('我的简历');
  });

  it('草稿存在本地，刷新后能恢复（后端不保存半成品）', () => {
    saveLocalDraft(stateWith({
      targetRole: '测试开发',
      basics: { name: '李四' },
      projects: [{ ...emptyEntry(), title: '订单中心改造' }],
    }));

    const restored = loadLocalDraft();

    expect(restored.targetRole).toBe('测试开发');
    expect(restored.basics.name).toBe('李四');
    expect(restored.projects[0].title).toBe('订单中心改造');
  });

  it('读草稿时只认已知字段：脏数据不能让界面炸掉', () => {
    localStorage.setItem('light-interview-resume-guide', JSON.stringify({
      targetRole: '前端',
      basics: { name: '王五', unknown: '忽略我' },
      education: 'not-an-array',
      work: [{ title: '某公司', 额外字段: 'x' }],
    }));

    const restored = loadLocalDraft();

    expect(restored.targetRole).toBe('前端');
    expect(restored.basics.name).toBe('王五');
    expect(restored.basics.unknown).toBeUndefined();
    expect(restored.education).toEqual([]);
    expect(restored.work[0].title).toBe('某公司');
  });

  it('没有实质内容的草稿视作没有：不弹"已恢复"', () => {
    saveLocalDraft(emptyState());

    expect(loadLocalDraft()).toBeNull();
  });

  it('清空草稿后读不回来', () => {
    saveLocalDraft(stateWith({ targetRole: 'Java' }));
    clearLocalDraft();

    expect(loadLocalDraft()).toBeNull();
  });

  it('步骤表覆盖了采集顺序，且每步都有对应的状态字段', () => {
    const state = emptyState();

    expect(WIZARD_STEPS.map((item) => item.label)).toEqual(
      ['目标岗位', '基本信息', '教育背景', '工作经历', '项目经历', '技能与证书', '其他'],
    );
    WIZARD_STEPS.forEach((item) => {
      expect(Object.prototype.hasOwnProperty.call(state, item.key)).toBe(true);
    });
  });
});
