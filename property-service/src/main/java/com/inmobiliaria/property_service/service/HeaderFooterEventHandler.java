package com.inmobiliaria.property_service.service;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.TextAlignment;

public class HeaderFooterEventHandler implements IEventHandler {

  private static final DeviceRgb NAVY = new DeviceRgb(15, 30, 61);
  private static final DeviceRgb GOLD = new DeviceRgb(200, 149, 108);

  @Override
  public void handleEvent(Event event) {
    try {
      PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
      Rectangle page = docEvent.getPage().getPageSize();
      PdfCanvas pdfCanvas =
          new PdfCanvas(
              docEvent.getPage().newContentStreamBefore(),
              docEvent.getPage().getResources(),
              docEvent.getDocument());

      drawHeader(page, pdfCanvas);
      drawFooter(page, pdfCanvas);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private void drawHeader(Rectangle page, PdfCanvas pdfCanvas) throws Exception {
    float headerHeight = 78;

    pdfCanvas.saveState();
    pdfCanvas
        .setFillColor(NAVY)
        .rectangle(0, page.getTop() - headerHeight, page.getWidth(), headerHeight)
        .fill();
    pdfCanvas.restoreState();

    Canvas canvas = new Canvas(pdfCanvas, page);
    PdfFont font = PdfFontFactory.createFont();
    canvas.setFont(font);

    // Title
    Paragraph titlePara =
        new Paragraph()
            .add(new Text("DROCA ").setBold().setFontColor(ColorConstants.WHITE))
            .add(new Text("INMOBILIARIA").setBold().setFontColor(GOLD))
            .setFontSize(18);

    canvas.showTextAligned(titlePara, 36, page.getTop() - 38, TextAlignment.LEFT);

    // Subtitle
    canvas.showTextAligned(
        new Paragraph("SERVICIOS INMOBILIARIOS")
            .setFontSize(7)
            .setFontColor(ColorConstants.LIGHT_GRAY),
        36,
        page.getTop() - 51,
        TextAlignment.LEFT);

    // Right side badge box
    pdfCanvas.saveState();
    pdfCanvas
        .setStrokeColor(GOLD)
        .rectangle(page.getWidth() - 146, page.getTop() - 48, 110, 20)
        .stroke();
    pdfCanvas.restoreState();

    // Right side badge text
    canvas.showTextAligned(
        new Paragraph("FICHA DE PROPIEDAD").setFontSize(8).setFontColor(GOLD).setBold(),
        page.getWidth() - 91,
        page.getTop() - 42,
        TextAlignment.CENTER);

    canvas.close();
  }

  private void drawFooter(Rectangle page, PdfCanvas pdfCanvas) throws Exception {
    float footerY = 25;

    pdfCanvas.saveState();
    pdfCanvas.setStrokeColor(new DeviceRgb(226, 232, 240)); // Slate 200
    pdfCanvas.setLineWidth(1f);
    pdfCanvas.moveTo(36, footerY + 18);
    pdfCanvas.lineTo(page.getWidth() - 36, footerY + 18);
    pdfCanvas.stroke();
    pdfCanvas.restoreState();

    Canvas canvas = new Canvas(pdfCanvas, page);
    canvas.showTextAligned(
        new Paragraph("© 2026 DROCA INMOBILIARIA  ·  Información sujeta a verificación")
            .setFontSize(8)
            .setFontColor(new DeviceRgb(148, 163, 184)), // Slate 400
        page.getWidth() / 2,
        footerY,
        TextAlignment.CENTER);

    canvas.close();
  }
}
