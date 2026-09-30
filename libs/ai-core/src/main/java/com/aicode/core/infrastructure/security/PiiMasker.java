package com.aicode.core.infrastructure.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 常见 PII 掩码工具（手机号、身份证、邮箱）。纯函数，无状态。
 */
final class PiiMasker {

    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)(\\d{6})(\\d{8})([\\dXx])(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("([\\w.+-]{1,3})[\\w.+-]*@([\\w.-]+\\.[A-Za-z]{2,})");

    private PiiMasker() {
    }

    static String mask(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String masked = maskMobile(text);
        masked = maskIdCard(masked);
        masked = maskEmail(masked);
        return masked;
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

    private static String maskIdCard(String text) {
        Matcher matcher = ID_CARD.matcher(text);
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
