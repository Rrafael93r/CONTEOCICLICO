package com.pharmaser.conteociclico.repository;

import com.pharmaser.conteociclico.model.CacheIdempotencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CacheIdempotenciaRepository extends JpaRepository<CacheIdempotencia, String> {
}
