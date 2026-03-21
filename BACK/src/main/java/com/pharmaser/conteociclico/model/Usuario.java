package com.pharmaser.conteociclico.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

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
    private String contrasena;

    @Column(name = "sede", length = 45)
    private String sede;

    @Column(name = "numeroconteo")
    private Integer numeroConteo;

    @jakarta.persistence.Transient
    private Role roles = new Role(1, "ADMIN");

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Role {
        private int id;
        private String name;
    }
}
