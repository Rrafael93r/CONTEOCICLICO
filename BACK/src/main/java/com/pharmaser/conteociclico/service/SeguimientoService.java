package com.pharmaser.conteociclico.service;

import com.pharmaser.conteociclico.dto.GlobalSeguimientoMensualDTO;
import com.pharmaser.conteociclico.dto.SeguimientoMensualDTO;
import com.pharmaser.conteociclico.dto.SeguimientoSedeDTO;
import com.pharmaser.conteociclico.model.DetalleConteo;
import com.pharmaser.conteociclico.model.Personalizado;
import com.pharmaser.conteociclico.model.Usuario;
import com.pharmaser.conteociclico.repository.DetalleConteoRepository;
import com.pharmaser.conteociclico.repository.PersonalizadoRepository;
import com.pharmaser.conteociclico.repository.UsuarioRepository;
import com.pharmaser.conteociclico.repository.MedicamentoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SeguimientoService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private DetalleConteoRepository detalleConteoRepository;

    @Autowired
    private PersonalizadoRepository personalizadoRepository;

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
        List<Object[]> stats = medicamentoRepository.getMedicamentoStatsPerSedeAndType();

        Map<String, SeguimientoMensualDTO> reportePorSedesMap = new HashMap<>();
        SeguimientoMensualDTO consolidadoGlobal = new SeguimientoMensualDTO();
        consolidadoGlobal.setSede("Global");

        for (Object[] row : stats) {
            Integer idUser = ((Number) row[0]).intValue();
            String usuario = (String) row[1];
            String sede = (String) row[2];
            String fechaExtra = (row[3] != null) ? row[3].toString() : null;
            String type = (row[4] != null) ? row[4].toString().toUpperCase() : "C";
            long total = ((Number) row[5]).longValue();
            long contadas = ((Number) row[6]).longValue();
            long aUna = (row[7] != null) ? ((Number) row[7]).longValue() : 0;
            long aDos = (row[8] != null) ? ((Number) row[8]).longValue() : 0;
            
            // Usamos una combinacion para la llave del mapa para asegurar unicidad
            String key = idUser + "_" + sede;
            SeguimientoMensualDTO dto = reportePorSedesMap.computeIfAbsent(key, k -> {
                SeguimientoMensualDTO n = new SeguimientoMensualDTO();
                n.setIdUsuario(idUser);
                String nombreAMostrar = (usuario != null && !usuario.trim().isEmpty()) ? usuario : (sede != null ? sede : "DESCONOCIDO");
                n.setUsuario(nombreAMostrar);
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
