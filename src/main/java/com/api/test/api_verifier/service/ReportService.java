package com.api.test.api_verifier.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.PdfPageEventHelper;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

@Service
public class ReportService {

    private static final String directory = Paths.get(System.getProperty("user.home"), "testiq", "report").toString();

    @SuppressWarnings("unchecked")
    public byte[] generatePdfReport(Map<String, Object> data) throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 60, 40);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);

        List<Map<String, Object>> csvRows = (List<Map<String, Object>>) data.get("csvRows");

        writer.setPageEvent(new PdfPageEventHelper() {
            Font headerFont = new Font(Font.HELVETICA, 11, Font.BOLD);
            Font footerFont = new Font(Font.HELVETICA, 10);

            @Override
            public void onEndPage(PdfWriter writer, Document document) {
                PdfContentByte cb = writer.getDirectContent();
                int page = writer.getPageNumber();
                Rectangle rect = document.getPageSize();

                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase("Testiq-Lite - Test Execution Report", headerFont), rect.getLeft() + 40, rect.getTop() - 30, 0);
                ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, new Phrase(String.valueOf(page), footerFont), (rect.getLeft() + rect.getRight()) / 2, rect.getBottom() + 25, 0);
            }
        });

        doc.open();

        Font bigTitle = new Font(Font.HELVETICA, 26, Font.BOLD);
        Font normal = new Font(Font.HELVETICA, 12);

        Paragraph title = new Paragraph("Testiq-Lite - Test Execution Report", bigTitle);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);

        Paragraph date = new Paragraph("Generated: " + new Date(), normal);
        date.setAlignment(Element.ALIGN_CENTER);

        doc.add(title);
        doc.add(date);
        doc.add(Chunk.NEWLINE);
        doc.add(Chunk.NEWLINE);
        doc.add(Chunk.NEWLINE);

        Font tocTitle = new Font(Font.HELVETICA, 20, Font.BOLD);
        Paragraph tocHeading = new Paragraph("TABLE OF CONTENTS:", tocTitle);
        tocHeading.setSpacingAfter(20);
        doc.add(tocHeading);

        Chunk inputDetailsLink = new Chunk("1. Input Details Provided by User", normal);
        inputDetailsLink.setLocalGoto("inputdetails");
        doc.add(inputDetailsLink);
        doc.add(Chunk.NEWLINE);

        Chunk overallLink = new Chunk("2. Overall Summary", normal);
        overallLink.setLocalGoto("overall");
        doc.add(overallLink);
        doc.add(Chunk.NEWLINE);

        Chunk accountLink = new Chunk("3. Account Level Summary", normal);
        accountLink.setLocalGoto("accountlevel");
        doc.add(accountLink);
        doc.add(Chunk.NEWLINE);

        Chunk suiteLink = new Chunk("4. Testsuite Details", normal);
        suiteLink.setLocalGoto("suites");
        doc.add(suiteLink);

        doc.newPage();

        Chunk userInputDest = new Chunk("INPUT DETAILS PROVIDED BY USER", new Font(Font.HELVETICA, 18, Font.BOLD));
        userInputDest.setLocalDestination("inputdetails");
        Paragraph inputTitle = new Paragraph(userInputDest);
        inputTitle.setSpacingAfter(15);
        doc.add(inputTitle);

        List<String> inputHeaders = new java.util.ArrayList<>();

        if (!csvRows.isEmpty()) {
            Map<String, Object> firstRow = csvRows.get(0);
            inputHeaders.addAll(firstRow.keySet());
        }

        PdfPTable inputTable = new PdfPTable(inputHeaders.size());
        inputTable.setWidthPercentage(100);

        for (String header : inputHeaders) {
            addHeaderCell(inputTable, header);
        }

        for (Map<String, Object> row : csvRows) {
            for (String header : inputHeaders) {
                Object val = row.get(header);
                String cellText = val == null ? "-" : val.toString();

                if (cellText.length() > 50) {
                    cellText = cellText.substring(0, 50) + "...";
                }

                inputTable.addCell(cellText);
            }
        }
        doc.add(inputTable);
        doc.newPage();

        Chunk overallDest = new Chunk("OVERALL SUMMARY", new Font(Font.HELVETICA, 18, Font.BOLD));
        overallDest.setLocalDestination("overall");
        Paragraph overallTitle = new Paragraph(overallDest);
        overallTitle.setSpacingAfter(15);
        doc.add(overallTitle);

        Map<String, Object> overall = (Map<String, Object>) data.get("overall");
        doc.add(new Paragraph("Total Test Suites: " + overall.get("totalSuites")));
        doc.add(new Paragraph("Total Cookies: " + overall.get("totalCookies")));
        doc.add(new Paragraph("Total Testcases: " + overall.get("uniqueTestcases")));
        doc.add(new Paragraph("Passed Testcases: " + overall.get("passedTestcases")));
        doc.add(new Paragraph("Failed Testcases: " + overall.get("failedTestcases")));
        doc.add(new Paragraph("Skipped Testcases: " + overall.get("skippedTestcases")));
        doc.add(new Paragraph("Total Executions: " + overall.get("totalExecutionsObserved")));
        doc.add(new Paragraph("Passed Executions: " + overall.get("passedExecutionsObserved")));
        doc.add(new Paragraph("Failed Executions: " + overall.get("failedExecutionsObserved")));
        doc.add(new Paragraph("Skipped Executions: " + overall.get("skippedExecutionsObserved")));
        doc.add(new Paragraph("Pass Rate: " + overall.get("executionPassRateObserved") + "%"));
        doc.add(new Paragraph("Total Execution Time: " + overall.get("executionTimeSec") + "s"));
        doc.add(Chunk.NEWLINE);
        doc.add(Chunk.NEWLINE);

        int totalExecution = (int) overall.get("totalExecutionsObserved");
        int passedExecution = (int) overall.get("passedExecutionsObserved");
        int failedExecution = (int) overall.get("failedExecutionsObserved");

        BufferedImage chartImg = generatePieChart(passedExecution, failedExecution);
        ByteArrayOutputStream chartBaos = new ByteArrayOutputStream();
        ImageIO.write(chartImg, "png", chartBaos);
        com.lowagie.text.Image chartImage = com.lowagie.text.Image.getInstance(chartBaos.toByteArray());
        chartImage.scaleToFit(400, 250);
        chartImage.setAlignment(Element.ALIGN_CENTER);
        doc.add(chartImage);
        doc.add(Chunk.NEWLINE);
        doc.newPage();

        Chunk accDest = new Chunk("ACCOUNT LEVEL SUMMARY", new Font(Font.HELVETICA, 18, Font.BOLD));
        accDest.setLocalDestination("accountlevel");
        Paragraph accTitle = new Paragraph(accDest);
        accTitle.setSpacingAfter(15);
        doc.add(accTitle);

        Map<String, Object> cookies = (Map<String, Object>) data.get("cookies");
        Map<String, Double> cookieExecutionTimeMap = new HashMap<>();

        Map<String, Object> details = (Map<String, Object>) data.get("details");

        for (Object suiteObj : details.values()) {
            List<Map<String, Object>> cookieEntries = (List<Map<String, Object>>) suiteObj;

            for (Map<String, Object> cookieEntry : cookieEntries) {
                String uid = (String) cookieEntry.get("uniqueId");

                if (!cookieExecutionTimeMap.containsKey(uid)) {
                    Object secObj = cookieEntry.get("executionTimeSec");
                    if (secObj instanceof Number) {
                        cookieExecutionTimeMap.put(uid, ((Number) secObj).doubleValue());
                    }
                }
            }
        }

        PdfPTable cookieTable = new PdfPTable(5);
        cookieTable.setWidthPercentage(100);

        addHeaderCell(cookieTable, "Account Name");
        addHeaderCell(cookieTable, "Passed");
        addHeaderCell(cookieTable, "Failed");
        addHeaderCell(cookieTable, "Execution Time (s)");
        addHeaderCell(cookieTable, "Suites");

        for (String cookieId : cookies.keySet()) {
            Map<String, Object> c = (Map<String, Object>) cookies.get(cookieId);

            String cookieName = (String) c.get("cookieName");
            int passed = (int) c.get("passed");
            int failed = (int) c.get("failed");
            double execTime = cookieExecutionTimeMap.getOrDefault(cookieId, 0.0);
            List<String> suiteList = (List<String>) c.get("suites");

            cookieTable.addCell(cookieName);
            cookieTable.addCell(String.valueOf(passed));
            cookieTable.addCell(String.valueOf(failed));
            cookieTable.addCell(String.format("%.3f s", execTime));
            cookieTable.addCell(String.join(", ", suiteList));

        }

        doc.add(cookieTable);
        doc.newPage();

        BufferedImage barChart = generateCookiePassBarChart(cookies);
        ByteArrayOutputStream chartBaos1 = new ByteArrayOutputStream();
        ImageIO.write(barChart, "png", chartBaos1);
        com.lowagie.text.Image chartImage1 = com.lowagie.text.Image.getInstance(chartBaos1.toByteArray());
        chartImage1.scaleToFit(500, 350);
        chartImage1.setAlignment(Element.ALIGN_CENTER);
        doc.add(chartImage1);
        doc.newPage();

        Chunk suiteDest = new Chunk("TESTSUITE DETAILS", new Font(Font.HELVETICA, 18, Font.BOLD));
        suiteDest.setLocalDestination("suites");
        Paragraph suiteTitle = new Paragraph(suiteDest);
        suiteTitle.setSpacingAfter(20);
        doc.add(suiteTitle);


        for (String suiteKey : details.keySet()) {
            Paragraph suiteHeader = new Paragraph("TestSuite: " + suiteKey, new Font(Font.HELVETICA, 14, Font.BOLD));
            suiteHeader.setSpacingBefore(10);
            suiteHeader.setSpacingAfter(10);
            doc.add(suiteHeader);

            List<Map<String, Object>> cookieEntries = (List<Map<String, Object>>) details.get(suiteKey);

            for (Map<String, Object> cookieEntry : cookieEntries) {
                Paragraph cookieNameHeading = new Paragraph("Cookie Name: " + cookieEntry.get("cookieName"));
                cookieNameHeading.setSpacingAfter(10f);
                doc.add(cookieNameHeading);

                PdfPTable tctable = new PdfPTable(6);
                tctable.setWidthPercentage(100);

                addHeaderCell(tctable, "Testcase ID");
                addHeaderCell(tctable, "Name");
                addHeaderCell(tctable, "URL / Endpoint");
                addHeaderCell(tctable, "Passed");
                addHeaderCell(tctable, "Failed");
                addHeaderCell(tctable, "Failure Messages");

                List<Map<String, Object>> testcaseResults = (List<Map<String, Object>>) cookieEntry.get("results");

                for (Map<String, Object> tc : testcaseResults) {

                    Map<String, Object> summary = (Map<String, Object>) tc.get("summary");
                    boolean isSkipped = Boolean.TRUE.equals(tc.get("skipped"));

                    String url = "-";
                    if (tc.get("fullUrl") != null) url = tc.get("fullUrl").toString();
                    else if (tc.get("endpoint") != null) url = tc.get("endpoint").toString();

                    tctable.addCell(tc.get("id") + "");
                    tctable.addCell(tc.get("name") + "");
                    tctable.addCell(url);
                    tctable.addCell(summary.get("passed") + "");
                    tctable.addCell(summary.get("failed") + "");

                    if (isSkipped) {
                        PdfPCell skippedCell = new PdfPCell(new Phrase("SKIPPED"));
                        skippedCell.setBackgroundColor(new Color(255, 230, 230));
                        tctable.addCell(skippedCell);
                    } else {
                        StringBuilder failureMsgs = new StringBuilder();
                        List<Map<String, Object>> checks = (List<Map<String, Object>>) tc.get("results");

                        if (checks != null) {
                            for (Map<String, Object> chk : checks) {
                                if ("FAIL".equals(chk.get("status"))) {
                                    failureMsgs.append("• ").append(chk.get("message")).append("\n");
                                }
                            }
                        }
                        tctable.addCell(failureMsgs.length() == 0 ? "-" : failureMsgs.toString());
                    }
                }

                doc.add(tctable);
                doc.add(Chunk.NEWLINE);
            }
        }

        doc.close();
        return baos.toByteArray();
    }

    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, new Font(Font.HELVETICA, 12, Font.BOLD)));
        cell.setPadding(8);
        cell.setBackgroundColor(new Color(230, 230, 230));
        table.addCell(cell);
    }

    private BufferedImage generatePieChart(int passed, int failed) {
        DefaultPieDataset<String> dataset = new DefaultPieDataset<>();
        dataset.setValue("Passed", passed);
        dataset.setValue("Failed", failed);

        JFreeChart chart = ChartFactory.createPieChart("Execution Summary", dataset, true, true, false);
        PiePlot<?> plot = (PiePlot<?>) chart.getPlot();
        plot.setLabelGenerator(new StandardPieSectionLabelGenerator("{0}: {1}"));
        return chart.createBufferedImage(500, 300);
    }

    @SuppressWarnings("unchecked")
    private BufferedImage generateCookiePassBarChart(Map<String, Object> cookies) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        for (String cookieId : cookies.keySet()) {
            Map<String, Object> c = (Map<String, Object>) cookies.get(cookieId);
            dataset.addValue((int) c.get("passed"), "Passed", (String) c.get("cookieName"));
        }

        JFreeChart chart = ChartFactory.createBarChart("Passed Testcases per Account", "Account Name", "Passed Count", dataset);

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setOrientation(org.jfree.chart.plot.PlotOrientation.HORIZONTAL);
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setDefaultItemLabelsVisible(true);
        renderer.setDefaultItemLabelGenerator(new StandardCategoryItemLabelGenerator());
        renderer.setMaximumBarWidth(0.15);

        return chart.createBufferedImage(600, 400);
    }

    public String savePdfToDisk(byte[] pdfBytes, String runId) throws IOException {
        File dir = new File(directory);
        if (!dir.exists()) dir.mkdirs();
        String fileName = runId + ".pdf";
        Path pdfPath = Paths.get(dir.getAbsolutePath(), fileName);

        Files.write(pdfPath, pdfBytes);

        return pdfPath.toString();
    }
}
