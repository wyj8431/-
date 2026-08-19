# P1 审计日志导出设计规格

**日期：** 2026-08-19
**状态：** 已确认范围，进入实现

## 目标与范围

为现有租户审计查询增加 CSV 下载能力，复用动作、结果和时间范围筛选。`ADMIN` 与 `OPERATOR` 均可导出当前租户事件；普通用户仍无权访问。导出不改变分页查询契约，不增加数据表。

导出最多返回 5,000 条最新匹配记录。超过上限时仍返回文件，并通过 `X-Audit-Export-Total` 和 `X-Audit-Export-Truncated: true` 响应头明确标记，不执行无界查询。

## 安全边界

CSV 只来自现有脱敏 `AuditView`：操作者手机号保持掩码，不包含 `metadata_json`、验证码、Cookie、访问令牌或其他敏感字段。查询始终带当前租户 ID，不能跨租户导出。

## API

```text
GET /api/v1/admin/audit-logs/export
  ?action=LOGIN&outcome=FAILURE&from=2026-08-18T00:00:00Z&to=2026-08-19T00:00:00Z
```

响应为 `text/csv; charset=UTF-8` 文件，`Content-Disposition` 为 `audit-logs.csv`。列固定为：`id`、`actor_user_id`、`actor_phone_masked`、`action`、`resource_type`、`resource_id`、`outcome`、`request_id`、`created_at`。字段统一 CSV 引号转义，文件带 UTF-8 BOM 以兼容 Excel 中文显示。

## 实现与前端

- `AuditLogService` 新增有界导出查询，复用动作/结果/时间校验和租户过滤。
- `AuditCsvExporter` 负责固定列和 RFC 4180 风格字段转义。
- `AdminService` 与 `AdminController` 增加文件下载接口。
- HTTP 客户端增加带一次刷新重试的 Blob 下载方法。
- 后台审计筛选旁新增“导出”按钮，成功触发浏览器下载，失败显示可读错误且不清空现有列表。

## 验收

- 服务端覆盖筛选归一化、5,000 上限、截断标记、租户边界和 CSV 引号/换行转义。
- 前端覆盖请求参数、CSV 下载调用和导出失败提示。
- 全量前端单测、构建、后端打包通过；完整容器集成测试限制继续记录。
