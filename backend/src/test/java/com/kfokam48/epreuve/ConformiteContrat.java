package com.kfokam48.epreuve;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.SimpleValidationReportFormat;
import com.atlassian.oai.validator.report.ValidationReport;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultMatcher;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * ENF1 : vérifie qu'une réponse MockMvc respecte api/contrat.yaml — statut déclaré pour la route,
 * type de contenu, champs obligatoires, types, formats, énumérations et valeurs nulles autorisées.
 */
public final class ConformiteContrat {

    private static final OpenApiInteractionValidator VALIDATEUR = OpenApiInteractionValidator
            .createForSpecificationUrl(localiserContrat())
            .build();

    private ConformiteContrat() {
    }

    /** Échoue si la réponse ne correspond pas à ce que le contrat déclare pour cette route et ce statut. */
    public static ResultMatcher conformeAuContrat() {
        return resultat -> {
            MockHttpServletRequest requete = resultat.getRequest();
            MockHttpServletResponse reponse = resultat.getResponse();

            SimpleResponse.Builder attendue = SimpleResponse.Builder.status(reponse.getStatus());
            if (reponse.getContentType() != null) {
                attendue.withContentType(reponse.getContentType());
            }
            String corps = reponse.getContentAsString(StandardCharsets.UTF_8);
            if (!corps.isEmpty()) {
                attendue.withBody(corps);
            }

            ValidationReport rapport = VALIDATEUR.validateResponse(
                    requete.getRequestURI(), Request.Method.valueOf(requete.getMethod()), attendue.build());
            if (rapport.hasErrors()) {
                throw new AssertionError("Réponse " + requete.getMethod() + " " + requete.getRequestURI() + " "
                        + reponse.getStatus() + " non conforme à api/contrat.yaml :\n"
                        + SimpleValidationReportFormat.getInstance().apply(rapport));
            }
        };
    }

    /** Le contrat est à la racine du dépôt : `../api` depuis le module backend (Maven), `api` depuis la racine (IDE). */
    private static String localiserContrat() {
        for (Path candidat : List.of(Path.of("..", "api", "contrat.yaml"), Path.of("api", "contrat.yaml"))) {
            if (Files.exists(candidat)) {
                return candidat.toAbsolutePath().normalize().toUri().toString();
            }
        }
        throw new IllegalStateException("api/contrat.yaml introuvable depuis " + Path.of("").toAbsolutePath());
    }
}
