package com.pharmaser.conteociclico.repository;

import com.pharmaser.conteociclico.model.SistemaLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SistemaLockRepository extends JpaRepository<SistemaLock, String> {
}
