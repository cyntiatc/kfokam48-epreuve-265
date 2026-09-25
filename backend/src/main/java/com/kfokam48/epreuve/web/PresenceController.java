package com.kfokam48.epreuve.web;

import com.kfokam48.epreuve.domain.Presence;
import com.kfokam48.epreuve.service.PresenceService;
import com.kfokam48.epreuve.web.dto.PresenceCreation;
import com.kfokam48.epreuve.web.dto.PresenceReponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/presences")
public class PresenceController {

    private final PresenceService presenceService;

    public PresenceController(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    /** EF2 : marquer sa présence avec le code de la session. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PresenceReponse marquerPresence(@Valid @RequestBody PresenceCreation requete) {
        Presence presence = presenceService.marquerPresence(requete.code(), requete.etudiantId());
        return PresenceReponse.depuis(presence);
    }
}
