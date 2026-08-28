package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.exception.BusinessRuleException;
import com.cloudfuze.onboarding.model.OfferField;
import com.cloudfuze.onboarding.model.OfferTextFont;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stamps the candidate's answers onto the fields HR placed on the offer PDF,
 * producing the final signed document. Signature fields take a drawn, typed or
 * uploaded image; every other field takes text.
 */
@Service
public class OfferSigningService {

    private static final long MAX_SIGNATURE_BYTES = 2 * 1024 * 1024;
    private static final Pattern DATA_URI = Pattern.compile(
            "^data:image/(png|jpe?g);base64,", Pattern.CASE_INSENSITIVE);

    /** Decodes and sanity-checks a data-URI signature image. */
    public byte[] decodeSignatureImage(String signatureImage) {
        if (signatureImage == null || signatureImage.isBlank()) {
            throw new BusinessRuleException("SIGNATURE_REQUIRED", "Add your signature before submitting.");
        }
        Matcher matcher = DATA_URI.matcher(signatureImage.trim());
        if (!matcher.find()) {
            throw new BusinessRuleException("INVALID_SIGNATURE_IMAGE",
                    "Your signature could not be read. Please add it again.");
        }
        String base64 = signatureImage.trim().substring(matcher.end()).replaceAll("\\s", "");
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("INVALID_SIGNATURE_IMAGE",
                    "Your signature could not be read. Please add it again.");
        }
        if (bytes.length == 0) {
            throw new BusinessRuleException("INVALID_SIGNATURE_IMAGE",
                    "Your signature could not be read. Please add it again.");
        }
        if (bytes.length > MAX_SIGNATURE_BYTES) {
            throw new BusinessRuleException("SIGNATURE_TOO_LARGE",
                    "That signature image is too large (maximum 2MB).");
        }
        return bytes;
    }

    /**
     * Draws every field's value into its box and returns the flattened PDF.
     * {@code yPct} is measured from the top; PDF space is measured from the
     * bottom, so it is flipped here.
     *
     * @param values field index to answer - a data URI for signatures, text otherwise
     */
    public byte[] stamp(byte[] originalPdf, List<OfferField> fields, Map<Integer, String> values) {
        try (PDDocument document = Loader.loadPDF(originalPdf)) {
            int pageCount = document.getNumberOfPages();
            PDFont helvetica = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont times = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
            PDFont courier = new PDType1Font(Standard14Fonts.FontName.COURIER);

            for (int i = 0; i < fields.size(); i++) {
                OfferField field = fields.get(i);
                String value = values.get(i);
                if (value == null || value.isBlank()) {
                    continue;
                }
                int pageIndex = field.getPage() - 1;
                if (pageIndex < 0 || pageIndex >= pageCount) {
                    continue;
                }
                PDPage page = document.getPage(pageIndex);
                PDRectangle box = page.getMediaBox();
                float pageWidth = box.getWidth();
                float pageHeight = box.getHeight();

                float fieldWidth = (float) (field.getWidthPct() / 100.0) * pageWidth;
                float fieldHeight = (float) (field.getHeightPct() / 100.0) * pageHeight;
                float fieldX = (float) (field.getXPct() / 100.0) * pageWidth;
                float fieldYFromTop = (float) (field.getYPct() / 100.0) * pageHeight;
                float fieldY = pageHeight - fieldYFromTop - fieldHeight;

                if (field.getType().isSignature()) {
                    drawSignature(document, page, decodeSignatureImage(value),
                            fieldX, fieldY, fieldWidth, fieldHeight);
                } else {
                    PDFont font = fontFor(field.getTextFont(), helvetica, times, courier);
                    float[] rgb = hexToRgb(field.getTextColor());
                    drawText(document, page, value, font, rgb, fieldX, fieldY, fieldWidth, fieldHeight);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessRuleException("SIGNING_FAILED",
                    "Could not complete the offer letter. Please try again.");
        }
    }

    private void drawSignature(PDDocument document, PDPage page, byte[] imageBytes,
                               float x, float y, float width, float height) throws IOException {
        PDImageXObject image = PDImageXObject.createFromByteArray(document, imageBytes, "signature");
        // Contain-fit: scale to the smaller ratio so the signature never stretches
        // or overflows the box, then centre it inside.
        float scale = Math.min(width / image.getWidth(), height / image.getHeight());
        float drawWidth = image.getWidth() * scale;
        float drawHeight = image.getHeight() * scale;
        try (PDPageContentStream stream = new PDPageContentStream(document, page,
                PDPageContentStream.AppendMode.APPEND, true, true)) {
            stream.drawImage(image, x + (width - drawWidth) / 2f, y + (height - drawHeight) / 2f,
                    drawWidth, drawHeight);
        }
    }

    private void drawText(PDDocument document, PDPage page, String text, PDFont font, float[] rgb,
                          float x, float y, float width, float height) throws IOException {
        float padding = 2f;
        float innerWidth = Math.max(8f, width - padding * 2);
        // Size to the box, then shrink further if the text still will not fit -
        // a long answer should stay inside its field rather than run over the
        // surrounding contract text.
        float fontSize = Math.min(12f, Math.max(6f, height * 0.55f));
        List<String> lines = wrap(text, font, fontSize, innerWidth);
        while (lines.size() * fontSize * 1.2f > height && fontSize > 6f) {
            fontSize -= 0.5f;
            lines = wrap(text, font, fontSize, innerWidth);
        }

        try (PDPageContentStream stream = new PDPageContentStream(document, page,
                PDPageContentStream.AppendMode.APPEND, true, true)) {
            stream.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
            stream.setFont(font, fontSize);
            float lineHeight = fontSize * 1.2f;
            // Vertically centre the block within the box.
            float blockHeight = lines.size() * lineHeight;
            float cursorY = y + (height + blockHeight) / 2f - fontSize;
            for (String line : lines) {
                if (cursorY < y - fontSize) break;
                stream.beginText();
                stream.newLineAtOffset(x + padding, cursorY);
                stream.showText(line);
                stream.endText();
                cursorY -= lineHeight;
            }
        }
    }

    /** Greedy word wrap; a single word longer than the line is split character-wise. */
    private List<String> wrap(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String paragraph : sanitise(text).split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (textWidth(font, candidate, fontSize) <= maxWidth) {
                    line = new StringBuilder(candidate);
                    continue;
                }
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line = new StringBuilder();
                }
                // The word alone may still be too wide; break it up rather than overflow.
                StringBuilder chunk = new StringBuilder();
                for (char c : word.toCharArray()) {
                    if (textWidth(font, chunk.toString() + c, fontSize) > maxWidth && !chunk.isEmpty()) {
                        lines.add(chunk.toString());
                        chunk = new StringBuilder();
                    }
                    chunk.append(c);
                }
                line = chunk;
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private float textWidth(PDFont font, String text, float fontSize) throws IOException {
        return font.getStringWidth(text) / 1000f * fontSize;
    }

    /**
     * The PDF standard fonts cover WinAnsi only, so anything outside it (a
     * smart quote pasted from Word, an emoji) would throw at draw time. Those
     * characters are replaced rather than allowed to fail the whole signing.
     */
    private String sanitise(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c == '\n' || c == '\r') {
                out.append('\n');
            } else if (c == '‘' || c == '’') {
                out.append('\'');
            } else if (c == '“' || c == '”') {
                out.append('"');
            } else if (c == '–' || c == '—') {
                out.append('-');
            } else if (c >= 32 && c <= 255) {
                out.append(c);
            } else {
                out.append('?');
            }
        }
        return out.toString();
    }

    private PDFont fontFor(OfferTextFont font, PDFont helvetica, PDFont times, PDFont courier) {
        if (font == null) return helvetica;
        return switch (font) {
            case TIMES -> times;
            case COURIER -> courier;
            case HELVETICA -> helvetica;
        };
    }

    private float[] hexToRgb(String hex) {
        if (hex == null || !hex.matches("^#[0-9A-Fa-f]{6}$")) {
            return new float[] {0.07f, 0.09f, 0.15f};
        }
        return new float[] {
                Integer.parseInt(hex.substring(1, 3), 16) / 255f,
                Integer.parseInt(hex.substring(3, 5), 16) / 255f,
                Integer.parseInt(hex.substring(5, 7), 16) / 255f,
        };
    }
}
