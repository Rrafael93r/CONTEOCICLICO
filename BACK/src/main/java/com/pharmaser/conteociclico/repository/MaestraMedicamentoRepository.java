package com.pharmaser.conteociclico.repository;

import com.pharmaser.conteociclico.model.MaestraMedicamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface MaestraMedicamentoRepository extends JpaRepository<MaestraMedicamento, Long> {
    Optional<MaestraMedicamento> findByPlu(String plu);
}
