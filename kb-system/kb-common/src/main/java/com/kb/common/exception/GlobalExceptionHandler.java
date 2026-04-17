package com.kb.common.exception;

import com.kb.common.dto.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 * <p>
 * 使用 {@code @RestControllerAdvice} 拦截所有 Controller 层抛出的异常，
 * 统一转换为 {@link Result} 格式返回，避免前端收到原始的 HTTP 500 错误页面。
 * <p>
 * 异常捕获优先级（从具体到通用）：
 * <ol>
 *   <li>{@link BusinessException} — 可预期的业务异常，WARN 级别日志</li>
 *   <li>{@link MethodArgumentNotValidException} — 参数校验失败（@Valid 触发）</li>
 *   <li>{@link Exception} — 兜底：所有未预期的系统异常，ERROR 级别日志</li>
 * </ol>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 捕获业务异常。
     * <p>
     * 场景：Service 层通过 {@code throw BusinessException.of(code, msg)} 主动抛出的可预期错误，
     * 例如"用户名已存在"、"文档不存在"、"无权限"等。
     * <p>
     * 日志级别为 WARN，因为这是正常业务流程中的错误分支，不需要告警。
     *
     * @param e 业务异常
     * @return 包含业务错误码和描述的 Result
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 捕获参数校验异常。
     * <p>
     * 场景：Controller 方法参数使用了 {@code @Valid} 注解，当请求体中的字段不满足
     * {@code @NotBlank}、{@code @Size}、{@code @Email} 等校验规则时，
     * Spring 会自动抛出此异常。
     * <p>
     * 响应会拼接所有校验失败的字段和原因，方便前端定位问题。
     *
     * @param e 参数校验异常
     * @return 包含校验错误详情的 Result，code=400
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String errorMsg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", errorMsg);
        return Result.fail(400, errorMsg);
    }

    /**
     * 兜底异常处理。
     * <p>
     * 场景：捕获所有未被上面两个方法拦截的异常，例如 NullPointerException、
     * 数据库连接超时、JSON 序列化错误等不可预期的系统级异常。
     * <p>
     * 日志级别为 ERROR 并打印完整堆栈，便于排查问题。
     * 返回通用错误提示，避免向前端暴露内部实现细节。
     *
     * @param e 未预期的异常
     * @return 通用错误 Result，code=500
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail(500, "服务器内部错误");
    }
}
