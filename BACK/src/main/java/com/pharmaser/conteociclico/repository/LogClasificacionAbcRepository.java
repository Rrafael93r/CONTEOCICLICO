package com.pharmaser.conteociclico.repository;

import com.pharmaser.conteociclico.model.LogClasificacionAbc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LogClasificacionAbcRepository extends JpaRepository<LogClasificacionAbc, Long> {
}
