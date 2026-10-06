package com.inmobiliaria.property_service.service;

import com.inmobiliaria.property_service.domain.PropertyDocument;
import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.html2pdf.resolver.font.DefaultFontProvider;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.font.FontProvider;
import java.io.ByteArrayOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
public class PdfGenerationService {

  private final TemplateEngine templateEngine;

  public byte[] generatePropertyPdf(
      PropertyDocument property,
      String qrBase64,
      String mainImageBase64,
      String agentName,
      String agentEmail,
      String agentPhone,
      String formattedPrice)
      throws Exception {

    Context context = new Context();
    context.setVariable("property", property);
    context.setVariable("qrCode", "data:image/png;base64," + qrBase64);
    context.setVariable("mainImageBase64", mainImageBase64);
    context.setVariable("agentName", agentName != null ? agentName : "Support Team");
    context.setVariable("agentEmail", agentEmail != null ? agentEmail : "support@drocainmob.com");
    context.setVariable("agentPhone", agentPhone != null ? agentPhone : "N/A");
    context.setVariable("formattedPrice", formattedPrice);

    String htmlContent = templateEngine.process("property_details_pdf", context);

    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

      // 1. Crear PdfWriter y PdfDocument con tamaño LETTER explícito
      PdfWriter writer = new PdfWriter(outputStream);
      PdfDocument pdfDoc = new PdfDocument(writer);

      // Registrar el manejador de eventos para encabezado y pie de página
      pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, new HeaderFooterEventHandler());

      // FORZAR tamaño de página Letter (612 x 792 puntos)
      pdfDoc.setDefaultPageSize(PageSize.LETTER);

      // 2. Configurar propiedades del conversor
      ConverterProperties properties = new ConverterProperties();

      // 3. Configurar fuentes para evitar problemas de renderizado
      FontProvider fontProvider = new DefaultFontProvider();
      properties.setFontProvider(fontProvider);

      // 4. Convertir HTML a PDF usando el PdfDocument ya configurado
      HtmlConverter.convertToPdf(htmlContent, pdfDoc, properties);

      pdfDoc.close();
      return outputStream.toByteArray();
    }
  }
}
