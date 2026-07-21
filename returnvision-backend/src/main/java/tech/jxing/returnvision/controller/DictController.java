package tech.jxing.returnvision.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tech.jxing.returnvision.audit.AuditLog;
import tech.jxing.returnvision.common.ResponseResult;
import tech.jxing.returnvision.model.dto.CreateDictItemRequest;
import tech.jxing.returnvision.model.dto.UpdateDictItemRequest;
import tech.jxing.returnvision.model.entity.SysDictItem;
import tech.jxing.returnvision.service.DictService;

import java.util.HashMap;
import java.util.Map;

/**
 * 【接口层】字典管理控制器（F08 退货分类标准字典）
 *
 * 职责：字典查询（登录即可）+ 字典项 CRUD（仅 ADMIN）
 * 层级：Controller 层
 * 关联：docs/06 第六章字典接口
 *
 * 权限：
 *   - GET /api/dict/categories         登录即可读（STAFF/SUPERVISOR/ADMIN）
 *   - /api/admin/dict/**               仅 ADMIN（SecurityConfig 已通配 /api/admin/** hasRole('ADMIN')）
 *
 * 接口列表：
 *   GET    /api/dict/categories        - 查询退货分类字典树
 *   POST   /api/admin/dict/items       - 创建字典项
 *   PUT    /api/admin/dict/items/{id}  - 修改字典项
 *   DELETE /api/admin/dict/items/{id}  - 停用字典项（软删）
 */
@Slf4j
@RestController
public class DictController {

    private final DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    /**
     * 查询退货分类字典树
     *
     * 业务流程：
     *   1. 调 DictService.listActiveCategories
     *   2. 返回字典树
     *
     * 权限：登录即可（由 /api/** authenticated() 兜底）
     */
    @GetMapping("/api/dict/categories")
    public ResponseResult<Map<String, Object>> listCategories() {
        // 步骤1：调 Service
        Map<String, Object> result = dictService.listActiveCategories();
        // 步骤2：返回
        return ResponseResult.success(result);
    }

    /**
     * 创建字典项
     *
     * 业务流程：
     *   1. 校验请求参数（@Valid）
     *   2. 转 SysDictItem 调 DictService.createItem
     *   3. 返回新建 id
     *
     * 权限：仅 ADMIN（类级 @PreAuthorize + SecurityConfig /api/admin/** 兜底）
     */
    @PostMapping("/api/admin/dict/items")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(action = "CREATE_DICT_ITEM", targetType = "dict", description = "创建字典项")
    public ResponseResult<Map<String, Object>> createItem(@RequestBody CreateDictItemRequest request) {
        // 步骤1：组装实体
        SysDictItem item = new SysDictItem();
        item.setDictId(request.getDictId());
        item.setParentId(request.getParentId());
        item.setItemCode(request.getItemCode());
        item.setItemLabel(request.getItemLabel());
        item.setIsLeaf(request.getIsLeaf());
        item.setSortOrder(request.getSortOrder());

        // 步骤2：调 Service
        Long id = dictService.createItem(item);

        // 步骤3：返回
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        return ResponseResult.success(data);
    }

    /**
     * 修改字典项
     *
     * 业务流程：
     *   1. 校验请求参数（@Valid）
     *   2. 调 DictService.updateItem
     *   3. 返回更新后的项
     *
     * 权限：仅 ADMIN
     */
    @PutMapping("/api/admin/dict/items/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(action = "UPDATE_DICT_ITEM", targetType = "dict", description = "修改字典项")
    public ResponseResult<Map<String, Object>> updateItem(@PathVariable Long id,
                                                          @RequestBody UpdateDictItemRequest request) {
        // 步骤1：调 Service
        SysDictItem updated = dictService.updateItem(id, request.getItemLabel(),
                request.getSortOrder(), request.getIsLeaf());
        // 步骤2：返回
        return ResponseResult.success(itemToMap(updated));
    }

    /**
     * 停用字典项（软删）
     *
     * 业务流程：
     *   1. 调 DictService.disableItem
     *   2. 返回影响行数（含级联）
     *
     * 权限：仅 ADMIN
     */
    @DeleteMapping("/api/admin/dict/items/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(action = "DISABLE_DICT_ITEM", targetType = "dict", description = "停用字典项")
    public ResponseResult<Map<String, Object>> disableItem(@PathVariable Long id) {
        // 步骤1：调 Service
        int affected = dictService.disableItem(id);
        // 步骤2：返回
        Map<String, Object> data = new HashMap<>();
        data.put("affected", affected);
        return ResponseResult.success(data);
    }

    /** SysDictItem 转 Map */
    private Map<String, Object> itemToMap(SysDictItem item) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", item.getId());
        m.put("dict_id", item.getDictId());
        m.put("parent_id", item.getParentId());
        m.put("item_code", item.getItemCode());
        m.put("item_label", item.getItemLabel());
        m.put("is_leaf", item.getIsLeaf());
        m.put("sort_order", item.getSortOrder());
        m.put("status", item.getStatus());
        return m;
    }
}