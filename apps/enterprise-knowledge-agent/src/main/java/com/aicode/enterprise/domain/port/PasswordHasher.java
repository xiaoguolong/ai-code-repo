package com.aicode.enterprise.domain.port;

/**
 * 出站端口：密码哈希。当前默认实现为 Sa-Token 加盐 md5；未来可替换为 BCrypt/Argon2 而不改用例。
 */
public interface PasswordHasher {

    /**
     * 对明文密码做加盐哈希。盐与用户名绑定，使相同密码在不同用户名下得到不同哈希。
     *
     * @param rawPassword 明文密码
     * @param username    用户名，作为盐的一部分
     * @return 哈希结果，非 null
     */
    String hash(String rawPassword, String username);

    /**
     * 校验明文密码是否与已存哈希匹配。
     *
     * @param rawPassword 明文密码
     * @param username    用户名
     * @param hashed      已存哈希
     * @return 匹配为 true
     */
    boolean matches(String rawPassword, String username, String hashed);
}
