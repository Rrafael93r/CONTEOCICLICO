package com.pharmaser.conteociclico.service;

import com.pharmaser.conteociclico.model.LogMaestraImport;
import com.pharmaser.conteociclico.model.MaestraMedicamento;
import com.pharmaser.conteociclico.repository.LogMaestraImportRepository;
import com.pharmaser.conteociclico.repository.MaestraMedicamentoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.*;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MaestraMedicamentoService {

    @Autowired
    private MaestraMedicamentoRepository repository;

    @Autowired
    private LogMaestraImportRepository logRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<MaestraMedicamento> findAll() {
        return repository.findAll();
    }

    public Optional<MaestraMedicamento> findById(Long id) {
        return repository.findById(id);
    }

    public Optional<MaestraMedicamento> findByPlu(String plu) {
        return repository.findByPlu(plu);
    }

    public MaestraMedicamento save(MaestraMedicamento maestraMedicamento) {
        if (maestraMedicamento == null) {
            throw new IllegalArgumentException("La entidad maestra_medicamento no puede ser nula");
        }
        return repository.save(maestraMedicamento);
    }

    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    public List<MaestraMedicamento> saveAllItems(List<MaestraMedicamento> lista) {
        if (lista == null) {
            return java.util.Collections.emptyList();
        }
        return repository.saveAll(lista);
    }

    @Transactional
    public void bulkUpsert(List<MaestraMedicamento> items) {
        if (items == null || items.isEmpty()) return;

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO maestra_medicamentos (item, plu, codigogenerico, nombre, nombre_comercial, laboratorio, ");
        sql.append("forma_farmaceutica, concentracion, concentracion2, unidad_concentracion, unidad_contenido, ");
        sql.append("concentracion_agrupada, registro_sanitario, cum, contrato, unidad_medida, rips_codigo, rips_unidad) ");
        sql.append("VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ");
        sql.append("ON DUPLICATE KEY UPDATE ");
        sql.append("item = VALUES(item), codigogenerico = VALUES(codigogenerico), nombre = VALUES(nombre), ");
        sql.append("nombre_comercial = VALUES(nombre_comercial), laboratorio = VALUES(laboratorio), ");
        sql.append("forma_farmaceutica = VALUES(forma_farmaceutica), concentracion = VALUES(concentracion), ");
        sql.append("concentracion2 = VALUES(concentracion2), unidad_concentracion = VALUES(unidad_concentracion), ");
        sql.append("unidad_contenido = VALUES(unidad_contenido), concentracion_agrupada = VALUES(concentracion_agrupada), ");
        sql.append("registro_sanitario = VALUES(registro_sanitario), cum = VALUES(cum), contrato = VALUES(contrato), ");
        sql.append("unidad_medida = VALUES(unidad_medida), rips_codigo = VALUES(rips_codigo), rips_unidad = VALUES(rips_unidad)");

        String finalSql = sql.toString();
        int batchSize = 1000;
        
        for (int i = 0; i < items.size(); i += batchSize) {
            final List<MaestraMedicamento> batch = items.subList(i, Math.min(i + batchSize, items.size()));
            jdbcTemplate.batchUpdate(finalSql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                @Override
                public void setValues(java.sql.PreparedStatement ps, int j) throws java.sql.SQLException {
                    MaestraMedicamento m = batch.get(j);
                    ps.setObject(1, m.getItem());
                    ps.setString(2, m.getPlu());
                    ps.setString(3, m.getCodigogenerico());
                    ps.setString(4, m.getNombre());
                    ps.setString(5, m.getNombreComercial());
                    ps.setString(6, m.getLaboratorio());
                    ps.setString(7, m.getFormaFarmaceutica());
                    ps.setString(8, m.getConcentracion());
                    ps.setString(9, m.getConcentracion2());
                    ps.setString(10, m.getUnidadConcentracion());
                    ps.setString(11, m.getUnidadContenido());
                    ps.setString(12, m.getConcentracionAgrupada());
                    ps.setString(13, m.getRegistroSanitario());
                    ps.setString(14, m.getCum());
                    ps.setString(15, m.getContrato());
                    ps.setString(16, m.getUnidadMedida());
                    ps.setString(17, m.getRipsCodigo());
                    ps.setString(18, m.getRipsUnidad());
                }
                @Override
                public int getBatchSize() {
                    return batch.size();
                }
            });
        }
    }

    @org.springframework.cache.annotation.Cacheable(value = "maestraPluMap", key = "'all'")
    public Map<String, String> getPluDescriptionMap() {
        return repository.findAll().stream()
                .filter(m -> m.getPlu() != null && m.getNombre() != null)
                .collect(Collectors.toMap(
                        MaestraMedicamento::getPlu,
                        MaestraMedicamento::getNombre,
                        (existing, replacement) -> existing
                ));
    }

    @Transactional
    public Map<String, Object> importarArchivo(MultipartFile file, String usuario) {
        LogMaestraImport log = new LogMaestraImport();
        log.setFechaEjecucion(LocalDateTime.now());
        log.setUsuario(usuario);
        log.setEstado("PROCESANDO");
        List<MaestraMedicamento> items = new ArrayList<>();
        Map<String, MaestraMedicamento> uniqueItems = new HashMap<>();
        List<String> skippedLines = new ArrayList<>();
        int totalRowsRead = 0;

        try {
            List<Map<String, String>> rawData;
            String fileName = file.getOriginalFilename().toLowerCase();

            if (fileName.endsWith(".csv")) {
                rawData = readCsv(file);
            } else if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
                rawData = readExcel(file);
            } else {
                throw new RuntimeException("Formato de archivo no soportado");
            }

            totalRowsRead = rawData.size();
            int lineNum = 1;

            for (Map<String, String> row : rawData) {
                lineNum++;
                Map<String, String> n = new HashMap<>();
                row.forEach((k, v) -> n.put(k.toLowerCase().trim(), v != null ? v.trim() : ""));

                String plu = n.getOrDefault("referencia", n.getOrDefault("plu", n.getOrDefault("codigo", "")));
                String nombre = n.getOrDefault("notas", n.getOrDefault("nombre", n.getOrDefault("descripcion", "")));

                if (plu.isEmpty()) {
                    skippedLines.add("Línea " + lineNum + ": PLU/Referencia vacío.");
                    continue;
                }
                if (nombre.isEmpty()) {
                    nombre = n.getOrDefault("med-nombre comercial", n.getOrDefault("nombre", ""));
                    if (nombre.isEmpty()) {
                        skippedLines.add("Línea " + lineNum + ": Descripción/Notas vacía para PLU " + plu + ".");
                        continue;
                    }
                }
                
                MaestraMedicamento m = new MaestraMedicamento();
                m.setPlu(plu);
                m.setNombre(nombre);
                
                try {
                    String itemStr = n.getOrDefault("item", "");
                    if (!itemStr.isEmpty()) m.setItem((int)Double.parseDouble(itemStr.replace(",", ".")));
                } catch (Exception ignore) {}

                String codGen = n.getOrDefault("med-codigo generico audifarma", "");
                if (codGen.isEmpty()) {
                    codGen = plu;
                    if (plu.contains("_")) codGen = plu.substring(0, plu.indexOf("_"));
                }
                m.setCodigogenerico(codGen);
                m.setNombreComercial(n.getOrDefault("med-nombre comercial", ""));
                m.setLaboratorio(n.getOrDefault("med-laboratorio", ""));
                m.setFormaFarmaceutica(n.getOrDefault("med-forma farmaceutica", ""));
                m.setConcentracion(n.getOrDefault("med-concentracion", ""));
                m.setConcentracion2(n.getOrDefault("med-concentracion 2", ""));
                m.setUnidadConcentracion(n.getOrDefault("med-unidad de concentracion", ""));
                m.setUnidadContenido(n.getOrDefault("med-unidad de contenido", ""));
                m.setConcentracionAgrupada(n.getOrDefault("med-concentracion agrupada", ""));
                m.setRegistroSanitario(n.getOrDefault("med-registro sanitario", ""));
                m.setCum(n.getOrDefault("med-cum", ""));
                m.setContrato(n.getOrDefault("med-contrato", ""));
                
                String um = n.getOrDefault("u.m. invent.", n.getOrDefault("u.m. orden", ""));
                m.setUnidadMedida(um);
                m.setRipsCodigo(n.getOrDefault("med-concentracion medicamento rips", ""));
                m.setRipsUnidad(n.getOrDefault("med-unidad medida rips", ""));

                uniqueItems.put(plu, m);
            }

            items.addAll(uniqueItems.values());

            if (!items.isEmpty()) {
                bulkUpsert(items);
                log.setEstado(skippedLines.isEmpty() ? "EXITOSO" : "ADVERTENCIA");
                log.setRegistrosProcesados(items.size());
                if (!skippedLines.isEmpty()) {
                    log.setMensajeError("Se omitieron " + skippedLines.size() + " líneas. Se cargaron " + items.size() + " registros.");
                }
            } else {
                log.setEstado("ADVERTENCIA");
                log.setMensajeError("No se encontraron registros válidos.");
            }

        } catch (Exception e) {
            log.setEstado("FALLIDO");
            log.setMensajeError(e.getMessage());
        } finally {
            logRepository.save(log);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("estado", log.getEstado());
        response.put("leidos", totalRowsRead);
        response.put("procesados", items.size());
        response.put("duplicadosOmitidos", totalRowsRead - items.size() - skippedLines.size());
        response.put("error", log.getMensajeError());
        response.put("detallesOmitidos", skippedLines);
        return response;
    }

    private List<Map<String, String>> readCsv(MultipartFile file) throws Exception {
        List<Map<String, String>> data = new ArrayList<>();
        char[] delimiters = { ';', ',', '\t' };
        char detectedDelimiter = 0;
        for (char d : delimiters) {
            try (CSVReader reader = new CSVReaderBuilder(new InputStreamReader(file.getInputStream()))
                    .withCSVParser(new com.opencsv.RFC4180ParserBuilder().withSeparator(d).build())
                    .build()) {
                String[] header = reader.readNext();
                if (header != null && header.length > 1) {
                    detectedDelimiter = d;
                    break;
                }
            } catch (Exception ignore) {}
        }
        if (detectedDelimiter == 0) detectedDelimiter = ';';
        try (CSVReader reader = new CSVReaderBuilder(new InputStreamReader(file.getInputStream()))
                .withCSVParser(new com.opencsv.RFC4180ParserBuilder().withSeparator(detectedDelimiter).build())
                .build()) {
            String[] header = reader.readNext();
            if (header != null) {
                String[] line;
                while ((line = reader.readNext()) != null) {
                    Map<String, String> row = new HashMap<>();
                    for (int i = 0; i < Math.min(header.length, line.length); i++) row.put(header[i], line[i]);
                    data.add(row);
                }
            }
        }
        return data;
    }

    private List<Map<String, String>> readExcel(MultipartFile file) throws Exception {
        List<Map<String, String>> data = new ArrayList<>();
        DataFormatter dataFormatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return data;
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) headers.add(dataFormatter.formatCellValue(cell).trim());
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Map<String, String> rowDataMap = new HashMap<>();
                for (int j = 0; j < headers.size(); j++) {
                    Cell cell = row.getCell(j);
                    rowDataMap.put(headers.get(j), dataFormatter.formatCellValue(cell));
                }
                data.add(rowDataMap);
            }
        }
        return data;
    }
}
