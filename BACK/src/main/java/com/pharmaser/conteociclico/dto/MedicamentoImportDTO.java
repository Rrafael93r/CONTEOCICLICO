package com.pharmaser.conteociclico.dto;

import lombok.Data;

@Data
public class MedicamentoImportDTO {
    private String plu;
    private String descripcion;
    /** Código de sede destino (ej. "037"). Reemplaza idUsuario. */
    private String sede;
    private Integer inventario;
    private Double costo;
    private Double costoTotal;
    private String tipomolecula;
    private Integer reglaAplicadaId;
    private java.time.LocalDateTime fechaClasificacion;
    private String codigogenerico;
}
