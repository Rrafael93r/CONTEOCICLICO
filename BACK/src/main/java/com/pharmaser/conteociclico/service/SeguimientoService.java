package com.pharmaser.conteociclico.service;

import com.pharmaser.conteociclico.dto.GlobalSeguimientoMensualDTO;
import com.pharmaser.conteociclico.dto.SeguimientoMensualDTO;
import com.pharmaser.conteociclico.dto.SeguimientoSedeDTO;
import com.pharmaser.conteociclico.repository.MedicamentoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SeguimientoService {

    @Autowired
    private MedicamentoRepository medicamentoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<SeguimientoSedeDTO> getSeguimientoDiario() {
        String sql = "SELECT u.usuario as nombre_usuario, " +
                     "(SELECT COUNT(*) FROM detalleconteo d WHERE d.idusuario = u.id AND d.fecharegistro = CURDATE()) as total_asignados, " +
                     "(SELECT COUNT(*) FROM detalleconteo d WHERE d.idusuario = u.id AND d.fecharegistro = CURDATE() AND d.cantidadcontada IS NOT NULL) as total_contados " +
                     "FROM usuario u " +
                     "WHERE u.idrol = 1 " +
                     "ORDER BY total_asignados DESC";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String nombre = rs.getString("nombre_usuario");
            long asignados = rs.getLong("total_asignados");
            long contados = rs.getLong("total_contados");
            
            double porcentaje = asignados > 0 ? (double) contados / asignados * 100 : 0;
            if (porcentaje > 100) porcentaje = 100;
            
            return new SeguimientoSedeDTO(nombre, asignados, contados, Math.round(porcentaje * 10.0) / 10.0, "ABC");
        });
    }

    public GlobalSeguimientoMensualDTO getSeguimientoMensual() {
        // getMedicamentoStatsWithCanonicalUser devuelve 8 columnas:
        // [0] idusuario, [1] sede, [2] fecha_bloque_extra, [3] tipo,
        // [4] total,     [5] contados, [6] a_una, [7] a_dos
        List<Object[]> stats = medicamentoRepository.getMedicamentoStatsWithCanonicalUser();

        Map<String, SeguimientoMensualDTO> reportePorSedesMap = new HashMap<>();
        SeguimientoMensualDTO consolidadoGlobal = new SeguimientoMensualDTO();
        consolidadoGlobal.setSede("Global");

        for (Object[] row : stats) {
            Integer idUser = (row[0] != null) ? ((Number) row[0]).intValue() : 0;
            String sede     = (String) row[1];
            String fechaExtra = (row[2] != null) ? row[2].toString() : null;
            String type     = (row[3] != null) ? row[3].toString().toUpperCase() : "C";
            long total      = ((Number) row[4]).longValue();
            long contadas   = ((Number) row[5]).longValue();
            long aUna       = (row[6] != null) ? ((Number) row[6]).longValue() : 0;
            long aDos       = (row[7] != null) ? ((Number) row[7]).longValue() : 0;

            // Usamos sede como llave única (una entrada por sede)
            String key = sede != null ? sede : "SIN_SEDE";
            SeguimientoMensualDTO dto = reportePorSedesMap.computeIfAbsent(key, k -> {
                SeguimientoMensualDTO n = new SeguimientoMensualDTO();
                n.setIdUsuario(idUser);
                n.setUsuario(sede != null ? sede : "DESCONOCIDO");
                n.setSede(sede);
                n.setFechaBloqueExtra(fechaExtra);
                return n;
            });

            if ("A".equals(type)) {
                dto.setTotalA(dto.getTotalA() + total);
                dto.setContadasA(dto.getContadasA() + contadas);
                dto.setAContadasUnaVez(dto.getAContadasUnaVez() + aUna);
                dto.setAContadasDosVeces(dto.getAContadasDosVeces() + aDos);
            } else if ("B".equals(type)) {
                dto.setTotalB(dto.getTotalB() + total);
                dto.setContadasB(dto.getContadasB() + contadas);
            } else {
                dto.setTotalC(dto.getTotalC() + total);
                dto.setContadasC(dto.getContadasC() + contadas);
            }
            
            sumarAlConsolidadoRaw(consolidadoGlobal, type, total, contadas, aUna, aDos);
        }

        List<SeguimientoMensualDTO> listResponse = new ArrayList<>(reportePorSedesMap.values());
        for (SeguimientoMensualDTO d : listResponse) calcularCobertura(d);
        calcularCobertura(consolidadoGlobal);

        return new GlobalSeguimientoMensualDTO(listResponse, consolidadoGlobal);
    }

    private void calcularCobertura(SeguimientoMensualDTO dto) {
        long total = dto.getTotalA() + dto.getTotalB() + dto.getTotalC();
        long contadas = dto.getContadasA() + dto.getContadasB() + dto.getContadasC();
        if (total > 0) dto.setCoberturaSede(Math.round(((double) contadas / total * 100) * 100.0) / 100.0);
        dto.setNoContadasA(dto.getTotalA() - dto.getContadasA());
        dto.setNoContadasB(dto.getTotalB() - dto.getContadasB());
        dto.setNoContadasC(dto.getTotalC() - dto.getContadasC());
    }

    private void sumarAlConsolidadoRaw(SeguimientoMensualDTO consolidado, String type, long total, long contadas, long aUna, long aDos) {
        if ("A".equals(type)) {
            consolidado.setTotalA(consolidado.getTotalA() + total);
            consolidado.setContadasA(consolidado.getContadasA() + contadas);
            consolidado.setAContadasUnaVez(consolidado.getAContadasUnaVez() + aUna);
            consolidado.setAContadasDosVeces(consolidado.getAContadasDosVeces() + aDos);
        } else if ("B".equals(type)) {
            consolidado.setTotalB(consolidado.getTotalB() + total);
            consolidado.setContadasB(consolidado.getContadasB() + contadas);
        } else {
            consolidado.setTotalC(consolidado.getTotalC() + total);
            consolidado.setContadasC(consolidado.getContadasC() + contadas);
        }
    }
}
