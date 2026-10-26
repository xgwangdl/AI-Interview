export function buildQuestions(role, count, interviewType = 'behavioral') {
  const questionSets = {
    behavioral: [
      [`请结合一次具体经历，介绍你在${role}相关工作中解决过的一个核心问题。`, '项目经验'],
      ['当需求不明确且时间紧张时，你通常怎样澄清目标并安排优先级？', '问题解决'],
      ['分享一次你与团队成员意见不一致的经历，你如何推动问题解决？', '协作沟通'],
      ['请讲讲最近一次失败或结果不及预期的项目，你从中学到了什么？', '复盘成长'],
      ['如果加入新团队，你会如何在前三个月理解业务并创造价值？', '岗位匹配'],
      ['你做过的哪项工作最能体现你的专业判断？请说明结果。', '项目经验'],
      ['遇到线上问题或突发情况时，你如何定位、沟通并复盘？', '问题解决'],
      ['你希望在下一份工作中承担什么样的挑战？为什么？', '岗位匹配'],
    ],
    technical: [
      [`请结合${role}工作中的一个项目，说明核心技术方案与关键取舍。`, '项目设计'],
      ['如果核心接口的响应时间突然变慢，你会怎样逐层定位？', '问题排查'],
      ['请解释你熟悉的一种并发控制方式，并说明适用场景和限制。', '技术原理'],
      ['如何设计一个支持高并发且可水平扩展的任务处理服务？', '系统设计'],
      ['一次技术方案评审中，如果你发现潜在的数据一致性问题，会怎么验证和处理？', '工程实践'],
      ['你如何设计服务的可观测性，快速发现并定位线上故障？', '稳定性'],
      ['描述一个你曾做出的架构或性能优化，并说明如何验证收益。', '性能优化'],
      ['面对一个陌生技术领域，你如何评估、试验并决定是否引入？', '技术判断'],
    ],
    manager: [
      [`请介绍一次你在${role}工作中推动团队交付关键目标的经历。`, '目标管理'],
      ['当多个团队同时争取有限资源时，你如何排序并达成共识？', '资源决策'],
      ['你会如何帮助表现持续不佳的团队成员改善？', '团队发展'],
      ['讲一次目标或策略需要调整的经历，你如何带队应对变化？', '变革管理'],
      ['如何在短期业务压力与长期团队能力建设之间做取舍？', '业务判断'],
      ['当数据与团队经验判断不一致时，你怎样作出决策？', '决策方法'],
      ['分享一次你与关键合作方存在分歧并推动事情落地的经历。', '跨团队协作'],
      ['你如何判断一个团队当前最需要解决的问题，并衡量改善效果？', '组织能力'],
    ],
  };
  // 未知类型退回综合行为题库：服务端只校验 interviewType 非空，不能假定客户端一定给三种之一。
  const questionSet = questionSets[interviewType] || questionSets.behavioral;
  return questionSet.slice(0, count).map(([text, category], index) => ({ id: `q-${index + 1}`, text, category }));
}

export const styleLabels = { balanced: '专业平和', direct: '直接追问', supportive: '循序引导' };
export const typeLabels = { behavioral: '综合行为面试', technical: '技术能力面试', manager: '管理岗位面试' };
export const STYLE_LABEL_KEYS = Object.fromEntries(
  Object.keys(styleLabels).map((key) => [key, `setup.${key}`]),
);
export const TYPE_LABEL_KEYS = Object.fromEntries(
  Object.keys(typeLabels).map((key) => [key, `setup.${key}`]),
);
