package com.pharmaser.conteociclico.service;

import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CostoSedeService {

    private static final Logger logger = LoggerFactory.getLogger(CostoSedeService.class);
    private static final int BATCH_SIZE = 1000;

    // INSERT nativo con UPSERT: si ya existe (centro_costo, plu) solo actualiza el costo.
    // Un solo viaje a MySQL por cada lote de 1000 filas — sin findAll(), sin Hibernate por fila.
    private static final String UPSERT_SQL =
        "INSERT INTO costos_sede (centro_costo, plu, costo_unitario) " +
        "VALUES (?, ?, ?) " +
        "ON DUPLICATE KEY UPDATE costo_unitario = VALUES(costo_unitario)";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Carga el mapa de costos reales (centro_costo, plu) → costo_unitario en caché.
     * Se usa en cada importación automática para enriquecer los precios antes de guardar.
     * La caché se invalida automáticamente cuando se sube un nuevo archivo de costos.
     */
    @org.springframework.cache.annotation.Cacheable(value = "costoSedeMap", key = "'all'")
    public Map<String, Double> buildCostMap() {
        logger.info("Cargando mapa de costos por sede desde BD...");
        return jdbcTemplate.query(
            "SELECT centro_costo, plu, costo_unitario FROM costos_sede WHERE costo_unitario > 0",
            rs -> {
                Map<String, Double> map = new HashMap<>();
                while (rs.next()) {
                    // Clave normalizada: int del centro_costo + "-" + plu
                    // Así "001" y "1" del archivo siempre mapean al mismo registro
                    String key = rs.getInt("centro_costo") + "-" + rs.getString("plu").trim();
                    map.put(key, rs.getDouble("costo_unitario"));
                }
                logger.info("Mapa de costos cargado: {} registros.", map.size());
                return map;
            }
        );
    }

    @org.springframework.cache.annotation.CacheEvict(value = "costoSedeMap", allEntries = true)
    @Transactional
    public String procesarArchivo(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("El archivo está vacío");
        }

        int registrosLeidos = 0;
        int registrosProcesados = 0;
        int errores = 0;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            if (headerRow == null) {
                throw new RuntimeException("El archivo Excel no tiene encabezados.");
            }

            // Detectar columnas por nombre de encabezado (flexible ante variaciones)
            int centroCostoIdx = -1;
            int pluIdx = -1;
            int costoIdx = -1;

            DataFormatter formatter = new DataFormatter();
            for (Cell cell : headerRow) {
                String header = formatter.formatCellValue(cell).trim().toLowerCase();
                if (header.contains("centro")) centroCostoIdx = cell.getColumnIndex();
                else if (header.contains("plu"))  pluIdx          = cell.getColumnIndex();
                else if (header.contains("costo")) costoIdx        = cell.getColumnIndex();
            }

            if (centroCostoIdx == -1 || pluIdx == -1 || costoIdx == -1) {
                throw new RuntimeException(
                    "Columnas requeridas no encontradas. Se esperan: 'Centro Costo', 'PLU', 'Costo Unitario'."
                );
            }

            // Acumular filas en lotes y enviar con batchUpdate (un round-trip por lote)
            List<Object[]> batch = new ArrayList<>(BATCH_SIZE);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                registrosLeidos++;

                try {
                    String centroCostoStr = formatter.formatCellValue(row.getCell(centroCostoIdx)).trim();
                    String plu            = formatter.formatCellValue(row.getCell(pluIdx)).trim();
                    String costoStr       = formatter.formatCellValue(row.getCell(costoIdx))
                                                     .trim()
                                                     .replace(",", ".")
                                                     .replaceAll("[^\\d.]", ""); // limpiar formato moneda

                    if (centroCostoStr.isEmpty() || plu.isEmpty() || costoStr.isEmpty()) continue;

                    Integer centroCosto   = Integer.parseInt(centroCostoStr);
                    BigDecimal costo      = new BigDecimal(costoStr);

                    if (costo.compareTo(BigDecimal.ZERO) < 0) continue; // ignorar costos negativos

                    batch.add(new Object[]{centroCosto, plu, costo});
                    registrosProcesados++;

                    if (batch.size() >= BATCH_SIZE) {
                        jdbcTemplate.batchUpdate(UPSERT_SQL, batch);
                        batch.clear();
                    }

                } catch (Exception ex) {
                    errores++;
                    logger.warn("Fila {} ignorada: {}", i + 1, ex.getMessage());
                }
            }

            // Enviar el lote final (si quedaron filas)
            if (!batch.isEmpty()) {
                jdbcTemplate.batchUpdate(UPSERT_SQL, batch);
            }

            return String.format(
                "Importación completada. Leídos: %d | Procesados: %d | Errores: %d",
                registrosLeidos, registrosProcesados, errores
            );

        } catch (Exception e) {
            logger.error("Error al procesar archivo de costos por sede: {}", e.getMessage(), e);
            throw new RuntimeException("Error al procesar el archivo: " + e.getMessage());
        }
    }
}
