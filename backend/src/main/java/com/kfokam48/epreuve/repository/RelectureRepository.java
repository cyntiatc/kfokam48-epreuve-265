package com.kfokam48.epreuve.repository;

import com.kfokam48.epreuve.domain.Relecture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RelectureRepository extends JpaRepository<Relecture, Long> {

    /** Charge de chaque relecteur ayant au moins une relecture dans la session ; les autres ont une charge nulle. */
    @Query("""
            select new com.kfokam48.epreuve.repository.ChargeRelecteur(r.relecteur.id, count(r))
            from Relecture r
            where r.exercice.session.id = :sessionId
            group by r.relecteur.id
            """)
    List<ChargeRelecteur> compterRelecturesParRelecteur(@Param("sessionId") Long sessionId);
}
