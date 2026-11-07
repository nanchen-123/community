package cn.sju.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 按文件类型抽取纯文本（AI 总结报告的前置步骤）
 * 只做文本抽取，不做 OCR；扫描件 PDF / 图片抽取结果为空
 */
@Slf4j
@Component
public class AiFileTextExtractor {

    /**
     * 抽取文本
     *
     * @param path 文件路径
     * @param ext  小写扩展名（docx/pdf/txt/md/csv/xlsx）
     */
    public String extract(Path path, String ext) throws IOException {
        String text = switch (ext) {
            case "txt", "md", "csv" -> readPlainText(path);
            case "docx" -> extractDocx(path);
            case "xlsx" -> extractXlsx(path);
            case "pdf" -> extractPdf(path);
            default -> throw new IllegalArgumentException("暂不支持该文件类型：" + ext);
        };
        return normalize(text);
    }

    /** 纯文本：先按 UTF-8 读，失败再按 GBK 兜底（老文档常见） */
    private String readPlainText(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (utf8.indexOf('\uFFFD') >= 0) {
            return new String(bytes, java.nio.charset.Charset.forName("GBK"));
        }
        return utf8;
    }

    /** Word：段落 + 表格（表格转成「a | b | c」行，避免结构丢失） */
    private String extractDocx(Path path) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = Files.newInputStream(path); XWPFDocument doc = new XWPFDocument(in)) {
            for (XWPFParagraph paragraph : doc.getParagraphs()) {
                String line = paragraph.getText();
                if (line != null && !line.isBlank()) {
                    sb.append(line.strip()).append('\n');
                }
            }
            for (XWPFTable table : doc.getTables()) {
                sb.append("\n[表格]\n");
                for (XWPFTableRow row : table.getRows()) {
                    List<XWPFTableCell> cells = row.getTableCells();
                    for (int i = 0; i < cells.size(); i++) {
                        if (i > 0) {
                            sb.append(" | ");
                        }
                        String cellText = cells.get(i).getText();
                        sb.append(cellText == null ? "" : cellText.replace('\n', ' ').strip());
                    }
                    sb.append('\n');
                }
            }
        }
        return sb.toString();
    }

    /** Excel：每个 sheet 逐行转文本，单元格用「 | 」分隔 */
    private String extractXlsx(Path path) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = Files.newInputStream(path); Workbook workbook = new XSSFWorkbook(in)) {
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                sb.append("[工作表] ").append(sheet.getSheetName()).append('\n');
                for (Row row : sheet) {
                    StringBuilder line = new StringBuilder();
                    for (Cell cell : row) {
                        if (line.length() > 0) {
                            line.append(" | ");
                        }
                        line.append(cellText(cell));
                    }
                    if (line.length() > 0) {
                        sb.append(line).append('\n');
                    }
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private String cellText(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().strip();
            case NUMERIC -> {
                double value = cell.getNumericCellValue();
                yield value == Math.floor(value) && !Double.isInfinite(value)
                        ? String.valueOf((long) value)
                        : String.valueOf(value);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> "";
        };
    }

    /** PDF：逐页抽取，保留页码分隔，方便模型引用页码 */
    private String extractPdf(Path path) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (PDDocument doc = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            int pages = doc.getNumberOfPages();
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(doc);
                if (pageText != null && !pageText.isBlank()) {
                    sb.append("[第").append(page).append("页]\n").append(pageText.strip()).append('\n');
                }
            }
        }
        return sb.toString();
    }

    /** 统一换行、压缩空行，去掉零宽字符 */
    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\u00A0", " ")
                .replaceAll("[ \\t]{2,}", " ")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }
}
