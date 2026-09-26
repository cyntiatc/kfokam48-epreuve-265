import { useState } from 'react';
import { consulterTableau } from '../api/client.js';
import Banniere from './Banniere.jsx';
import Champ from './Champ.jsx';

const formatMoyenne = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 2 });

/** RG6 (évolution de l'Étape 3) : note d'un exercice dont une seule des deux relectures est rendue. */
const DEFINITION_PROVISOIRE =
  'La moyenne inclut au moins une note provisoire : un seul des deux relecteurs de l’exercice a rendu sa note.';

/** EF7 : le formateur consulte, pour une promotion, l'activité de chaque étudiant (RG11). */
export default function TableauRecapitulatif() {
  const [promotionId, setPromotionId] = useState('');
  const [chargement, setChargement] = useState(false);
  const [tableau, setTableau] = useState(null);
  const [erreur, setErreur] = useState(null);

  async function soumettre(evenement) {
    evenement.preventDefault();
    setChargement(true);
    setErreur(null);
    try {
      const lignes = await consulterTableau(Number(promotionId));
      setTableau({ promotionId: Number(promotionId), lignes });
    } catch (e) {
      setErreur(e);
      setTableau(null);
    } finally {
      setChargement(false);
    }
  }

  return (
    <>
      <form className="formulaire" onSubmit={soumettre}>
        <Champ
          libelle="Identifiant de la promotion"
          aide="Le tableau reflète l’état actuel, notes encore modifiables et notes provisoires comprises."
          placeholder="ex. 1"
          type="number"
          min="1"
          step="1"
          value={promotionId}
          onChange={(e) => setPromotionId(e.target.value)}
          required
        />
        <button
          type="submit"
          className="bouton bouton--principal"
          disabled={chargement}
          aria-busy={chargement}
        >
          {chargement && <span className="spinner" aria-hidden="true" />}
          {chargement ? 'Chargement…' : 'Afficher le tableau'}
        </button>
      </form>

      {erreur && (
        <Banniere titre="Tableau indisponible" code={erreur.code}>
          {erreur.message}
        </Banniere>
      )}

      {tableau && <Resultat tableau={tableau} />}
    </>
  );
}

function Resultat({ tableau }) {
  if (tableau.lignes.length === 0) {
    return <p className="tableau-vide">Aucun étudiant dans cette promotion.</p>;
  }

  return (
    <div className="tableau-conteneur">
      <table className="tableau">
        <caption className="visuellement-masque">
          Tableau récapitulatif de la promotion n° {tableau.promotionId}
        </caption>
        <thead>
          <tr>
            <th scope="col">Étudiant</th>
            <th scope="col" className="nombre">Présences</th>
            <th scope="col" className="nombre">Exercices déposés</th>
            <th scope="col" className="nombre">Moyenne /20</th>
            <th scope="col" className="nombre">Relectures en attente</th>
          </tr>
        </thead>
        <tbody>
          {tableau.lignes.map((ligne) => (
            <tr key={ligne.etudiantId}>
              <th scope="row">{ligne.nom}</th>
              <td className="nombre">{ligne.presences}</td>
              <td className="nombre">{ligne.exercicesDeposes}</td>
              <td className="nombre">
                {ligne.moyenne === null
                  ? <span aria-label="aucune note">—</span>
                  : formatMoyenne.format(ligne.moyenne)}
                {ligne.estProvisoire && (
                  <span className="pastille pastille--provisoire" title={DEFINITION_PROVISOIRE}>
                    provisoire
                  </span>
                )}
              </td>
              <td className="nombre">
                {ligne.relecturesEnAttente > 0
                  ? <span className="pastille pastille--attente">{ligne.relecturesEnAttente}</span>
                  : 0}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {tableau.lignes.some((ligne) => ligne.estProvisoire) && (
        <p className="tableau-legende">
          <span className="pastille pastille--provisoire">provisoire</span> {DEFINITION_PROVISOIRE}
        </p>
      )}
    </div>
  );
}
