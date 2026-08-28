package com.cloudfuze.onboarding.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An administrator's choice for one candidate detail field.
 *
 * <p>Only overrides are stored. A field with no row here behaves as declared on
 * {@link CandidateField}, so the defaults stay in one place and a newly added
 * field does not need a migration to appear.
 */
@Entity
@Table(name = "candidate_field_settings")
@Getter
@Setter
@NoArgsConstructor
public class CandidateFieldSetting {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "field", nullable = false, length = 60)
    private CandidateField field;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "required", nullable = false)
    private boolean required = true;

    public CandidateFieldSetting(CandidateField field, boolean enabled, boolean required) {
        this.field = field;
        this.enabled = enabled;
        this.required = required;
    }
}
