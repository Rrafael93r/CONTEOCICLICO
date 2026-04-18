package com.pharmaser.conteociclico.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "sistema_configuracion")
@Data
public class SistemaConfiguracion {

    @Id
    @Column(name = "clave_config", length = 100)
    private String clave;

    @Column(name = "valor_config")
    private String valor;
}
