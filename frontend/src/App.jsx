import { useState } from 'react';
import Carte from './components/Carte.jsx';
import EnTete from './components/EnTete.jsx';
import FormulaireSession from './components/FormulaireSession.jsx';
import { IconeFormateur } from './components/Icones.jsx';

export default function App() {
  const [session, setSession] = useState(null);

  return (
    <>
      <EnTete session={session} />

      <main className="page">
        <Carte
          icone={<IconeFormateur />}
          titre="Ouvrir une session de cours"
          sousTitre="Espace formateur · le code de présence est valable 15 minutes"
        >
          <FormulaireSession session={session} onSessionOuverte={setSession} />
        </Carte>
      </main>
    </>
  );
}
