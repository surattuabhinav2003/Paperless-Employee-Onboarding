package com.cloudfuze.onboarding.exception;

import com.cloudfuze.onboarding.model.Stage;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The candidate tried to reach a stage they have not unlocked yet. Always a 403
 * so a candidate cannot probe the workflow by guessing URLs.
 */
public class StageForbiddenException extends ApiException {

    public StageForbiddenException(Stage current, Stage required, String message) {
        super(HttpStatus.FORBIDDEN, "STAGE_FORBIDDEN", message, details(current, required));
    }

    private static Map<String, Object> details(Stage current, Stage required) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("currentStage", current.getCode());
        map.put("requiredStage", required.getCode());
        return map;
    }

    public static StageForbiddenException offerLocked(Stage current) {
        return new StageForbiddenException(current, Stage.DOCS_APPROVED,
                "Your offer letter is not available yet. HR is still reviewing your documents - "
                        + "the offer unlocks once every required document is approved.");
    }

    public static StageForbiddenException bondLocked(Stage current) {
        return new StageForbiddenException(current, Stage.OFFER_ACCEPTED,
                "Bond signing is locked. Please accept your offer letter before proceeding to bond signing.");
    }

    public static StageForbiddenException documentsClosed(Stage current) {
        return new StageForbiddenException(current, Stage.DOCS_PENDING,
                "Document uploads are closed because your documents have already been approved.");
    }
}
