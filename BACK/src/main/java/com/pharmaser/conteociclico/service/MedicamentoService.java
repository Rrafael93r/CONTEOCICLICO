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

    @Autowired
    private MaestraMedicamentoService maestraService;

    @Autowired
    private CostoSedeService costoSedeService;

    private static final String LOCK_NAME = "ABC_RECLASSIFICATION";
    private static final int LOCK_TIMEOUT_MINUTES = 15;
    private final String instanciaId = java.util.UUID.randomUUID().toString();

    // ═══════════════════════════════════════════════════════════════════════════
    // INICIALIZACIÓN: migración de esquema y creación de índices
    // ═══════════════════════════════════════════════════════════════════════════
    @PostConstruct
    public void initIndexes() {

        // ── 1. Columna codigogenerico (si no existe) ───────────────────────────
        try {
            jdbcTemplate.execute(
                "ALTER TABLE medicamento ADD COLUMN IF NOT EXISTS codigogenerico VARCHAR(255)");
        } catch (Exception ignore) {}

        // ── 2. MIGRACIÓN: columna sede en medicamento ──────────────────────────
        // Reemplaza idusuario como identificador de pertenencia de sede.
        // Un medicamento pertenece a la SEDE, no a un usuario individual.
        migrarColumnaSedeEnMedicamento();

        // ── 3. DEDUPLICAR detalleconteo ────────────────────────────────────────
        consolidarDetalleConteo();

        // ── 4. Índice único (plu, sede) — CRÍTICO para ON DUPLICATE KEY UPDATE ─
        Integer existeUniqueSede = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM information_schema.statistics " +
            "WHERE table_schema = DATABASE() AND table_name = 'medicamento' " +
            "AND index_name = 'idx_plu_sede'",
            Integer.class);

        if (existeUniqueSede == null || existeUniqueSede == 0) {
            // Antes de crear el índice único, eliminar duplicados (plu, sede)
            Integer duplicados = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM medicamento WHERE id NOT IN " +
                "(SELECT max_id FROM (SELECT MAX(id) AS max_id FROM medicamento GROUP BY plu, sede) t)",
                Integer.class);
            if (duplicados != null && duplicados > 0) {
                logger.warn(">>> DEDUPLICACIÓN: {} duplicados (plu, sede). Eliminando...", duplicados);
                jdbcTemplate.update(
                    "DELETE FROM medicamento WHERE id NOT IN " +
                    "(SELECT max_id FROM (SELECT MAX(id) AS max_id FROM medicamento GROUP BY plu, sede) t)");
            }
            try {
                jdbcTemplate.execute(
                    "ALTER TABLE medicamento ADD UNIQUE INDEX idx_plu_sede (plu, sede)");
                logger.info(">>> Índice único idx_plu_sede creado correctamente.");
            } catch (Exception e) {
                logger.error(">>> ERROR creando idx_plu_sede: {}", e.getMessage());
            }
        }

        // ── 5. Índice compuesto ABC ────────────────────────────────────────────
        executeNonUniqueIndexIfNotExists("idx_abc_consulta_sede",
            "ALTER TABLE medicamento ADD INDEX idx_abc_consulta_sede (sede, costototal DESC)");

        // ── 6. Índice detalle conteo ───────────────────────────────────────────
        executeNonUniqueIndexIfNotExists("idx_detalle_usr_fecha",
            "ALTER TABLE detalleconteo ADD INDEX idx_detalle_usr_fecha (idusuario, fecharegistro)");

        // ── 7. Eliminar índices obsoletos basados en idusuario ─────────────────
        dropIndexIfExists("medicamento", "idx_plu_usuario");
        dropIndexIfExists("medicamento", "uk_medicamento_plu_usuario");
        dropIndexIfExists("medicamento", "idx_abc_consulta");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Migra medicamento.idusuario → medicamento.sede
    // Se ejecuta de forma idempotente (seguro de llamar varias veces al arrancar)
    // ─────────────────────────────────────────────────────────────────────────
    private void migrarColumnaSedeEnMedicamento() {
        // 1. Añadir columna sede si no existe
        try {
            jdbcTemplate.execute(
                "ALTER TABLE medicamento ADD COLUMN IF NOT EXISTS sede VARCHAR(45)");
        } catch (Exception ignore) {}

        // 2. Poblar sede para registros que aún apuntan a idusuario
        try {
            Integer sinSede = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM medicamento WHERE sede IS NULL",
                Integer.class);
            if (sinSede != null && sinSede > 0) {
                logger.info(">>> MIGRACIÓN SEDE: {} registros sin sede. Poblando desde idusuario...", sinSede);
                int actualizados = jdbcTemplate.update(
                    "UPDATE medicamento m " +
                    "JOIN usuario u ON m.idusuario = u.id " +
                    "SET m.sede = u.sede " +
                    "WHERE m.sede IS NULL AND u.sede IS NOT NULL");
                logger.info(">>> MIGRACIÓN SEDE completada: {} registros actualizados.", actualizados);
            }
        } catch (Exception e) {
            logger.error(">>> ERROR en migración de sede: {}", e.getMessage());
        }

        // 3. Consolidar: si hay duplicados (plu, sede) producto de haber tenido
        //    N usuarios canónicos distintos para la misma sede, quedarse con MAX(id)
        try {
            Integer cruzados = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (" +
                "  SELECT plu, sede FROM medicamento WHERE sede IS NOT NULL" +
                "  GROUP BY plu, sede HAVING COUNT(*) > 1) t",
                Integer.class);
            if (cruzados != null && cruzados > 0) {
                logger.warn(">>> CONSOLIDACIÓN: {} pares (plu,sede) duplicados. Consolidando...", cruzados);

                // Redirigir FK de detalleconteo al sobreviviente antes de borrar
                jdbcTemplate.update(
                    "UPDATE detalleconteo dc " +
                    "JOIN medicamento m ON dc.idmedicamento = m.id " +
                    "JOIN (SELECT plu, sede, MAX(id) AS survivor_id " +
                    "      FROM medicamento WHERE sede IS NOT NULL GROUP BY plu, sede) surv " +
                    "  ON m.plu = surv.plu AND m.sede = surv.sede " +
                    "SET dc.idmedicamento = surv.survivor_id " +
                    "WHERE dc.idmedicamento <> surv.survivor_id");

                int eliminados = jdbcTemplate.update(
                    "DELETE m FROM medicamento m " +
                    "WHERE m.id NOT IN (" +
                    "  SELECT max_id FROM (" +
                    "    SELECT MAX(id) AS max_id FROM medicamento " +
                    "    WHERE sede IS NOT NULL GROUP BY plu, sede" +
                    "  ) t)");
                logger.info(">>> CONSOLIDACIÓN completada. Eliminados: {}", eliminados);
            }
        } catch (Exception e) {
            logger.error(">>> ERROR en consolidación de sede: {}", e.getMessage());
        }
    }

    /**
     * Elimina filas duplicadas en detalleconteo que apuntan al mismo
     * (idmedicamento, idusuario, fecharegistro, tipoconteo).
     */
    private void consolidarDetalleConteo() {
        try {
            Integer dupDc = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (" +
                "  SELECT idmedicamento, idusuario, fecharegistro, tipoconteo " +
                "  FROM detalleconteo " +
                "  WHERE idmedicamento IS NOT NULL " +
                "  GROUP BY idmedicamento, idusuario, fecharegistro, tipoconteo " +
                "  HAVING COUNT(*) > 1" +
                ") t",
                Integer.class);

            if (dupDc == null || dupDc == 0) return;

            logger.warn(">>> CONSOLIDACION DETALLE: {} grupos duplicados en detalleconteo. Limpiando...", dupDc);

            int eliminados = jdbcTemplate.update(
                "DELETE FROM detalleconteo " +
                "WHERE id NOT IN (" +
                "  SELECT keeper FROM (" +
                "    SELECT COALESCE(" +
                "      MIN(CASE WHEN cantidadcontada IS NOT NULL THEN id ELSE NULL END)," +
                "      MAX(id)" +
                "    ) AS keeper " +
                "    FROM detalleconteo " +
                "    WHERE idmedicamento IS NOT NULL " +
                "    GROUP BY idmedicamento, idusuario, fecharegistro, tipoconteo" +
                "  ) t" +
                ")");

            logger.info(">>> CONSOLIDACION DETALLE completada. Filas eliminadas: {}", eliminados);
        } catch (Exception e) {
            logger.error(">>> ERROR en consolidarDetalleConteo: {}", e.getMessage());
        }
    }

    private void executeNonUniqueIndexIfNotExists(String indexName, String alterQuery) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.statistics " +
                "WHERE table_schema = DATABASE() AND table_name = 'medicamento' AND index_name = ?",
                Integer.class, indexName);
            if (count != null && count == 0) {
                jdbcTemplate.execute(alterQuery);
            }
        } catch (Exception ignore) {}
    }

    private void dropIndexIfExists(String table, String indexName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.statistics " +
                "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                Integer.class, table, indexName);
            if (count != null && count > 0) {
                jdbcTemplate.execute("ALTER TABLE " + table + " DROP INDEX " + indexName);
                logger.info(">>> Índice obsoleto {} eliminado de {}.", indexName, table);
            }
        } catch (Exception ignore) {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CRUD BÁSICO
    // ═══════════════════════════════════════════════════════════════════════════

    public List<Medicamento> getAllMedicamentos() {
        return medicamentoRepository.findAll();
    }

    /**
     * Devuelve los medicamentos de la SEDE del usuario indicado.
     * El medicamento ya no pertenece al usuario sino a su sede.
     */
    public List<Medicamento> getMedicamentosByUsuario(Integer idUsuario) {
        if (idUsuario == null) return new ArrayList<>();
        return usuarioRepository.findById(idUsuario)
                .map(u -> medicamentoRepository.findBySede(u.getSede()))
                .orElse(new ArrayList<>());
    }

    public Optional<Medicamento> getMedicamentoById(Integer id) {
        if (id == null) return Optional.empty();
        return medicamentoRepository.findById(java.util.Objects.requireNonNull(id));
    }

    public Medicamento saveMedicamento(Medicamento medicamento) {
        if (medicamento == null) return null;
        if (medicamento.getPlu() != null && !medicamento.getPlu().isEmpty()) {
            String plu = medicamento.getPlu();
            medicamento.setCodigogenerico(plu.contains("_") ? plu.substring(0, plu.indexOf("_")) : plu);
        }
        return medicamentoRepository.save(java.util.Objects.requireNonNull(medicamento));
    }

    public void deleteMedicamento(Integer id) {
        if (id == null) return;
        medicamentoRepository.deleteById(java.util.Objects.requireNonNull(id));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IMPORTACIÓN
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Construye el mapa sede → código de sede para importaciones.
     * Devuelve variantes de clave (código, mayúsculas, numérico) apuntando
     * todas al mismo código canónico de sede.
     */
    public Map<String, String> buildSedeMap() {
        java.util.List<com.pharmaser.conteociclico.model.Usuario> todos = usuarioRepository.findAll();
        // Recopilar todos los códigos de sede únicos
        java.util.Set<String> sedesSet = new java.util.HashSet<>();
        for (com.pharmaser.conteociclico.model.Usuario u : todos) {
            if (u.getSede() != null && !u.getSede().trim().isEmpty()) {
                sedesSet.add(u.getSede().trim());
            }
        }
        Map<String, String> result = new HashMap<>();
        for (String s : sedesSet) {
            result.put(s, s);
            result.put(s.toUpperCase(), s);
            try {
                result.put(String.valueOf(Integer.parseInt(s)), s);
            } catch (Exception ignore) {}
        }
        return result;
    }

    /**
     * @deprecated Alias de buildSedeMap() para compatibilidad con llamadas existentes.
     *             Devuelve Map&lt;String,Integer&gt; (canonical user id) — usar buildSedeMap() en código nuevo.
     */
    @Deprecated
    public Map<String, Integer> buildUserSedeMap() {
        // Se mantiene para AutomatedImportService y otros callers que aún no migraron.
        // Devuelve sede_variant → id del usuario FARMACIA canónico de esa sede.
        java.util.List<com.pharmaser.conteociclico.model.Usuario> todos = usuarioRepository.findAll();
        Map<String, Integer> farmaciaPorSede = new HashMap<>();
        Map<String, Integer> fallbackPorSede = new HashMap<>();
        for (com.pharmaser.conteociclico.model.Usuario u : todos) {
            if (u.getSede() == null) continue;
            String s = u.getSede().trim();
            if (Integer.valueOf(1).equals(u.getIdRol())) {
                farmaciaPorSede.merge(s, u.getId(), Math::min);
            } else {
                fallbackPorSede.merge(s, u.getId(), Math::min);
            }
        }
        Map<String, Integer> result = new HashMap<>();
        java.util.Set<String> todas = new java.util.HashSet<>();
        todas.addAll(farmaciaPorSede.keySet());
        todas.addAll(fallbackPorSede.keySet());
        for (String s : todas) {
            Integer canonical = farmaciaPorSede.containsKey(s) ? farmaciaPorSede.get(s) : fallbackPorSede.get(s);
            result.put(s, canonical);
            result.put(s.toUpperCase(), canonical);
            try { result.put(String.valueOf(Integer.parseInt(s)), canonical); } catch (Exception ignore) {}
        }
        return result;
    }

    public List<MedicamentoImportDTO> normalizeAndValidate(java.util.List<Map<String, String>> rawData,
            Map<String, Integer> userSedeMap, StringBuilder logBuilder) {
        // Construir mapa sede_variant → código de sede real
        Map<String, String> sedeMap = buildSedeMap();

        java.util.LinkedHashMap<String, MedicamentoImportDTO> uniqueMap = new java.util.LinkedHashMap<>();
        int lineNum = 1;
        int crossedCount = 0, originalCount = 0, costoSedeCount = 0, costoArchivoCount = 0, intraFileDuplicates = 0;

        Map<String, String> masterMap = maestraService.getPluDescriptionMap();
        Map<String, Double> costMap = costoSedeService.buildCostMap();

        for (Map<String, String> row : rawData) {
            lineNum++;
            Map<String, String> normalized = new HashMap<>();
            row.forEach((k, v) -> normalized.put(k.toLowerCase().trim(), v != null ? v.trim() : ""));

            String centroCosto = normalized.getOrDefault("centro costo",
                    normalized.getOrDefault("punto",
                            normalized.getOrDefault("centro de costo",
                                    normalized.getOrDefault("sede", ""))));
            String plu = normalized.getOrDefault("plu", normalized.getOrDefault("codigo", ""));
            String descripcion = normalized.getOrDefault("descripcion",
                    normalized.getOrDefault("descripción", normalized.getOrDefault("nombre", "")));
            String inventarioStr = normalized.getOrDefault("inventario",
                    normalized.getOrDefault("existencia", normalized.getOrDefault("cantidad", "0")));
            String costoStr = normalized.getOrDefault("ultimo costo",
                    normalized.getOrDefault("costo", normalized.getOrDefault("último costo", "0")));

            if (plu.isEmpty()) {
                for (String k : normalized.keySet()) {
                    if (k.contains("plu")) { plu = normalized.get(k); break; }
                }
            }
            if (plu.isEmpty()) {
                logBuilder.append("Línea ").append(lineNum).append(": PLU vacío. Saltando.\n");
                continue;
            }

            String sedeCode = sedeMap.get(centroCosto) != null
                    ? sedeMap.get(centroCosto)
                    : sedeMap.get(centroCosto.toUpperCase());
            if (sedeCode == null) {
                logBuilder.append("Línea ").append(lineNum).append(": Centro Costo '").append(centroCosto)
                        .append("' no coincide con ninguna sede activa. Saltando.\n");
                continue;
            }

            try {
                int inv = (int) Double.parseDouble(
                        inventarioStr.replace(".", "").replace(",", ".").replaceAll("[^0-9.-]", ""));
                double costArchivo = Double.parseDouble(
                        costoStr.replace(".", "").replace(",", ".").replaceAll("[^0-9.-]", ""));

                if (inv < 0 || costArchivo < 0) {
                    logBuilder.append("Línea ").append(lineNum).append(": Valores negativos (PLU: ").append(plu)
                            .append("). Saltando.\n");
                    continue;
                }

                double cost = costArchivo;
                try {
                    int ccInt = Integer.parseInt(centroCosto.trim().replaceAll("[^0-9]", ""));
                    Double costoReal = costMap.get(ccInt + "-" + plu);
                    if (costoReal != null && costoReal > 0) { cost = costoReal; costoSedeCount++; }
                    else costoArchivoCount++;
                } catch (Exception ex) { costoArchivoCount++; }

                MedicamentoImportDTO dto = new MedicamentoImportDTO();
                dto.setPlu(plu);
                dto.setSede(sedeCode);
                dto.setInventario(inv);
                dto.setCosto(cost);
                dto.setCostoTotal(inv * cost);

                if (masterMap.containsKey(plu)) {
                    dto.setDescripcion(masterMap.get(plu));
                    crossedCount++;
                } else {
                    dto.setDescripcion(descripcion);
                    originalCount++;
                }

                String codGen = plu.contains("_") ? plu.substring(0, plu.indexOf("_")) : plu;
                dto.setCodigogenerico(codGen);

                String dedupeKey = plu + "|" + sedeCode;
                if (uniqueMap.containsKey(dedupeKey)) intraFileDuplicates++;
                uniqueMap.put(dedupeKey, dto);

            } catch (Exception e) {
                logBuilder.append("Línea ").append(lineNum).append(": Error numérico en PLU ").append(plu)
                        .append(". Saltando.\n");
            }
        }

        List<MedicamentoImportDTO> validItems = new ArrayList<>(uniqueMap.values());

        logBuilder.append("\n--- RESUMEN DE ENRIQUECIMIENTO ---\n");
        logBuilder.append("Descripciones desde MAESTRA:       ").append(crossedCount).append("\n");
        logBuilder.append("Descripciones desde ARCHIVO:       ").append(originalCount).append("\n");
        logBuilder.append("Costos desde COSTOS_SEDE (reales): ").append(costoSedeCount).append("\n");
        logBuilder.append("Costos desde ARCHIVO (fallback):   ").append(costoArchivoCount).append("\n");
        if (intraFileDuplicates > 0)
            logBuilder.append("Duplicados en el archivo (omitidos): ").append(intraFileDuplicates).append("\n");
        logBuilder.append("----------------------------------\n");

        return validItems;
    }

    public void importFromExternalData(java.util.List<MedicamentoImportDTO> items) {
        if (items == null || items.isEmpty()) return;

        String sql = "INSERT INTO medicamento " +
                "(plu, sede, descripcion, inventario, costo, costototal, tipomolecula, estadodelconteo, " +
                " fecha_clasificacion, fecha_actualizacion, contado_mes_anterior, codigogenerico) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'C', 'no', ?, ?, 0, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "descripcion = VALUES(descripcion), " +
                "inventario = VALUES(inventario), " +
                "costo = IF(VALUES(costo) > 0, VALUES(costo), costo), " +
                "costototal = IF(VALUES(costototal) > 0, VALUES(costototal), " +
                "               COALESCE(inventario, 0) * IF(VALUES(costo) > 0, VALUES(costo), costo)), " +
                "fecha_actualizacion = VALUES(fecha_actualizacion), " +
                "codigogenerico = IF(VALUES(codigogenerico) IS NOT NULL AND VALUES(codigogenerico) != '', " +
                "                   VALUES(codigogenerico), codigogenerico)";

        java.time.LocalDateTime ahora = java.time.LocalDateTime.now();
        int batchSize = 1000;
        for (int i = 0; i < items.size(); i += batchSize) {
            final java.util.List<MedicamentoImportDTO> batch = items.subList(i, Math.min(i + batchSize, items.size()));
            jdbcTemplate.batchUpdate(sql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                @Override
                public void setValues(@org.springframework.lang.NonNull java.sql.PreparedStatement ps, int j)
                        throws java.sql.SQLException {
                    MedicamentoImportDTO item = batch.get(j);
                    int inv = item.getInventario() != null ? item.getInventario() : 0;
                    double costo = item.getCosto() != null ? item.getCosto() : 0.0;
                    ps.setString(1, item.getPlu());
                    ps.setString(2, item.getSede());
                    ps.setString(3, item.getDescripcion());
                    ps.setInt(4, inv);
                    ps.setDouble(5, costo);
                    ps.setDouble(6, inv * costo);
                    ps.setObject(7, ahora);
                    ps.setObject(8, ahora);
                    ps.setString(9, item.getCodigogenerico());
                }
                @Override public int getBatchSize() { return batch.size(); }
            });
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public void bulkUpdateInventory(java.util.List<java.util.Map<String, Object>> items) {
        Map<String, String> sedeMap = buildSedeMap();
        final java.util.List<java.util.Map<String, Object>> validItems = new java.util.ArrayList<>();
        for (java.util.Map<String, Object> item : items) {
            String sedeStr = (String) item.get("sede");
            if (sedeStr != null && sedeMap.containsKey(sedeStr.trim())) {
                item.put("sedeCode", sedeMap.get(sedeStr.trim()));
                validItems.add(item);
            }
        }
        String sql = "UPDATE medicamento SET inventario = ? WHERE plu = ? AND sede = ?";
        int batchSize = 10000;
        for (int i = 0; i < validItems.size(); i += batchSize) {
            final java.util.List<java.util.Map<String, Object>> batch =
                    validItems.subList(i, Math.min(i + batchSize, validItems.size()));
            jdbcTemplate.batchUpdate(sql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                @Override
                public void setValues(@org.springframework.lang.NonNull java.sql.PreparedStatement ps, int j)
                        throws java.sql.SQLException {
                    java.util.Map<String, Object> it = batch.get(j);
                    ps.setInt(1, it.get("cantidad") != null ? (Integer) it.get("cantidad") : 0);
                    ps.setString(2, (String) it.get("plu"));
                    ps.setString(3, (String) it.get("sedeCode"));
                }
                @Override public int getBatchSize() { return batch.size(); }
            });
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ESTADOS DE CONTEO
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * REQUIRES_NEW: corre en su propia transacción independiente.
     *
     * Razón: saveAllDetalles() tiene @Transactional y llama a este método dentro
     * de un try-catch por ítem. Si usara REQUIRED (propagación por defecto),
     * al lanzar una excepción aquí Spring marcaría la transacción externa como
     * "rollback-only". Aunque saveAllDetalles atrapa la excepción, al intentar
     * hacer commit al final Spring lanza UnexpectedRollbackException → HTTP 500.
     * Con REQUIRES_NEW cada llamada tiene su propia transacción; un fallo aquí
     * solo afecta a ese ítem y no envenena la transacción del llamador.
     */
    @org.springframework.transaction.annotation.Transactional(
        propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void marcarComoContado(Integer idMed, Double cantidadReal) {
        if (idMed == null) return;
        // ifPresent: si el medicamento no existe simplemente se ignora en lugar
        // de lanzar RuntimeException que envenenaba la transacción exterior.
        medicamentoRepository.findById(idMed).ifPresent(med -> {
            if ("A".equals(med.getTipomolecula())) {
                if (med.getEstadoConteoMensual() < 2) med.setEstadoConteoMensual(med.getEstadoConteoMensual() + 1);
            } else {
                med.setEstadoConteoMensual(1);
            }
            med.setInventario(cantidadReal.intValue());
            med.setEstadoDelConteo("SÍ");
            med.setFechaUltimoConteo(java.time.LocalDateTime.now());
            medicamentoRepository.save(med);
        });
    }

    @org.springframework.transaction.annotation.Transactional
    public void markAsCounted(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return;
        String idList = ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        jdbcTemplate.update("UPDATE medicamento SET estadodelconteo = 'SÍ' WHERE id IN (" + idList + ")");
    }

    /** Reinicia el ciclo de conteo de la SEDE del usuario indicado. */
    @org.springframework.transaction.annotation.Transactional
    public void resetStatusByUsuario(Integer idUsuario) {
        String sede = getSedeByUserId(idUsuario);
        if (sede == null) return;
        jdbcTemplate.update(
            "UPDATE medicamento SET estadodelconteo = 'no', ciclosmes = ciclosmes + 1 " +
            "WHERE sede = ? AND (estadodelconteo = 'sí' OR estadodelconteo = 'SÍ')", sede);
    }

    /** Reinicia el ciclo de conteo de la SEDE del usuario para un tipo de molécula. */
    @org.springframework.transaction.annotation.Transactional
    public void resetStatusByUsuarioAndTipo(Integer idUsuario, String tipo) {
        String sede = getSedeByUserId(idUsuario);
        if (sede == null) return;
        jdbcTemplate.update(
            "UPDATE medicamento SET estadodelconteo = 'no', ciclosmes = ciclosmes + 1 " +
            "WHERE sede = ? AND tipomolecula = ? AND estadodelconteo = 'sí'", sede, tipo);
    }

    @org.springframework.transaction.annotation.Transactional
    public void resetAllStatus() {
        jdbcTemplate.update("UPDATE medicamento SET estadodelconteo = 'no', ciclosmes = 0");
    }

    private String getSedeByUserId(Integer idUsuario) {
        return usuarioRepository.findById(idUsuario)
                .map(com.pharmaser.conteociclico.model.Usuario::getSede)
                .orElse(null);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CLASIFICACIÓN ABC
    // ═══════════════════════════════════════════════════════════════════════════

    @org.springframework.transaction.annotation.Transactional
    public void reclassifyAllMedicamentos() {
        long startTime = System.currentTimeMillis();
        com.pharmaser.conteociclico.model.LogClasificacionAbc log = new com.pharmaser.conteociclico.model.LogClasificacionAbc();
        log.setFechaEjecucion(java.time.LocalDateTime.now());

        java.util.List<String> sedes = new java.util.ArrayList<>();
        try {
            if (!acquireDistributedLock()) return;

            // 1. Recálculo de costo total
            jdbcTemplate.update("UPDATE medicamento SET costototal = COALESCE(inventario, 0) * COALESCE(costo, 0)");

            // 2. Reglas de porcentaje
            java.util.List<com.pharmaser.conteociclico.model.ClasificacionAbcPorcentajeConfig> reglas =
                    abcPorcentajeRepository.findByActivoTrueOrderByPorcentajeMaxAsc();
            double threshA = 80.0, threshB = 95.0;
            for (com.pharmaser.conteociclico.model.ClasificacionAbcPorcentajeConfig r : reglas) {
                if ("A".equalsIgnoreCase(r.getTipo())) threshA = r.getPorcentajeMax();
                if ("B".equalsIgnoreCase(r.getTipo())) threshB = r.getPorcentajeMax();
            }

            log.setSnapshotConfig("Modo Porcentual: A<=" + threshA + "%, B<=" + threshB + "%, C> " + threshB + "%");
            log.setVersionReglas(java.util.Objects.hash(threshA, threshB));

            // 3. Sedes con medicamentos — directo desde medicamento.sede (sin JOIN)
            sedes = jdbcTemplate.queryForList(
                    "SELECT DISTINCT sede FROM medicamento WHERE sede IS NOT NULL",
                    String.class);

            int totalA = 0, totalB = 0, totalC = 0;
            StringBuilder sedeLog = new StringBuilder("Métricas por Sede (Clasificación por Familias):\n");

            for (String sede : sedes) {
                Double totalValueSede = jdbcTemplate.queryForObject(
                        "SELECT COALESCE(SUM(costototal), 0) FROM medicamento WHERE sede = ? AND costototal > 0",
                        Double.class, sede);

                if (totalValueSede == null || totalValueSede <= 0) {
                    jdbcTemplate.update("UPDATE medicamento SET tipomolecula = 'C' WHERE sede = ?", sede);
                    continue;
                }

                java.util.List<Map<String, Object>> families = jdbcTemplate.queryForList(
                        "SELECT codigogenerico, SUM(costototal) AS valor_familia " +
                        "FROM medicamento WHERE sede = ? AND costototal > 0 " +
                        "GROUP BY codigogenerico ORDER BY valor_familia DESC",
                        sede);

                java.util.List<Object[]> batchUpdateArgs = new java.util.ArrayList<>();
                double runningSum = 0;
                java.time.LocalDateTime ahora = java.time.LocalDateTime.now();
                int famA = 0, famB = 0, famC = 0;

                for (Map<String, Object> f : families) {
                    String codGen = (String) f.get("codigogenerico");
                    Double famValue = ((Number) f.get("valor_familia")).doubleValue();
                    runningSum += famValue;
                    double percent = (runningSum / totalValueSede) * 100.0;
                    String finalTipo;
                    if (percent <= threshA)      { finalTipo = "A"; famA++; }
                    else if (percent <= threshB) { finalTipo = "B"; famB++; }
                    else                         { finalTipo = "C"; famC++; }
                    batchUpdateArgs.add(new Object[]{ finalTipo, ahora, sede, codGen });
                }

                if (!batchUpdateArgs.isEmpty()) {
                    jdbcTemplate.batchUpdate(
                            "UPDATE medicamento SET tipomolecula = ?, fecha_clasificacion = ? " +
                            "WHERE sede = ? AND codigogenerico = ?",
                            batchUpdateArgs);
                }

                // Sin costo o sin código → C
                jdbcTemplate.update(
                        "UPDATE medicamento SET tipomolecula = 'C' " +
                        "WHERE sede = ? AND (costototal <= 0 OR costototal IS NULL OR codigogenerico IS NULL)",
                        sede);

                totalA += famA; totalB += famB; totalC += famC;
                sedeLog.append("- Sede ").append(sede).append(": $")
                        .append(String.format("%.2f", totalValueSede))
                        .append(" | A:").append(famA).append(", B:").append(famB).append(", C:").append(famC).append("\n");
            }

            log.setCountA(totalA);
            log.setCountB(totalB);
            Integer extraC = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM medicamento WHERE costototal <= 0", Integer.class);
            log.setCountC(totalC + (extraC != null ? extraC : 0));
            log.setRegistrosProcesados(sedes.size());
            log.setEstado("EXITO");
            log.setMensajeError(sedeLog.toString());

        } catch (Exception e) {
            log.setEstado("ERROR");
            log.setMensajeError(e.getMessage());
            logger.error(">>> ERROR EN CLASIFICACIÓN ABC: {}", e.getMessage());
            throw e;
        } finally {
            try { releaseDistributedLock(); } catch (Exception ex) {}
            log.setDuracionMs(System.currentTimeMillis() - startTime);
            logAbcRepository.save(log);
            logger.info("Clasificación ABC finalizada ({} sedes, {}ms)", sedes.size(), log.getDuracionMs());
        }
    }

    private boolean acquireDistributedLock() {
        java.time.LocalDateTime ahora = java.time.LocalDateTime.now();
        java.time.LocalDateTime expiracion = ahora.plusMinutes(LOCK_TIMEOUT_MINUTES);
        jdbcTemplate.update(
                "INSERT INTO sistema_lock (proceso_nombre, instancia_id, fecha_adquisicion, fecha_expiracion, estado) " +
                "VALUES (?, ?, ?, ?, 'OCUPADO') " +
                "ON DUPLICATE KEY UPDATE " +
                "instancia_id = IF(fecha_expiracion < NOW(), VALUES(instancia_id), instancia_id), " +
                "fecha_adquisicion = IF(fecha_expiracion < NOW(), VALUES(fecha_adquisicion), fecha_adquisicion), " +
                "fecha_expiracion = IF(fecha_expiracion < NOW(), VALUES(fecha_expiracion), fecha_expiracion), " +
                "estado = IF(fecha_expiracion < NOW(), 'OCUPADO', estado)",
                LOCK_NAME, instanciaId, ahora, expiracion);
        return lockRepository.findById(LOCK_NAME)
                .map(l -> instanciaId.equals(l.getInstanciaId()) && "OCUPADO".equals(l.getEstado()))
                .orElse(false);
    }

    private void releaseDistributedLock() {
        jdbcTemplate.update(
                "UPDATE sistema_lock SET estado = 'LIBRE', fecha_expiracion = NOW() " +
                "WHERE proceso_nombre = ? AND instancia_id = ?",
                LOCK_NAME, instanciaId);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONSULTAS / BÚSQUEDAS
    // ═══════════════════════════════════════════════════════════════════════════

    public List<com.pharmaser.conteociclico.dto.MedicamentoSummaryDTO> getSedesSummary() {
        // Agrupa directamente por medicamento.sede (sin JOIN usuario).
        // Incluye usuario canónico FARMACIA para compatibilidad con el frontend.
        String sql =
            "SELECT m.sede, " +
            "COALESCE(" +
            "  (SELECT MIN(u2.id) FROM usuario u2 WHERE u2.sede = m.sede AND u2.idrol = 1), " +
            "  (SELECT MIN(u3.id) FROM usuario u3 WHERE u3.sede = m.sede)" +
            ") AS idusuario, " +
            "COUNT(*) AS total, " +
            "SUM(CASE WHEN UPPER(m.estadodelconteo) = 'SÍ' THEN 1 ELSE 0 END) AS contados, " +
            "SUM(CASE WHEN UPPER(m.estadodelconteo) != 'SÍ' THEN 1 ELSE 0 END) AS pendientes " +
            "FROM medicamento m " +
            "GROUP BY m.sede";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            com.pharmaser.conteociclico.dto.MedicamentoSummaryDTO dto =
                    new com.pharmaser.conteociclico.dto.MedicamentoSummaryDTO();
            dto.setSede(rs.getString("sede"));
            dto.setIdUsuario(rs.getInt("idusuario"));
            dto.setSedeNombre(rs.getString("sede"));
            dto.setTotal(rs.getLong("total"));
            dto.setContados(rs.getLong("contados"));
            dto.setPendientes(rs.getLong("pendientes"));
            return dto;
        });
    }

    public List<Medicamento> searchMedicamentos(String term, int limit) {
        if (term == null || term.trim().isEmpty()) return new ArrayList<>();
        String pattern = "%" + term.toLowerCase() + "%";
        return jdbcTemplate.query(
                "SELECT * FROM medicamento WHERE LOWER(descripcion) LIKE ? OR LOWER(plu) LIKE ? LIMIT ?",
                (rs, rowNum) -> mapRow(rs), pattern, pattern, limit);
    }

    public List<Medicamento> searchMedicamentosBySede(String term, String sede, int limit) {
        if (term == null || term.trim().isEmpty()) return new ArrayList<>();
        String pattern = "%" + term.toLowerCase() + "%";
        return jdbcTemplate.query(
                "SELECT * FROM medicamento " +
                "WHERE (LOWER(descripcion) LIKE ? OR LOWER(plu) LIKE ?) AND sede = ? LIMIT ?",
                (rs, rowNum) -> mapRow(rs), pattern, pattern, sede, limit);
    }

    public Map<String, Object> getGlobalStats() {
        String sql = "SELECT " +
                "COUNT(*) as total, " +
                "SUM(CASE WHEN UPPER(estadodelconteo) = 'SÍ' THEN 1 ELSE 0 END) as contados, " +
                "SUM(CASE WHEN tipomolecula = 'A' THEN 1 ELSE 0 END) as totalA, " +
                "SUM(CASE WHEN tipomolecula = 'A' AND UPPER(estadodelconteo) = 'SÍ' THEN 1 ELSE 0 END) as contadosA, " +
                "SUM(CASE WHEN tipomolecula = 'B' THEN 1 ELSE 0 END) as totalB, " +
                "SUM(CASE WHEN tipomolecula = 'B' AND UPPER(estadodelconteo) = 'SÍ' THEN 1 ELSE 0 END) as contadosB, " +
                "SUM(CASE WHEN (tipomolecula = 'C' OR tipomolecula IS NULL) THEN 1 ELSE 0 END) as totalC, " +
                "SUM(CASE WHEN (tipomolecula = 'C' OR tipomolecula IS NULL) AND UPPER(estadodelconteo) = 'SÍ' THEN 1 ELSE 0 END) as contadosC " +
                "FROM medicamento";
        return jdbcTemplate.queryForMap(sql);
    }

    private Medicamento mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Medicamento m = new Medicamento();
        m.setId(rs.getInt("id"));
        m.setPlu(rs.getString("plu"));
        m.setDescripcion(rs.getString("descripcion"));
        m.setSede(rs.getString("sede"));
        m.setInventario(rs.getInt("inventario"));
        m.setCosto(rs.getDouble("costo"));
        m.setCostoTotal(rs.getDouble("costototal"));
        m.setTipomolecula(rs.getString("tipomolecula"));
        m.setEstadoDelConteo(rs.getString("estadodelconteo"));
        return m;
    }
}
