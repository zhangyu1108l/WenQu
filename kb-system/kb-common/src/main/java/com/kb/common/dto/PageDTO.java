package com.kb.common.dto;

import java.util.List;

/**
 * 分页响应包装类。
 * <p>
 * 用于所有分页查询接口的 data 部分，统一返回格式：
 * <pre>
 * {
 *   "total": 100,
 *   "page": 1,
 *   "size": 10,
 *   "list": [...]
 * }
 * </pre>
 *
 * @param <T> 列表元素的类型
 */
public class PageDTO<T> {

    /** 总记录数 */
    private long total;

    /** 当前页码（从 1 开始） */
    private int page;

    /** 每页条数 */
    private int size;

    /** 当前页数据列表 */
    private List<T> list;

    public PageDTO() {
    }

    public PageDTO(long total, int page, int size, List<T> list) {
        this.total = total;
        this.page = page;
        this.size = size;
        this.list = list;
    }

    /**
     * 快速构建分页响应。
     *
     * @param total 总记录数
     * @param page  当前页码
     * @param size  每页条数
     * @param list  当前页数据
     * @param <T>   列表元素类型
     * @return PageDTO 实例
     */
    public static <T> PageDTO<T> of(long total, int page, int size, List<T> list) {
        return new PageDTO<>(total, page, size, list);
    }

    // ==================== Getter / Setter ====================

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }
}
