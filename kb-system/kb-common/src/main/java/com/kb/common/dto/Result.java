package com.kb.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一响应包装类。
 * <p>
 * 所有接口的返回值都必须使用此类包装，保证前端收到的 JSON 格式统一为：
 * <pre>{"code": 0, "msg": "ok", "data": {...}}</pre>
 * <p>
 * <b>code 约定</b>：
 * <ul>
 *   <li>0 — 请求成功</li>
 *   <li>非0 — 业务失败，具体含义由各业务模块定义</li>
 * </ul>
 * <p>
 * <b>为什么使用泛型 {@code <T>}</b>：
 * 不同接口返回的数据类型不同（单个对象、列表、分页等），
 * 使用泛型可以在编译期保证类型安全，同时让 Swagger/OpenAPI 自动推断 data 结构。
 *
 * @param <T> data 字段的实际类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    /** 状态码，0 表示成功，非 0 表示失败 */
    private int code;

    /** 提示信息，成功时为 "ok"，失败时为具体错误描述 */
    private String msg;

    /** 响应数据，失败时为 null */
    private T data;

    public Result() {
    }

    public Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 成功响应（带数据）。
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return Result 实例，code=0, msg="ok"
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", data);
    }

    /**
     * 成功响应（不带数据）。
     *
     * @return Result 实例，code=0, msg="ok", data=null
     */
    public static <T> Result<T> ok() {
        return new Result<>(0, "ok", null);
    }

    /**
     * 失败响应。
     *
     * @param code 业务错误码（非0）
     * @param msg  错误描述
     * @return Result 实例
     */
    public static <T> Result<T> fail(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    // ==================== Getter / Setter ====================

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
