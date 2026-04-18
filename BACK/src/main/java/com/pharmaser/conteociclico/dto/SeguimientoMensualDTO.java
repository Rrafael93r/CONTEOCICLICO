package com.pharmaser.conteociclico.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SeguimientoMensualDTO {
    private Integer idUsuario;
    private String fechaBloqueExtra;
    private String sede;
    private String usuario;
    private long totalA;
    private long totalB;
    private long totalC;
    private long contadasA;
    private long contadasB;
    private long contadasC;
    private long noContadasA;
    private long noContadasB;
    private long noContadasC;
    private long aContadasUnaVez;
    private long aContadasDosVeces;
    private double coberturaSede;
}
