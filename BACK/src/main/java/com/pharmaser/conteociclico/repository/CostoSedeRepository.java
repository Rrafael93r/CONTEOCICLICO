package com.pharmaser.conteociclico.repository;

import com.pharmaser.conteociclico.model.CostoSede;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CostoSedeRepository extends JpaRepository<CostoSede, Integer> {
    Optional<CostoSede> findByCentroCostoAndPlu(Integer centroCosto, String plu);
}
