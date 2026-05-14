package com.pharmaser.conteociclico.service;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.pharmaser.conteociclico.dto.MedicamentoImportDTO;
import com.pharmaser.conteociclico.model.LogCargaAutomatica;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import com.jcraft.jsch.*;
import java.io.FileOutputStream;
import java.nio.file.StandardCopyOption;

@Service
public class AutomatedImportService {

    private static final Logger logger = LoggerFactory.getLogger(AutomatedImportService.class);

    @Value("${app.auto-import.input-path}")
    private String inputPath;

    @Value("${app.auto-import.processed-path}")
    private String processedPath;

    @Value("${app.auto-import.enabled:true}")
    private boolean enabled;

    @Value("${app.auto-import.delete-after-process:false}")
    private boolean deleteAfterProcess;

    @Value("${app.sftp.host}")
    private String sftpHost;

    @Value("${app.sftp.port:22}")
    private int sftpPort;

    @Value("${app.sftp.username}")
    private String sftpUser;

    @Value("${app.sftp.password}")
    private String sftpPassword;

    @Value("${app.sftp.remote-path}")
    private String sftpRemotePath;

    @Autowired
    private MedicamentoService medicamentoService;

    @Autowired
    private com.pharmaser.conteociclico.repository.LogCargaAutomaticaRepository logRepository;

    private final java.util.concurrent.atomic.AtomicBoolean isProcessing = new java.util.concurrent.atomic.AtomicBoolean(
            false);

    @Autowired
    private com.pharmaser.conteociclico.repository.CacheIdempotenciaRepository idempotenciaRepository;

    private String calculateFileHash(File file) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(Files.readAllBytes(file.toPath()));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            logger.error("Error calculando hash para {}: {}", file.getName(), e.getMessage());
            return null;
        }
    }

    @Scheduled(fixedRateString = "${app.auto-import.scan-rate:300000}")
    public void processFiles() {
        if (!enabled)
            return;
        // compareAndSet garantiza atomicidad: evita que dos hilos entren simultáneamente
        if (!isProcessing.compareAndSet(false, true)) {
            return;
        }
        try {
            doProcessLocalFiles();
        } finally {
            isProcessing.set(false);
        }
    }

    /**
     * Lógica interna de procesamiento de archivos locales.
     * Solo se llama desde métodos que ya adquirieron el lock isProcessing.
     */
    private void doProcessLocalFiles() {
        try {
            File folder = new File(inputPath);
            if (!folder.exists()) {
                folder.mkdirs();
            }

            File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".csv")
                    || name.toLowerCase().endsWith(".xlsx") || name.toLowerCase().endsWith(".xls"));

            if (files == null || files.length == 0)
                return;

            // Ordenar por fecha para procesar los más viejos primero
            Arrays.sort(files, Comparator.comparingLong(File::lastModified));

            Map<String, Integer> userSedeMap = medicamentoService.buildUserSedeMap();

            for (File file : files) {
                String hash = calculateFileHash(file);
                if (hash == null) continue;

                if (idempotenciaRepository.existsById(hash)) {
                    logger.info("Archivo {} ya procesado anteriormente (Hash duplicado). Moviendo a procesados.", file.getName());
                    try {
                        moveFile(file, true);
                    } catch (IOException e) {
                        logger.error("Error moviendo archivo duplicado {}: {}", file.getName(), e.getMessage());
                    }
                    continue;
                }

                processSingleFile(file, userSedeMap, hash, false);
            }

            // Una única reclasificación al final de todo el lote
            try {
                medicamentoService.reclassifyAllMedicamentos();
            } catch (Exception e) {
                logger.error("Error en reclasificación final del lote: {}", e.getMessage());
            }

        } catch (Exception e) {
            logger.error("Error crítico en proceso de archivos: {}", e.getMessage());
        }
    }

    private void processSingleFile(File file, Map<String, Integer> userSedeMap, String hash, boolean triggerReclass) {
        LogCargaAutomatica logEntry = new LogCargaAutomatica();
        logEntry.setFechaInicio(LocalDateTime.now());
        logEntry.setNombreArchivo(file.getName());
        logEntry.setEstado("PROCESANDO");
        logRepository.save(logEntry);

        StringBuilder logDetails = new StringBuilder(
                "Iniciando procesamiento de " + file.getName() + " (Hash: " + hash + ")\n");
        List<Map<String, String>> rawData = new ArrayList<>();

        try {
            if (file.getName().toLowerCase().endsWith(".csv")) {
                rawData = tryReadAsCsv(file);
            } else if (file.getName().toLowerCase().endsWith(".xlsx")
                    || file.getName().toLowerCase().endsWith(".xls")) {
                try {
                    DataFormatter dataFormatter = new DataFormatter();
                    try (Workbook workbook = WorkbookFactory.create(file)) {
                        Sheet sheet = workbook.getSheetAt(0);
                        Row headerRow = sheet.getRow(0);
                        if (headerRow == null)
                            throw new RuntimeException("Archivo Excel vacío");

                        List<String> headers = new ArrayList<>();
                        for (Cell cell : headerRow) {
                            headers.add(dataFormatter.formatCellValue(cell).trim());
                        }

                        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                            Row row = sheet.getRow(i);
                            if (row == null)
                                continue;
                            Map<String, String> rowDataMap = new HashMap<>();
                            for (int j = 0; j < headers.size(); j++) {
                                Cell cell = row.getCell(j);
                                rowDataMap.put(headers.get(j), dataFormatter.formatCellValue(cell));
                            }
                            rawData.add(rowDataMap);
                        }
                    }
                } catch (Exception excelEx) {
                    logDetails.append("Error de formato Excel (POI): ").append(excelEx.getMessage())
                            .append(". Intentando fallback como CSV...\n");
                    rawData = tryReadAsCsv(file);
                }
            }

            if (rawData != null && !rawData.isEmpty()) {
                logDetails.append("Registros leídos: ").append(rawData.size()).append("\n");
                List<MedicamentoImportDTO> validItems = medicamentoService.normalizeAndValidate(rawData, userSedeMap,
                        logDetails);
                medicamentoService.importFromExternalData(validItems);

                logEntry.setRegistrosLeidos(rawData.size());
                logEntry.setRegistrosProcesados(validItems.size());   // ← antes siempre quedaba en 0
                logEntry.setFechaFin(LocalDateTime.now());

                String detailStr = logDetails.toString();
                if (detailStr.length() > 60000) {
                    detailStr = detailStr.substring(0, 60000) + "... [Truncado]";
                }
                logEntry.setDetalle(detailStr);

                // Registro de Idempotencia: Guardar el hash después del éxito total
                com.pharmaser.conteociclico.model.CacheIdempotencia cache = new com.pharmaser.conteociclico.model.CacheIdempotencia();
                cache.setHashSha256(hash);
                cache.setNombreArchivo(file.getName());
                cache.setTamanoBytes(file.length());
                cache.setFechaProcesamiento(LocalDateTime.now());
                idempotenciaRepository.save(cache);

                // Reclasificación opcional (ahora se controla desde el lote)
                if (triggerReclass) {
                    try {
                        medicamentoService.reclassifyAllMedicamentos();
                        logDetails.append("\n>>> Reclasificación ABC completada exitosamente.");
                    } catch (Exception reclassEx) {
                        logger.error("Error en reclasificación post-import: {}", reclassEx.getMessage());
                        logDetails.append("\n>>> ERROR en reclasificación: ").append(reclassEx.getMessage());
                    }
                }

                logEntry.setEstado("EXITOSO");
                moveFile(file, true);

            } else {
                logEntry.setEstado("ERROR");
                logEntry.setDetalle("No se encontraron registros válidos o el archivo está corrupto/vacío.\n"
                        + logDetails.toString());
                moveFile(file, false);
            }

            logEntry.setFechaFin(LocalDateTime.now());

        } catch (Exception e) {
            logger.error("Error procesando archivo automático {}: ", file.getName(), e);
            logEntry.setEstado("FALLIDO");
            logEntry.setFechaFin(LocalDateTime.now());
            String finalLog = logDetails.append("\nError fatal: ").append(e.getMessage()).toString();
            if (finalLog.length() > 60000)
                finalLog = finalLog.substring(0, 60000) + "... [Truncado]";
            logEntry.setDetalle(finalLog);
            try {
                moveFile(file, false);
            } catch (IOException ex) {
                logger.error("Error moviendo archivo fallido: {}", ex.getMessage());
            }
        } finally {
            logRepository.save(logEntry);
        }
    }

    private List<Map<String, String>> tryReadAsCsv(File file) {
        char[] delimiters = { ';', ',', '\t' };
        char detectedDelimiter = 0;

        // 1. Detectar el delimitador probando las primeras líneas
        for (char delimiter : delimiters) {
            try (CSVReader reader = new CSVReaderBuilder(new FileReader(file))
                    .withCSVParser(new com.opencsv.RFC4180ParserBuilder().withSeparator(delimiter).build())
                    .build()) {
                String[] header = reader.readNext();
                if (header != null && header.length > 1) {
                    detectedDelimiter = delimiter;
                    break;
                }
            } catch (Exception ignore) {
            }
        }

        List<Map<String, String>> data = new ArrayList<>();
        if (detectedDelimiter == 0)
            detectedDelimiter = ';'; // Default

        // 2. Leer el archivo completo con el delimitador detectado
        try (CSVReader reader = new CSVReaderBuilder(new FileReader(file))
                .withCSVParser(new com.opencsv.RFC4180ParserBuilder().withSeparator(detectedDelimiter).build())
                .build()) {

            String[] header = reader.readNext();
            if (header != null) {
                String[] line;
                while ((line = reader.readNext()) != null) {
                    Map<String, String> row = new HashMap<>();
                    for (int i = 0; i < Math.min(header.length, line.length); i++) {
                        row.put(header[i], line[i]);
                    }
                    data.add(row);
                }
            }
        } catch (Exception e) {
            logger.error("Error definitivo leyendo CSV con delimitador " + detectedDelimiter, e);
        }
        return data;
    }

    private void moveFile(File file, boolean success) throws IOException {
        if (deleteAfterProcess && success) {
            Files.deleteIfExists(file.toPath());
            logger.info("Archivo {} eliminado tras procesamiento exitoso.", file.getName());
            return;
        }

        String subFolder = success ? "" : "/fallidos";
        Path targetDir = Paths.get(processedPath + subFolder);
        
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        String timestamp = String.valueOf(System.currentTimeMillis());
        Path targetPath = targetDir.resolve(timestamp + "_" + file.getName());
        
        try {
            Files.move(file.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.error("No se pudo mover el archivo {} a {}: {}", file.getName(), targetPath, e.getMessage());
            // Si falla el movimiento, intentamos copiar y borrar (algunas veces falla en Windows si hay bloqueos)
            Files.copy(file.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(file.toPath());
        }
    }

    // Ejecutar inmediatamente al arrancar el proyecto
    @org.springframework.context.event.EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!enabled)
            return;
        logger.info(">>> ARRANQUE: Iniciando primera sincronización automática desde SFTP...");
        scheduledBotSync();
    }

    @Scheduled(fixedDelayString = "${app.auto-import.polling-rate:1800000}") // Default 30 min
    public void scheduledBotSync() {
        if (!enabled)
            return;
        // Guard al nivel del ciclo SFTP completo: descarga + procesamiento son una sola
        // unidad atómica. Si el scheduler y onApplicationReady disparan casi al mismo
        // tiempo, el segundo hilo ve isProcessing=true y desiste ANTES de conectarse
        // al SFTP, evitando la doble descarga y el "No such file" al borrar.
        if (!isProcessing.compareAndSet(false, true)) {
            logger.info("Sincronización SFTP ya en progreso (otro hilo activo). Saltando esta ejecución.");
            return;
        }
        try {
            logger.info("Iniciando sincronización programada desde SFTP...");
            downloadFilesFromSftp();
            doProcessLocalFiles();
            logger.info("Sincronización técnica completada.");
        } finally {
            isProcessing.set(false);
        }
    }

    private void downloadFilesFromSftp() {
        logger.info("Conectando a SFTP {}:{}...", sftpHost, sftpPort);
        Session session = null;
        ChannelSftp channelSftp = null;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(sftpUser, sftpHost, sftpPort);
            session.setPassword(sftpPassword);

            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);

            session.connect();
            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect();

            channelSftp.cd(sftpRemotePath);

            Vector<ChannelSftp.LsEntry> fileList = channelSftp.ls("*");
            List<ChannelSftp.LsEntry> remoteFiles = new ArrayList<>();

            for (ChannelSftp.LsEntry entry : fileList) {
                if (!entry.getAttrs().isDir()) {
                    String fileName = entry.getFilename();
                    if (fileName.toLowerCase().endsWith(".xls") || fileName.toLowerCase().endsWith(".xlsx")
                            || fileName.toLowerCase().endsWith(".csv")) {
                        remoteFiles.add(entry);
                    }
                }
            }

            if (remoteFiles.isEmpty()) {
                logger.info("No se encontraron archivos nuevos en el SFTP.");
                return;
            }

            logger.info("Se detectaron {} archivos en SFTP. Iniciando descarga...", remoteFiles.size());

            for (ChannelSftp.LsEntry entry : remoteFiles) {
                String fileName = entry.getFilename();
                File localFile = new File(inputPath, fileName);
                
                try (FileOutputStream fos = new FileOutputStream(localFile)) {
                    channelSftp.get(fileName, fos);
                    logger.info("Archivo {} descargado exitosamente.", fileName);
                    
                    // Solo eliminamos del SFTP si la descarga local fue exitosa
                    try {
                        channelSftp.rm(fileName);
                    } catch (Exception e) {
                        logger.error("No se pudo eliminar {} del SFTP tras descarga: {}", fileName, e.getMessage());
                    }
                } catch (Exception downloadEx) {
                    logger.error("Error descargando archivo {} desde SFTP: {}", fileName, downloadEx.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("Error durante el proceso SFTP: {}", e.getMessage());
        } finally {
            if (channelSftp != null && channelSftp.isConnected())
                channelSftp.disconnect();
            if (session != null && session.isConnected())
                session.disconnect();
        }
    }
}
