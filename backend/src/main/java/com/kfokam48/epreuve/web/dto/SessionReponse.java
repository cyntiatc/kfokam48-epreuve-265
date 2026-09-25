package com.kfokam48.epreuve.web.dto;

import com.kfokam48.epreuve.domain.SessionCours;

import java.time.Instant;

/** Réponse 201 de {@code POST /api/sessions} (schéma {@code Session} du contrat). */
public record SessionReponse(Long id, String code, Instant ouvertureAt, Instant expirationAt) {

    public static SessionReponse depuis(SessionCours session) {
        return new SessionReponse(session.getId(), session.getCode(),
                session.getOuvertureAt(), session.getExpirationAt());
    }
}
