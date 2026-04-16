package com.scheduler.service;

import com.scheduler.domain.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Slf4j
@Service
public class ExcelParserService {

    private static final List<DateTimeFormatter> DT_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    );

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy")
    );

    public ParsedExcelData parse(MultipartFile file) {
        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            List<Order> orders = parseOrders(workbook);
            List<ProcessStep> processSteps = parseProcessSteps(workbook);
            Map<String, Line> linesMap = parseLines(workbook);

            enrichLines(workbook, linesMap);

            ParsedExcelData result = new ParsedExcelData();
            result.setOrders(orders);
            result.setProcessSteps(processSteps.isEmpty() ? getDefaultProcessSteps() : processSteps);
            result.setLinesMap(linesMap.isEmpty() ? getDefaultLines() : linesMap);
            return result;

        } catch (Exception e) {
            log.error("Failed to parse Excel: {}", e.getMessage(), e);
            throw new RuntimeException("Excel parse error: " + e.getMessage(), e);
        }
    }

    private void enrichLines(Workbook wb, Map<String, Line> linesMap) {
        // Shift overrides
        Sheet shifts = wb.getSheet("Shifts");
        if (shifts != null) {
            Iterator<Row> rows = shifts.rowIterator();
            if (rows.hasNext()) rows.next();
            while (rows.hasNext()) {
                Row row = rows.next();
                if (isRowEmpty(row)) continue;
                String lineId = getCellString(row, 0);
                if (!lineId.isBlank() && linesMap.containsKey(lineId)) {
                    ShiftOverride ov = new ShiftOverride();
                    ov.setDate(parseLocalDate(getCellString(row, 1)));
                    ov.setStartTime(getCellString(row, 2));
                    ov.setEndTime(getCellString(row, 3));
                    linesMap.get(lineId).getShiftOverrides().add(ov);
                }
            }
        }

        // Breaks
        Sheet breakSheet = wb.getSheet("Breaks");
        if (breakSheet != null) {
            Iterator<Row> rows = breakSheet.rowIterator();
            if (rows.hasNext()) rows.next();
            while (rows.hasNext()) {
                Row row = rows.next();
                if (isRowEmpty(row)) continue;
                String lineId = getCellString(row, 0);
                if (!lineId.isBlank() && linesMap.containsKey(lineId)) {
                    Break brk = new Break();
                    brk.setBreakStart(getCellString(row, 1));
                    brk.setBreakEnd(getCellString(row, 2));
                    brk.setDailyOccurrence((int) getCellNumeric(row, 3));
                    linesMap.get(lineId).getBreaks().add(brk);
                }
            }
        }

        // Downtime events
        Sheet dtSheet = wb.getSheet("DowntimeEvents");
        if (dtSheet != null) {
            Iterator<Row> rows = dtSheet.rowIterator();
            if (rows.hasNext()) rows.next();
            while (rows.hasNext()) {
                Row row = rows.next();
                if (isRowEmpty(row)) continue;
                String lineId = getCellString(row, 1);
                if (!lineId.isBlank() && linesMap.containsKey(lineId)) {
                    DowntimeEvent event = new DowntimeEvent();
                    event.setEventId(getCellString(row, 0));
                    event.setLineId(lineId);
                    event.setStartDateTime(parseLocalDateTime(getCellString(row, 2)));
                    event.setEndDateTime(parseLocalDateTime(getCellString(row, 3)));
                    event.setReason(getCellString(row, 4));
                    event.setType(getCellString(row, 5));
                    linesMap.get(lineId).getDowntimeEvents().add(event);
                }
            }
        }
    }

    private List<Order> parseOrders(Workbook wb) {
        Sheet sheet = wb.getSheet("Orders");
        if (sheet == null) throw new RuntimeException("'Orders' sheet not found in Excel file");
        List<Order> orders = new ArrayList<>();
        Iterator<Row> rows = sheet.rowIterator();
        if (rows.hasNext()) rows.next(); // skip header

        while (rows.hasNext()) {
            Row row = rows.next();
            if (isRowEmpty(row)) continue;
            try {
                Order order = new Order();
                order.setOrderId(getCellString(row, 0));
                order.setPanelType(getCellString(row, 1));
                order.setWattage((int) getCellNumeric(row, 2));
                order.setQuantity((int) getCellNumeric(row, 3));
                order.setOrderReceived(parseCellDateTime(row, 4));
                order.setDueDate(parseCellDateTime(row, 5));
                order.setPriority((int) getCellNumeric(row, 6));
                order.setCustomSetup(parseBool(getCellString(row, 7)));
                order.setStatus("PENDING");
                if (order.getOrderId() != null && !order.getOrderId().isBlank()) {
                    orders.add(order);
                }
            } catch (Exception e) {
                log.warn("Skipping row {} due to: {}", row.getRowNum(), e.getMessage());
            }
        }
        return orders;
    }

    private List<ProcessStep> parseProcessSteps(Workbook wb) {
        Sheet sheet = wb.getSheet("ProcessSteps");
        if (sheet == null) return new ArrayList<>();
        List<ProcessStep> steps = new ArrayList<>();
        Iterator<Row> rows = sheet.rowIterator();
        if (rows.hasNext()) rows.next();

        while (rows.hasNext()) {
            Row row = rows.next();
            if (isRowEmpty(row)) continue;
            ProcessStep step = new ProcessStep();
            step.setStepSeq((int) getCellNumeric(row, 0));
            step.setProcessName(getCellString(row, 1));
            step.setLineId(getCellString(row, 2));
            step.setSetupTimeMin((int) getCellNumeric(row, 3));
            step.setProcessingTimeMinPerUnit(getCellNumeric(row, 4));
            step.setBatchSize(Math.max(1, (int) getCellNumeric(row, 5)));
            step.setPrecedingStep((int) getCellNumeric(row, 6));
            step.setReconfigPenaltyMin(row.getLastCellNum() > 7 ? (int) getCellNumeric(row, 7) : 0);
            steps.add(step);
        }
        return steps;
    }

    private Map<String, Line> parseLines(Workbook wb) {
        Sheet sheet = wb.getSheet("Lines");
        if (sheet == null) return new HashMap<>();
        Map<String, Line> lines = new LinkedHashMap<>();
        Iterator<Row> rows = sheet.rowIterator();
        if (rows.hasNext()) rows.next();

        while (rows.hasNext()) {
            Row row = rows.next();
            if (isRowEmpty(row)) continue;
            Line line = new Line();
            String lineId = getCellString(row, 0);
            line.setLineId(lineId);
            line.setParallelCapacity(Math.max(1, (int) getCellNumeric(row, 1)));
            line.setShiftHoursPerDay((int) getCellNumeric(row, 2));
            line.setDefaultStart(getCellString(row, 3));
            line.setDefaultEnd(getCellString(row, 4));
            lines.put(lineId, line);
        }
        return lines;
    }

    // ---- Cell helpers ----

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                } else {
                    // Handle Excel time values stored as fraction of day (0.3333 = 08:00)
                    double val = cell.getNumericCellValue();
                    if (val >= 0 && val < 1) {
                        // This is likely a time value
                        yield convertExcelTimeToString(val);
                    } else {
                        // Normal number
                        yield (val == Math.floor(val)) ? String.valueOf((long) val) : String.valueOf(val);
                    }
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (Exception e) {
                    try {
                        yield String.valueOf(cell.getNumericCellValue());
                    } catch (Exception ex) {
                        yield "";
                    }
                }
            }
            default -> "";
        };
    }

    private double getCellNumeric(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return 0;
        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getNumericCellValue();
            case STRING -> {
                try {
                    yield Double.parseDouble(cell.getStringCellValue().trim());
                } catch (NumberFormatException e) {
                    yield 0;
                }
            }
            case FORMULA -> {
                try {
                    yield cell.getNumericCellValue();
                } catch (Exception e) {
                    yield 0;
                }
            }
            default -> 0;
        };
    }

    private LocalDateTime parseCellDateTime(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return LocalDateTime.now();
        // Numeric date cell (Excel stores dates as numbers)
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue();
        }
        // Try parsing string
        String s = getCellString(row, col);
        return parseLocalDateTime(s);
    }

    private LocalDateTime parseLocalDateTime(String s) {
        if (s == null || s.isBlank()) return LocalDateTime.now();
        // Handle ISO format with T separator
        String clean = s.replace("T", " ").replace("Z", "");
        for (DateTimeFormatter f : DT_FORMATTERS) {
            try {
                if (clean.length() <= 10) {
                    return LocalDate.parse(clean, f).atStartOfDay();
                }
                return LocalDateTime.parse(clean.substring(0, Math.min(clean.length(), 16)), f);
            } catch (DateTimeParseException ignored) {
            }
        }
        log.warn("Could not parse datetime: {}", s);
        return LocalDateTime.now();
    }

    private LocalDate parseLocalDate(String s) {
        if (s == null || s.isBlank()) return LocalDate.now();
        for (DateTimeFormatter f : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(s.trim().substring(0, 10), f);
            } catch (DateTimeParseException ignored) {
            }
        }
        return LocalDate.now();
    }

    private boolean parseBool(String s) {
        return "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s) || "1".equals(s);
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String v = getCellString(row, c);
                if (!v.isBlank()) return false;
            }
        }
        return true;
    }

    // ---- Defaults (used when sheets are missing) ----

    public List<ProcessStep> getDefaultProcessSteps() {
        List<ProcessStep> steps = new ArrayList<>();
        Object[][] data = {
                {1, "Order & Production Planning", "PLAN-LINE", 30, 0.1, 100},
                {2, "Silicon to Ingots & Wafers", "CELL-LINE", 60, 0.5, 50},
                {3, "Wafer to Solar Cell", "CELL-LINE", 45, 0.8, 50},
                {4, "Cell Testing & Sorting", "QC-LINE", 20, 0.2, 100},
                {5, "Cell Cutting (Half-Cut)", "CELL-LINE", 25, 0.3, 50},
                {6, "Stringing (Welding)", "ASSY-LINE", 30, 0.4, 50},
                {7, "Layup", "ASSY-LINE", 20, 0.4, 50},
                {8, "Pre-Lamination EL Test", "QC-LINE", 15, 0.15, 100},
                {9, "Lamination", "LAMI-LINE", 40, 0.6, 25},
                {10, "Framing", "FRAME-LINE", 25, 0.35, 50},
                {11, "Junction Box Attachment", "FINAL-LINE", 15, 0.25, 50},
                {12, "Final Performance Testing", "TEST-LINE", 20, 0.3, 100},
                {13, "Final EL & Safety Tests", "TEST-LINE", 10, 0.2, 100},
                {14, "Packaging & Dispatch", "PACK-LINE", 15, 0.2, 100},
        };
        for (Object[] d : data) {
            ProcessStep s = new ProcessStep();
            s.setStepSeq((Integer) d[0]);
            s.setProcessName((String) d[1]);
            s.setLineId((String) d[2]);
            s.setSetupTimeMin((Integer) d[3]);
            s.setProcessingTimeMinPerUnit((Double) d[4]);
            s.setBatchSize((Integer) d[5]);
            s.setPrecedingStep(s.getStepSeq() - 1);
            s.setReconfigPenaltyMin(15);
            steps.add(s);
        }
        return steps;
    }

    public Map<String, Line> getDefaultLines() {
        Map<String, Line> lines = new LinkedHashMap<>();
        Object[][] data = {
                {"PLAN-LINE", 2, "08:00", "20:00", "12:00", "12:30"},
                {"CELL-LINE", 2, "06:00", "22:00", "12:00", "12:30"},
                {"QC-LINE", 3, "08:00", "20:00", "12:00", "12:30"},
                {"ASSY-LINE", 4, "07:00", "21:00", "12:00", "12:30"},
                {"LAMI-LINE", 2, "06:00", "22:00", "12:00", "12:30"},
                {"FRAME-LINE", 3, "07:00", "21:00", "12:00", "12:30"},
                {"FINAL-LINE", 2, "08:00", "20:00", "12:00", "12:30"},
                {"TEST-LINE", 3, "08:00", "20:00", "12:00", "12:30"},
                {"PACK-LINE", 4, "07:00", "21:00", "12:00", "12:30"},
        };
        for (Object[] d : data) {
            Line line = new Line();
            line.setLineId((String) d[0]);
            line.setParallelCapacity((Integer) d[1]);
            line.setShiftHoursPerDay(Integer.parseInt(((String) d[3]).split(":")[0]) - Integer.parseInt(((String) d[2]).split(":")[0]));
            line.setDefaultStart((String) d[2]);
            line.setDefaultEnd((String) d[3]);
            // Add default lunch break
            Break lunchBreak = new Break();
            lunchBreak.setBreakStart((String) d[4]);
            lunchBreak.setBreakEnd((String) d[5]);
            lunchBreak.setDailyOccurrence(1);
            line.getBreaks().add(lunchBreak);
            lines.put(line.getLineId(), line);
        }
        return lines;
    }

    private String convertExcelTimeToString(double serialTime) {
        if (serialTime >= 0 && serialTime < 1) {
            int totalMinutes = (int) Math.round(serialTime * 24 * 60);
            int hours = totalMinutes / 60;
            int minutes = totalMinutes % 60;
            return String.format("%02d:%02d", hours, minutes);
        }
        return String.valueOf(serialTime);
    }
}