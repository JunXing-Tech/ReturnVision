> 所属：退货OCR解决方案 | 版本：v2.1 | 日期：2026-07-18
>
> API 接口设计

# API 接口设计

## 一、业务接口（已有）

| 接口 | 方法 | 说明 | 鉴权 |
|------|------|------|------|
| `/api/upload` | POST | 上传图片，返回 OCR 识别结果 | ✅ 需登录 |
| `/api/upload/sse` | POST | SSE 流式上传识别 | ✅ 需登录 |
| `/api/upload/batch` | POST | 批量上传识别 | ✅ 需登录 |
| `/api/confirm` | POST | 确认识别结果，写入飞书表格 | ✅ 需登录 |
| `/api/confirm/batch` | POST | 批量确认写入飞书 | ✅ 需登录 |
| `/api/records` | GET | 查询退货记录列表 | ✅ 需登录 |
| `/api/records/{id}` | DELETE | 删除单条记录 | ✅ 需登录 |
| `/api/records/batch` | DELETE | 批量删除记录 | ✅ 需登录 |
| `/api/dashboard/stats` | GET | 仪表盘统计数据 | ✅ 主管/管理员 |

**上传识别接口示例**：

```
POST /api/upload
Content-Type: multipart/form-data

# 请求
file: [快递单照片]

# 响应（交叉验证一致）
{
  "code": 0,
  "msg": "success",
  "data": {
    "waybill_no": "SF1234567890",
    "rec_name": "张三",
    "rec_phone": "138****8888",
    "rec_address": "北京市朝阳区...",
    "express_company": "顺丰",
    "cross_validation": "matched",
    "confidence": "high",
    "engines": ["zhipu_ocr", "aliyun_waybill"],
    "llm_analysis": {
      "return_reason": "商品质量-开胶",
      "category": "质量问题",
      "confidence": 0.92,
      "validation": {"has_issue": false, "issues": []}
    }
  }
}

# 响应（交叉验证有差异）
{
  "code": 0,
  "msg": "success",
  "data": {
    "waybill_no": "SF1234567890",
    "rec_name": "张三",
    "cross_validation": "review",
    "diff_fields": ["rec_address"],
    "confidence": "medium",
    "engines": ["zhipu_ocr", "aliyun_waybill"],
    "diff_detail": {
      "rec_address": {
        "zhipu": "北京市朝阳区A路1号",
        "aliyun": "北京市朝阳区A路2号",
        "aliyun_prob": 67
      }
    }
  }
}
```

**确认写入接口示例**：

```
POST /api/confirm
Content-Type: application/json

# 请求
{
  "record_id": 1,
  "waybill_no": "SF1234567890",
  "rec_name": "张三",
  "rec_phone": "138****8888",
  "rec_address": "北京市朝阳区...",
  "express_company": "顺丰",
  "return_reason": "商品质量-开胶",
  "return_category": "质量问题"
}

# 响应
{
  "code": 0,
  "msg": "success",
  "data": {
    "feishu_record_id": "recXXXXXX",
    "status": "synced"
  }
}
```

**记录查询接口示例**：

```
GET /api/records?page=1&page_size=20&status=pending

# 响应
{
  "code": 0,
  "msg": "success",
  "data": {
    "total": 50,
    "list": [
      {
        "id": 1,
        "waybill_no": "SF1234567890",
        "rec_name": "张三",
        "express_company": "顺丰",
        "return_reason": "商品质量-开胶",
        "return_category": "质量问题",
        "status": "pending",
        "created_at": "2026-07-10T14:30:00"
      }
    ]
  }
}
```

---

## 二、鉴权接口（F01，v2.1 新增）

> 关联：[docs/10-产品演进与功能可行性方案.md](./10-产品演进与功能可行性方案.md) 第 7.1 节 F01
> 状态：✅ 已落地（2026-07-18）
> 所有鉴权接口返回统一 `ResponseResult<T>` 格式，业务接口需在 Header 携带 `Authorization: Bearer <access_token>`

### 2.1 接口列表

| 接口 | 方法 | 说明 | 鉴权 |
|------|------|------|------|
| `/api/auth/login` | POST | 账号密码登录 | ❌ 公开 |
| `/api/auth/refresh` | POST | 刷新 access token | ❌ 公开（需 refresh_token） |
| `/api/auth/logout` | POST | 登出（失效 refresh token） | ✅ 需登录 |
| `/api/auth/me` | GET | 获取当前用户信息 | ✅ 需登录 |
| `/api/auth/feishu/url` | GET | 获取飞书 OAuth 授权 URL | ❌ 公开 |
| `/api/auth/feishu/callback` | POST | 飞书 OAuth 回调处理 | ❌ 公开 |
| `/api/auth/change-password` | POST | 修改密码（首次登录强制改密用） | ✅ 需登录 |

### 2.2 账号密码登录

```
POST /api/auth/login
Content-Type: application/json

# 请求
{
  "username": "admin",
  "password": "admin123"
}

# 响应（成功）
{
  "code": 0,
  "msg": "success",
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiJ9...",
    "refresh_token": "dGhpcyBpcyBhIHJlZnJl...",
    "expires_in": 7200,
    "user": {
      "id": 1,
      "username": "admin",
      "display_name": "默认管理员",
      "roles": ["ADMIN"],
      "must_change_password": true
    }
  }
}

# 响应（失败）
{
  "code": 1001,
  "msg": "用户名或密码错误",
  "data": null
}
```

> `must_change_password: true` 时前端强制跳转改密页（初始密码 admin123 登录时为 true）。

### 2.3 刷新 access token

```
POST /api/auth/refresh
Content-Type: application/json

# 请求
{
  "refresh_token": "dGhpcyBpcyBhIHJlZnJl..."
}

# 响应（成功）
{
  "code": 0,
  "msg": "success",
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiJ9...",
    "expires_in": 7200
  }
}

# 响应（refresh token 已失效或过期）
{
  "code": 1003,
  "msg": "refresh token 已失效，请重新登录",
  "data": null
}
```

### 2.4 登出

```
POST /api/auth/logout
Authorization: Bearer <access_token>

# 响应
{
  "code": 0,
  "msg": "success",
  "data": {
    "success": true
  }
}
```

> 登出会删除该用户所有 refresh token 记录（多端登出），access token 因无状态无法主动失效，等 2h 自然过期。

### 2.5 获取当前用户信息

```
GET /api/auth/me
Authorization: Bearer <access_token>

# 响应
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": 1,
    "username": "admin",
    "display_name": "默认管理员",
    "roles": ["ADMIN"],
    "last_login_at": "2026-07-18T14:30:00",
    "must_change_password": false
  }
}
```

### 2.6 飞书 OAuth 登录

**步骤1：获取授权 URL**

```
GET /api/auth/feishu/url

# 响应
{
  "code": 0,
  "msg": "success",
  "data": {
    "auth_url": "https://open.feishu.cn/open-apis/authen/v1/index?app_id=cli_xxx&redirect_uri=https%3A%2F%2Freturnvision.jxing.tech%2Flogin&state=xxx"
  }
}
```

**步骤2：飞书回调**

```
POST /api/auth/feishu/callback
Content-Type: application/json

# 请求（前端拿到 code 后调后端）
{
  "code": "xxxxxx",
  "state": "xxx"
}

# 响应（成功，已绑定账号）
{
  "code": 0,
  "msg": "success",
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiJ9...",
    "refresh_token": "dGhpcyBpcyBhIHJlZnJl...",
    "expires_in": 7200,
    "user": {
      "id": 2,
      "username": "zhangsan",
      "display_name": "张三",
      "roles": ["STAFF"],
      "must_change_password": false
    }
  }
}

# 响应（飞书账号未绑定）
{
  "code": 1004,
  "msg": "飞书账号未绑定，请联系管理员绑定",
  "data": null
}
```

### 2.7 修改密码

```
POST /api/auth/change-password
Authorization: Bearer <access_token>
Content-Type: application/json

# 请求
{
  "old_password": "admin123",
  "new_password": "newSecurePassword456"
}

# 响应
{
  "code": 0,
  "msg": "success",
  "data": {
    "success": true
  }
}
```

### 2.8 错误码定义

| 错误码 | 说明 |
|--------|------|
| 1001 | 用户名或密码错误 |
| 1002 | 记录不存在（已有，复用） |
| 1003 | refresh token 已失效或过期 |
| 1004 | 飞书账号未绑定 |
| 1005 | 账号已禁用 |
| 1006 | 旧密码错误 |
| 1007 | 用户名已存在（用户管理用） |
| 401 | 未登录（token 无效或过期） |
| 403 | 权限不足 |

### 2.9 个人中心接口（F01.2，v2.1 补充）

> 关联：docs/10 F01.2 个人中心
> 状态：✅ 已落地（2026-07-18）
> 权限：所有登录用户可访问自己的信息

#### 2.9.1 获取自己的信息

```
GET /api/auth/profile
Authorization: Bearer <access_token>

# 响应
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": 2,
    "username": "zhangsan",
    "display_name": "张三",
    "feishu_user_id": "abc123",
    "feishu_bound": true,
    "roles": ["STAFF"],
    "last_login_at": "2026-07-18T15:00:00",
    "created_at": "2026-07-18T11:00:00"
  }
}
```

> 与 `/api/auth/me` 区别：profile 返回更完整的信息（含 feishu_bound / created_at），me 用于登录后快速获取基本信息。

#### 2.9.2 修改自己的显示名

```
PUT /api/auth/profile
Authorization: Bearer <access_token>
Content-Type: application/json

# 请求
{
  "display_name": "张三（已改名）"
}

# 响应
{
  "code": 0,
  "msg": "success",
  "data": {
    "success": true
  }
}
```

> 用户只能改自己的显示名。用户名（登录标识）、角色、飞书绑定、状态都不能自己改，只能管理员改。

### 2.10 SSE 接口鉴权说明

> 关键：SSE 用 fetch 实现，**不能复用 axios 拦截器自动注入 token**。

前端 `uploadSSE` 的 fetch 调用需手动加 Header：

```javascript
const response = await fetch('/api/upload/sse', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${localStorage.getItem('access_token')}`
  },
  body: formData
});
```

> 后端 JwtAuthenticationFilter 会从 Header 解析 token，对 SSE 接口同样生效。

---

## 三、用户管理接口（F01.1，v2.1 新增）

> 关联：docs/10 F01.1 用户管理 CRUD
> 状态：✅ 已落地（2026-07-18）
> 权限：所有接口仅 ADMIN 角色可访问（SecurityConfig 已配置 `/api/admin/**` hasRole("ADMIN")）

### 3.1 接口列表

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/admin/users` | GET | 用户列表（含角色） |
| `/api/admin/users` | POST | 创建用户 |
| `/api/admin/users/{id}` | PUT | 编辑用户（改密/改角色/改状态） |
| `/api/admin/users/{id}` | DELETE | 删除用户 |
| `/api/admin/users/{id}/reset-password` | POST | 重置密码 |

### 3.2 用户列表

```
GET /api/admin/users

# 响应
{
  "code": 0,
  "msg": "success",
  "data": {
    "users": [
      {
        "id": 1,
        "username": "admin",
        "display_name": "默认管理员",
        "feishu_user_id": null,
        "status": "active",
        "last_login_at": "2026-07-18T14:30:00",
        "created_at": "2026-07-18T10:00:00",
        "roles": ["ADMIN"]
      },
      {
        "id": 2,
        "username": "zhangsan",
        "display_name": "张三",
        "feishu_user_id": "abc123",
        "status": "active",
        "last_login_at": "2026-07-18T15:00:00",
        "created_at": "2026-07-18T11:00:00",
        "roles": ["STAFF"]
      }
    ],
    "total": 2
  }
}
```

### 3.3 创建用户

```
POST /api/admin/users
Content-Type: application/json

# 请求
{
  "username": "zhangsan",
  "password": "initialPassword123",
  "display_name": "张三",
  "role_codes": ["STAFF"],
  "feishu_user_id": "abc123"      # 可选，绑定飞书后可用 OAuth 登录
}

# 响应
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": 2,
    "username": "zhangsan"
  }
}

# 响应（用户名已存在）
{
  "code": 1007,
  "msg": "用户名已存在",
  "data": null
}
```

### 3.4 编辑用户

```
PUT /api/admin/users/{id}
Content-Type: application/json

# 请求（所有字段可选，只传需要改的）
{
  "display_name": "张三（已改名）",
  "role_codes": ["SUPERVISOR"],   # 改角色
  "status": "disabled",           # 禁用
  "feishu_user_id": "new_feishu_id"
}

# 响应
{
  "code": 0,
  "msg": "success",
  "data": {
    "success": true
  }
}
```

### 3.5 删除用户

```
DELETE /api/admin/users/{id}

# 响应
{
  "code": 0,
  "msg": "success",
  "data": {
    "success": true
  }
}

# 响应（不能删自己）
{
  "code": 1008,
  "msg": "不能删除自己",
  "data": null
}

# 响应（不能删最后一个管理员）
{
  "code": 1009,
  "msg": "不能删除最后一个管理员",
  "data": null
}
```

### 3.6 重置密码

```
POST /api/admin/users/{id}/reset-password
Content-Type: application/json

# 请求
{
  "new_password": "newPassword456"
}

# 响应
{
  "code": 0,
  "msg": "success",
  "data": {
    "success": true
  }
}
```

> 重置后用户需用新密码登录，原 refresh token 全部失效（被禁用账号同等效果）。

### 3.7 安全约束（5 项）

| # | 约束 | 错误码 |
|---|------|--------|
| 1 | 不能删除自己 | 1008 |
| 2 | 不能禁用自己 | 1008 |
| 3 | 不能删除最后一个管理员 | 1009 |
| 4 | 不能撤销自己的 ADMIN 角色（避免自降级后无管理员） | 1010 |
| 5 | 创建/重置密码时 BCrypt 哈希存储 | - |

### 3.8 错误码补充

| 错误码 | 说明 |
|--------|------|
| 1007 | 用户名已存在 |
| 1008 | 不能操作自己的账号 |
| 1009 | 不能删除最后一个管理员 |
| 1010 | 不能撤销自己的管理员角色 |

---

## 四、审计日志接口（F03，v2.1 新增）

> 关联：docs/10 F03 操作审计日志
> 状态：✅ 已落地（2026-07-18）
> 权限：仅 SUPERVISOR / ADMIN 可访问

### 4.1 接口列表

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/audit/logs` | GET | 查询审计日志（支持分页+筛选） |

### 4.2 查询审计日志

```
GET /api/audit/logs?page=1&size=20&user_id=&action=&start_date=&end_date=
Authorization: Bearer <access_token>

# 查询参数（均可选）
# page: 页码，默认1
# size: 每页条数，默认20
# user_id: 按操作者筛选
# action: 按操作类型筛选（如 LOGIN/CONFIRM）
# start_date: 开始日期（YYYY-MM-DD）
# end_date: 结束日期（YYYY-MM-DD）

# 响应
{
  "code": 0,
  "msg": "success",
  "data": {
    "logs": [
      {
        "id": 1,
        "user_id": 1,
        "username": "admin",
        "action": "LOGIN",
        "target_type": "auth",
        "target_id": null,
        "description": "管理员登录",
        "success": true,
        "ip": "192.168.1.100",
        "user_agent": "Mozilla/5.0...",
        "request_data": "{\"username\":\"admin\"}",
        "created_at": "2026-07-18T14:30:00"
      }
    ],
    "total": 156,
    "page": 1,
    "size": 20
  }
}
```

---

## 五、数据导出接口（F02，v2.1 新增）

> 关联：docs/10 F02 数据保留期与导出管控
> 状态：✅ 已落地（2026-07-19）
> 权限：STAFF/SUPERVISOR/ADMIN 都可导出（按角色过滤数据范围，复用 F03 客服范围细化）

### 5.1 导出退货记录

```
POST /api/records/export
Authorization: Bearer <access_token>
Content-Type: application/json

# 请求（筛选条件，均可选）
{
  "status": "synced",
  "start_date": "2026-07-01",
  "end_date": "2026-07-18"
}

# 响应（成功，返回 Excel 文件流）
Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
Content-Disposition: attachment; filename="return_records_20260718_143000.xlsx"

# 响应（超限）
{
  "code": 1011,
  "msg": "导出失败：单次最多导出 1000 条 / 每日导出次数超限",
  "data": null
}
```

**导出防护**：
1. 强制审计：action=EXPORT_RECORDS 记录到 operation_log
2. 水印：Excel 页眉含导出人+时间+IP+条数
3. 限流：单次最多 1000 条，每人每日最多 5 次
4. 告警：导出后飞书群通知主管

**数据范围**：
- STAFF：只能导出自己处理的记录（created_by = 当前用户）
- SUPERVISOR/ADMIN：可导出全部记录

### 5.2 错误码补充

| 错误码 | 说明 |
|--------|------|
| 1011 | 导出次数/条数超限 |

---

## 六、字典管理接口（F08，v2.2 新增）

> 关联：[docs/04 第 4.11 节](./04-详细方案设计.md) F08
> 权限：查询接口登录即可，CRUD 接口仅 ADMIN（SecurityConfig 通配 `/api/admin/** hasRole('ADMIN')`）

### 6.1 接口列表

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | /api/dict/categories | 登录 | 查询退货分类字典树 |
| POST | /api/admin/dict/items | ADMIN | 创建字典项 |
| PUT | /api/admin/dict/items/{id} | ADMIN | 修改字典项（不改 item_code） |
| DELETE | /api/admin/dict/items/{id} | ADMIN | 停用字典项（软删 + 级联子项） |

### 6.2 查询退货分类字典树

```
GET /api/dict/categories
```

**响应**：
```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "items": [
      {
        "id": 1, "dict_id": 1, "parent_id": null,
        "item_code": "QUALITY", "item_label": "质量问题",
        "is_leaf": true, "sort_order": 1, "status": "active",
        "children": [
          {
            "id": 11, "dict_id": 1, "parent_id": 1,
            "item_code": "QUALITY_BROKEN", "item_label": "破损",
            "is_leaf": true, "sort_order": 1, "status": "active"
          }
        ]
      }
    ]
  }
}
```

### 6.3 创建字典项

```
POST /api/admin/dict/items
Content-Type: application/json

{
  "dict_id": 1,
  "parent_id": null,          // 可空，一级项为 null
  "item_code": "NEW_CAT",     // 必填，同层级内唯一
  "item_label": "新分类",     // 必填
  "is_leaf": true,            // 可空，默认 true
  "sort_order": 5             // 可空，默认 0
}
```

**响应**：`{ "code": 0, "data": { "id": 16 } }`

### 6.4 修改字典项

```
PUT /api/admin/dict/items/{id}
Content-Type: application/json

{
  "item_label": "新名称",    // 可空，按需更新
  "sort_order": 10,
  "is_leaf": false
}
```

> 注意：不允许改 item_code（保护历史 return_records 反查）

### 6.5 停用字典项

```
DELETE /api/admin/dict/items/{id}
```

**响应**：`{ "code": 0, "data": { "affected": 3 } }`（含级联子项数）

### 6.6 错误码补充

| 错误码 | 说明 |
|--------|------|
| 2100 | 字典项必填字段缺失 / 长度超限 |
| 2101 | 字典不存在或已停用 |
| 2102 | 同层级内 item_code 已存在 |
| 2103 | 父项不存在或不属于该字典 |
| 2104 | 字典项不存在 |

---

## 七、退货报表接口（F04，v2.3 新增）

> 关联：[docs/04 第 4.12 节](./04-详细方案设计.md) F04
> 权限：所有登录用户可访问（数据范围按角色过滤）

### 7.1 接口列表

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | /api/reports | 登录 | 多维度退货报表（4 个维度） |

### 7.2 获取多维度退货报表

```
GET /api/reports?days=7
```

**参数**：
| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| days | int | 7 | 统计天数，可选 7/30/90，越界回退 7 |

**数据范围**：
- STAFF：只能看自己处理的记录（created_by = 当前用户）
- SUPERVISOR/ADMIN：看全部记录

**状态过滤**：只统计 synced（已闭环的退货）

**响应**：
```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "days": 7,
    "category_breakdown": [
      { "name": "质量问题", "count": 15, "percentage": 50.0 }
    ],
    "express_breakdown": [
      { "name": "顺丰", "count": 10, "percentage": 33.33 }
    ],
    "reason_top10": [
      { "name": "破损", "count": 8, "percentage": 26.67 }
    ],
    "trend": [
      { "date": "2026-07-15", "count": 5 }
    ]
  }
}
```

**空值兜底**：
- return_category 为空 -> 归到"未分类"
- express_company 为空 -> 归到"未知"
- return_reason 为空 -> 归到"未标注"

---
