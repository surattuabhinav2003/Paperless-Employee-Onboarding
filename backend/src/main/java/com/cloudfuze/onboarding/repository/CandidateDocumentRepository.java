package com.cloudfuze.onboarding.repository;

import com.cloudfuze.onboarding.model.CandidateDocument;
import com.cloudfuze.onboarding.model.DocumentStatus;
import com.cloudfuze.onboarding.model.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CandidateDocumentRepository extends JpaRepository<CandidateDocument, UUID> {

    List<CandidateDocument> findByCandidateIdOrderByUploadedAtAsc(UUID candidateId);

    Optional<CandidateDocument> findByCandidateIdAndDocumentType(UUID candidateId, DocumentType documentType);

    /**
     * Looks a document up by its type code, whichever kind of type it is.
     *
     * <p>A built-in type is stored in the enum column with no custom code; an
     * admin-created one under OTHER plus its code. One query covers both rather
     * than making every caller decide which it is holding.
     */
    @Query("""
            select d from CandidateDocument d
            where d.candidate.id = :candidateId
              and (d.customTypeCode = :typeCode
                   or (d.customTypeCode is null and str(d.documentType) = :enumName))
            """)
    Optional<CandidateDocument> findByCandidateIdAndTypeCode(@Param("candidateId") UUID candidateId,
                                                            @Param("typeCode") String typeCode,
                                                            @Param("enumName") String enumName);

    /** Convenience overload that works out the enum name for a built-in code. */
    default Optional<CandidateDocument> findByCandidateIdAndTypeCode(UUID candidateId, String typeCode) {
        String enumName = DocumentType.fromCodeOrEmpty(typeCode).map(Enum::name).orElse(null);
        return findByCandidateIdAndTypeCode(candidateId, typeCode, enumName);
    }

    long countByStatus(DocumentStatus status);

    @Query("""
            select d from CandidateDocument d
            join fetch d.candidate c
            where d.status = :status
            order by d.uploadedAt asc
            """)
    List<CandidateDocument> findByStatusWithCandidate(DocumentStatus status);

    @Query("select d from CandidateDocument d where d.candidate.id in :candidateIds")
    List<CandidateDocument> findByCandidateIdIn(@Param("candidateIds") Collection<UUID> candidateIds);

    @Query("select d.candidate.id, count(d) from CandidateDocument d where d.status = :status group by d.candidate.id")
    List<Object[]> countByStatusGroupedByCandidate(DocumentStatus status);

    @Query("select d.candidate.id, d.documentType, d.status from CandidateDocument d")
    List<Object[]> findAllDocumentStates();
}
