package com.kfokam48.epreuve.web;

import com.kfokam48.epreuve.service.ClotureService;
import com.kfokam48.epreuve.web.dto.SessionCloturee;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** EF8 : clôture d'une session (route complémentaire, extension H3). */
@RestController
public class ClotureController {

    private final ClotureService clotureService;

    public ClotureController(ClotureService clotureService) {
        this.clotureService = clotureService;
    }

    @PostMapping("/api/sessions/{id}/cloture")
    public SessionCloturee cloturerSession(@PathVariable Long id) {
        return SessionCloturee.depuis(clotureService.cloturerSession(id));
    }
}
