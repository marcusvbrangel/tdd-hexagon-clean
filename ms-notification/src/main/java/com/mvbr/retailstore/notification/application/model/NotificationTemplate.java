package com.mvbr.retailstore.notification.application.model;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public enum NotificationTemplate {
    ORDER_RECEIVED("Pedido recebido"),
    INVENTORY_RESERVED("Estoque reservado"),
    INVENTORY_REJECTED("Pedido cancelado - sem estoque"),
    PAYMENT_AUTHORIZED("Pagamento aprovado"),
    PAYMENT_DECLINED("Pagamento recusado"),
    ORDER_CANCELED("Pedido cancelado");

    private static final String BRAND = "Retail Store";
    private static final String DIVIDER = "----------------------------------------\n";
    private static final String FONT_FAMILY = "font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Arial, sans-serif;";

    private final String subject;

    NotificationTemplate(String subject) {
        this.subject = subject;
    }

    public NotificationMessage render(String orderId, Map<String, Object> data) {
        String itemsCount = value(data, "itemsCount", "0");
        String total = value(data, "total", "0.00");
        String currency = value(data, "currency", "BRL");
        String reason = value(data, "reason", "");

        String subjectLine = subject + " | Pedido " + orderId;
        TemplateLayout layout = switch (this) {
            case ORDER_RECEIVED -> new TemplateLayout(
                    "Pedido recebido",
                    "#2563eb",
                    List.of(
                            "Estamos reservando os itens do seu pedido.",
                            "Voce recebera a confirmacao do pagamento em seguida."
                    )
            );
            case INVENTORY_RESERVED -> new TemplateLayout(
                    "Estoque reservado",
                    "#2563eb",
                    List.of(
                            "Estamos processando o pagamento.",
                            "Assim que aprovado, iremos finalizar seu pedido."
                    )
            );
            case INVENTORY_REJECTED -> new TemplateLayout(
                    "Pedido cancelado por falta de estoque",
                    "#dc2626",
                    List.of(
                            "Nenhuma cobranca sera realizada.",
                            "Voce pode tentar novamente mais tarde."
                    )
            );
            case PAYMENT_AUTHORIZED -> new TemplateLayout(
                    "Pagamento aprovado",
                    "#16a34a",
                    List.of(
                            "Estamos finalizando seu pedido.",
                            "Em breve voce recebera a confirmacao de envio."
                    )
            );
            case PAYMENT_DECLINED -> new TemplateLayout(
                    "Pagamento recusado",
                    "#dc2626",
                    List.of(
                            "Verifique o metodo de pagamento e tente novamente.",
                            "Se precisar, fale com nosso atendimento."
                    )
            );
            case ORDER_CANCELED -> new TemplateLayout(
                    "Pedido cancelado",
                    "#f97316",
                    List.of("Se voce desejar, pode refazer o pedido.")
            );
        };

        String textBody = renderText(orderId, itemsCount, total, currency, reason, layout);
        String htmlBody = renderHtml(orderId, itemsCount, total, currency, reason, subjectLine, layout);
        return new NotificationMessage(subjectLine, textBody, htmlBody);
    }

    public static Optional<NotificationTemplate> from(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(NotificationTemplate.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static String value(Map<String, Object> data, String key, String fallback) {
        if (data == null) {
            return fallback;
        }
        Object raw = data.get(key);
        if (raw == null) {
            return fallback;
        }
        String text = String.valueOf(raw);
        return text.isBlank() ? fallback : text;
    }

    private static String reasonLine(String reason) {
        if (reason == null || reason.isBlank()) {
            return "";
        }
        return "Motivo: " + reason + "\n";
    }

    private static String header(String orderId) {
        return BRAND + "\n" +
                "Pedido: " + orderId + "\n" +
                DIVIDER;
    }

    private static String summary(String itemsCount, String total, String currency) {
        return "Resumo do pedido\n" +
                "- Itens: " + itemsCount + "\n" +
                "- Total: " + total + " " + currency + "\n\n";
    }

    private static String reasonBlock(String reason) {
        String line = reasonLine(reason);
        if (line.isBlank()) {
            return "";
        }
        return line + "\n";
    }

    private static String footer() {
        return DIVIDER +
                "Precisa de ajuda?\n" +
                "Responda este email.\n" +
                "Equipe " + BRAND + "\n";
    }

    private static String renderText(String orderId,
                                     String itemsCount,
                                     String total,
                                     String currency,
                                     String reason,
                                     TemplateLayout layout) {
        StringBuilder text = new StringBuilder();
        text.append(header(orderId));
        text.append("Status: ").append(layout.status()).append("\n\n");
        text.append(summary(itemsCount, total, currency));
        text.append(reasonBlock(reason));
        text.append("Proximos passos\n");
        for (String step : layout.nextSteps()) {
            text.append("- ").append(step).append("\n");
        }
        text.append("\n").append(footer());
        return text.toString();
    }

    private static String renderHtml(String orderId,
                                     String itemsCount,
                                     String total,
                                     String currency,
                                     String reason,
                                     String subjectLine,
                                     TemplateLayout layout) {
        String safeOrderId = escapeHtml(orderId);
        String safeItems = escapeHtml(itemsCount);
        String safeTotal = escapeHtml(total);
        String safeCurrency = escapeHtml(currency);
        String safeReason = escapeHtml(reason);
        String safeStatus = escapeHtml(layout.status());
        String safeSubject = escapeHtml(subjectLine);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"pt-BR\">\n");
        html.append("<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("<title>").append(safeSubject).append("</title>\n");
        html.append("</head>\n");
        html.append("<body style=\"margin:0; padding:0; background-color:#f4f5f7;\">");
        html.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ");
        html.append("style=\"background-color:#f4f5f7;\">");
        html.append("<tr><td align=\"center\" style=\"padding:24px 16px;\">");
        html.append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" ");
        html.append("style=\"width:600px; max-width:100%; background:#ffffff; border-radius:12px; ");
        html.append("overflow:hidden; border:1px solid #e5e7eb;\">");
        html.append("<tr><td style=\"padding:20px 24px; background:#111827; color:#ffffff; ");
        html.append(FONT_FAMILY);
        html.append(" font-size:18px; letter-spacing:0.3px;\">");
        html.append(BRAND).append("</td></tr>");

        html.append("<tr><td style=\"padding:24px; ");
        html.append(FONT_FAMILY);
        html.append(" color:#111827;\">");
        html.append("<p style=\"margin:0 0 8px; font-size:12px; color:#6b7280; ");
        html.append("text-transform:uppercase; letter-spacing:0.08em;\">Pedido ");
        html.append(safeOrderId).append("</p>");
        html.append("<div style=\"margin:0 0 16px; padding:12px 14px; border-left:4px solid ");
        html.append(layout.accentColor());
        html.append("; background:#f8fafc;\">");
        html.append("<div style=\"font-size:18px; font-weight:600; margin:0;\">");
        html.append(safeStatus).append("</div>");
        html.append("</div>");

        html.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ");
        html.append("style=\"border-collapse:collapse; margin:0 0 16px;\">");
        html.append(summaryRow("Itens", safeItems));
        html.append(summaryRow("Total", safeTotal + " " + safeCurrency));
        html.append("</table>");

        if (!safeReason.isBlank()) {
            html.append("<div style=\"margin:0 0 16px; padding:12px 14px; border:1px solid #fee2e2; ");
            html.append("background:#fef2f2; color:#991b1b; border-radius:8px; font-size:14px;\">");
            html.append("Motivo: ").append(safeReason);
            html.append("</div>");
        }

        html.append("<p style=\"margin:16px 0 8px; font-weight:600; font-size:14px;\">");
        html.append("Proximos passos</p>");
        html.append("<ul style=\"margin:0; padding-left:18px; color:#374151; font-size:14px;\">");
        for (String step : layout.nextSteps()) {
            html.append("<li style=\"margin:0 0 6px;\">");
            html.append(escapeHtml(step)).append("</li>");
        }
        html.append("</ul>");
        html.append("</td></tr>");

        html.append("<tr><td style=\"padding:16px 24px; background:#f3f4f6; ");
        html.append(FONT_FAMILY);
        html.append(" font-size:12px; color:#6b7280;\">");
        html.append("Precisa de ajuda? Responda este email.<br>");
        html.append("Equipe ").append(BRAND);
        html.append("</td></tr>");
        html.append("</table>");
        html.append("</td></tr>");
        html.append("</table>");
        html.append("</body></html>");
        return html.toString();
    }

    private static String summaryRow(String label, String value) {
        return "<tr>" +
                "<td style=\"padding:8px 0; font-size:14px; color:#6b7280;\">" + label + "</td>" +
                "<td style=\"padding:8px 0; font-size:14px; color:#111827; text-align:right; " +
                "font-weight:600;\">" + value + "</td>" +
                "</tr>";
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private record TemplateLayout(String status, String accentColor, List<String> nextSteps) {
    }
}
