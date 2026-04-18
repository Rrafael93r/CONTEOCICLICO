package com.pharmaser.conteociclico.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "sistema_lock")
@Data
public class SistemaLock {

    @Id
    @Column(name = "proceso_nombre", length = 100)
    private String procesoNombre;

    @Column(name = "instancia_id", length = 100)
    private String instanciaId;

    @Column(name = "fecha_adquisicion")
    private LocalDateTime fechaAdquisicion;

    @Column(name = "fecha_expiracion")
    private LocalDateTime fechaExpiracion;

    @Column(name = "estado", length = 20)
    private String estado; // OCUPADO, LIBRE
}
