package com.kfokam48.epreuve.repository;

import com.kfokam48.epreuve.domain.Etudiant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EtudiantRepository extends JpaRepository<Etudiant, Long> {
}
