package com.kfokam48.epreuve.web;

import com.kfokam48.epreuve.service.RelectureService;
import com.kfokam48.epreuve.web.dto.RelectureAttribuee;
import com.kfokam48.epreuve.web.dto.RelectureSoumission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Relectures", description = "Consultation et notation des exercices par les pairs")
@RestController
@RequestMapping("/api/relectures")
public class RelectureController {

    private final RelectureService relectureService;

    public RelectureController(RelectureService relectureService) {
        this.relectureService = relectureService;
    }

    /** H3 (route complémentaire) : relectures attribuées à un relecteur, avec le lien de chaque exercice. */
    @GetMapping
    public List<RelectureAttribuee> relecturesAttribuees(@RequestParam Long relecteurId) {
        return relectureService.relecturesAttribuees(relecteurId).stream()
                .map(RelectureAttribuee::depuis)
                .toList();
    }

    /** EF5, EF6 : rendre la relecture, ou la modifier avant la clôture. Réponse 200 sans corps (contrat). */
    @PostMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public void rendreRelecture(@PathVariable Long id, @Valid @RequestBody RelectureSoumission requete) {
        relectureService.rendreRelecture(id, requete.note(), requete.commentaire());
    }
}
