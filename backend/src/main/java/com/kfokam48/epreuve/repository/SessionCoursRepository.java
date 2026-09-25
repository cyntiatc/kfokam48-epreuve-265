package com.kfokam48.epreuve.repository;

import com.kfokam48.epreuve.domain.SessionCours;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SessionCoursRepository extends JpaRepository<SessionCours, Long> {

    boolean existsByCode(String code);

    Optional<SessionCours> findByCode(String code);

    /** Session verrouillée (SELECT ... FOR UPDATE) : deux clôtures simultanées sont traitées l'une après l'autre. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SessionCours s where s.id = :id")
    Optional<SessionCours> findPourMiseAJour(@Param("id") Long id);
}
