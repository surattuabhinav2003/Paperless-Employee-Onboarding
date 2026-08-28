package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.exception.FileValidationException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Word uploads have to become real, readable PDFs - everything downstream
 * (merging, field coordinates, stamping) assumes PDF.
 */
class DocumentConversionServiceTest {

    private final DocumentConversionService service = new DocumentConversionService();

    @Test
    @DisplayName("A .docx upload is converted to a PDF that keeps its text")
    void convertsDocxToPdf() throws Exception {
        byte[] docx = wordDocument("Non-Disclosure Agreement", "This agreement is made between the parties.");
        MockMultipartFile upload = new MockMultipartFile("file", "nda.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx);

        byte[] pdf = service.toPdf(upload);

        assertThat(pdf).isNotEmpty();
        // A real PDF, not just bytes that came back.
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Non-Disclosure Agreement");
            assertThat(text).contains("This agreement is made between the parties.");
        }
    }

    @Test
    @DisplayName("A PDF upload is passed through untouched")
    void leavesPdfAlone() {
        byte[] pdf = "%PDF-1.4 pretend".getBytes();
        MockMultipartFile upload = new MockMultipartFile("file", "offer.pdf", "application/pdf", pdf);
        assertThat(service.toPdf(upload)).isEqualTo(pdf);
        assertThat(service.isWord(upload)).isFalse();
    }

    @Test
    @DisplayName("A legacy binary .doc is refused with advice, not a generic error")
    void refusesLegacyDoc() {
        // OLE2 compound-file header - the pre-2007 Word container.
        byte[] ole2 = new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 0, 0, 0, 0};
        MockMultipartFile upload = new MockMultipartFile("file", "old.doc", "application/msword", ole2);

        assertThatThrownBy(() -> service.toPdf(upload))
                .isInstanceOf(FileValidationException.class)
                .hasMessageContaining(".docx");
    }

    @Test
    @DisplayName("Something renamed to .docx is refused rather than half-converted")
    void refusesFakeDocx() {
        MockMultipartFile upload = new MockMultipartFile("file", "fake.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "just some text".getBytes());

        assertThatThrownBy(() -> service.toPdf(upload)).isInstanceOf(FileValidationException.class);
    }

    @Test
    @DisplayName("FO that FOP considers invalid still renders instead of failing the upload")
    void rendersInvalidFoRatherThanFailing() throws Exception {
        // Exactly the shape that failed in production: fo:float with no block
        // child. Strict FOP aborts the whole document on this; the renderer is
        // deliberately relaxed so the rest of a long contract still converts.
        String fo = """
                <fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format">
                  <fo:layout-master-set>
                    <fo:simple-page-master master-name="p" page-width="210mm" page-height="297mm">
                      <fo:region-body/>
                    </fo:simple-page-master>
                  </fo:layout-master-set>
                  <fo:page-sequence master-reference="p">
                    <fo:flow flow-name="xsl-region-body">
                      <fo:block>Agreement text that must survive</fo:block>
                      <fo:float/>
                      <fo:block>More text after the bad element</fo:block>
                    </fo:flow>
                  </fo:page-sequence>
                </fo:root>""";

        // The production path: repair the layout, then render it.
        byte[] pdf = DocumentConversionService.renderFoForTest(
                DocumentConversionService.sanitiseFo(fo, "contract.docx"));

        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Agreement text that must survive");
            assertThat(text).contains("More text after the bad element");
        }
    }

    @Test
    @DisplayName("An empty fo:float is stripped so FOP does not reject the whole document")
    void stripsEmptyFloats() {
        // Word documents with floating images, text boxes or anchored shapes make
        // docx4j emit an empty <fo:float/>. FOP rejects it outright - its content
        // model requires at least one block - and the entire conversion fails.
        String fo = """
                <fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format">
                  <fo:block>Kept</fo:block>
                  <fo:float fo:float="left"/>
                  <fo:float clear="both"></fo:float>
                  <fo:float>   </fo:float>
                  <fo:block>Also kept</fo:block>
                </fo:root>""";

        String cleaned = DocumentConversionService.sanitiseFo(fo, "doc.docx");

        assertThat(cleaned).doesNotContain("fo:float");
        assertThat(cleaned).contains("Kept").contains("Also kept");
    }

    @Test
    @DisplayName("A float that has real content is left alone")
    void keepsPopulatedFloats() {
        String fo = "<fo:float><fo:block>Sidebar</fo:block></fo:float>";
        assertThat(DocumentConversionService.sanitiseFo(fo, "doc.docx")).isEqualTo(fo);
    }

    /** A genuine .docx, built rather than checked in as a fixture. */
    private byte[] wordDocument(String heading, String body) throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        pkg.getMainDocumentPart().addStyledParagraphOfText("Title", heading);
        pkg.getMainDocumentPart().addParagraphOfText(body);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        pkg.save(out);
        return out.toByteArray();
    }
}
