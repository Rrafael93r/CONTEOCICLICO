package com.pharmaser.conteociclico.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GlobalSeguimientoMensualDTO {
    private List<SeguimientoMensualDTO> reportePorSedes;
    private SeguimientoMensualDTO consolidadoGlobal;
}
