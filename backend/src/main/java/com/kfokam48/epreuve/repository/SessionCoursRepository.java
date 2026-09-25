package com.kfokam48.epreuve.repository;

import com.kfokam48.epreuve.domain.SessionCours;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionCoursRepository extends JpaRepository<SessionCours, Long> {

    boolean existsByCode(String code);
}
