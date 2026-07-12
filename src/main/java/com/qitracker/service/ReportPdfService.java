package com.qitracker.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.qitracker.domain.Project;
import com.qitracker.dto.DashboardDtos.DashboardResponse;
import com.qitracker.dto.DashboardDtos.IndicatorTrend;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
public class ReportPdfService {

    private final DashboardService dashboardService;

    public ReportPdfService(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    public byte[] buildProjectReportPdf(Project project) {
        DashboardResponse dashboard = dashboardService.build(project.getId(), project.getCreator());
        String html = buildHtml(dashboard);
        return renderPdf(html);
    }

    private String buildHtml(DashboardResponse d) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><style>")
          .append("body { font-family: Helvetica, Arial, sans-serif; color:#16262B; font-size: 12px; }")
          .append("h1 { font-size: 20px; margin-bottom: 2px; }")
          .append("h2 { font-size: 14px; margin-top: 22px; border-bottom: 1px solid #DCE4E2; padding-bottom: 4px; }")
          .append(".muted { color: #5B6E73; }")
          .append("table { width: 100%; border-collapse: collapse; margin-top: 8px; }")
          .append("th, td { text-align: left; padding: 5px 8px; border-bottom: 1px solid #E7ECEA; font-size: 11px; }")
          .append("th { color:#5B6E73; text-transform: uppercase; font-size: 9px; letter-spacing: 0.04em; }")
          .append(".badge { padding: 2px 8px; border-radius: 10px; font-size: 9px; font-weight:bold; }")
          .append(".on { background:#E4EFEC; color:#1F4F47; }")
          .append(".off { background:#F5E4E1; color:#B14A3E; }")
          .append(".summary-row { display:flex; }")
          .append(".stat { display:inline-block; width: 24%; }")
          .append(".stat .num { font-size: 20px; font-weight:bold; }")
          .append(".stat .lbl { font-size: 9px; color:#5B6E73; text-transform:uppercase; }")
          .append("</style></head><body>");
        sb.append("<h1>").append(esc(d.project().name())).append("</h1>");
        sb.append("<div class='muted'>").append(esc(d.project().objectives())).append("</div>");

        sb.append("<h2>Snapshot</h2>");
        sb.append("<div class='summary-row'>");
        sb.append(stat(String.valueOf(d.summary().totalIndicators()), "Indicators"));
        sb.append(stat(String.valueOf(d.summary().onTargetCount()), "On target"));
        sb.append(stat(String.valueOf(d.summary().offTargetCount()), "Off target"));
        sb.append(stat(d.stage().label(), "Project stage"));
        sb.append("</div>");
        if (d.stage().percentElapsed() != null) {
            sb.append("<p class='muted'>").append(Math.round(d.stage().percentElapsed()))
              .append("% of the planned timeline elapsed")
              .append(d.stage().endDate() != null ? " &mdash; target end date " + d.stage().endDate() : "")
              .append("</p>");
        }

        sb.append("<h2>Indicators</h2>");
        sb.append("<table><tr><th>Indicator</th><th>Process area</th><th>Latest</th><th>Target</th><th>Status</th></tr>");
        for (var group : d.groups()) {
            appendRows(sb, group.indicators(), group.processAreaName());
        }
        appendRows(sb, d.ungrouped(), "—");
        sb.append("</table>");

        sb.append("</body></html>");
        return sb.toString();
    }

    private void appendRows(StringBuilder sb, List<IndicatorTrend> trends, String areaName) {
        for (IndicatorTrend t : trends) {
            String status = t.onTarget() == null ? "—" : (t.onTarget() ? "<span class='badge on'>On target</span>" : "<span class='badge off'>Off target</span>");
            sb.append("<tr><td>").append(esc(t.indicatorName())).append("</td>")
              .append("<td>").append(esc(areaName)).append("</td>")
              .append("<td>").append(t.latestValue() == null ? "—" : t.latestValue()).append(" ").append(t.unit() == null ? "" : esc(t.unit())).append("</td>")
              .append("<td>").append(t.targetValue() == null ? "—" : t.targetValue()).append("</td>")
              .append("<td>").append(status).append("</td></tr>");
        }
    }

    private String stat(String num, String label) {
        return "<div class='stat'><div class='num'>" + esc(num) + "</div><div class='lbl'>" + esc(label) + "</div></div>";
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private byte[] renderPdf(String html) {
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Could not generate PDF report: " + e.getMessage(), e);
        }
    }
}
