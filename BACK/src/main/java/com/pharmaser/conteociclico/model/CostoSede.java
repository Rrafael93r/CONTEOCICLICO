package com.pharmaser.conteociclico.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Entity
@Table(
    name = "costos_sede",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_costos_sede_centro_plu",
        columnNames = {"centro_costo", "plu"}
    )
)
@Data
public class CostoSede {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "centro_costo")
    private Integer centroCosto;

    @Column(name = "plu", length = 50)
    private String plu;

    @Column(name = "costo_unitario", precision = 10, scale = 2)
    private BigDecimal costoUnitario;
}
