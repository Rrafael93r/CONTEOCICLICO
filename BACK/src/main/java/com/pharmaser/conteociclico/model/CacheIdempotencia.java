package com.pharmaser.conteociclico.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "cache_idempotencia")
@Data
public class CacheIdempotencia {

    @Id
    @Column(name = "hash_sha256", length = 64)
    private String hashSha256;

    @Column(name = "nombre_archivo", length = 255)
    private String nombreArchivo;

    @Column(name = "tamano_bytes")
    private Long tamanoBytes;

    @Column(name = "fecha_procesamiento")
    private LocalDateTime fechaProcesamiento;
}
