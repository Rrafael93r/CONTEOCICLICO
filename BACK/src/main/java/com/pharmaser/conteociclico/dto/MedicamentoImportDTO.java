package com.pharmaser.conteociclico.dto;

import lombok.Data;

@Data
public class MedicamentoImportDTO {
    private String plu;
    private String descripcion;
    private String codigoGenerico;
    private String laboratorio;
    private Integer idUsuario;
    private Integer inventario;
    private Double costo;
    private Double costoTotal;
}
