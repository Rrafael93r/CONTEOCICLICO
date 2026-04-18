package com.pharmaser.conteociclico.repository;

import com.pharmaser.conteociclico.model.ClasificacionAbcPorcentajeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ClasificacionAbcPorcentajeConfigRepository extends JpaRepository<ClasificacionAbcPorcentajeConfig, Integer> {
    List<ClasificacionAbcPorcentajeConfig> findByActivoTrueOrderByPorcentajeMaxAsc();
}
