package tech.jxing.returnvision.export;

import org.apache.poi.ss.usermodel.Header;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import tech.jxing.returnvision.model.entity.ReturnRecord;

/**
 * 【导出管控模块】Excel 水印工具（F02）
 *
 * 职责：生成带水印的 Excel（页眉含导出人+时间+IP+条数）
 * 层级：export 层
 * 关联：docs/04 第 4.9.7 节
 *
 * 水印策略：用页眉不用背景水印（实现简单、每页都有、溯源效果一样）
 */
public class ExcelWatermarkUtil {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 生成带水印的退货记录 Excel
     *
     * 实现步骤：
     *   1. 创建 Workbook 和 Sheet
     *   2. 设置页眉水印
     *   3. 写表头
     *   4. 写数据行
     *   5. 转 byte[]
     *
     * @param records    退货记录列表
     * @param exportUser 导出人用户名（username + display_name）
     * @param exportIp   导出人 IP
     * @return Excel 文件字节数组
     */
    public static byte[] buildReturnRecordsExcel(List<ReturnRecord> records, String exportUser, String exportIp) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("退货记录");

            // 步骤2：设置页眉水印
            String watermark = String.format("导出人：%s | 时间：%s | IP：%s | 条数：%d",
                    exportUser,
                    LocalDateTime.now().format(FMT),
                    exportIp,
                    records.size());
            Header header = sheet.getHeader();
            header.setLeft(watermark);
            header.setRight("退运智录 · 内部数据");

            // 步骤3：写表头
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            String[] columns = {"ID", "运单号", "收件人", "收件电话", "收件地址",
                    "寄件人", "寄件电话", "快递公司", "托寄物", "退货日期", "状态", "创建时间"};
            for (int i = 0; i < columns.length; i++) {
                headerRow.createCell(i).setCellValue(columns[i]);
            }

            // 步骤4：写数据行
            for (int i = 0; i < records.size(); i++) {
                ReturnRecord r = records.get(i);
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(r.getId() != null ? r.getId() : 0);
                row.createCell(1).setCellValue(safe(r.getWaybillNo()));
                row.createCell(2).setCellValue(safe(r.getRecName()));
                row.createCell(3).setCellValue(safe(r.getRecPhone()));
                row.createCell(4).setCellValue(safe(r.getRecAddress()));
                row.createCell(5).setCellValue(safe(r.getSenderName()));
                row.createCell(6).setCellValue(safe(r.getSenderPhone()));
                row.createCell(7).setCellValue(safe(r.getExpressCompany()));
                row.createCell(8).setCellValue(safe(r.getGoods()));
                row.createCell(9).setCellValue(r.getReturnDate() != null ? r.getReturnDate().toString() : "");
                row.createCell(10).setCellValue(safe(r.getStatus()));
                row.createCell(11).setCellValue(r.getCreatedAt() != null ? r.getCreatedAt().format(FMT) : "");
            }

            // 步骤5：自动列宽
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("生成 Excel 失败：" + e.getMessage(), e);
        }
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }
}
