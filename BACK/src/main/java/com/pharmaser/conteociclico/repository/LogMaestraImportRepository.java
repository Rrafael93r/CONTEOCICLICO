package com.pharmaser.conteociclico.repository;

import com.pharmaser.conteociclico.model.LogMaestraImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LogMaestraImportRepository extends JpaRepository<LogMaestraImport, Long> {
}
