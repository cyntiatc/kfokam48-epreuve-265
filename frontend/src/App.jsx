import { useState } from 'react';
import Carte from './components/Carte.jsx';
import EnTete from './components/EnTete.jsx';
import FormulaireExercice from './components/FormulaireExercice.jsx';
import FormulairePresence from './components/FormulairePresence.jsx';
import FormulaireRelecture from './components/FormulaireRelecture.jsx';
import FormulaireSession from './components/FormulaireSession.jsx';
import {
  IconeEtudiant, IconeExercice, IconeFormateur, IconeRelecture, IconeTableau,
} from './components/Icones.jsx';
import Onglets, { PanneauOnglet } from './components/Onglets.jsx';
import TableauRecapitulatif from './components/TableauRecapitulatif.jsx';

const ONGLETS = [
  { id: 'formateur', libelle: 'Espace Formateur', icone: <IconeFormateur taille={18} /> },
  { id: 'etudiant', libelle: 'Espace Étudiant', icone: <IconeEtudiant taille={18} /> },
  { id: 'exercice', libelle: 'Dépôt d’exercice', icone: <IconeExercice taille={18} /> },
  { id: 'relecture', libelle: 'Relecture', icone: <IconeRelecture taille={18} /> },
  { id: 'tableau', libelle: 'Tableau', icone: <IconeTableau taille={18} /> },
];

export default function App() {
  const [session, setSession] = useState(null);
  const [presence, setPresence] = useState(null);
  const [ongletActif, setOngletActif] = useState('formateur');

  return (
    <>
      <EnTete session={session} />

      <main className="page">
        <Onglets onglets={ONGLETS} actif={ongletActif} onChange={setOngletActif} libelle="Choix de l'espace" />

        <PanneauOnglet id="formateur" actif={ongletActif}>
          <Carte
            icone={<IconeFormateur />}
            titre="Ouvrir une session de cours"
            sousTitre="Le code de présence généré est valable 15 minutes"
          >
            <FormulaireSession session={session} onSessionOuverte={setSession} />
          </Carte>
        </PanneauOnglet>

        <PanneauOnglet id="etudiant" actif={ongletActif}>
          <Carte
            icone={<IconeEtudiant />}
            titre="Émarger"
            sousTitre="Saisissez le code communiqué par le formateur"
          >
            <FormulairePresence onPresenceEnregistree={setPresence} />
          </Carte>
        </PanneauOnglet>

        <PanneauOnglet id="exercice" actif={ongletActif}>
          <Carte
            icone={<IconeExercice />}
            titre="Déposer un exercice"
            sousTitre="Espace étudiant · le lien de votre travail pour la session"
          >
            {/* La clé recrée le formulaire à chaque émargement, pour le pré-remplir avec la nouvelle présence. */}
            <FormulaireExercice
              key={presence?.id ?? 'sans-presence'}
              sessionIdInitial={presence?.sessionId}
              etudiantIdInitial={presence?.etudiantId}
            />
          </Carte>
        </PanneauOnglet>

        <PanneauOnglet id="relecture" actif={ongletActif}>
          <Carte
            icone={<IconeRelecture />}
            titre="Relire l’exercice d’un pair"
            sousTitre="Note modifiable jusqu’à la clôture de la session"
          >
            {/* Le relecteur est un étudiant présent : son identifiant est repris du dernier émargement. */}
            <FormulaireRelecture key={presence?.id ?? 'sans-presence'} etudiantIdInitial={presence?.etudiantId} />
          </Carte>
        </PanneauOnglet>

        <PanneauOnglet id="tableau" actif={ongletActif}>
          <Carte
            icone={<IconeTableau />}
            titre="Tableau récapitulatif"
            sousTitre="Espace formateur · présences, dépôts, moyennes et relectures par étudiant"
          >
            <TableauRecapitulatif />
          </Carte>
        </PanneauOnglet>
      </main>
    </>
  );
}
