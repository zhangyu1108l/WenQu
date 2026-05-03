package com.kb.app.module.document.controller;

import com.kb.app.module.document.dto.*;
import com.kb.app.module.document.service.DocUploadService;
import com.kb.app.module.document.service.DocumentService;
import com.kb.common.dto.PageDTO;
import com.kb.common.dto.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 文档管理 Controller — 处理文档上传、查询、下载、过期设置、删除等接口。
 * <p>
 * 接口路径统一前缀 /api/docs，严格按照架构文档定义，不可新增或修改路径。
 * <p>
 * 包含的接口及权限要求：
 * <ul>
 *     <li>POST   /api/docs/upload          — 租户管理员  — multipart 上传文档，返回 {docId, taskId}</li>
 *     <li>GET    /api/docs                 — 登录用户    — 分页文档列表，支持关键词搜索</li>
 *     <li>GET    /api/docs/{id}            — 登录用户    — 文档详情 + 当前激活版本信息</li>
 *     <li>GET    /api/docs/{id}/versions   — 登录用户    — 文档历史版本列表（最多 5 个）</li>
 *     <li>GET    /api/docs/{id}/download   — 登录用户    — MinIO 预签名 URL，有效期 15 分钟</li>
 *     <li>PUT    /api/docs/{id}/expire     — 租户管理员  — 设置/清除过期时间</li>
 *     <li>DELETE /api/docs/{id}            — 租户管理员  — 删除文档 + 同步清理 Milvus 向量</li>
 * </ul>
 * <p>
 * Controller 层只做参数获取和调用转发，不包含任何业务逻辑。
 * <p>
 * <b>tenantId / userId 获取方式：</b>
 * 由 Gateway 的 JwtAuthFilter 解析 JWT 后写入请求头 X-Tenant-Id 和 X-User-Id，
 * Controller 通过 @RequestHeader 注解直接读取，无需再次解析 JWT。
 *
 * @author kb-system
 */
@RestController
@RequestMapping("/api/docs")
@RequiredArgsConstructor
public class DocumentController {

    private final DocUploadService docUploadService;
    private final DocumentService documentService;

    /**
     * 上传文档。
     * <p>
     * 接口：POST /api/docs/upload
     * 权限：TENANT_ADMIN（租户管理员）
     * 请求格式：multipart/form-data，文件字段名 file
     * <p>
     * <b>tenantId / userId 来源说明：</b>
     * Gateway 的 JwtAuthFilter 解析 JWT 中的 claims 后，
     * 将 tenantId 写入请求头 X-Tenant-Id，userId 写入 X-User-Id。
     * Spring Boot 主服务的 TenantInterceptor 会从请求头读取并存入 ThreadLocal，
     * 此处通过 @RequestHeader 直接获取，避免重复解析 JWT。
     * <p>
     * <b>响应说明：</b>
     * 返回 {docId, taskId}，前端拿到 taskId 后应每 2 秒轮询
     * GET /api/tasks/{taskId}/status 查询异步处理进度。
     *
     * @param file     上传的文件（仅支持 pdf / docx）
     * @param tenantId 租户ID，从请求头 X-Tenant-Id 获取
     * @param userId   用户ID，从请求头 X-User-Id 获取
     * @return 包含 docId 和 taskId 的统一响应
     */
    @PostMapping("/upload")
    public Result<UploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader("X-User-Id") Long userId) {
        Map<String, Long> result = docUploadService.upload(file, tenantId, userId);
        UploadResponse response = UploadResponse.builder()
                .docId(result.get("docId"))
                .taskId(result.get("taskId"))
                .build();
        return Result.ok(response);
    }

    /**
     * 分页查询文档列表。
     * <p>
     * 接口：GET /api/docs?keyword=&page=&size=
     * 权限：登录用户
     * <p>
     * 支持按 title 关键词模糊搜索，keyword 为空时返回全部文档。
     * 分页参数 page 从 1 开始，size 默认 10。
     *
     * @param tenantId 租户ID，从请求头 X-Tenant-Id 获取
     * @param keyword  搜索关键词（可选），对文档标题模糊匹配
     * @param page     页码，默认 1
     * @param size     每页条数，默认 10
     * @return 分页结果，包含 total / page / size / list
     */
    @GetMapping
    public Result<PageDTO<DocumentVO>> list(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        PageDTO<DocumentVO> pageResult = documentService.getDocumentList(tenantId, keyword, page, size);
        return Result.ok(pageResult);
    }

    /**
     * 查询文档详情。
     * <p>
     * 接口：GET /api/docs/{id}
     * 权限：登录用户
     * <p>
     * 返回文档基础信息及当前激活版本信息（嵌套 VersionVO）。
     *
     * @param id       文档ID
     * @param tenantId 租户ID，从请求头 X-Tenant-Id 获取
     * @return 文档详情 VO
     */
    @GetMapping("/{id}")
    public Result<DocumentVO> detail(
            @PathVariable("id") Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        DocumentVO vo = documentService.getDocumentDetail(id, tenantId);
        return Result.ok(vo);
    }

    /**
     * 查询文档版本列表。
     * <p>
     * 接口：GET /api/docs/{id}/versions
     * 权限：登录用户
     * <p>
     * 返回该文档所有版本（最多 5 个），按 version_no 降序排列（最新版本在前）。
     *
     * @param id       文档ID
     * @param tenantId 租户ID，从请求头 X-Tenant-Id 获取
     * @return 版本列表
     */
    @GetMapping("/{id}/versions")
    public Result<List<VersionVO>> versions(
            @PathVariable("id") Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        List<VersionVO> versions = documentService.getVersionList(id, tenantId);
        return Result.ok(versions);
    }

    /**
     * 获取文档下载链接。
     * <p>
     * 接口：GET /api/docs/{id}/download
     * 权限：登录用户
     * <p>
     * 返回 MinIO 预签名 URL，有效期 15 分钟。
     * 前端拿到 URL 后应立即使用（或在 15 分钟内使用），
     * 过期后需重新调用此接口获取新的 URL。
     * <p>
     * 不返回永久链接是安全规范的强制要求，防止 URL 泄露后被长期滥用。
     *
     * @param id       文档ID
     * @param tenantId 租户ID，从请求头 X-Tenant-Id 获取
     * @return 预签名 URL 字符串
     */
    @GetMapping("/{id}/download")
    public Result<String> download(
            @PathVariable("id") Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        String url = documentService.getDownloadUrl(id, tenantId);
        return Result.ok(url);
    }

    /**
     * 设置文档过期时间。
     * <p>
     * 接口：PUT /api/docs/{id}/expire
     * 权限：TENANT_ADMIN（租户管理员）
     * <p>
     * 请求体中 expireAt 传 null 表示永不过期（清除之前设置的过期时间），
     * 传具体时间表示到期后文档标记为过期。
     *
     * @param id       文档ID
     * @param tenantId 租户ID，从请求头 X-Tenant-Id 获取
     * @param request  包含 expireAt 的请求体
     * @return 统一响应，data 为 null
     */
    @PutMapping("/{id}/expire")
    public Result<Void> setExpire(
            @PathVariable("id") Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestBody ExpireRequest request) {
        documentService.setExpireAt(id, tenantId, request.getExpireAt());
        return Result.ok();
    }

    /**
     * 删除文档。
     * <p>
     * 接口：DELETE /api/docs/{id}
     * 权限：TENANT_ADMIN（租户管理员）
     * <p>
     * 删除文档时会同步清理所有关联资源：
     * Milvus 向量 → doc_chunk 记录 → MinIO 文件 → document_version 记录 → document 记录。
     * 此操作不可逆，请谨慎使用。
     *
     * @param id       文档ID
     * @param tenantId 租户ID，从请求头 X-Tenant-Id 获取
     * @return 统一响应，data 为 null
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @PathVariable("id") Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        documentService.deleteDocument(id, tenantId);
        return Result.ok();
    }
}
