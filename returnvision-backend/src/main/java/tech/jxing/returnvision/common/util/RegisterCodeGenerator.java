package tech.jxing.returnvision.common.util;

import java.security.SecureRandom;

/**
 * 【公共模块】注册码生成工具
 *
 * 职责：生成 8 位字母数字注册码，避免易混淆字符 0/O/1/I
 */
public final class RegisterCodeGenerator {

    private static final char[] CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private RegisterCodeGenerator() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(CHARS[RANDOM.nextInt(CHARS.length)]);
        }
        return sb.toString();
    }
}
