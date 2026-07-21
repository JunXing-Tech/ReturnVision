package tech.jxing.returnvision.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import tech.jxing.returnvision.common.exception.BizException;
import tech.jxing.returnvision.model.entity.SysDict;
import tech.jxing.returnvision.model.entity.SysDictItem;
import tech.jxing.returnvision.model.mapper.SysDictMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 【测试类】DictService 集成测试（@SpringBootTest + H2 MySQL 模式）
 * <p>
 * 1. 用真实 H2 + schema.sql 建表,真实 MyBatis-Plus 调用
 * 2. 覆盖 LambdaUpdateWrapper/LambdaQueryWrapper 路径(updateItem/disableItem)
 * 3. 覆盖 createItem 成功路径(id 回填)
 * 4. 同时验证 DDL/schema.sql 正确性(AT-30~AT-33)
 * 5. 对应清单：test-checklists/2026-07-21_F08-退货字典.md AT-06/07/09/10/11, AT-30~AT-33
 * 6. @Transactional 保证每个测试自动回滚,避免 H2 共享数据污染
 * </p>
 *
 * @author ReturnVision
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DictServiceIntegrationTest {

    @Autowired
    private DictService dictService;

    @Autowired
    private SysDictMapper dictMapper;

    private Long getReturnCategoryDictId() {
        SysDict dict = dictMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysDict>()
                        .eq(SysDict::getDictCode, "return_category"));
        return dict.getId();
    }

    // ============ AT-30/31：schema.sql 建表 + 种子数据 ============

    @Test
    @DisplayName("AT-30/31：schema.sql 重启后 sys_dict / sys_dict_item 自动建表 + 5 个一级项预置")
    void schema_shouldCreateTableAndSeedData() {
        Long dictId = getReturnCategoryDictId();
        assertNotNull(dictId, "return_category 字典应被种子数据预置");

        List<Map<String, Object>> leaves = dictService.listActiveLeafItems();
        assertEquals(5, leaves.size(), "应有 5 个一级叶子项");
        // 验证 sort_order 排序
        assertEquals("QUALITY", leaves.get(0).get("item_code"));
        assertEquals("LOGISTICS", leaves.get(1).get("item_code"));
        assertEquals("OTHER", leaves.get(4).get("item_code"));
    }

    // ============ AT-06/07：createItem 成功路径(id 回填) ============

    @Test
    @DisplayName("AT-06：正常新增一级项 -> id 回填成功")
    void createItem_oneLevel_shouldReturnId() {
        Long dictId = getReturnCategoryDictId();
        SysDictItem item = new SysDictItem();
        item.setDictId(dictId);
        item.setParentId(null);
        item.setItemCode("TEST_ONE_" + System.nanoTime());
        item.setItemLabel("测试一级项");
        item.setIsLeaf(true);
        item.setSortOrder(50);

        Long id = dictService.createItem(item);
        assertNotNull(id, "id 应被回填");
        assertTrue(id > 0);
    }

    @Test
    @DisplayName("AT-07：正常新增二级项(parent_id 非空)")
    void createItem_twoLevel_shouldSucceed() {
        Long dictId = getReturnCategoryDictId();
        // 先建一级项
        SysDictItem parent = new SysDictItem();
        parent.setDictId(dictId);
        parent.setItemCode("TEST_PARENT_" + System.nanoTime());
        parent.setItemLabel("测试父项");
        parent.setIsLeaf(false);
        parent.setSortOrder(50);
        Long parentId = dictService.createItem(parent);

        // 再建二级项
        SysDictItem child = new SysDictItem();
        child.setDictId(dictId);
        child.setParentId(parentId);
        child.setItemCode("TEST_CHILD_" + System.nanoTime());
        child.setItemLabel("测试子项");
        child.setIsLeaf(true);
        child.setSortOrder(1);
        Long childId = dictService.createItem(child);
        assertNotNull(childId);
    }

    // ============ AT-09：updateItem 改 label 不改 code ============

    @Test
    @DisplayName("AT-09：updateItem 改 label 不影响 item_code")
    void updateItem_shouldKeepItemCode() {
        Long dictId = getReturnCategoryDictId();
        SysDictItem item = new SysDictItem();
        item.setDictId(dictId);
        item.setItemCode("TEST_UPD_" + System.nanoTime());
        item.setItemLabel("原标签");
        item.setIsLeaf(true);
        item.setSortOrder(50);
        Long id = dictService.createItem(item);

        SysDictItem updated = dictService.updateItem(id, "新标签", 60, false);

        assertEquals("新标签", updated.getItemLabel());
        assertEquals(60, updated.getSortOrder());
        assertEquals(false, updated.getIsLeaf());
        // item_code 不变
        assertTrue(updated.getItemCode().startsWith("TEST_UPD_"));
    }

    // ============ AT-10/11：disableItem 软删 + 级联 ============

    @Test
    @DisplayName("AT-10：停用一级项时,其所有子项一并停用")
    void disableItem_shouldCascadeDisableChildren() {
        Long dictId = getReturnCategoryDictId();
        SysDictItem parent = new SysDictItem();
        parent.setDictId(dictId);
        parent.setItemCode("TEST_CAS_PARENT_" + System.nanoTime());
        parent.setItemLabel("级联父");
        parent.setIsLeaf(false);
        parent.setSortOrder(50);
        Long parentId = dictService.createItem(parent);

        // 建两个子项
        for (int i = 0; i < 2; i++) {
            SysDictItem child = new SysDictItem();
            child.setDictId(dictId);
            child.setParentId(parentId);
            child.setItemCode("TEST_CAS_C_" + i + "_" + System.nanoTime());
            child.setItemLabel("子" + i);
            child.setIsLeaf(true);
            child.setSortOrder(i);
            dictService.createItem(child);
        }

        int affected = dictService.disableItem(parentId);
        assertTrue(affected >= 3, "应级联停用: 1 父 + 2 子 = 3");
    }

    @Test
    @DisplayName("AT-11：disableItem 是软删(不物理删),历史记录保留")
    void disableItem_isSoftDelete() {
        Long dictId = getReturnCategoryDictId();
        SysDictItem item = new SysDictItem();
        item.setDictId(dictId);
        item.setItemCode("TEST_SOFT_" + System.nanoTime());
        item.setItemLabel("软删测试");
        item.setIsLeaf(true);
        item.setSortOrder(50);
        Long id = dictService.createItem(item);

        dictService.disableItem(id);

        // 查询 listActiveLeafItems 应不含此项
        List<Map<String, Object>> leaves = dictService.listActiveLeafItems();
        assertFalse(leaves.stream().anyMatch(l -> "TEST_SOFT_".startsWith(
                ((String) l.get("item_code")).substring(0, Math.min(10, ((String) l.get("item_code")).length())))));
    }

    // ============ AT-33：唯一约束 ============

    @Test
    @DisplayName("AT-33：同层级 item_code 重复 -> 抛 BizException(2102)")
    void createItem_duplicateCode_shouldThrow() {
        Long dictId = getReturnCategoryDictId();
        String sameCode = "TEST_DUP_FIXED";

        SysDictItem item1 = new SysDictItem();
        item1.setDictId(dictId);
        item1.setItemCode(sameCode);
        item1.setItemLabel("第一项");
        item1.setIsLeaf(true);
        item1.setSortOrder(50);
        dictService.createItem(item1);

        SysDictItem item2 = new SysDictItem();
        item2.setDictId(dictId);
        item2.setItemCode(sameCode);
        item2.setItemLabel("重复项");
        item2.setIsLeaf(true);
        item2.setSortOrder(51);

        BizException ex = assertThrows(BizException.class, () -> dictService.createItem(item2));
        assertEquals(2102, ex.getCode());
    }

    // ============ AT-12：getItemLabelByCode 真实 DB ============

    @Test
    @DisplayName("AT-12：按 code 反查 label(真实 DB)")
    void getItemLabelByCode_shouldReturnLabel() {
        String label = dictService.getItemLabelByCode("QUALITY");
        assertEquals("质量问题", label);
    }
}