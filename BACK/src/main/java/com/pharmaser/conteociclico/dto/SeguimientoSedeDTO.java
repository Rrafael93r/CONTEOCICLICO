package com.pharmaser.conteociclico.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SeguimientoSedeDTO {
    private String sede;
    private long asignados;
    private long contados;
    private double porcentaje;
    private String tipoConteo;
}
