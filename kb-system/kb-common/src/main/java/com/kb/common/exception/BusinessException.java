package com.kb.common.exception;

/**
 * 自定义业务异常类。
 * <p>
 * <b>为什么不直接使用 RuntimeException？</b>
 * <ol>
 *   <li>业务异常需要携带错误码（code），RuntimeException 只有 message，无法区分不同类型的业务错误</li>
 *   <li>GlobalExceptionHandler 需要区分"可预期的业务异常"和"不可预期的系统异常"，
 *       业务异常返回具体错误码和提示信息，系统异常返回通用 500 错误</li>
 *   <li>统一使用 BusinessException 便于日志分级：业务异常 WARN 级别，系统异常 ERROR 级别</li>
 * </ol>
 * <p>
 * 使用方式：
 * <pre>
 *   throw BusinessException.of(1001, "用户名已存在");
 *   throw BusinessException.of(1002, "文档不存在");
 * </pre>
 */
public class BusinessException extends RuntimeException {

    /** 业务错误码，与 Result.code 对应，非 0 */
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 静态工厂方法，创建业务异常。
     *
     * @param code    业务错误码（非0）
     * @param message 错误描述
     * @return BusinessException 实例
     */
    public static BusinessException of(int code, String message) {
        return new BusinessException(code, message);
    }

    public int getCode() {
        return code;
    }
}
