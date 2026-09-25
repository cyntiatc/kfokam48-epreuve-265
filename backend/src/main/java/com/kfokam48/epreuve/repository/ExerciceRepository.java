package com.kfokam48.epreuve.repository;

import com.kfokam48.epreuve.domain.Exercice;
import com.kfokam48.epreuve.domain.StatutExercice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;

public interface ExerciceRepository extends JpaRepository<Exercice, Long> {

    boolean existsBySessionIdAndAuteurId(Long sessionId, Long auteurId);

    /**
     * Exercices d'une session à un statut donné, du plus ancien au plus récent. Verrouillés (SELECT ... FOR UPDATE)
     * pour que deux émargements simultanés n'attribuent pas deux fois le même exercice (RG8, ENF3).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Exercice> findBySessionIdAndStatutOrderByDeposeAtAsc(Long sessionId, StatutExercice statut);

    /** Tous les exercices d'une session, verrouillés le temps de la clôture (EF8). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Exercice> findBySessionIdOrderByDeposeAtAsc(Long sessionId);
}
