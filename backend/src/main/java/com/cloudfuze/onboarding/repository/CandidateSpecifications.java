package com.cloudfuze.onboarding.repository;

import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.Stage;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pipeline filters built as criteria predicates rather than JPQL with nullable
 * parameters: only the filters actually supplied reach the SQL, so the database
 * never has to infer the type of a null bind parameter.
 */
public final class CandidateSpecifications {

    private CandidateSpecifications() {
    }

    public static Specification<Candidate> matching(String query, Stage stage) {
        return (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>(2);
            if (stage != null) {
                predicates.add(builder.equal(root.get("stage"), stage));
            }
            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("name")), pattern),
                        builder.like(builder.lower(root.get("email")), pattern),
                        builder.like(builder.lower(root.get("role")), pattern),
                        builder.like(builder.lower(root.get("department")), pattern)));
            }
            return predicates.isEmpty()
                    ? builder.conjunction()
                    : builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
