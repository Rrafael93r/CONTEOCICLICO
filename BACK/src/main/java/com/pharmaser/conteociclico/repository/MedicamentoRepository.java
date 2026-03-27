package com.pharmaser.conteociclico.repository;

import com.pharmaser.conteociclico.model.Medicamento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MedicamentoRepository extends JpaRepository<Medicamento, Integer> {
    Optional<Medicamento> findByPluAndIdUsuario(String plu, Integer idUsuario);
    List<Medicamento> findAllByPlu(String plu);
}
