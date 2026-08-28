package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.exception.FileValidationException;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.Loader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Combines several PDFs into one.
 *
 * <p>Used to turn the NDA and the NOC into a single document before HR places
 * any fields on it: the recipient should sign one thing once, and page numbers
 * in the field coordinates only mean something against the merged file.
 */
@Service
public class PdfMergeService {

    private static final Logger log = LoggerFactory.getLogger(PdfMergeService.class);

    /** Refuses anything that would make the combined document unusable. */
    public byte[] merge(List<byte[]> documents) {
        if (documents == null || documents.size() < 2) {
            throw new FileValidationException("MERGE_NEEDS_TWO_FILES",
                    "Two documents are needed to combine into one.");
        }

        PDFMergerUtility merger = new PDFMergerUtility();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        merger.setDestinationStream(out);

        try {
            for (byte[] pdf : documents) {
                // Rejects an encrypted or corrupt file here, where the message can
                // still name which upload was the problem.
                merger.addSource(new RandomAccessReadBuffer(pdf));
            }
            merger.mergeDocuments(null);
        } catch (IOException e) {
            log.warn("Could not combine the uploaded documents: {}", e.getMessage());
            throw new FileValidationException("MERGE_FAILED",
                    "These files could not be combined. Please upload two readable, unlocked PDFs.");
        }

        byte[] merged = out.toByteArray();
        log.info("Combined {} documents into one ({} bytes)", documents.size(), merged.length);
        return merged;
    }

    /** Page count of a PDF, so the UI knows how many pages to render. */
    public int pageCount(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        } catch (IOException e) {
            throw new FileValidationException("PDF_UNREADABLE", "That PDF could not be read.");
        }
    }
}
