package tech.jxing.returnvision.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.jxing.returnvision.common.exception.BizException;
import tech.jxing.returnvision.model.entity.SysDict;
import tech.jxing.returnvision.model.entity.SysDictItem;
import tech.jxing.returnvision.model.mapper.SysDictItemMapper;
import tech.jxing.returnvision.model.mapper.SysDictMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 【测试类】DictService 单元测试（纯逻辑分支,不依赖 LambdaWrapper 的部分）
 * <p>
 * 1. Mockito mock SysDictMapper / SysDictItemMapper
 * 2. 覆盖 listActiveCategories(查询路径) / createItem 校验路径 / getItemLabelByCode / listActiveLeafItems
 * 3. 对应清单：test-checklists/2026-07-21_F08-退货字典.md AT-01~AT-05, AT-08, AT-12, AT-19
 * 4. 注：涉及 LambdaUpdateWrapper 的 updateItem/disableItem 放 DictServiceIntegrationTest（@SpringBootTest + H2）
 * </p>
 *
 * @author ReturnVision
 */
class DictServiceTest {

    private SysDictMapper dictMapper;
    private SysDictItemMapper dictItemMapper;
    private DictService service;

    private static final Long DICT_ID = 1L;
    private static final String DICT_CODE = "return_category";

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        dictMapper = mock(SysDictMapper.class);
        dictItemMapper = mock(SysDictItemMapper.class);
        service = new DictService(dictMapper, dictItemMapper);
    }

    private SysDict activeDict() {
        SysDict d = new SysDict();
        d.setId(DICT_ID);
        d.setDictCode(DICT_CODE);
        d.setStatus("active");
        return d;
    }

    private SysDictItem leafItem(Long id, String code, String label, int sort) {
        SysDictItem item = new SysDictItem();
        item.setId(id);
        item.setDictId(DICT_ID);
        item.setParentId(null);
        item.setItemCode(code);
        item.setItemLabel(label);
        item.setIsLeaf(true);
        item.setSortOrder(sort);
        item.setStatus("active");
        return item;
    }

    // ============ listActiveCategories（AT-01~AT-05） ============

    @Test
    @DisplayName("AT-01：返回 active 字典项（mock 已排序，Service 依赖 DB 排序）")
    void listActiveCategories_shouldReturnItems() {
        when(dictMapper.selectOne(any())).thenReturn(activeDict());
        // mock 返回已按 sort_order 排序的 List（DB 排序的模拟）
        when(dictItemMapper.selectList(any())).thenReturn(List.of(
                leafItem(1L, "QUALITY", "质量问题", 1),
                leafItem(2L, "LOGISTICS", "物流问题", 2),
                leafItem(3L, "SIZE", "尺寸不符", 3)
        ));

        Map<String, Object> result = service.listActiveCategories();

        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertEquals(3, items.size());
        assertEquals("QUALITY", items.get(0).get("item_code"));
        assertEquals("LOGISTICS", items.get(1).get("item_code"));
        assertEquals("SIZE", items.get(2).get("item_code"));
    }

    @Test
    @DisplayName("AT-02：status=disabled 的项不返回（Service 查询已过滤）")
    void listActiveCategories_shouldFilterDisabled() {
        when(dictMapper.selectOne(any())).thenReturn(activeDict());
        when(dictItemMapper.selectList(any())).thenReturn(List.of(
                leafItem(1L, "QUALITY", "质量问题", 1)
        ));

        Map<String, Object> result = service.listActiveCategories();
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertEquals(1, items.size());
        assertEquals("active", items.get(0).get("status"));
    }

    @Test
    @DisplayName("AT-03/AT-04：两级结构正确组装（一级含 children）")
    void listActiveCategories_shouldAssembleTwoLevelTree() {
        when(dictMapper.selectOne(any())).thenReturn(activeDict());
        SysDictItem parent = leafItem(1L, "QUALITY", "质量问题", 1);
        parent.setIsLeaf(false);
        SysDictItem child = leafItem(11L, "QUALITY_BROKEN", "破损", 1);
        child.setParentId(1L);
        when(dictItemMapper.selectList(any())).thenReturn(List.of(parent, child));

        Map<String, Object> result = service.listActiveCategories();
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertEquals(1, items.size());
        assertEquals("QUALITY", items.get(0).get("item_code"));
        List<Map<String, Object>> children = (List<Map<String, Object>>) items.get(0).get("children");
        assertEquals(1, children.size());
        assertEquals("QUALITY_BROKEN", children.get(0).get("item_code"));
    }

    @Test
    @DisplayName("AT-05：字典为空（dict 不存在） -> 返回空列表")
    void listActiveCategories_dictMissing_shouldReturnEmpty() {
        when(dictMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> result = service.listActiveCategories();
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertTrue(items.isEmpty());
    }

    @Test
    @DisplayName("AT-05b：字典存在但无 active 项 -> 返回空列表")
    void listActiveCategories_noActiveItems_shouldReturnEmpty() {
        when(dictMapper.selectOne(any())).thenReturn(activeDict());
        when(dictItemMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = service.listActiveCategories();
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertTrue(items.isEmpty());
    }

    // ============ createItem（AT-08 校验路径,AT-06/07 在集成测试） ============

    @Test
    @DisplayName("AT-08：item_code 同层级重复 -> 抛 BizException(2102)")
    void createItem_duplicateCode_shouldThrow() {
        when(dictMapper.selectById(DICT_ID)).thenReturn(activeDict());
        when(dictItemMapper.selectCount(any())).thenReturn(1L);

        SysDictItem item = leafItem(null, "QUALITY", "质量问题", 1);
        BizException ex = assertThrows(BizException.class, () -> service.createItem(item));
        assertEquals(2102, ex.getCode());
    }

    @Test
    @DisplayName("AT-08b：必填字段缺失 -> 抛 BizException(2100)")
    void createItem_missingFields_shouldThrow() {
        SysDictItem item = new SysDictItem();
        item.setDictId(null);
        BizException ex = assertThrows(BizException.class, () -> service.createItem(item));
        assertEquals(2100, ex.getCode());
    }

    @Test
    @DisplayName("AT-08c：dict_id 不存在 -> 抛 BizException(2101)")
    void createItem_dictNotExist_shouldThrow() {
        when(dictMapper.selectById(999L)).thenReturn(null);
        SysDictItem item = leafItem(null, "NEW", "新项", 1);
        item.setDictId(999L);
        BizException ex = assertThrows(BizException.class, () -> service.createItem(item));
        assertEquals(2101, ex.getCode());
    }

    // ============ getItemLabelByCode（AT-12） ============

    @Test
    @DisplayName("AT-12：按 code 反查 label（LLM 返回 code 后转 label）")
    void getItemLabelByCode_shouldReturnLabel() {
        when(dictMapper.selectOne(any())).thenReturn(activeDict());
        SysDictItem item = leafItem(1L, "QUALITY", "质量问题", 1);
        when(dictItemMapper.selectList(any())).thenReturn(List.of(item));

        String label = service.getItemLabelByCode("QUALITY");
        assertEquals("质量问题", label);
    }

    @Test
    @DisplayName("AT-12b：code 不在活跃字典中 -> 返回 null（调用方降级'其他'）")
    void getItemLabelByCode_notFound_shouldReturnNull() {
        when(dictMapper.selectOne(any())).thenReturn(activeDict());
        when(dictItemMapper.selectList(any())).thenReturn(List.of());

        String label = service.getItemLabelByCode("UNKNOWN");
        assertNull(label);
    }

    @Test
    @DisplayName("AT-12c：code 为 null/空 -> 返回 null（不查 DB）")
    void getItemLabelByCode_emptyInput_shouldReturnNullWithoutDb() {
        assertNull(service.getItemLabelByCode(null));
        assertNull(service.getItemLabelByCode(""));
        verifyNoInteractions(dictMapper);
        verifyNoInteractions(dictItemMapper);
    }

    // ============ listActiveLeafItems（供 LLM 用,AT-19 边界） ============

    @Test
    @DisplayName("AT-19：字典叶子项数量边界 - 50 个叶子项正常返回")
    void listActiveLeafItems_manyItems_shouldReturnAll() {
        when(dictMapper.selectOne(any())).thenReturn(activeDict());
        List<SysDictItem> items = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            items.add(leafItem((long) i, "CODE_" + i, "标签" + i, i));
        }
        when(dictItemMapper.selectList(any())).thenReturn(items);

        List<Map<String, Object>> result = service.listActiveLeafItems();
        assertEquals(50, result.size());
    }

    @Test
    @DisplayName("AT-19b：字典为空 -> listActiveLeafItems 返回空列表（LLM 走硬编码兜底）")
    void listActiveLeafItems_emptyDict_shouldReturnEmpty() {
        when(dictMapper.selectOne(any())).thenReturn(null);
        List<Map<String, Object>> result = service.listActiveLeafItems();
        assertTrue(result.isEmpty());
    }
}