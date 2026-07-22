package tech.jxing.returnvision.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jxing.returnvision.common.exception.BizException;
import tech.jxing.returnvision.model.entity.SysDict;
import tech.jxing.returnvision.model.entity.SysDictItem;
import tech.jxing.returnvision.model.mapper.SysDictItemMapper;
import tech.jxing.returnvision.model.mapper.SysDictMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 【业务逻辑层】字典管理服务（F08 退货分类标准字典）
 *
 * 职责：字典项的增删改查 + LLM 用的字典查询 + code->label 反查
 * 层级：Service 层
 * 调用方：DictController（CRUD）+ LlmAnalyzerService（查字典、反查 label）
 * 关联：docs/04 第 4.11 节、docs/05 第 4.5.11 节
 *
 * 设计要点：
 *   1. listActiveCategories 只返回 status=active AND is_leaf=1 的叶子项，按 sort_order 排序
 *   2. 字典为空时返回空列表（调用方 LLM 走硬编码 4 类兜底）
 *   3. disableItem 软删，不物理删除；停用一级项时级联停用其所有子项
 *   4. updateItem 只改 label/sortOrder/is_leaf，不改 item_code（保护历史数据反查）
 *   5. getItemLabelByCode 给 LLM 用：LLM 返回 code，反查 label 写入 return_records.return_category
 */
@Service
@Slf4j
public class DictService {

    private static final String DICT_CODE_RETURN_CATEGORY = "return_category";
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_DISABLED = "disabled";

    private final SysDictMapper dictMapper;
    private final SysDictItemMapper dictItemMapper;

    public DictService(SysDictMapper dictMapper, SysDictItemMapper dictItemMapper) {
        this.dictMapper = dictMapper;
        this.dictItemMapper = dictItemMapper;
    }

    /**
     * 查询退货分类字典的活跃叶子项（树形结构）
     *
     * 实现步骤：
     *   1. 按 dict_code 查 sys_dict 主表，拿到 dict_id
     *   2. 查 sys_dict_item 中 dict_id 匹配且 status=active 的所有项
     *   3. 组装两级树形结构（一级项含 children 子项列表）
     *   4. 按 sort_order 排序
     *
     * @return Map 含 items（一级项列表，每个含 children）
     */
    public Map<String, Object> listActiveCategories() {
        log.info("[字典管理] 查询退货分类活跃字典项");

        // 步骤1：查 sys_dict 主表
        SysDict dict = dictMapper.selectOne(
                new LambdaQueryWrapper<SysDict>()
                        .eq(SysDict::getDictCode, DICT_CODE_RETURN_CATEGORY)
                        .eq(SysDict::getStatus, STATUS_ACTIVE));
        if (dict == null) {
            log.warn("[字典管理] 字典 {} 不存在或已停用，返回空列表", DICT_CODE_RETURN_CATEGORY);
            Map<String, Object> empty = new HashMap<>();
            empty.put("items", new ArrayList<>());
            return empty;
        }

        // 步骤2：查所有 active 项
        List<SysDictItem> allItems = dictItemMapper.selectList(
                new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getDictId, dict.getId())
                        .eq(SysDictItem::getStatus, STATUS_ACTIVE)
                        .orderByAsc(SysDictItem::getSortOrder));

        if (allItems.isEmpty()) {
            log.info("[字典管理] 字典 {} 无活跃项", DICT_CODE_RETURN_CATEGORY);
            Map<String, Object> empty = new HashMap<>();
            empty.put("items", new ArrayList<>());
            return empty;
        }

        // 步骤3：组装两级树形结构
        Map<Long, List<SysDictItem>> childrenByParent = allItems.stream()
                .filter(item -> item.getParentId() != null)
                .collect(Collectors.groupingBy(SysDictItem::getParentId));

        List<Map<String, Object>> tree = allItems.stream()
                .filter(item -> item.getParentId() == null)
                .map(parent -> {
                    Map<String, Object> node = itemToMap(parent);
                    List<SysDictItem> children = childrenByParent.getOrDefault(parent.getId(), new ArrayList<>());
                    node.put("children", children.stream()
                            .sorted(Comparator.comparingInt(SysDictItem::getSortOrder))
                            .map(this::itemToMap)
                            .collect(Collectors.toList()));
                    return node;
                })
                .collect(Collectors.toList());

        // 步骤4：返回
        Map<String, Object> result = new HashMap<>();
        result.put("items", tree);
        log.info("[字典管理] 查询完成，一级项 {} 个", tree.size());
        return result;
    }

    /**
     * 查询所有活跃叶子项（扁平，供 LLM prompt 注入用）
     *
     * 实现步骤：
     *   1. 查 dict_id
     *   2. 查 status=active AND is_leaf=1 的项
     *   3. 按 sort_order 排序
     *
     * @return List<Map> 每项含 code/label
     */
    public List<Map<String, Object>> listActiveLeafItems() {
        log.info("[字典管理] 查询活跃叶子项（供 LLM prompt 注入）");

        // 步骤1：查 dict_id
        SysDict dict = dictMapper.selectOne(
                new LambdaQueryWrapper<SysDict>()
                        .eq(SysDict::getDictCode, DICT_CODE_RETURN_CATEGORY)
                        .eq(SysDict::getStatus, STATUS_ACTIVE));
        if (dict == null) {
            return new ArrayList<>();
        }

        // 步骤2：查 is_leaf=1 且 active 的项
        return dictItemMapper.selectList(
                        new LambdaQueryWrapper<SysDictItem>()
                                .eq(SysDictItem::getDictId, dict.getId())
                                .eq(SysDictItem::getStatus, STATUS_ACTIVE)
                                .eq(SysDictItem::getIsLeaf, true)
                                .orderByAsc(SysDictItem::getSortOrder))
                .stream()
                .map(this::itemToMap)
                .collect(Collectors.toList());
    }

    /**
     * 创建字典项
     *
     * 实现步骤：
     *   1. 校验 dict_id 存在
     *   2. 校验同层级内 item_code 唯一
     *   3. 若 parent_id 非空，校验父项存在且属于同字典
     *   4. 插入
     *
     * @return 新建项的 id
     */
    @Transactional
    public Long createItem(SysDictItem item) {
        log.info("[字典管理] 创建字典项 dict_id={}, code={}, label={}",
                item.getDictId(), item.getItemCode(), item.getItemLabel());

        // 步骤0：手动校验必填字段（与现有 CreateUserRequest 风格一致，不引入 jakarta.validation）
        if (item.getDictId() == null) {
            throw new BizException(2100, "字典ID不能为空");
        }
        if (item.getItemCode() == null || item.getItemCode().isEmpty()) {
            throw new BizException(2100, "项编码不能为空");
        }
        if (item.getItemCode().length() > 50) {
            throw new BizException(2100, "项编码长度不能超过50");
        }
        if (item.getItemLabel() == null || item.getItemLabel().isEmpty()) {
            throw new BizException(2100, "项名称不能为空");
        }
        if (item.getItemLabel().length() > 50) {
            throw new BizException(2100, "项名称长度不能超过50");
        }

        // 步骤1：校验 dict_id 存在
        SysDict dict = dictMapper.selectById(item.getDictId());
        if (dict == null || STATUS_DISABLED.equals(dict.getStatus())) {
            throw new BizException(2101, "字典不存在或已停用");
        }

        // 步骤2：校验同层级内 item_code 唯一
        // parent_id=null 时用 isNull（SQL 里 = NULL 永远 false），parent_id 非空时用 eq
        LambdaQueryWrapper<SysDictItem> codeCheck = new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getDictId, item.getDictId())
                .eq(SysDictItem::getItemCode, item.getItemCode());
        if (item.getParentId() == null) {
            codeCheck.isNull(SysDictItem::getParentId);
        } else {
            codeCheck.eq(SysDictItem::getParentId, item.getParentId());
        }
        Long count = dictItemMapper.selectCount(codeCheck);
        if (count > 0) {
            throw new BizException(2102, "同层级内 item_code 已存在：" + item.getItemCode());
        }

        // 步骤3：若 parent_id 非空，校验父项存在且属于同字典
        if (item.getParentId() != null) {
            SysDictItem parent = dictItemMapper.selectById(item.getParentId());
            if (parent == null || !parent.getDictId().equals(item.getDictId())) {
                throw new BizException(2103, "父项不存在或不属于该字典");
            }
        }

        // 步骤4：插入
        if (item.getStatus() == null) {
            item.setStatus(STATUS_ACTIVE);
        }
        if (item.getIsLeaf() == null) {
            item.setIsLeaf(true);
        }
        if (item.getSortOrder() == null) {
            item.setSortOrder(0);
        }
        dictItemMapper.insert(item);
        log.info("[字典管理] 字典项创建成功 id={}", item.getId());
        return item.getId();
    }

    /**
     * 修改字典项（只改 label/sortOrder/is_leaf，不改 item_code）
     *
     * 实现步骤：
     *   1. 校验项存在
     *   2. 更新 label/sortOrder/is_leaf
     *   3. 不改 item_code（保护历史 return_records 反查）
     *
     * @return 更新后的项
     */
    @Transactional
    public SysDictItem updateItem(Long id, String itemLabel, Integer sortOrder, Boolean isLeaf) {
        log.info("[字典管理] 修改字典项 id={}, label={}, sortOrder={}, isLeaf={}",
                id, itemLabel, sortOrder, isLeaf);

        // 步骤1：校验存在
        SysDictItem existing = dictItemMapper.selectById(id);
        if (existing == null) {
            throw new BizException(2104, "字典项不存在");
        }

        // 步骤2：更新（不改 item_code）
        LambdaUpdateWrapper<SysDictItem> update = new LambdaUpdateWrapper<SysDictItem>()
                .eq(SysDictItem::getId, id);
        if (itemLabel != null) {
            update.set(SysDictItem::getItemLabel, itemLabel);
        }
        if (sortOrder != null) {
            update.set(SysDictItem::getSortOrder, sortOrder);
        }
        if (isLeaf != null) {
            update.set(SysDictItem::getIsLeaf, isLeaf);
        }
        dictItemMapper.update(null, update);

        // 步骤3：返回最新值
        return dictItemMapper.selectById(id);
    }

    /**
     * 停用字典项（软删）
     *
     * 实现步骤：
     *   1. 校验项存在
     *   2. 设 status=disabled
     *   3. 若有子项，级联停用所有子项
     *
     * @return 影响行数（含级联）
     */
    @Transactional
    public int disableItem(Long id) {
        log.info("[字典管理] 停用字典项 id={}", id);

        // 步骤1：校验存在
        SysDictItem existing = dictItemMapper.selectById(id);
        if (existing == null) {
            throw new BizException(2104, "字典项不存在");
        }

        // 步骤2：停用本项
        int affected = dictItemMapper.update(null,
                new LambdaUpdateWrapper<SysDictItem>()
                        .eq(SysDictItem::getId, id)
                        .set(SysDictItem::getStatus, STATUS_DISABLED));

        // 步骤3：级联停用子项
        Long childCount = dictItemMapper.selectCount(
                new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getParentId, id)
                        .eq(SysDictItem::getStatus, STATUS_ACTIVE));
        if (childCount > 0) {
            int childAffected = dictItemMapper.update(null,
                    new LambdaUpdateWrapper<SysDictItem>()
                            .eq(SysDictItem::getParentId, id)
                            .eq(SysDictItem::getStatus, STATUS_ACTIVE)
                            .set(SysDictItem::getStatus, STATUS_DISABLED));
            affected += childAffected;
            log.info("[字典管理] 级联停用子项 {} 个", childAffected);
        }

        log.info("[字典管理] 停用完成，影响 {} 项", affected);
        return affected;
    }

    /**
     * 按 item_code 反查 item_label（LLM 返回 code 后转 label）
     *
     * 实现步骤：
     *   1. 按 dict_code 查 dict_id
     *   2. 按 dict_id + item_code 查活跃项
     *   3. 返回 label；查不到返回 null（调用方降级为"其他"）
     *
     * @param itemCode LLM 返回的分类编码
     * @return itemLabel 或 null
     */
    public String getItemLabelByCode(String itemCode) {
        if (itemCode == null || itemCode.isEmpty()) {
            return null;
        }
        log.info("[字典管理] 按 code 反查 label: {}", itemCode);

        // 步骤1：查 dict_id
        SysDict dict = dictMapper.selectOne(
                new LambdaQueryWrapper<SysDict>()
                        .eq(SysDict::getDictCode, DICT_CODE_RETURN_CATEGORY)
                        .eq(SysDict::getStatus, STATUS_ACTIVE));
        if (dict == null) {
            return null;
        }

        // 步骤2：查活跃项
        // 注意：用 selectList 取第一条而非 selectOne，防御历史脏数据（F08 早期 uk 含 parent_id=NULL 导致重复）
        List<SysDictItem> items = dictItemMapper.selectList(
                new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getDictId, dict.getId())
                        .eq(SysDictItem::getItemCode, itemCode)
                        .eq(SysDictItem::getStatus, STATUS_ACTIVE)
                        .orderByAsc(SysDictItem::getId)
                        .last("LIMIT 1"));
        if (items.isEmpty()) {
            log.warn("[字典管理] code={} 在活跃字典中未找到", itemCode);
            return null;
        }
        if (items.size() > 1) {
            log.warn("[字典管理] code={} 存在 {} 条重复记录，取第一条（请执行清理脚本）", itemCode, items.size());
        }

        return items.get(0).getItemLabel();
    }

    /** SysDictItem 转 Map（前端/LLM 友好的扁平结构） */
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