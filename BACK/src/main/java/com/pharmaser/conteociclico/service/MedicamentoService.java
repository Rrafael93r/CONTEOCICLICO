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

    @PostConstruct
    public void initIndexes() {

        // ── 1. Columna codigogenerico (si no existe) ──────────────────────────
        try {
            jdbcTemplate.execute(
                "ALTER TABLE medicamento ADD COLUMN IF NOT EXISTS codigogenerico VARCHAR(255)");
        } catch (Exception e) {
            // Motor sin IF NOT EXISTS o columna ya existente — ignorar
        }

        // ── 2. CONSOLIDACIÓN POR SEDE ─────────────────────────────────────────
        // Si la misma sede tiene N usuarios, el mismo PLU puede existir con
        // distintos idusuarios. Este paso lo consolida al usuario canónico
        // (FARMACIA, menor id) antes de crear el índice único.
        consolidarPlusPorSede();

        // ── 3. Índice único (plu, idusuario) — CRÍTICO para ON DUPLICATE KEY UPDATE ──
        Integer existeUnique = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM information_schema.statistics " +
            "WHERE table_schema = DATABASE() AND table_name = 'medicamento' " +
            "AND index_name = 'idx_plu_usuario'",
            Integer.class);

        if (existeUnique == null || existeUnique == 0) {
            // Antes de crear el índice único, eliminar duplicados existentes.
            // Conservamos el registro con mayor id (última importación) por cada (plu, idusuario).
            Integer duplicados = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM medicamento WHERE id NOT IN " +
                "(SELECT max_id FROM (SELECT MAX(id) AS max_id FROM medicamento GROUP BY plu, idusuario) t)",
                Integer.class);

            if (duplicados != null && duplicados > 0) {
                logger.warn(">>> DEDUPLICACIÓN: Se encontraron {} registros duplicados en medicamento. Eliminando...", duplicados);
                jdbcTemplate.update(
                    "DELETE FROM medicamento WHERE id NOT IN " +
                    "(SELECT max_id FROM (SELECT MAX(id) AS max_id FROM medicamento GROUP BY plu, idusuario) t)");
                logger.info(">>> DEDUPLICACIÓN completada. Registros eliminados: {}", duplicados);
            }

            // Ahora sí crear el índice único de forma segura
            try {
                jdbcTemplate.execute(
                    "ALTER TABLE medicamento ADD UNIQUE INDEX idx_plu_usuario (plu, idusuario)");
                logger.info(">>> Índice único idx_plu_usuario creado correctamente.");
            } catch (Exception e) {
                logger.error(">>> ERROR creando índice único idx_plu_usuario: {}", e.getMessage());
            }
        }

        // ── 3. Índice compuesto ABC ───────────────────────────────────────────
        executeNonUniqueIndexIfNotExists("idx_abc_consulta",
            "ALTER TABLE medicamento ADD INDEX idx_abc_consulta (idusuario, costototal DESC)");

        // ── 4. Índice detalle conteo ──────────────────────────────────────────
        executeNonUniqueIndexIfNotExists("idx_detalle_usr_fecha",
            "ALTER TABLE detalleconteo ADD INDEX idx_detalle_usr_fecha (idusuario, fecharegistro)");
    }

    /**
     * Consolida medicamentos para que cada (plu, sede) tenga un único registro
     * apuntando al usuario canónico (rol FARMACIA = idRol 1, menor id por sede).
     *
     * Problema original: una sede con N usuarios (FARMACIA, CONTROL, ADMIN) generaba
     * que el mismo PLU se importara con distintos idusuarios para la misma sede,
     * causando duplicados de negocio que el índice único (plu, idusuario) no detectaba.
     *
     * Estrategia de 3 pasos para manejar la FK de detalleconteo:
     *   A) Redirigir detalleconteo al registro sobreviviente → libera la FK
     *   B) Borrar los registros redundantes (ya sin referencias FK)
     *   C) Asignar el idusuario canónico FARMACIA al sobreviviente
     */
    private void consolidarPlusPorSede() {
        try {
            Integer cruzados = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (" +
                "  SELECT m.plu, u.sede " +
                "  FROM medicamento m JOIN usuario u ON m.idusuario = u.id " +
                "  WHERE u.sede IS NOT NULL " +
                "  GROUP BY m.plu, u.sede " +
                "  HAVING COUNT(DISTINCT m.idusuario) > 1" +
                ") t",
                Integer.class);

            if (cruzados == null || cruzados == 0) return;

            logger.warn(">>> CONSOLIDACION SEDE: {} PLUs con multiples usuarios en la misma sede. Consolidando...", cruzados);

            // ── Paso A: Redirigir detalleconteo al registro sobreviviente ──────
            // El sobreviviente es: usuario FARMACIA (idRol=1) con menor id,
            // o MAX(id) si la sede no tiene usuario FARMACIA.
            // detalleconteo no tiene unique constraint en idmedicamento → operación segura.
            jdbcTemplate.update(
                "UPDATE detalleconteo dc " +
                "JOIN medicamento m ON dc.idmedicamento = m.id " +
                "JOIN usuario u ON m.idusuario = u.id " +
                "JOIN (" +
                "  SELECT m2.plu, u2.sede, " +
                "    COALESCE(MIN(CASE WHEN u2.idrol = 1 THEN m2.id ELSE NULL END), MAX(m2.id)) AS survivor_id " +
                "  FROM medicamento m2 JOIN usuario u2 ON m2.idusuario = u2.id " +
                "  WHERE u2.sede IS NOT NULL " +
                "  GROUP BY m2.plu, u2.sede" +
                ") surv ON u.sede = surv.sede AND m.plu = surv.plu " +
                "SET dc.idmedicamento = surv.survivor_id " +
                "WHERE dc.idmedicamento <> surv.survivor_id");

            // ── Paso B: Borrar los registros redundantes ──────────────────────
            // En este punto ningún detalleconteo los referencia → FK no falla.
            int eliminados = jdbcTemplate.update(
                "DELETE m FROM medicamento m " +
                "JOIN usuario u ON m.idusuario = u.id " +
                "WHERE m.id NOT IN (" +
                "  SELECT kept_id FROM (" +
                "    SELECT COALESCE(" +
                "      MIN(CASE WHEN u2.idrol = 1 THEN m2.id ELSE NULL END)," +
                "      MAX(m2.id)" +
                "    ) AS kept_id " +
                "    FROM medicamento m2 JOIN usuario u2 ON m2.idusuario = u2.id " +
                "    WHERE u2.sede IS NOT NULL " +
                "    GROUP BY m2.plu, u2.sede" +
                "  ) t" +
                ")");

            // ── Paso C: Asignar idusuario canónico FARMACIA al sobreviviente ──
            // Solo aplica a sedes con al menos un usuario de idRol=1.
            jdbcTemplate.update(
                "UPDATE medicamento m " +
                "JOIN usuario u ON m.idusuario = u.id " +
                "JOIN (SELECT sede, MIN(id) AS canonical_id " +
                "      FROM usuario WHERE idrol = 1 AND sede IS NOT NULL GROUP BY sede) canon " +
                "  ON u.sede = canon.sede " +
                "SET m.idusuario = canon.canonical_id " +
                "WHERE m.idusuario <> canon.canonical_id");

            logger.info(">>> CONSOLIDACION SEDE completada. Registros eliminados: {}. PLUs consolidados al usuario FARMACIA canonico.", eliminados);
        } catch (Exception e) {
            logger.error(">>> ERROR en consolidarPlusPorSede: {}", e.getMessage());
        }
    }

    /** Crea un índice NO único solo si no existe. Errores silenciosos (ya existe = normal). */
    private void executeNonUniqueIndexIfNotExists(String indexName, String alterQuery) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.statistics " +
                "WHERE table_schema = DATABASE() AND table_name = 'medicamento' AND index_name = ?",
                Integer.class, indexName);
            if (count != null && count == 0) {
                jdbcTemplate.execute(alterQuery);
            }
        } catch (Exception ignore) {
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
        // Usa el mismo mapa canónico que importFromExternalData para garantizar
        // que ambas operaciones afecten siempre al mismo idusuario por sede.
        java.util.Map<String, Integer> sedeMap = buildUserSedeMap();

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

        // Reglas de actualización:
        // - inventario: siempre se actualiza (el archivo trae la cantidad real del día)
        // - costo/costototal: solo se actualiza si el archivo trae valor > 0 (preserva costo conocido)
        // - tipomolecula: NO se toca — la reclasificación ABC lo gestiona; solo INSERTs nuevos arrancan en 'C'
        // - estadodelconteo: NO se toca — no se resetea si ya fue contado hoy
        // - codigogenerico: solo se actualiza si el archivo trae uno no vacío
        String sql = "INSERT INTO medicamento " +
                "(plu, idusuario, descripcion, inventario, costo, costototal, tipomolecula, estadodelconteo, " +
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
                    ps.setInt(2, item.getIdUsuario());
                    ps.setString(3, item.getDescripcion());
                    ps.setInt(4, inv);
                    ps.setDouble(5, costo);
                    ps.setDouble(6, inv * costo);
                    ps.setObject(7, ahora);   // fecha_clasificacion (solo INSERT nuevo)
                    ps.setObject(8, ahora);   // fecha_actualizacion
                    ps.setString(9, item.getCodigogenerico());
                }

                @Override
                public int getBatchSize() {
                    return batch.size();
                }
            });
        }
    }

    /**
     * Construye el mapa sede → idUsuario para importaciones.
     * Usa el usuario FARMACIA (idRol=1) con menor id como representante
     * canónico de cada sede. Si la sede no tiene usuario FARMACIA, usa el
     * usuario de menor id de cualquier rol.
     * Esto garantiza que todas las importaciones asignen el mismo idusuario
     * para la misma sede, evitando duplicados de negocio en medicamento.
     */
    public Map<String, Integer> buildUserSedeMap() {
        java.util.List<com.pharmaser.conteociclico.model.Usuario> todos = usuarioRepository.findAll();

        // Primer paso: acumular el menor id de FARMACIA (idRol=1) y el menor id de cualquier rol por sede
        Map<String, Integer> farmaciaPorSede  = new HashMap<>();
        Map<String, Integer> fallbackPorSede  = new HashMap<>();

        for (com.pharmaser.conteociclico.model.Usuario u : todos) {
            if (u.getSede() == null) continue;
            String s = u.getSede().trim();
            if (Integer.valueOf(1).equals(u.getIdRol())) {
                farmaciaPorSede.merge(s, u.getId(), Math::min);
            } else {
                fallbackPorSede.merge(s, u.getId(), Math::min);
            }
        }

        // Segundo paso: combinar — FARMACIA tiene prioridad
        Map<String, Integer> result = new HashMap<>();
        java.util.Set<String> todasSedes = new java.util.HashSet<>();
        todasSedes.addAll(farmaciaPorSede.keySet());
        todasSedes.addAll(fallbackPorSede.keySet());

        for (String s : todasSedes) {
            Integer canonicalId = farmaciaPorSede.containsKey(s)
                    ? farmaciaPorSede.get(s)
                    : fallbackPorSede.get(s);
            result.put(s, canonicalId);
            result.put(s.toUpperCase(), canonicalId);
            try {
                result.put(String.valueOf(Integer.parseInt(s)), canonicalId);
            } catch (Exception ignore) {}
        }
        return result;
    }

    public List<MedicamentoImportDTO> normalizeAndValidate(java.util.List<Map<String, String>> rawData,
            Map<String, Integer> userSedeMap, StringBuilder logBuilder) {
        // LinkedHashMap keyed by "plu|idusuario" — si el mismo PLU+sede aparece varias veces
        // en el archivo, la última ocurrencia gana (más reciente en el lote).
        // Esto garantiza que el batch enviado a importFromExternalData no tenga duplicados.
        java.util.LinkedHashMap<String, MedicamentoImportDTO> uniqueMap = new java.util.LinkedHashMap<>();
        int lineNum = 1;
        int crossedCount = 0;
        int originalCount = 0;
        int costoSedeCount = 0;
        int costoArchivoCount = 0;
        int intraFileDuplicates = 0;

        // Cargar maestra de descripciones (caché 30 min)
        Map<String, String> masterMap = maestraService.getPluDescriptionMap();

        // Cargar mapa de costos reales por sede (caché 60 min, se invalida al subir costos nuevos)
        Map<String, Double> costMap = costoSedeService.buildCostMap();

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
                int inv = (int) Double
                        .parseDouble(inventarioStr.replace(".", "").replace(",", ".").replaceAll("[^0-9.-]", ""));
                double costArchivo = Double
                        .parseDouble(costoStr.replace(".", "").replace(",", ".").replaceAll("[^0-9.-]", ""));

                if (inv < 0 || costArchivo < 0) {
                    logBuilder.append("Línea ").append(lineNum).append(": Valores negativos (PLU: ").append(plu)
                            .append("). Saltando.\n");
                    continue;
                }

                // ENRIQUECIMIENTO DE COSTO: buscar en costos_sede por (centroCosto, plu)
                // La clave se normaliza a entero para que "001" == "1"
                double cost = costArchivo;
                try {
                    int ccInt = Integer.parseInt(centroCosto.trim().replaceAll("[^0-9]", ""));
                    Double costoReal = costMap.get(ccInt + "-" + plu);
                    if (costoReal != null && costoReal > 0) {
                        cost = costoReal;
                        costoSedeCount++;
                    } else {
                        costoArchivoCount++;
                    }
                } catch (Exception ex) {
                    costoArchivoCount++; // sede no numérica — usar costo del archivo
                }

                MedicamentoImportDTO dto = new MedicamentoImportDTO();
                dto.setPlu(plu);
                dto.setIdUsuario(idUsuario);
                dto.setInventario(inv);
                dto.setCosto(cost);
                dto.setCostoTotal(inv * cost);

                // ENRIQUECIMIENTO CON MAESTRA
                if (masterMap.containsKey(plu)) {
                    dto.setDescripcion(masterMap.get(plu));
                    crossedCount++;
                } else {
                    dto.setDescripcion(descripcion);
                    originalCount++;
                }

                // Lógica de Código Genérico
                String codGen = plu;
                if (plu.contains("_")) {
                    codGen = plu.substring(0, plu.indexOf("_"));
                }
                dto.setCodigogenerico(codGen);

                // Deduplicación intra-archivo: si ya existe este (plu, sede) en el mapa,
                // la nueva entrada lo sobreescribe (última línea del archivo prevalece).
                String dedupeKey = plu + "|" + idUsuario;
                if (uniqueMap.containsKey(dedupeKey)) {
                    intraFileDuplicates++;
                }
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
        if (intraFileDuplicates > 0) {
            logBuilder.append("Duplicados en el archivo (omitidos): ").append(intraFileDuplicates).append("\n");
        }
        logBuilder.append("----------------------------------\n");

        return validItems;
    }

    public Optional<Medicamento> getMedicamentoById(Integer id) {
        if (id == null) return Optional.empty();
        return medicamentoRepository.findById(java.util.Objects.requireNonNull(id));
    }

    public Medicamento saveMedicamento(Medicamento medicamento) {
        if (medicamento == null) return null;
        
        // Poblar codigogenerico si el PLU está presente
        if (medicamento.getPlu() != null && !medicamento.getPlu().isEmpty()) {
            String plu = medicamento.getPlu();
            String codGen = plu;
            if (plu.contains("_")) {
                codGen = plu.substring(0, plu.indexOf("_"));
            }
            medicamento.setCodigogenerico(codGen);
        }

        return medicamentoRepository.save(java.util.Objects.requireNonNull(medicamento));
    }

    @org.springframework.transaction.annotation.Transactional
    public void marcarComoContado(Integer idMed, Double cantidadReal) {
        if (idMed == null) return;
        Medicamento med = medicamentoRepository.findById(java.util.Objects.requireNonNull(idMed))
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
        if (id == null) return;
        medicamentoRepository.deleteById(java.util.Objects.requireNonNull(id));
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

        java.util.List<String> sedes = new java.util.ArrayList<>();
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

            // 3. Obtener sedes REALES (via JOIN con usuario).
            // Una sede puede tener N usuarios; clasificamos el inventario COMPLETO
            // de la sede como una unidad de Pareto, no por usuario individual.
            sedes = jdbcTemplate.queryForList(
                    "SELECT DISTINCT u.sede FROM medicamento m " +
                    "JOIN usuario u ON m.idusuario = u.id " +
                    "WHERE u.sede IS NOT NULL",
                    String.class);

            int totalA = 0, totalB = 0, totalC = 0;
            StringBuilder sedeLog = new StringBuilder("Métricas por Sede (Clasificación por Familias):\n");

            for (String sede : sedes) {
                // Valor total de la sede: suma de TODOS los usuarios de esa sede
                Double totalValueSede = jdbcTemplate.queryForObject(
                        "SELECT COALESCE(SUM(m.costototal), 0) FROM medicamento m " +
                        "JOIN usuario u ON m.idusuario = u.id " +
                        "WHERE u.sede = ? AND m.costototal > 0",
                        Double.class, sede);

                if (totalValueSede == null || totalValueSede <= 0) {
                    jdbcTemplate.update(
                        "UPDATE medicamento SET tipomolecula = 'C' " +
                        "WHERE idusuario IN (SELECT id FROM usuario WHERE sede = ?)", sede);
                    continue;
                }

                // Familias de la sede completa, ordenadas por valor consolidado
                java.util.List<Map<String, Object>> families = jdbcTemplate.queryForList(
                        "SELECT m.codigogenerico, SUM(m.costototal) AS valor_familia " +
                        "FROM medicamento m JOIN usuario u ON m.idusuario = u.id " +
                        "WHERE u.sede = ? AND m.costototal > 0 " +
                        "GROUP BY m.codigogenerico ORDER BY valor_familia DESC",
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

                    // Todos los registros de esta familia en esta sede → misma clasificación
                    batchUpdateArgs.add(new Object[] { finalTipo, ahora, sede, codGen });
                }

                if (!batchUpdateArgs.isEmpty()) {
                    jdbcTemplate.batchUpdate(
                            "UPDATE medicamento SET tipomolecula = ?, fecha_clasificacion = ? " +
                            "WHERE idusuario IN (SELECT id FROM usuario WHERE sede = ?) " +
                            "AND codigogenerico = ?",
                            batchUpdateArgs);
                }

                // Registros sin costo o sin código genérico → C por defecto
                jdbcTemplate.update(
                        "UPDATE medicamento SET tipomolecula = 'C' " +
                        "WHERE idusuario IN (SELECT id FROM usuario WHERE sede = ?) " +
                        "AND (costototal <= 0 OR costototal IS NULL OR codigogenerico IS NULL)",
                        sede);

                totalA += famA;
                totalB += famB;
                totalC += famC;

                sedeLog.append("- Sede ").append(sede).append(": Valor $")
                        .append(String.format("%.2f", totalValueSede))
                        .append(" | Familias A:").append(famA)
                        .append(", B:").append(famB)
                        .append(", C:").append(famC).append("\n");
            }

            log.setCountA(totalA);
            log.setCountB(totalB);
            log.setCountC(totalC);
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
        // Agrupa por SEDE REAL (no por idusuario individual) para que sedes con
        // múltiples usuarios muestren el consolidado correcto.
        // MIN(m.idusuario) como representante para compatibilidad con el DTO.
        String sql = "SELECT MIN(m.idusuario) AS idusuario, u.sede, " +
                "COUNT(*) AS total, " +
                "SUM(CASE WHEN UPPER(m.estadodelconteo) = 'SÍ' THEN 1 ELSE 0 END) AS contados, " +
                "SUM(CASE WHEN UPPER(m.estadodelconteo) != 'SÍ' THEN 1 ELSE 0 END) AS pendientes " +
                "FROM medicamento m " +
                "JOIN usuario u ON m.idusuario = u.id " +
                "GROUP BY u.sede";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            com.pharmaser.conteociclico.dto.MedicamentoSummaryDTO dto = new com.pharmaser.conteociclico.dto.MedicamentoSummaryDTO();
            dto.setIdUsuario(rs.getInt("idusuario"));
            dto.setSedeNombre(rs.getString("sede"));
            dto.setTotal(rs.getLong("total"));
            dto.setContados(rs.getLong("contados"));
            dto.setPendientes(rs.getLong("pendientes"));
            return dto;
        });
    }

    public List<Medicamento> searchMedicamentos(String term, int limit) {
        if (term == null || term.trim().isEmpty())
            return new ArrayList<>();
        String pattern = "%" + term.toLowerCase() + "%";
        return jdbcTemplate.query(
                "SELECT * FROM medicamento WHERE LOWER(descripcion) LIKE ? OR LOWER(plu) LIKE ? LIMIT ?",
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

    public List<Medicamento> searchMedicamentosBySede(String term, String sede, int limit) {
        if (term == null || term.trim().isEmpty())
            return new ArrayList<>();
        String pattern = "%" + term.toLowerCase() + "%";
        return jdbcTemplate.query(
                "SELECT m.* FROM medicamento m JOIN usuario u ON m.idusuario = u.id " +
                "WHERE (LOWER(m.descripcion) LIKE ? OR LOWER(m.plu) LIKE ?) AND u.sede = ? LIMIT ?",
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
                }, pattern, pattern, sede, limit);
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

}
