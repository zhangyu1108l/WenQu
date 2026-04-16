package com.kb.app.module.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 逻辑文档实体类 — 对应数据库 document 表。
 * <p>
 * 同一租户下，相同文件名的多次上传视为同一文档的不同版本（version_no 递增）。
 * <p>
 * status 字段驱动异步处理状态机：
 * <ul>
 *     <li>PENDING   → 已创建，等待处理</li>
 *     <li>PARSING   → Python 侧车正在解析文档</li>
 *     <li>EMBEDDING → 正在调用智谱 Embedding 并写入 Milvus</li>
 *     <li>READY     → 处理完成，可以参与对话检索</li>
 *     <li>FAILED    → 处理失败，错误信息记录在 async_task 表</li>
 * </ul>
 * <p>
 * document 表包含 tenant_id 字段，所有查询会被租户拦截器自动追加租户隔离条件。
 *
 * @author kb-system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("document")
public class DocumentDO {

    /**
     * 文档主键，数据库自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属租户ID，由租户拦截器自动注入查询条件
     */
    private Long tenantId;

    /**
     * 上传用户ID，关联 user.id
     */
    private Long uploaderId;

    /**
     * 文档标题（文件名去掉扩展名）
     */
    private String title;

    /**
     * 文件类型：pdf / docx
     */
    private String fileType;

    /**
     * 处理状态：PENDING / PARSING / EMBEDDING / READY / FAILED
     * 数据库用 VARCHAR(16) 存储枚举字符串
     */
    private String status;

    /**
     * 原始文件过期时间，到期后自动删除 MinIO 文件；NULL 表示永不过期
     */
    private LocalDateTime expireAt;

    /**
     * 创建时间，数据库自动填充
     */
    private LocalDateTime createdAt;
}
