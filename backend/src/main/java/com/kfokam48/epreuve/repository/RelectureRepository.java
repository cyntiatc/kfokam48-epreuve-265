package com.kfokam48.epreuve.repository;

import com.kfokam48.epreuve.domain.Relecture;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RelectureRepository extends JpaRepository<Relecture, Long> {

    /** Relecture verrouillée (SELECT ... FOR UPDATE) : deux soumissions simultanées sont traitées l'une après l'autre. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Relecture r where r.id = :id")
    Optional<Relecture> findPourMiseAJour(@Param("id") Long id);

    /** Relectures d'un exercice : deux au plus, de relecteurs distincts (RG8). */
    @EntityGraph(attributePaths = "relecteur")
    List<Relecture> findByExerciceId(Long exerciceId);

    /** Relectures attribuées à un relecteur, avec leur exercice et leur session chargés en une seule requête. */
    @EntityGraph(attributePaths = {"exercice", "exercice.session"})
    List<Relecture> findByRelecteurIdOrderByAttribueeAtDesc(Long relecteurId);

    /** Charge de chaque relecteur ayant au moins une relecture dans la session ; les autres ont une charge nulle. */
    @Query("""
            select new com.kfokam48.epreuve.repository.ChargeRelecteur(r.relecteur.id, count(r))
            from Relecture r
            where r.exercice.session.id = :sessionId
            group by r.relecteur.id
            """)
    List<ChargeRelecteur> compterRelecturesParRelecteur(@Param("sessionId") Long sessionId);
}
