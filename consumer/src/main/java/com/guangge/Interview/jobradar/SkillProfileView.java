package com.guangge.Interview.jobradar;

import java.math.BigDecimal;
import java.util.List;

public record SkillProfileView(
        List<SkillItem> skills,
        Integer years,
        String city,
        Integer expectedMinK,
        Integer expectedMaxK,
        boolean configured,
        String profileHash) {

    public record SkillItem(String name, int level, BigDecimal weight) { }
}
