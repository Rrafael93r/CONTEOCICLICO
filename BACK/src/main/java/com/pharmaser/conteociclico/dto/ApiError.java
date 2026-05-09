package com.pharmaser.conteociclico.dto;

import java.time.LocalDateTime;

public class ApiError {

    private String codigo;
    private String mensaje;
    private LocalDateTime timestamp;

    public ApiError(String codigo, String mensaje, LocalDateTime timestamp) {
        this.codigo = codigo;
        this.mensaje = mensaje;
        this.timestamp = timestamp;
    }

    public String getCodigo()       { return codigo; }
    public String getMensaje()      { return mensaje; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
