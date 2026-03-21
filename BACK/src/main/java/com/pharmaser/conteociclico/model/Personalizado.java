package com.pharmaser.conteociclico.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "personalizado")
@Data
public class Personalizado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "idmedicamento")
    private Integer idMedicamento;

    @ManyToOne
    @JoinColumn(name = "idmedicamento", insertable = false, updatable = false)
    private Medicamento medicamento;

    @Column(name = "idusuario")
    private Integer idUsuario;

    @ManyToOne
    @JoinColumn(name = "idusuario", insertable = false, updatable = false)
    private Usuario usuario;

    @Column(name = "fecharegistro")
    private LocalDate fechaRegistro;

    @Column(name = "horaregistro")
    private LocalTime horaRegistro;

    @Column(name = "fechaprogramacion")
    private LocalDate fechaProgramacion;
}
