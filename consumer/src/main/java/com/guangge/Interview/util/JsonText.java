package com.guangge.Interview.util;

/** 模型返回 JSON 时常见的包装处理。 */
public final class JsonText {

    private JsonText() { }

    /** 去掉 ```json 围栏，模型经常不遵守「只输出 JSON」的要求。 */
    public static String stripCodeFence(String raw) {
        String text = raw.trim();
        if (!text.startsWith("```")) return text;
        int firstBreak = text.indexOf('\n');
        if (firstBreak < 0) return text;
        String body = text.substring(firstBreak + 1);
        int closing = body.lastIndexOf("```");
        return (closing >= 0 ? body.substring(0, closing) : body).trim();
    }
}
