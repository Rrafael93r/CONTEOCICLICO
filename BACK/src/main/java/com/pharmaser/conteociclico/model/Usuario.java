package com.pharmaser.conteociclico.model;

import jakarta.persistence.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "usuario")
@Data
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
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

    @Column(name = "idrol")
    @JsonProperty("idRol")
    private Integer idRol;

    @Column(name = "fecha_bloque_extra")
    private java.time.LocalDate fechaBloqueExtra;

    @ManyToOne
    @JoinColumn(name = "idrol", insertable = false, updatable = false)
    private Rol rol;
}
