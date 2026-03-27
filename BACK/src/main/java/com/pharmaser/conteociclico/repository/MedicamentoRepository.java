package com.pharmaser.conteociclico.repository;

import com.pharmaser.conteociclico.model.Medicamento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MedicamentoRepository extends JpaRepository<Medicamento, Integer> {
    Optional<Medicamento> findByPlu(String plu);
    List<Medicamento> findAllByPlu(String plu);
}
