package com.docusphere.backend.admin.report.service;

import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.team.entity.Team;
import com.docusphere.backend.team.repository.TeamRepository;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AdminReportExportService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final DocumentRepository documentRepository;

    public AdminReportExportService(UserRepository userRepository,
                                   TeamRepository teamRepository,
                                   DocumentRepository documentRepository) {
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.documentRepository = documentRepository;
    }

    // ==================== USERS EXPORT ====================

    /**
     * Export all users to PDF
     */
    public byte[] exportUsersToPDF() throws DocumentException, IOException {
        com.itextpdf.text.Document document = new com.itextpdf.text.Document();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, baos);
        document.open();

        // Title
        com.itextpdf.text.Font titleFont = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 16, com.itextpdf.text.Font.BOLD);
        document.add(new Paragraph("Docusphere - Users Report", titleFont));
        document.add(new Paragraph("Generated: " + getCurrentDateTime(), new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 10)));
        document.add(new Paragraph("\n"));

        // Table
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        addTableHeader(table, new String[]{"User ID", "Name", "Email", "Role"});

        List<User> users = userRepository.findAll();
        for (User user : users) {
            table.addCell(user.getId().toString());
            table.addCell(user.getFullName() != null ? user.getFullName() : "N/A");
            table.addCell(user.getEmail());
            table.addCell(user.getRole() != null ? user.getRole().getName() : "USER");
        }

        document.add(table);
        document.close();

        return baos.toByteArray();
    }

    /**
     * Export all users to CSV
     */
    public byte[] exportUsersToCSV() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter osw = new OutputStreamWriter(baos);
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader("User ID", "Name", "Email", "Role").build();
        CSVPrinter csvPrinter = new CSVPrinter(osw, csvFormat);

        List<User> users = userRepository.findAll();
        for (User user : users) {
            csvPrinter.printRecord(
                user.getId().toString(),
                user.getFullName() != null ? user.getFullName() : "N/A",
                user.getEmail(),
                user.getRole() != null ? user.getRole().getName() : "USER"
            );
        }

        csvPrinter.flush();
        csvPrinter.close();
        osw.close();
        return baos.toByteArray();
    }

    // ==================== TEAMS EXPORT ====================

    /**
     * Export all teams to PDF
     */
    public byte[] exportTeamsToPDF() throws DocumentException, IOException {
        com.itextpdf.text.Document document = new com.itextpdf.text.Document();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, baos);
        document.open();

        com.itextpdf.text.Font titleFont = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 16, com.itextpdf.text.Font.BOLD);
        document.add(new Paragraph("Docusphere - Teams Report", titleFont));
        document.add(new Paragraph("Generated: " + getCurrentDateTime()));
        document.add(new Paragraph("\n"));

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        addTableHeader(table, new String[]{"Team ID", "Team Name", "Members", "Created Date"});

        List<Team> teams = teamRepository.findAll();
        for (Team team : teams) {
            table.addCell(team.getId().toString());
            table.addCell(team.getTeamName());
            table.addCell(String.valueOf(team.getMemberCount()));
            table.addCell(team.getCreatedAt() != null ? team.getCreatedAt().toString() : "N/A");
        }

        document.add(table);
        document.close();

        return baos.toByteArray();
    }

    /**
     * Export all teams to CSV
     */
    public byte[] exportTeamsToCSV() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter osw = new OutputStreamWriter(baos);
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader("Team ID", "Team Name", "Members", "Created Date").build();
        CSVPrinter csvPrinter = new CSVPrinter(osw, csvFormat);

        List<Team> teams = teamRepository.findAll();
        for (Team team : teams) {
            csvPrinter.printRecord(
                team.getId().toString(),
                team.getTeamName(),
                team.getMemberCount(),
                team.getCreatedAt() != null ? team.getCreatedAt().toString() : "N/A"
            );
        }

        csvPrinter.flush();
        csvPrinter.close();
        osw.close();
        return baos.toByteArray();
    }

    // ==================== DOCUMENTS EXPORT ====================

    /**
     * Export all documents to PDF
     */
    public byte[] exportDocumentsToPDF() throws DocumentException, IOException {
        com.itextpdf.text.Document document = new com.itextpdf.text.Document();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, baos);
        document.open();

        com.itextpdf.text.Font titleFont = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 16, com.itextpdf.text.Font.BOLD);
        document.add(new Paragraph("Docusphere - Documents Report", titleFont));
        document.add(new Paragraph("Generated: " + getCurrentDateTime()));
        document.add(new Paragraph("\n"));

        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        addTableHeader(table, new String[]{"Document ID", "Name", "Type", "Owner", "Team", "Created Date"});

        List<com.docusphere.backend.document.entity.Document> docs = documentRepository.findAll();
        for (com.docusphere.backend.document.entity.Document doc : docs) {
            table.addCell(doc.getId() != null ? doc.getId().toString() : "N/A");
            table.addCell(doc.getName() != null ? doc.getName() : "N/A");
            table.addCell(doc.getType() != null ? doc.getType() : "N/A");

            // Owner name
            String ownerName = (doc.getOwnerId() != null)
                    ? userRepository.findById(doc.getOwnerId())
                            .map(u -> u.getFullName() != null ? u.getFullName() : u.getEmail())
                            .orElse("N/A")
                    : "N/A";
            table.addCell(ownerName);

            // Team name
            String teamName = (doc.getTeamId() != null)
                    ? teamRepository.findById(doc.getTeamId()).map(t -> t.getTeamName()).orElse("N/A")
                    : "—";
            table.addCell(teamName);

            table.addCell(doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : "N/A");
        }

        document.add(table);
        document.close();

        return baos.toByteArray();
    }

    /**
     * Export all documents to CSV
     */
    public byte[] exportDocumentsToCSV() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter osw = new OutputStreamWriter(baos);
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader("Document ID", "Name", "Type", "Size (bytes)", "Owner Email", "Team", "Status", "Created Date").build();
        CSVPrinter csvPrinter = new CSVPrinter(osw, csvFormat);

        List<com.docusphere.backend.document.entity.Document> docs = documentRepository.findAll();
        for (com.docusphere.backend.document.entity.Document doc : docs) {
            String ownerEmail = (doc.getOwnerId() != null)
                    ? userRepository.findById(doc.getOwnerId()).map(u -> u.getEmail()).orElse("N/A")
                    : "N/A";
            String teamName = (doc.getTeamId() != null)
                    ? teamRepository.findById(doc.getTeamId()).map(t -> t.getTeamName()).orElse("N/A")
                    : "—";
            csvPrinter.printRecord(
                doc.getId() != null ? doc.getId().toString() : "N/A",
                doc.getName(),
                doc.getType(),
                doc.getSizeBytes(),
                ownerEmail,
                teamName,
                doc.getStatus() != null ? doc.getStatus().name() : "N/A",
                doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : "N/A"
            );
        }

        csvPrinter.flush();
        csvPrinter.close();
        osw.close();
        return baos.toByteArray();
    }

    // ==================== XLSX EXPORTS ====================

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    public byte[] exportUsersToXLSX() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Users");
            String[] headers = {"User ID", "Name", "Email", "Role"};
            CellStyle headerStyle = createHeaderStyle(wb);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            List<User> users = userRepository.findAll();
            int rowIdx = 1;
            for (User user : users) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(user.getId() != null ? user.getId().toString() : "");
                row.createCell(1).setCellValue(user.getFullName() != null ? user.getFullName() : "N/A");
                row.createCell(2).setCellValue(user.getEmail());
                row.createCell(3).setCellValue(user.getRole() != null ? user.getRole().getName() : "USER");
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        }
    }

    public byte[] exportTeamsToXLSX() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Teams");
            String[] headers = {"Team ID", "Team Name", "Members", "Created Date"};
            CellStyle headerStyle = createHeaderStyle(wb);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            List<Team> teams = teamRepository.findAll();
            int rowIdx = 1;
            for (Team team : teams) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(team.getId() != null ? team.getId().toString() : "");
                row.createCell(1).setCellValue(team.getTeamName());
                row.createCell(2).setCellValue(team.getMemberCount());
                row.createCell(3).setCellValue(team.getCreatedAt() != null ? team.getCreatedAt().toString() : "N/A");
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        }
    }

    public byte[] exportDocumentsToXLSX() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Documents");
            String[] headers = {"Document ID", "Name", "Type", "Size (bytes)", "Owner Email", "Team", "Status", "Created Date"};
            CellStyle headerStyle = createHeaderStyle(wb);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            List<com.docusphere.backend.document.entity.Document> docs = documentRepository.findAll();
            int rowIdx = 1;
            for (com.docusphere.backend.document.entity.Document doc : docs) {
                String ownerEmail = (doc.getOwnerId() != null)
                        ? userRepository.findById(doc.getOwnerId()).map(User::getEmail).orElse("N/A")
                        : "N/A";
                String teamName = (doc.getTeamId() != null)
                        ? teamRepository.findById(doc.getTeamId()).map(t -> t.getTeamName()).orElse("N/A") : "—";
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(doc.getId() != null ? doc.getId().toString() : "");
                row.createCell(1).setCellValue(doc.getName() != null ? doc.getName() : "");
                row.createCell(2).setCellValue(doc.getType() != null ? doc.getType() : "");
                row.createCell(3).setCellValue(doc.getSizeBytes() != null ? doc.getSizeBytes() : 0);
                row.createCell(4).setCellValue(ownerEmail);
                row.createCell(5).setCellValue(teamName);
                row.createCell(6).setCellValue(doc.getStatus() != null ? doc.getStatus().name() : "");
                row.createCell(7).setCellValue(doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : "");
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        }
    }

    // ==================== HELPER METHODS ====================

    private void addTableHeader(PdfPTable table, String[] headers) {
        com.itextpdf.text.Font headerFont = new com.itextpdf.text.Font(com.itextpdf.text.Font.FontFamily.HELVETICA, 11, com.itextpdf.text.Font.BOLD);
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
            cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
            table.addCell(cell);
        }
    }

    private String getCurrentDateTime() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return LocalDateTime.now().format(formatter);
    }
}
