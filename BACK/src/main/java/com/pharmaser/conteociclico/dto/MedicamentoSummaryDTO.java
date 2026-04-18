package com.pharmaser.conteociclico.dto;

public class MedicamentoSummaryDTO {
    private Integer idUsuario;
    private String sedeNombre;
    private Long total;
    private Long pendientes;
    private Long contados;
    private Integer coberturaA;
    private Integer coberturaB;
    private Integer coberturaC;

    public MedicamentoSummaryDTO(Integer idUsuario, String sedeNombre, Long total, Long pendientes, Long contados) {
        this.idUsuario = idUsuario;
        this.sedeNombre = sedeNombre;
        this.total = total;
        this.pendientes = pendientes;
        this.contados = contados;
    }

    public MedicamentoSummaryDTO() {}

    // Getters and Setters
    public Integer getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Integer idUsuario) { this.idUsuario = idUsuario; }

    public String getSedeNombre() { return sedeNombre; }
    public void setSedeNombre(String sedeNombre) { this.sedeNombre = sedeNombre; }

    public Long getTotal() { return total; }
    public void setTotal(Long total) { this.total = total; }

    public Long getPendientes() { return pendientes; }
    public void setPendientes(Long pendientes) { this.pendientes = pendientes; }

    public Long getContados() { return contados; }
    public void setContados(Long contados) { this.contados = contados; }

    public Integer getCoberturaA() { return coberturaA; }
    public void setCoberturaA(Integer coberturaA) { this.coberturaA = coberturaA; }

    public Integer getCoberturaB() { return coberturaB; }
    public void setCoberturaB(Integer coberturaB) { this.coberturaB = coberturaB; }

    public Integer getCoberturaC() { return coberturaC; }
    public void setCoberturaC(Integer coberturaC) { this.coberturaC = coberturaC; }
}
