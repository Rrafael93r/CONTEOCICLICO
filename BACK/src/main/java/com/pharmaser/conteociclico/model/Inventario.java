package com.pharmaser.conteociclico.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "inventario")
@Data
public class Inventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "idusuario")
    private Integer idUsuario;

    @Column(name = "idmedicamento")
    private Integer idMedicamento;

    @Column(name = "cantidadactual")
    private Integer cantidadActual;

    @Column(name = "fecharegistro")
    private LocalDate fechaRegistro;

    @Column(name = "horaregistro")
    private LocalTime horaRegistro;
}
