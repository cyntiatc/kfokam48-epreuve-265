package com.kfokam48.epreuve.web.dto;

import com.kfokam48.epreuve.domain.Presence;
import com.kfokam48.epreuve.domain.SourcePresence;

/** Réponse 201 de {@code POST /api/presences} (schéma {@code Presence} du contrat). */
public record PresenceReponse(Long id, Long sessionId, Long etudiantId, SourcePresence source) {

    public static PresenceReponse depuis(Presence presence) {
        return new PresenceReponse(presence.getId(), presence.getSession().getId(),
                presence.getEtudiant().getId(), presence.getSource());
    }
}
