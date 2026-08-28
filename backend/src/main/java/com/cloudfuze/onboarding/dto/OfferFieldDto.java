package com.cloudfuze.onboarding.dto;

import com.cloudfuze.onboarding.model.OfferFieldType;
import com.cloudfuze.onboarding.model.OfferTextFont;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * One field HR placed on the offer PDF, as a percentage of that page's
 * width/height so it is independent of how large the page is rendered.
 * {@code yPct} is measured from the top of the page.
 */
public record OfferFieldDto(
        @NotNull(message = "Field type is required")
        OfferFieldType type,

        @NotNull(message = "Page number is required")
        @Min(value = 1, message = "Page must be 1 or greater")
        @Max(value = 500, message = "Page number is out of range")
        Integer page,

        @NotNull(message = "xPct is required")
        @DecimalMin(value = "0", message = "xPct must be at least 0")
        @DecimalMax(value = "100", message = "xPct must be at most 100")
        Double xPct,

        @NotNull(message = "yPct is required")
        @DecimalMin(value = "0", message = "yPct must be at least 0")
        @DecimalMax(value = "100", message = "yPct must be at most 100")
        Double yPct,

        @NotNull(message = "widthPct is required")
        @DecimalMin(value = "2", message = "widthPct must be at least 2")
        @DecimalMax(value = "100", message = "widthPct must be at most 100")
        Double widthPct,

        @NotNull(message = "heightPct is required")
        @DecimalMin(value = "1", message = "heightPct must be at least 1")
        @DecimalMax(value = "100", message = "heightPct must be at most 100")
        Double heightPct,

        @Size(max = 2000, message = "Pre-filled text must be 2000 characters or fewer")
        String prefill,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Text colour must be a hex value like #dc2626")
        String textColor,

        OfferTextFont textFont
) {
}
