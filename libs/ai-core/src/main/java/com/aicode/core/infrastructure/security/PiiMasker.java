package com.aicode.core.infrastructure.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 常见 PII 掩码工具（手机号、身份证、邮箱）。纯函数，无状态。
 *
 * <p><b>重要边界</b>：本类被 {@code GuardrailToolPort} 直接作用在工具的 JSON 字符串上，
 * 因此身份证模式必须<b>只匹配 15 位或 18 位</b>（真实证件长度），不能匹配任意 15–18 位数字——
 * 否则 JSON 里 {@code "score":0.4868194162841034} 这类 16–17 位小数会被替换成带 {@code *} 的文本，
 * 产生非法 JSON（Week 31 真机踩过：knowledge-qa 报 "invalid knowledge tool result"）。</p>
 */
final class PiiMasker {

    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");

    /** 18 位身份证：6 + 8 + 3 位顺序码 + 校验位（数字或 X）。 */
    private static final Pattern ID_CARD_18 = Pattern.compile("(?<!\\d)(\\d{6})(\\d{8})(\\d{3}[\\dXx])(?!\\d)");

    /** 15 位老式身份证：6 + 8 + 1 位。 */
    private static final Pattern ID_CARD_15 = Pattern.compile("(?<!\\d)(\\d{6})(\\d{8})([\\dXx])(?!\\d)");

    private static final Pattern EMAIL = Pattern.compile("([\\w.+-]{1,3})[\\w.+-]*@([\\w.-]+\\.[A-Za-z]{2,})");

    private PiiMasker() {
    }

    static String mask(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String masked = maskMobile(text);
        masked = maskPattern(ID_CARD_18, masked);
        masked = maskPattern(ID_CARD_15, masked);
        return maskEmail(masked);
    }

    private static String maskMobile(String text) {
        Matcher matcher = MOBILE.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String mobile = matcher.group(1);
            matcher.appendReplacement(buffer, mobile.substring(0, 3) + "****" + mobile.substring(7));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String maskPattern(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, matcher.group(1) + "********" + matcher.group(3));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String maskEmail(String text) {
        Matcher matcher = EMAIL.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, matcher.group(1) + "***@" + matcher.group(2));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
