package com.pharmaser.conteociclico.dto;

/**
 * DTO para el resumen de medicamentos por sede.
 * - "sede" es el código real de la sede (ej. "037").
 * - "idUsuario" se mantiene por compatibilidad con el frontend: contiene el id del
 *   usuario FARMACIA canónico (menor id con idRol=1) de esa sede.
 */
public class MedicamentoSummaryDTO {
    private String sede;
    private Integer idUsuario; // usuario canónico — sólo para compat. API
    private String sedeNombre;
    private Long total;
    private Long pendientes;
    private Long contados;
    private Integer coberturaA;
    private Integer coberturaB;
    private Integer coberturaC;

    public MedicamentoSummaryDTO(String sede, Integer idUsuario, String sedeNombre,
                                  Long total, Long pendientes, Long contados) {
        this.sede       = sede;
        this.idUsuario  = idUsuario;
        this.sedeNombre = sedeNombre;
        this.total      = total;
        this.pendientes = pendientes;
        this.contados   = contados;
    }

    public MedicamentoSummaryDTO() {}

    // Getters and Setters
    public String getSede()               { return sede; }
    public void setSede(String sede)      { this.sede = sede; }

    public Integer getIdUsuario()         { return idUsuario; }
    public void setIdUsuario(Integer id)  { this.idUsuario = id; }

    public String getSedeNombre()                    { return sedeNombre; }
    public void setSedeNombre(String sedeNombre)     { this.sedeNombre = sedeNombre; }

    public Long getTotal()                { return total; }
    public void setTotal(Long total)      { this.total = total; }

    public Long getPendientes()                   { return pendientes; }
    public void setPendientes(Long pendientes)    { this.pendientes = pendientes; }

    public Long getContados()                     { return contados; }
    public void setContados(Long contados)        { this.contados = contados; }

    public Integer getCoberturaA()                { return coberturaA; }
    public void setCoberturaA(Integer coberturaA) { this.coberturaA = coberturaA; }

    public Integer getCoberturaB()                { return coberturaB; }
    public void setCoberturaB(Integer coberturaB) { this.coberturaB = coberturaB; }

    public Integer getCoberturaC()                { return coberturaC; }
    public void setCoberturaC(Integer coberturaC) { this.coberturaC = coberturaC; }
}
