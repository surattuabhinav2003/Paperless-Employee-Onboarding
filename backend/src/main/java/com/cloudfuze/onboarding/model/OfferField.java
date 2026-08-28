package com.cloudfuze.onboarding.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A field HR placed on the offer PDF for the candidate to complete, positioned
 * as a percentage of the page's own width/height so it survives independent of
 * render resolution. {@code yPct} is measured from the top of the page, matching
 * how a browser lays the page out; converting to PDF's bottom-left origin
 * happens only where the signed PDF is generated.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class OfferField {

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 30, columnDefinition = "varchar(30)")
    private OfferFieldType type = OfferFieldType.SIGNATURE;

    @Column(name = "page", nullable = false)
    private int page;

    @Column(name = "x_pct", nullable = false)
    private double xPct;

    @Column(name = "y_pct", nullable = false)
    private double yPct;

    @Column(name = "width_pct", nullable = false)
    private double widthPct;

    @Column(name = "height_pct", nullable = false)
    private double heightPct;

    /** Text HR pre-filled for the candidate; they can still change it. */
    @Column(name = "prefill", length = 2000)
    private String prefill;

    /** Hex colour for TEXT fields, e.g. {@code #dc2626}. Null means near-black. */
    @Column(name = "text_color", length = 7)
    private String textColor;

    @Enumerated(EnumType.STRING)
    @Column(name = "text_font", length = 20, columnDefinition = "varchar(20)")
    private OfferTextFont textFont;

    public OfferField(OfferFieldType type, int page, double xPct, double yPct, double widthPct,
                      double heightPct, String prefill, String textColor, OfferTextFont textFont) {
        this.type = type;
        this.page = page;
        this.xPct = xPct;
        this.yPct = yPct;
        this.widthPct = widthPct;
        this.heightPct = heightPct;
        this.prefill = prefill;
        this.textColor = textColor;
        this.textFont = textFont;
    }
}
