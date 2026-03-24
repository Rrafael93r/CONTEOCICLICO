package com.pharmaser.conteociclico.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "usuario")
@Data
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "usuario", length = 100)
    private String usuario;

    @Column(name = "contrasena", length = 100)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String contrasena;

    @Column(name = "sede", length = 45)
    private String sede;

    @Column(name = "numeroconteo")
    private Integer numeroConteo;

    @Column(name = "idrol")
    private Integer idRol;

    @ManyToOne
    @JoinColumn(name = "idrol", insertable = false, updatable = false)
    private Rol rol;
}
