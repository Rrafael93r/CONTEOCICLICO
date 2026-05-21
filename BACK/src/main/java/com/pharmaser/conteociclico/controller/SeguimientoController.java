package com.pharmaser.conteociclico.controller;

import com.pharmaser.conteociclico.dto.SeguimientoSedeDTO;
import com.pharmaser.conteociclico.service.SeguimientoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/seguimiento")
public class SeguimientoController {

    @Autowired
    private SeguimientoService seguimientoService;

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL_DE_INVENTARIO')")
    @GetMapping("/diario")
    public List<SeguimientoSedeDTO> getSeguimientoDiario() {
        return seguimientoService.getSeguimientoDiario();
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL_DE_INVENTARIO')")
    @GetMapping("/mensual")
    public com.pharmaser.conteociclico.dto.GlobalSeguimientoMensualDTO getSeguimientoMensual() {
        return seguimientoService.getSeguimientoMensual();
    }
}
