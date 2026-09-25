import { useState } from 'react';
import Carte from './components/Carte.jsx';
import EnTete from './components/EnTete.jsx';
import FormulairePresence from './components/FormulairePresence.jsx';
import FormulaireSession from './components/FormulaireSession.jsx';
import { IconeEtudiant, IconeFormateur } from './components/Icones.jsx';
import Onglets, { PanneauOnglet } from './components/Onglets.jsx';

const ONGLETS = [
  { id: 'formateur', libelle: 'Espace Formateur', icone: <IconeFormateur taille={18} /> },
  { id: 'etudiant', libelle: 'Espace Étudiant', icone: <IconeEtudiant taille={18} /> },
];

export default function App() {
  const [session, setSession] = useState(null);
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
            <FormulairePresence />
          </Carte>
        </PanneauOnglet>
      </main>
    </>
  );
}
