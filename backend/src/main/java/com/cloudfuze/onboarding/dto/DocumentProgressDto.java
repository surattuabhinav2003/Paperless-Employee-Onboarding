package com.cloudfuze.onboarding.dto;

public record DocumentProgressDto(
        int required,
        int verified,
        int submitted,
        int rejected,
        int missing
) {
    public int percentComplete() {
        return required == 0 ? 100 : Math.round((verified * 100f) / required);
    }
}
