package com.kfokam48.epreuve.web;

import com.kfokam48.epreuve.domain.Exercice;
import com.kfokam48.epreuve.service.ExerciceService;
import com.kfokam48.epreuve.web.dto.ExerciceDepose;
import com.kfokam48.epreuve.web.dto.ExerciceDepot;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Exercices", description = "Dépôt des exercices par les étudiants")
@RestController
@RequestMapping("/api/exercices")
public class ExerciceController {

    private final ExerciceService exerciceService;

    public ExerciceController(ExerciceService exerciceService) {
        this.exerciceService = exerciceService;
    }

    /** EF3 : déposer le lien de son exercice pour une session. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExerciceDepose deposerExercice(@Valid @RequestBody ExerciceDepot requete) {
        Exercice exercice = exerciceService.deposerExercice(requete.sessionId(), requete.etudiantId(), requete.lien());
        return ExerciceDepose.depuis(exercice);
    }
}
