package com.guangge.Interview.coding.dto;

import java.util.List;

/**
 * 编程填空的一空。
 *
 * accepted 是这一空的候选正确答案（可多个），属于服务端数据，不下发给前端。
 */
public record Blank(
        int index,
        List<String> accepted,
        String hint) {
}
