package com.kfokam48.epreuve.service;

import com.kfokam48.epreuve.domain.SessionCours;

/**
 * Résultat d'une clôture (EF8) : la session clôturée et ce que la clôture a changé pour ses exercices.
 *
 * @param exercicesDefinitifs    exercices dont la note vient de devenir définitive : aucune de leurs relectures
 *                               n'est en attente (RELU ou EN_RELECTURE -> DEFINITIF, H10)
 * @param exercicesSansRelecture exercices restés sans relecteur (DEPOSE -> SANS_RELECTURE)
 * @param relecturesEnAttente    relectures attribuées non rendues, qui pourront encore l'être une fois (H8)
 */
public record BilanCloture(
        SessionCours session,
        int exercicesDefinitifs,
        int exercicesSansRelecture,
        int relecturesEnAttente) {
}
