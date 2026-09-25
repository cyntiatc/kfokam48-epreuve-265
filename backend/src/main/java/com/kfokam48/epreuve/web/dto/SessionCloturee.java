package com.kfokam48.epreuve.web.dto;

import com.kfokam48.epreuve.domain.StatutSession;
import com.kfokam48.epreuve.service.BilanCloture;

import java.time.Instant;

/** Réponse 200 de {@code POST /api/sessions/{id}/cloture} (schéma {@code SessionCloturee}, extension H3). */
public record SessionCloturee(
        Long id,
        StatutSession statut,
        Instant clotureAt,
        int exercicesDefinitifs,
        int exercicesSansRelecture,
        int relecturesEnAttente) {

    public static SessionCloturee depuis(BilanCloture bilan) {
        return new SessionCloturee(
                bilan.session().getId(),
                bilan.session().getStatut(),
                bilan.session().getClotureAt(),
                bilan.exercicesDefinitifs(),
                bilan.exercicesSansRelecture(),
                bilan.relecturesEnAttente());
    }
}
