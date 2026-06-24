package com.prognimak.jobscanner.repository;

import com.prognimak.jobscanner.entity.ApplicationDraft;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationDraftRepository extends JpaRepository<ApplicationDraft, Long> {

    Optional<ApplicationDraft> findByJobOfferId(Long jobOfferId);
}
