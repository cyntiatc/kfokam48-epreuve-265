package com.kfokam48.epreuve.web;

import com.kfokam48.epreuve.domain.SessionCours;
import com.kfokam48.epreuve.service.SessionService;
import com.kfokam48.epreuve.web.dto.SessionCreation;
import com.kfokam48.epreuve.web.dto.SessionReponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** EF1 : ouvrir une session de cours et obtenir un code de présence. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionReponse ouvrirSession(@Valid @RequestBody SessionCreation requete) {
        SessionCours session = sessionService.ouvrirSession(requete.titre(), requete.promotionId());
        return SessionReponse.depuis(session);
    }
}
