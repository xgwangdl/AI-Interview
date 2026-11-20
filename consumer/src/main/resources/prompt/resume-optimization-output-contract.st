这是 Light-Interview 的 Spring AI 调用。除了遵循上面的 resume-builder 规则，还必须返回应用需要的
单层 JSON 包装结构。`resumeData` 必须严格符合随应用提供的 resume-data.schema.json：

{
  "resumeData": {
    "language": "zh | en | ja",
    "document_type": "resume | cv | career_history",
    "generated_date": "YYYY-MM 或 YYYY-MM-DD 或 null",
    "target_role": "目标岗位或 null",
    "person": {
      "name": "姓名",
      "reading": null,
      "location": null,
      "phone": null,
      "email": null,
      "links": []
    },
    "headline": "职业标题",
    "summary": [],
    "skills": [],
    "experience": [],
    "education": [],
    "projects": [],
    "portfolio": [],
    "certifications": [],
    "languages": [],
    "notes": []
  },
  "summary": "一句话说明本次结构和措辞优化的重点",
  "sections": [
    {
      "kind": "summary | skills | work | project | education | other",
      "original": "原文中可被准确替换的一段；新增段落为空字符串",
      "optimized": "优化后的同段文本",
      "reason": "为什么这样改",
      "changeType": "rephrase | restructure | add_detail | remove | keep"
    }
  ],
  "placeholders": ["正文中真实出现的【请补充：...】"],
  "keywordCoverage": {
    "matched": [],
    "missing": []
  }
}

规则：
1. `resumeData` 是最终 Word/PDF 的权威数据，不能省略，不能增加 schema 之外的字段。
2. `sections` 继续服务逐段对比和采纳；每一段的 `original` 必须逐字来自简历原文，新增段落才可为空。
3. 任何原文没有的公司、职位、日期、技能、证书、语言等级、数字或业绩都不得补造。
4. 缺少可选事实时保持 null 或空数组；需要用户补充的信息写入 notes，并仅在正文占位符中标注。
5. 中文输出使用 `resume`；日文默认输出 `career_history`；英文可使用 `resume` 或 `cv`。
6. 只输出 JSON，不要 Markdown 代码块或解释文字。
