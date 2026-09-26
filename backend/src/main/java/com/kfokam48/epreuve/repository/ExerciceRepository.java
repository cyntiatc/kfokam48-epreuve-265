package com.kfokam48.epreuve.repository;

import com.kfokam48.epreuve.domain.Exercice;
import com.kfokam48.epreuve.domain.StatutExercice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExerciceRepository extends JpaRepository<Exercice, Long> {

    boolean existsBySessionIdAndAuteurId(Long sessionId, Long auteurId);

    /**
     * Exercices d'une session aux statuts donnés, du plus ancien au plus récent. Verrouillés (SELECT ... FOR UPDATE),
     * toujours dans le même ordre, pour que deux émargements simultanés n'attribuent pas deux fois le même exercice
     * (RG8, ENF3, ticket #17).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Exercice> findBySessionIdAndStatutInOrderByDeposeAtAscIdAsc(Long sessionId, Collection<StatutExercice> statuts);

    /** Tous les exercices d'une session, verrouillés le temps de la clôture (EF8). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Exercice> findBySessionIdOrderByDeposeAtAscIdAsc(Long sessionId);

    /**
     * Exercice verrouillé (SELECT ... FOR UPDATE) : les deux relectures d'un même exercice rendues au même instant
     * sont traitées l'une après l'autre, et la seconde voit la première pour passer l'exercice RELU (RG8).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Exercice e where e.id = :id")
    Optional<Exercice> findPourMiseAJour(@Param("id") Long id);
}
