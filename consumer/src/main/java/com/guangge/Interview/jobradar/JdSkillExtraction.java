package com.guangge.Interview.jobradar;

import java.math.BigDecimal;
import java.util.List;

public record JdSkillExtraction(
        List<ExtractedSkill> required,
        List<ExtractedSkill> niceToHave,
        Integer years,
        String city,
        boolean remoteFriendly) {

    public record ExtractedSkill(String name, int level, BigDecimal weight) {
        public ExtractedSkill {
            level = Math.max(1, Math.min(5, level));
            weight = weight == null || weight.signum() <= 0 ? BigDecimal.ONE : weight;
        }
    }
}
