package com.guangge.Interview.practice.rewrite;

import org.springframework.stereotype.Component;

import com.guangge.Interview.util.NumberClaims;

import java.util.Set;

/**
 * 改写校验：保留原意最重要的硬约束是"不编数字"。
 *
 * 模型很容易把"优化了一点"改写成"提升了 40%"；这类数字用户没说过，
 * 拿去面试就是编经历。这里把改写里新增的数字拦下来。
 */
@Component
public class AnswerRewriteValidator {

    private static final int MAX_CHARS = 4000;

    public String validate(String rewrite, String original) {
        String text = basic(rewrite);
        Set<String> unsupported = NumberClaims.unsupported(text, original);
        if (!unsupported.isEmpty()) {
            throw new AnswerRewriteException("AI 改写引入了原回答没有的数字 "
                    + String.join("、", unsupported)
                    + "，已拦截以避免编造经历。请重试，或手动编辑补充真实数字。");
        }
        // 不能把英文回答改写成中文（反之亦然）：用户要背的是面试时说的那种语言。
        boolean originalChinese = hasChinese(original);
        boolean rewriteChinese = hasChinese(text);
        if (!originalChinese && rewriteChinese) {
            throw new AnswerRewriteException("原回答是英文，改写也必须用英文，不能翻译成中文");
        }
        if (originalChinese && !rewriteChinese) {
            throw new AnswerRewriteException("原回答是中文，改写也必须用中文");
        }
        return text;
    }

    /** 用户自己编辑时允许补充真实信息（例如他确实没在语音里说出来的数字）。 */
    public String validateUser(String rewrite) {
        return basic(rewrite);
    }

    private static String basic(String rewrite) {
        if (rewrite == null || rewrite.isBlank()) {
            throw new AnswerRewriteException("改写内容不能为空");
        }
        String text = rewrite.strip();
        if (text.length() > MAX_CHARS) throw new AnswerRewriteException("改写内容过长");
        return text;
    }

    static Set<String> numbers(String value) {
        return NumberClaims.arabic(value);
    }

    private static boolean hasChinese(String value) {
        if (value == null) return false;
        return value.codePoints().anyMatch(code -> code >= 0x4e00 && code <= 0x9fff);
    }
}
