package com.kb.common.enums;

/**
 * 用户角色枚举。
 * <p>
 * 数据库 user 表的 role 字段存储数字编码（TINYINT），本枚举负责 Java 层的类型安全映射。
 * <ul>
 *   <li>{@link #SUPER_ADMIN} (0) — 超级管理员：可创建/禁用租户、查看所有数据</li>
 *   <li>{@link #TENANT_ADMIN} (1) — 租户管理员：管理本租户用户、上传删除文档、运行评估</li>
 *   <li>{@link #USER} (2) — 普通用户：本租户内对话问答、查看自己会话</li>
 * </ul>
 */
public enum UserRole {

    /** 超级管理员：拥有全局管理权限，跨租户操作 */
    SUPER_ADMIN(0),

    /** 租户管理员：管理本租户内的用户、文档和评估 */
    TENANT_ADMIN(1),

    /** 普通用户：仅能在所属租户内进行问答对话 */
    USER(2);

    /** 数据库存储的数字编码 */
    private final int code;

    UserRole(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /**
     * 根据数字编码转换为枚举值。
     * <p>
     * 用于从数据库 TINYINT 字段反序列化为枚举。
     *
     * @param code 数字编码（0/1/2）
     * @return 对应的 UserRole 枚举
     * @throws IllegalArgumentException 如果 code 不在合法范围内
     */
    public static UserRole fromCode(int code) {
        for (UserRole role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        throw new IllegalArgumentException("无效的用户角色编码: " + code);
    }
}
