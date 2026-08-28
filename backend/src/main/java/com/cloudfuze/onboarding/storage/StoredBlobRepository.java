package com.cloudfuze.onboarding.storage;

import org.springframework.data.jpa.repository.JpaRepository;

/** Documents held in the database, keyed by the same storage key the disk store uses. */
public interface StoredBlobRepository extends JpaRepository<StoredBlob, String> {
}
