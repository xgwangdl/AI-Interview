package com.guangge.Interview.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumberClaimsTest {

    @Test
    void extractsArabicAndChineseQuantities() {
        var claims = NumberClaims.claims("从 900ms 优化到 100ms，提升二十五%，耗时两年，服务三个团队。");

        assertTrue(claims.contains("900ms"));
        assertTrue(claims.contains("100ms"));
        assertTrue(claims.contains("25%"));
        assertTrue(claims.contains("2年"));
        assertTrue(claims.contains("3个"));
    }

    @Test
    void treatsArabicAndChineseFormsOfTheSameQuantityAsEquivalent() {
        assertTrue(NumberClaims.unsupported("耗时 3 年", "耗时三年").isEmpty());
        assertTrue(NumberClaims.unsupported("提升 25%", "提升百分之二十五").isEmpty());
    }

    @Test
    void rejectsChineseQuantitiesMissingFromTheGroundedText() {
        assertTrue(NumberClaims.unsupported("性能提升了二十五%", "我优化了性能").contains("25%"));
    }

    @Test
    void parsesCompoundChineseNumbers() {
        assertEquals(25, NumberClaims.parseChineseNumber("二十五"));
        assertEquals(105, NumberClaims.parseChineseNumber("一百零五"));
        assertEquals(23000, NumberClaims.parseChineseNumber("两万三千"));
    }
}
