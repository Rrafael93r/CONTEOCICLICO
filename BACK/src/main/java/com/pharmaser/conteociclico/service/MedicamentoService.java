package com.pharmaser.conteociclico.service;

import com.pharmaser.conteociclico.model.Medicamento;
import com.pharmaser.conteociclico.dto.MedicamentoImportDTO;
import com.pharmaser.conteociclico.repository.MedicamentoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

@Service
public class MedicamentoService {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MedicamentoService.class);

    @Autowired
    private MedicamentoRepository medicamentoRepository;

    @Autowired
    private com.pharmaser.conteociclico.repository.UsuarioRepository usuarioRepository;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private com.pharmaser.conteociclico.repository.ClasificacionAbcPorcentajeConfigRepository abcPorcentajeRepository;

    @Autowired
    private com.pharmaser.conteociclico.repository.SistemaLockRepository lockRepository;

    @Autowired
    private com.pharmaser.conteociclico.repository.LogClasificacionAbcRepository logAbcRepository;

    private static final String LOCK_NAME = "ABC_RECLASSIFICATION";
    private static final int LOCK_TIMEOUT_MINUTES = 15;
    private final String instanciaId = java.util.UUID.randomUUID().toString();

    @PostConstruct
    public void initIndexes() {
        try {
            // Indice PLU/Usuario (Unico)
            executeIndexIfNotExists("idx_plu_usuario",
                    "ALTER TABLE medicamento ADD UNIQUE INDEX idx_plu_usuario (plu, idusuario)");

            // Indice Compuesto para ABC y Consultas (idusuario, costototal DESC)
            executeIndexIfNotExists("idx_abc_consulta",
                    "ALTER TABLE medicamento ADD INDEX idx_abc_consulta (idusuario, costototal DESC)");

            // Indice para Detalle Conteo
            executeIndexIfNotExists("idx_detalle_usr_fecha",
                    "ALTER TABLE detalleconteo ADD INDEX idx_detalle_usr_fecha (idusuario, fecharegistro)");
        } catch (Exception e) {
            logger.error("Error inicializando índices: {}", e.getMessage());
        }
    }

    private void executeIndexIfNotExists(String indexName, String alterQuery) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='medicamento' AND index_name=?",
                    Integer.class, indexName);

            if (count != null && count == 0) {
                jdbcTemplate.execute(alterQuery);
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
        java.util.List<com.pharmaser.conteociclico.model.Usuario> usuarios = usuarioRepository.findAll();
        java.util.Map<String, Integer> sedeMap = new java.util.HashMap<>();
        for (com.pharmaser.conteociclico.model.Usuario u : usuarios) {
            if (u.getSede() != null) {
                String s = u.getSede().trim();
                sedeMap.put(s, u.getId());
                try {
                    sedeMap.put(String.valueOf(Integer.parseInt(s)), u.getId());
                } catch (Exception ignore) {
                }
            }
        }

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
            final java.util.List<java.util.Map<String, Object>> batch = validItems.subList(i,
                    Math.min(i + batchSize, validItems.size()));
            jdbcTemplate.batchUpdate(sql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                @Override
                public void setValues(@org.springframework.lang.NonNull java.sql.PreparedStatement ps, int j)
                        throws java.sql.SQLException {
                    java.util.Map<String, Object> it = batch.get(j);
                    ps.setInt(1, it.get("cantidad") != null ? (Integer) it.get("cantidad") : 0);
                    ps.setString(2, (String) it.get("plu"));
                    ps.setInt(3, (Integer) it.get("idUsuario"));
                }

                @Override
                public int getBatchSize() {
                    return batch.size();
                }
            });
        }
    }

    public void importFromExternalData(java.util.List<MedicamentoImportDTO> items) {
        if (items == null || items.isEmpty())
            return;

        String sql = "INSERT INTO medicamento (plu, idusuario, descripcion, inventario, costo, costototal, tipomolecula, estadodelconteo, fecha_clasificacion, fecha_actualizacion, contado_mes_anterior) "
                +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 'no', ?, ?, 0) " +
                "ON DUPLICATE KEY UPDATE " +
                "descripcion = VALUES(descripcion), " +
                "inventario = VALUES(inventario), costo = VALUES(costo), costototal = VALUES(costototal), tipomolecula = VALUES(tipomolecula), fecha_clasificacion = VALUES(fecha_clasificacion), fecha_actualizacion = VALUES(fecha_actualizacion)";

        java.time.LocalDateTime ahora = java.time.LocalDateTime.now();

        for (MedicamentoImportDTO item : items) {
            item.setTipomolecula("C"); // Valor inicial por defecto, Pareto reclasificará
            item.setFechaClasificacion(ahora);
        }

        int batchSize = 1000;
        for (int i = 0; i < items.size(); i += batchSize) {
            final java.util.List<MedicamentoImportDTO> batch = items.subList(i, Math.min(i + batchSize, items.size()));
            jdbcTemplate.batchUpdate(sql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                @Override
                public void setValues(@org.springframework.lang.NonNull java.sql.PreparedStatement ps, int j)
                        throws java.sql.SQLException {
                    MedicamentoImportDTO item = batch.get(j);
                    ps.setString(1, item.getPlu());
                    ps.setInt(2, item.getIdUsuario());
                    ps.setString(3, item.getDescripcion());
                    ps.setInt(4, item.getInventario() != null ? item.getInventario() : 0);
                    ps.setDouble(5, item.getCosto() != null ? item.getCosto() : 0.0);
                    ps.setDouble(6,
                            (item.getInventario() != null && item.getCosto() != null)
                                    ? (item.getInventario() * item.getCosto())
                                    : 0.0);
                    ps.setString(7, item.getTipomolecula());
                    ps.setObject(8, item.getFechaClasificacion());
                    ps.setObject(9, ahora);
                }

                @Override
                public int getBatchSize() {
                    return batch.size();
                }
            });
        }

        // La reclasificación se delega al proceso orquestador para evitar redundancia
    }

    public Map<String, Integer> buildUserSedeMap() {
        java.util.List<com.pharmaser.conteociclico.model.Usuario> usuarios = usuarioRepository.findAll();
        Map<String, Integer> map = new HashMap<>();
        for (com.pharmaser.conteociclico.model.Usuario u : usuarios) {
            if (u.getSede() != null) {
                String s = u.getSede().trim();
                map.put(s, u.getId());
                map.put(s.toUpperCase(), u.getId());
                try {
                    map.put(String.valueOf(Integer.parseInt(s)), u.getId());
                } catch (Exception ignore) {
                }
            }
        }
        return map;
    }

    public List<MedicamentoImportDTO> normalizeAndValidate(java.util.List<Map<String, String>> rawData,
            Map<String, Integer> userSedeMap, StringBuilder logBuilder) {
        List<MedicamentoImportDTO> validItems = new ArrayList<>();
        int lineNum = 1;

        for (Map<String, String> row : rawData) {
            lineNum++;
            // Normalizar llaves
            Map<String, String> normalized = new HashMap<>();
            row.forEach((k, v) -> normalized.put(k.toLowerCase().trim(), v != null ? v.trim() : ""));

            String centroCosto = normalized.getOrDefault("centro costo",
                    normalized.getOrDefault("punto",
                            normalized.getOrDefault("centro de costo",
                                    normalized.getOrDefault("sede", ""))));
            String plu = normalized.getOrDefault("plu",
                    normalized.getOrDefault("codigo", ""));
            String descripcion = normalized.getOrDefault("descripcion",
                    normalized.getOrDefault("descripción",
                            normalized.getOrDefault("nombre", "")));
            String inventarioStr = normalized.getOrDefault("inventario",
                    normalized.getOrDefault("existencia",
                            normalized.getOrDefault("cantidad", "0")));
            String costoStr = normalized.getOrDefault("ultimo costo",
                    normalized.getOrDefault("costo",
                            normalized.getOrDefault("último costo", "0")));

            if (plu.isEmpty()) {
                // Try alternate key with possible invisible chars
                for (String k : normalized.keySet()) {
                    if (k.contains("plu")) {
                        plu = normalized.get(k);
                        break;
                    }
                }
            }

            if (plu.isEmpty()) {
                logBuilder.append("Línea ").append(lineNum).append(": PLU vacío. Saltando.\n");
                continue;
            }

            Integer idUsuario = userSedeMap.get(centroCosto) != null ? userSedeMap.get(centroCosto)
                    : userSedeMap.get(centroCosto.toUpperCase());
            if (idUsuario == null) {
                logBuilder.append("Línea ").append(lineNum).append(": Centro Costo '").append(centroCosto)
                        .append("' no coincide con ninguna sede activa. Saltando.\n");
                continue;
            }

            try {
                // Clean numeric strings: remove dots (thousands), then replace comma with dot
                // (decimal)
                String cleanInv = inventarioStr.replaceAll("[^0-9,-]", "").replace(",", ".");
                if (cleanInv.contains(".")) {
                    // It was likely something like 1,000.00 or 1.000,00 ... this is tricky.
                    // Assuming Spanish format from Medicar (1.234,56)
                }

                // Safer approach: Remove characters that common exports use as thousands
                // separators
                // mostly dots in Spanish context for quantities
                int inv = (int) Double
                        .parseDouble(inventarioStr.replace(".", "").replace(",", ".").replaceAll("[^0-9.-]", ""));
                double cost = Double
                        .parseDouble(costoStr.replace(".", "").replace(",", ".").replaceAll("[^0-9.-]", ""));

                if (inv < 0 || cost < 0) {
                    logBuilder.append("Línea ").append(lineNum).append(": Valores negativos (PLU: ").append(plu)
                            .append("). Saltando.\n");
                    continue;
                }

                MedicamentoImportDTO dto = new MedicamentoImportDTO();
                dto.setPlu(plu);
                dto.setDescripcion(descripcion);
                dto.setIdUsuario(idUsuario);
                dto.setInventario(inv);
                dto.setCosto(cost);
                dto.setCostoTotal(inv * cost);

                validItems.add(dto);
            } catch (Exception e) {
                logBuilder.append("Línea ").append(lineNum).append(": Error numérico en PLU ").append(plu)
                        .append(". Saltando.\n");
            }
        }
        return validItems;
    }

    public Optional<Medicamento> getMedicamentoById(Integer id) {
        return medicamentoRepository.findById(id);
    }

    public Medicamento saveMedicamento(Medicamento medicamento) {
        return medicamentoRepository.save(medicamento);
    }

    @org.springframework.transaction.annotation.Transactional
    public void marcarComoContado(Integer idMed, Double cantidadReal) {
        Medicamento med = medicamentoRepository.findById(idMed)
                .orElseThrow(() -> new RuntimeException("Medicamento no encontrado"));

        // Lógica de incremento de estado
        if ("A".equals(med.getTipomolecula())) {
            // Tipo A: Maximizar hasta 2 conteos
            if (med.getEstadoConteoMensual() < 2) {
                med.setEstadoConteoMensual(med.getEstadoConteoMensual() + 1);
            }
        } else {
            // Otros tipos: Solo 1 conteo
            med.setEstadoConteoMensual(1);
        }

        med.setInventario(cantidadReal.intValue());
        med.setEstadoDelConteo("SÍ"); // Para compatibilidad con reportes viejos
        med.setFechaUltimoConteo(java.time.LocalDateTime.now());
        medicamentoRepository.save(med);
    }

    @org.springframework.transaction.annotation.Transactional
    public void resetStatusByUsuario(Integer idUsuario) {
        jdbcTemplate.update(
                "UPDATE medicamento SET estadodelconteo = 'no', ciclosmes = ciclosmes + 1 WHERE idusuario = ? AND (estadodelconteo = 'sí' OR estadodelconteo = 'SÍ')",
                idUsuario);
    }

    @org.springframework.transaction.annotation.Transactional
    public void resetStatusByUsuarioAndTipo(Integer idUsuario, String tipo) {
        jdbcTemplate.update(
                "UPDATE medicamento SET estadodelconteo = 'no', ciclosmes = ciclosmes + 1 WHERE idusuario = ? AND tipomolecula = ? AND estadodelconteo = 'sí'",
                idUsuario, tipo);
    }

    @org.springframework.transaction.annotation.Transactional
    public void resetAllStatus() {
        jdbcTemplate.update("UPDATE medicamento SET estadodelconteo = 'no', ciclosmes = 0");
    }

    public void deleteMedicamento(Integer id) {
        medicamentoRepository.deleteById(id);
    }

    @org.springframework.transaction.annotation.Transactional
    public void markAsCounted(List<Integer> ids) {
        if (ids == null || ids.isEmpty())
            return;
        String idList = ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        String sql = "UPDATE medicamento SET estadodelconteo = 'SÍ' WHERE id IN (" + idList + ")";
        jdbcTemplate.update(sql);
    }

    @org.springframework.transaction.annotation.Transactional
    public void reclassifyAllMedicamentos() {
        long startTime = System.currentTimeMillis();
        com.pharmaser.conteociclico.model.LogClasificacionAbc log = new com.pharmaser.conteociclico.model.LogClasificacionAbc();
        log.setFechaEjecucion(java.time.LocalDateTime.now());

        try {
            if (!acquireDistributedLock()) {
                return;
            }

            // 1. Recálculo Mandatorio de Costo Total
            jdbcTemplate.update("UPDATE medicamento SET costototal = COALESCE(inventario, 0) * COALESCE(costo, 0)");

            // 2. Cargar Reglas de Porcentaje (O usar defaults: 80, 95, 100)
            java.util.List<com.pharmaser.conteociclico.model.ClasificacionAbcPorcentajeConfig> reglas = abcPorcentajeRepository
                    .findByActivoTrueOrderByPorcentajeMaxAsc();

            double threshA = 80.0;
            double threshB = 95.0;
            for (com.pharmaser.conteociclico.model.ClasificacionAbcPorcentajeConfig r : reglas) {
                if ("A".equalsIgnoreCase(r.getTipo()))
                    threshA = r.getPorcentajeMax();
                if ("B".equalsIgnoreCase(r.getTipo()))
                    threshB = r.getPorcentajeMax();
            }

            log.setSnapshotConfig("Modo Porcentual: A<=" + threshA + "%, B<=" + threshB + "%, C> " + threshB + "%");
            log.setVersionReglas(java.util.Objects.hash(threshA, threshB));

            // 3. Obtener todas las sedes con medicamentos
            java.util.List<Integer> sedes = jdbcTemplate.queryForList(
                    "SELECT DISTINCT idusuario FROM medicamento WHERE idusuario IS NOT NULL", Integer.class);

            int totalA = 0, totalB = 0, totalC = 0;
            StringBuilder sedeLog = new StringBuilder("Métricas por Sede:\n");

            for (Integer idSede : sedes) {
                Double totalValueSede = jdbcTemplate.queryForObject(
                        "SELECT COALESCE(SUM(costototal), 0) FROM medicamento WHERE idusuario = ? AND costototal > 0",
                        Double.class, idSede);

                if (totalValueSede <= 0) {
                    jdbcTemplate.update("UPDATE medicamento SET tipomolecula = 'C' WHERE idusuario = ?", idSede);
                    continue;
                }

                java.util.List<Map<String, Object>> meds = jdbcTemplate.queryForList(
                        "SELECT id, costototal FROM medicamento WHERE idusuario = ? AND costototal > 0 ORDER BY costototal DESC",
                        idSede);

                java.util.List<Object[]> batchArgs = new java.util.ArrayList<>();
                double runningSum = 0;
                java.time.LocalDateTime ahora = java.time.LocalDateTime.now();

                for (Map<String, Object> m : meds) {
                    Integer id = (Integer) m.get("id");
                    Double value = ((Number) m.get("costototal")).doubleValue();
                    runningSum += value;

                    double percent = (runningSum / totalValueSede) * 100.0;
                    String finalTipo = "C";
                    if (percent <= threshA) {
                        finalTipo = "A";
                        totalA++;
                    } else if (percent <= threshB) {
                        finalTipo = "B";
                        totalB++;
                    } else {
                        finalTipo = "C";
                        totalC++;
                    }
                    batchArgs.add(new Object[] { finalTipo, ahora, id });
                }

                // Ejecutar actualización en lote para la sede
                if (!batchArgs.isEmpty()) {
                    jdbcTemplate.batchUpdate(
                            "UPDATE medicamento SET tipomolecula = ?, fecha_clasificacion = ? WHERE id = ?", batchArgs);
                }

                jdbcTemplate.update(
                        "UPDATE medicamento SET tipomolecula = 'C' WHERE idusuario = ? AND (costototal <= 0 OR costototal IS NULL)",
                        idSede);
                sedeLog.append("- Sede ").append(idSede).append(": Valor Total $")
                        .append(String.format("%.2f", totalValueSede)).append("\n");
            }

            log.setCountA(totalA);
            log.setCountB(totalB);
            log.setCountC(totalC + jdbcTemplate.queryForObject("SELECT COUNT(1) FROM medicamento WHERE costototal <= 0",
                    Integer.class));
            log.setRegistrosProcesados(sedes.size()); // Guardamos número de sedes procesadas
            log.setEstado("EXITO");
            log.setMensajeError(sedeLog.toString());

        } catch (Exception e) {
            log.setEstado("ERROR");
            log.setMensajeError(e.getMessage());
            logger.error(">>> ERROR EN CLASIFICACIÓN ABC: {}", e.getMessage());
            throw e;
        } finally {
            try {
                releaseDistributedLock();
            } catch (Exception ex) {
            }
            log.setDuracionMs(System.currentTimeMillis() - startTime);
            logAbcRepository.save(log);
            logger.info("Clasificación ABC finalizada ({} sedes, {}ms)", sedes.size(), log.getDuracionMs());
        }
    }

    private boolean acquireDistributedLock() {
        java.time.LocalDateTime ahora = java.time.LocalDateTime.now();
        java.time.LocalDateTime expiracion = ahora.plusMinutes(LOCK_TIMEOUT_MINUTES);

        // Intento atómico de adquirir o renovar un lock expirado
        jdbcTemplate.update(
                "INSERT INTO sistema_lock (proceso_nombre, instancia_id, fecha_adquisicion, fecha_expiracion, estado) "
                        +
                        "VALUES (?, ?, ?, ?, 'OCUPADO') " +
                        "ON DUPLICATE KEY UPDATE " +
                        "instancia_id = IF(fecha_expiracion < NOW(), VALUES(instancia_id), instancia_id), " +
                        "fecha_adquisicion = IF(fecha_expiracion < NOW(), VALUES(fecha_adquisicion), fecha_adquisicion), "
                        +
                        "fecha_expiracion = IF(fecha_expiracion < NOW(), VALUES(fecha_expiracion), fecha_expiracion), "
                        +
                        "estado = IF(fecha_expiracion < NOW(), 'OCUPADO', estado)",
                LOCK_NAME, instanciaId, ahora, expiracion);

        // Si se insertó o se actualizó porque estaba expirado, verificamos si somos los
        // dueños
        return lockRepository.findById(LOCK_NAME)
                .map(l -> instanciaId.equals(l.getInstanciaId()) && "OCUPADO".equals(l.getEstado()))
                .orElse(false);
    }

    private void releaseDistributedLock() {
        jdbcTemplate.update(
                "UPDATE sistema_lock SET estado = 'LIBRE', fecha_expiracion = NOW() WHERE proceso_nombre = ? AND instancia_id = ?",
                LOCK_NAME, instanciaId);
    }

    public List<com.pharmaser.conteociclico.dto.MedicamentoSummaryDTO> getSedesSummary() {
        String sql = "SELECT m.idusuario, u.sede, " +
                     "COUNT(*) as total, " +
                     "SUM(CASE WHEN m.estadodelconteo = 'sí' THEN 1 ELSE 0 END) as contados, " +
                     "SUM(CASE WHEN m.estadodelconteo = 'no' THEN 1 ELSE 0 END) as pendientes " +
                     "FROM medicamento m " +
                     "JOIN usuario u ON m.idusuario = u.id " +
                     "GROUP BY m.idusuario, u.sede";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            com.pharmaser.conteociclico.dto.MedicamentoSummaryDTO dto = new com.pharmaser.conteociclico.dto.MedicamentoSummaryDTO();
            dto.setIdUsuario(rs.getInt("idusuario"));
            dto.setSedeNombre(rs.getString("sede"));
            dto.setTotal(rs.getLong("total"));
            dto.setContados(rs.getLong("contados"));
            dto.setPendientes(rs.getLong("pendientes"));

            // Calcular porcentajes de cobertura por categoría para esta sede de forma rápida
            // Podríamos hacerlo en el mismo query, pero para legibilidad y flexibilidad:
            return dto;
        });
    }

    public List<Medicamento> searchMedicamentos(String term, int limit) {
        if (term == null || term.trim().isEmpty()) return new ArrayList<>();
        String pattern = "%" + term.toLowerCase() + "%";
        return jdbcTemplate.query("SELECT * FROM medicamento WHERE LOWER(descripcion) LIKE ? OR LOWER(plu) LIKE ? LIMIT ?",
                (rs, rowNum) -> {
                    Medicamento m = new Medicamento();
                    m.setId(rs.getInt("id"));
                    m.setPlu(rs.getString("plu"));
                    m.setDescripcion(rs.getString("descripcion"));
                    m.setIdUsuario(rs.getInt("idusuario"));
                    m.setInventario(rs.getInt("inventario"));
                    m.setCosto(rs.getDouble("costo"));
                    m.setCostoTotal(rs.getDouble("costototal"));
                    m.setTipomolecula(rs.getString("tipomolecula"));
                    m.setEstadoDelConteo(rs.getString("estadodelconteo"));
                    return m;
                }, pattern, pattern, limit);
    }

    public Map<String, Object> getGlobalStats() {
        String sql = "SELECT " +
                     "COUNT(*) as total, " +
                     "SUM(CASE WHEN estadodelconteo = 'sí' THEN 1 ELSE 0 END) as contados, " +
                     "SUM(CASE WHEN tipomolecula = 'A' THEN 1 ELSE 0 END) as totalA, " +
                     "SUM(CASE WHEN tipomolecula = 'A' AND estadodelconteo = 'sí' THEN 1 ELSE 0 END) as contadosA, " +
                     "SUM(CASE WHEN tipomolecula = 'B' THEN 1 ELSE 0 END) as totalB, " +
                     "SUM(CASE WHEN tipomolecula = 'B' AND estadodelconteo = 'sí' THEN 1 ELSE 0 END) as contadosB, " +
                     "SUM(CASE WHEN (tipomolecula = 'C' OR tipomolecula IS NULL) THEN 1 ELSE 0 END) as totalC, " +
                     "SUM(CASE WHEN (tipomolecula = 'C' OR tipomolecula IS NULL) AND estadodelconteo = 'sí' THEN 1 ELSE 0 END) as contadosC " +
                     "FROM medicamento";
        
        return jdbcTemplate.queryForMap(sql);
    }
}
