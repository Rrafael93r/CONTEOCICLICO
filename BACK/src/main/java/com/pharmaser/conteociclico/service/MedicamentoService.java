package com.pharmaser.conteociclico.service;

import com.pharmaser.conteociclico.model.Medicamento;
import com.pharmaser.conteociclico.dto.MedicamentoImportDTO;
import com.pharmaser.conteociclico.repository.MedicamentoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;

@Service
public class MedicamentoService {

    @Autowired
    private MedicamentoRepository medicamentoRepository;

    @Autowired
    private com.pharmaser.conteociclico.repository.UsuarioRepository usuarioRepository;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initIndexes() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='medicamento' AND index_name='idx_plu_usuario'", Integer.class);
            
            if (count != null && count == 0) {
                jdbcTemplate.execute("ALTER TABLE medicamento ADD UNIQUE INDEX idx_plu_usuario (plu, idusuario)");
            } else {
            }
        } catch (Exception e) {
        }

        try {
            // Índices de rendimiento para consultas veloces (Evita Full Table Scans)
            Integer countUser = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='medicamento' AND index_name='idx_usuario_estado'", Integer.class);
            if (countUser != null && countUser == 0) {
                jdbcTemplate.execute("ALTER TABLE medicamento ADD INDEX idx_usuario_estado (idusuario, estadodelconteo)");
            }

            Integer countDetalle = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='detalleconteo' AND index_name='idx_detalle_usr_fecha'", Integer.class);
            if (countDetalle != null && countDetalle == 0) {
                jdbcTemplate.execute("ALTER TABLE detalleconteo ADD INDEX idx_detalle_usr_fecha (idusuario, fecharegistro)");
            }
        } catch (Exception e) {
        }
    }

    public List<Medicamento> getAllMedicamentos() {
        return medicamentoRepository.findAll();
    }

    public List<Medicamento> getMedicamentosByUsuario(Integer idUsuario) {
        return medicamentoRepository.findByIdUsuario(idUsuario);
    }

    @org.springframework.transaction.annotation.Transactional
    public void bulkUpdateInventory(java.util.List<java.util.Map<String, Object>> items) {
        
        // 1. Obtener mapeo de usuarios por sede
        java.util.List<com.pharmaser.conteociclico.model.Usuario> usuarios = usuarioRepository.findAll();
        java.util.Map<String, Integer> sedeMap = new java.util.HashMap<>();
        for (com.pharmaser.conteociclico.model.Usuario u : usuarios) {
            if (u.getSede() != null) {
                String s = u.getSede().trim();
                sedeMap.put(s, u.getId());
                try { sedeMap.put(String.valueOf(Integer.parseInt(s)), u.getId()); } catch (Exception ignore) {}
            }
        }

        // Para evitar duplicidad de lógica, usamos una lista filtrada y lista para JDBC
        final java.util.List<java.util.Map<String, Object>> validItems = new java.util.ArrayList<>();
        for (java.util.Map<String, Object> item : items) {
            String sedeStr = (String) item.get("sede");
            if (sedeStr != null && sedeMap.containsKey(sedeStr.trim())) {
                item.put("idUsuario", sedeMap.get(sedeStr.trim()));
                validItems.add(item);
            }
        }

        String sql = "UPDATE medicamento SET inventario = ? WHERE plu = ? AND idusuario = ?";

        int batchSize = 10000;
        for (int i = 0; i < validItems.size(); i += batchSize) {
            final java.util.List<java.util.Map<String, Object>> batch = validItems.subList(i, Math.min(i + batchSize, validItems.size()));
            jdbcTemplate.batchUpdate(sql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                @Override
                public void setValues(java.sql.PreparedStatement ps, int j) throws java.sql.SQLException {
                    java.util.Map<String, Object> it = batch.get(j);
                    ps.setInt(1, it.get("cantidad") != null ? (Integer) it.get("cantidad") : 0);
                    ps.setString(2, (String) it.get("plu"));
                    ps.setInt(3, (Integer) it.get("idUsuario"));
                }
                @Override
                public int getBatchSize() { return batch.size(); }
            });
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public void importFromExternalData(java.util.List<MedicamentoImportDTO> items) {
        long startTotal = System.currentTimeMillis();

        // El índice ya se verifica/crea en el @PostConstruct al arranque de la aplicación.
        // Hacemos el proceso Batch con ON DUPLICATE KEY UPDATE.

        String sql = "INSERT INTO medicamento (plu, idusuario, descripcion, codigogenerico, laboratorio, inventario, costo, costototal, estadodelconteo) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'no') " +
                     "ON DUPLICATE KEY UPDATE " +
                     "descripcion = VALUES(descripcion), codigogenerico = VALUES(codigogenerico), laboratorio = VALUES(laboratorio), " +
                     "inventario = VALUES(inventario), costo = VALUES(costo), costototal = VALUES(costototal)";

        int batchSize = 10000;
        int processed = 0;
        for (int i = 0; i < items.size(); i += batchSize) {
            long startBatch = System.currentTimeMillis();
            final java.util.List<MedicamentoImportDTO> batch = items.subList(i, Math.min(i + batchSize, items.size()));
            jdbcTemplate.batchUpdate(sql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                @Override
                public void setValues(java.sql.PreparedStatement ps, int j) throws java.sql.SQLException {
                    MedicamentoImportDTO item = batch.get(j);
                    ps.setString(1, item.getPlu());
                    ps.setInt(2, item.getIdUsuario());
                    ps.setString(3, item.getDescripcion());
                    ps.setString(4, item.getCodigoGenerico());
                    ps.setString(5, item.getLaboratorio());
                    ps.setInt(6, item.getInventario() != null ? item.getInventario() : 0);
                    ps.setDouble(7, item.getCosto() != null ? item.getCosto() : 0.0);
                    ps.setDouble(8, item.getCostoTotal() != null ? item.getCostoTotal() : 0.0);
                }
                @Override
                public int getBatchSize() { return batch.size(); }
            });
            processed += batch.size();
        }
    }

    public Optional<Medicamento> getMedicamentoById(Integer id) {
        return medicamentoRepository.findById(id);
    }

    public Medicamento saveMedicamento(Medicamento medicamento) {
        return medicamentoRepository.save(medicamento);
    }

    @org.springframework.transaction.annotation.Transactional
    public void resetStatusByUsuario(Integer idUsuario) {
        int updatedCount = jdbcTemplate.update("UPDATE medicamento SET estadodelconteo = 'no' WHERE idusuario = ? AND estadodelconteo = 'sí'", idUsuario);
    }

    public void deleteMedicamento(Integer id) {
        medicamentoRepository.deleteById(id);
    }

    @org.springframework.transaction.annotation.Transactional
    public void markAsCounted(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return;
        String idList = ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        String sql = "UPDATE medicamento SET estadodelconteo = 'sí' WHERE id IN (" + idList + ")";
        jdbcTemplate.update(sql);
    }
}
