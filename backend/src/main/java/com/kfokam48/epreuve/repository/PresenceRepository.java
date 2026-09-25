package com.kfokam48.epreuve.repository;

import com.kfokam48.epreuve.domain.Presence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PresenceRepository extends JpaRepository<Presence, Long> {

    boolean existsBySessionIdAndEtudiantId(Long sessionId, Long etudiantId);

    List<Presence> findBySessionIdOrderByEtudiantIdAsc(Long sessionId);
}
