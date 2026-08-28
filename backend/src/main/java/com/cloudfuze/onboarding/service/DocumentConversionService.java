package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.exception.FileValidationException;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.FopFactoryBuilder;
import org.apache.fop.apps.MimeConstants;
import org.docx4j.Docx4J;
import org.docx4j.convert.out.FOSettings;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

/**
 * Normalises an uploaded document to PDF.
 *
 * <p>HR can upload Word or PDF, but everything downstream - combining two
 * documents, placing fields at fixed coordinates, stamping a signature - only
 * works on PDF. Converting once at upload keeps a single format flowing through
 * the rest of the system rather than special-casing Word at every step.
 *
 * <p>Conversion is not pixel-perfect against Word's own renderer. That is
 * acceptable here because HR sees the converted PDF immediately, before placing
 * any fields: what they position fields on is exactly what the recipient signs.
 * A conversion that mangled the layout would be obvious at that point rather
 * than a surprise for the recipient.
 */
@Service
public class DocumentConversionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentConversionService.class);

    /** ZIP local file header - .docx is a zip container. */
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    /** OLE2 compound file - the legacy binary .doc format. */
    private static final byte[] OLE2_MAGIC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};

    /** True when this upload needs converting before anything else touches it. */
    public boolean isWord(MultipartFile file) {
        return isWordExtension(extensionOf(file.getOriginalFilename()));
    }

    public static boolean isWordExtension(String extension) {
        return "doc".equals(extension) || "docx".equals(extension);
    }

    /**
     * Returns PDF bytes for an upload: converted when it is Word, untouched
     * when it is already a PDF.
     */
    public byte[] toPdf(MultipartFile file) {
        byte[] bytes = read(file);
        if (!isWord(file)) {
            return bytes;
        }
        return convertWord(bytes, file.getOriginalFilename());
    }

    private byte[] convertWord(byte[] docx, String filename) {
        if (startsWith(docx, OLE2_MAGIC)) {
            // The pre-2007 binary format is a different container entirely and
            // docx4j cannot read it. Saying so beats a generic failure.
            throw new FileValidationException("LEGACY_DOC_FORMAT",
                    "That is an older .doc file. Please save it as .docx (or PDF) and upload it again.");
        }
        if (!startsWith(docx, ZIP_MAGIC)) {
            throw new FileValidationException("FILE_CONTENT_MISMATCH",
                    "That file is not a real Word document. Please upload the original.");
        }

        long started = System.currentTimeMillis();
        try {
            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(new ByteArrayInputStream(docx));

            // Two steps rather than Docx4J.toPDF, so the intermediate XSL-FO can
            // be repaired before the renderer sees it - see sanitiseFo.
            FOSettings settings = Docx4J.createFOSettings();
            settings.setOpcPackage(pkg);
            settings.setApacheFopMime(FOSettings.INTERNAL_FO_MIME);

            ByteArrayOutputStream foOut = new ByteArrayOutputStream();
            Docx4J.toFO(settings, foOut, Docx4J.FLAG_EXPORT_PREFER_XSL);
            String fo = sanitiseFo(foOut.toString(StandardCharsets.UTF_8), filename);

            byte[] pdf = renderFo(fo);
            if (pdf.length == 0) {
                throw new FileValidationException("CONVERSION_FAILED", conversionFailedMessage(filename));
            }
            log.info("Converted {} to PDF ({} bytes) in {}ms", filename, pdf.length,
                    System.currentTimeMillis() - started);
            return pdf;
        } catch (FileValidationException e) {
            throw e;
        } catch (Exception e) {
            // Log the root cause, not docx4j's generic wrapper - "Exception
            // exporting package" on its own is undiagnosable.
            log.warn("Could not convert {} to PDF: {}", filename, rootCause(e).toString());
            throw new FileValidationException("CONVERSION_FAILED", conversionFailedMessage(filename));
        }
    }

    private static final String FO_NS = "http://www.w3.org/1999/XSL/Format";

    /**
     * Repairs the generated XSL-FO before rendering.
     *
     * <p>Word documents with floating images, text boxes or anchored shapes make
     * docx4j emit {@code fo:float} elements that FOP refuses: its content model
     * demands at least one block child, and docx4j frequently produces floats
     * that are empty or hold only inline content. One such element fails the
     * whole document.
     *
     * <p>Every float is therefore unwrapped - its children are spliced into its
     * parent and the float itself dropped. A float is only a positioning hint,
     * so the content survives and only the wrapping is lost, which beats
     * refusing the upload outright.
     *
     * <p>Done over a parsed document rather than with a regex: the FO arrives as
     * one enormous line, and matching nested tags textually is what let the
     * first attempt at this silently miss the real cases.
     */
    static String sanitiseFo(String fo, String filename) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // Parsing generated markup, but keep the usual XXE protections on.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder()
                    .parse(new InputSource(new ByteArrayInputStream(fo.getBytes(StandardCharsets.UTF_8))));

            NodeList floats = document.getElementsByTagNameNS(FO_NS, "float");
            // Snapshot first: the list is live, so unwrapping while iterating it
            // would skip elements.
            List<Element> targets = new ArrayList<>();
            for (int i = 0; i < floats.getLength(); i++) {
                targets.add((Element) floats.item(i));
            }
            if (targets.isEmpty()) {
                return fo;
            }

            for (Element floatEl : targets) {
                Node parent = floatEl.getParentNode();
                if (parent == null) {
                    continue;
                }
                while (floatEl.getFirstChild() != null) {
                    parent.insertBefore(floatEl.getFirstChild(), floatEl);
                }
                parent.removeChild(floatEl);
            }
            log.info("Unwrapped {} float element(s) while converting {}", targets.size(), filename);

            StringWriter out = new StringWriter();
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.transform(new DOMSource(document), new StreamResult(out));
            return out.toString();
        } catch (Exception e) {
            // A repair that fails must not itself break the conversion: hand the
            // original back and let FOP report whatever is actually wrong.
            log.warn("Could not clean the generated layout for {}: {}", filename, e.toString());
            return fo;
        }
    }

    /**
     * Renders XSL-FO to PDF with FOP.
     *
     * <p>Strict validation is off. FOP's content models are stricter than what
     * docx4j generates from real Word documents - a single element in the wrong
     * shape, anywhere in a long contract, otherwise aborts the entire
     * conversion. Relaxed, FOP skips the offending element and renders the rest,
     * which is the right trade here: HR previews the result before placing any
     * fields, so anything genuinely lost is visible immediately, whereas strict
     * mode just refuses the document with no way forward.
     */
    private static byte[] renderFo(String fo) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FopFactory fopFactory = new FopFactoryBuilder(new File(".").toURI())
                .setStrictFOValidation(false)
                .build();
        Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, out);
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.transform(
                new StreamSource(new ByteArrayInputStream(fo.getBytes(StandardCharsets.UTF_8))),
                new SAXResult(fop.getDefaultHandler()));
        return out.toByteArray();
    }

    /** Exposed so the relaxed-validation behaviour can be asserted directly. */
    static byte[] renderFoForTest(String fo) throws Exception {
        return renderFo(fo);
    }

    private static String conversionFailedMessage(String filename) {
        return "\"" + (filename == null ? "That file" : filename) + "\" could not be converted from Word. "
                + "Please open it in Word, save a copy as PDF, and upload the PDF instead.";
    }

    private static Throwable rootCause(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new FileValidationException("FILE_UNREADABLE", "That file could not be read. Please try again.");
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
