package com.kfokam48.epreuve.web;

import com.kfokam48.epreuve.service.TableauService;
import com.kfokam48.epreuve.web.dto.LigneTableau;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Tableau", description = "Tableau récapitulatif du formateur")
@RestController
@RequestMapping("/api/tableau")
public class TableauController {

    private final TableauService tableauService;

    public TableauController(TableauService tableauService) {
        this.tableauService = tableauService;
    }

    /** EF7 : consulter le tableau récapitulatif d'une promotion. */
    @GetMapping
    public List<LigneTableau> consulter(@RequestParam Long promotionId) {
        return tableauService.consulter(promotionId).stream()
                .map(LigneTableau::depuis)
                .toList();
    }
}
