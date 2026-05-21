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

    @Value("${app.sftp.host:}")
    private String sftpHost;

    @Value("${app.sftp.port:22}")
    private int sftpPort;

    @Value("${app.sftp.username:}")
    private String sftpUser;

    @Value("${app.sftp.password:}")
    private String sftpPassword;

    @Value("${app.sftp.remote-path:}")
    private String sftpRemotePath;

    @Autowired
    private MedicamentoService medicamentoService;

    @Autowired
    private com.pharmaser.conteociclico.repository.LogCargaAutomaticaRepository logRepository;

    private final java.util.concurrent.atomic.AtomicBoolean isProcessing =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    @Autowired
    private com.pharmaser.conteociclico.repository.CacheIdempotenciaRepository idempotenciaRepository;

    // ── API pública para que el controller sepa si hay un ciclo activo ──────────
    public boolean isRunning() {
        return isProcessing.get();
    }

    // ── Hash SHA-256 de archivo ──────────────────────────────────────────────────
    private String calculateFileHash(File file) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(Files.readAllBytes(file.toPath()));
            StringBuilder hexString = new StringBuilder(2 * encodedHash.length);
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            logger.error("Error calculando hash para {}: {}", file.getName(), e.getMessage());
            return null;
        }
    }

    // ── Escaneo de archivos locales (cada 5 min) ─────────────────────────────────
    @Scheduled(fixedRateString = "${app.auto-import.scan-rate:300000}")
    public void processFiles() {
        if (!enabled) return;
        if (!isProcessing.compareAndSet(false, true)) return;
        try {
            doProcessLocalFiles();
        } finally {
            isProcessing.set(false);
        }
    }

    /**
     * Procesamiento local: escanea la carpeta de entrada y procesa cada archivo nuevo.
     * Crea un log entry para CADA archivo, más uno de tipo SFTP_SYNC cuando no hay archivos.
     */
    private void doProcessLocalFiles() {
        try {
            File folder = new File(inputPath);
            if (!folder.exists()) {
                folder.mkdirs();
            }

            File[] files = folder.listFiles((dir, name) ->
                    name.toLowerCase().endsWith(".csv") ||
                    name.toLowerCase().endsWith(".xlsx") ||
                    name.toLowerCase().endsWith(".xls"));

            if (files == null || files.length == 0) {
                // ── FIX: Registrar que el scan ocurrió pero no había archivos ──────
                crearLogEscaneoVacio();
                return;
            }

            // Ordenar por fecha para procesar los más viejos primero
            Arrays.sort(files, Comparator.comparingLong(File::lastModified));

            // userSedeMap se mantiene por compatibilidad con normalizeAndValidate (ignorado internamente)
            @SuppressWarnings("deprecation")
            Map<String, Integer> userSedeMap = medicamentoService.buildUserSedeMap();

            for (File file : files) {
                String hash = calculateFileHash(file);
                if (hash == null) {
                    // ── FIX: Registrar archivos que no pudieron hashearse ────────────
                    crearLogError(file.getName(), "No se pudo calcular el hash SHA-256 del archivo. " +
                            "Puede estar bloqueado o corrupto.");
                    continue;
                }

                if (idempotenciaRepository.existsById(hash)) {
                    logger.info("Archivo {} ya procesado (Hash duplicado). Moviendo a procesados.", file.getName());
                    try {
                        moveFile(file, true);
                    } catch (IOException e) {
                        logger.error("Error moviendo archivo duplicado {}: {}", file.getName(), e.getMessage());
                    }
                    continue;
                }

                processSingleFile(file, userSedeMap, hash, false);
            }

            // Única reclasificación al final del lote
            try {
                medicamentoService.reclassifyAllMedicamentos();
            } catch (Exception e) {
                logger.error("Error en reclasificación final del lote: {}", e.getMessage());
            }

        } catch (Exception e) {
            logger.error("Error crítico en proceso de archivos: {}", e.getMessage());
        }
    }

    /**
     * Procesa un único archivo. Log entry siempre creado (EXITOSO / ERROR / FALLIDO).
     *
     * FIX: El estado del log refleja el PROCESAMIENTO, no el movimiento del archivo.
     * Si el procesamiento fue exitoso pero mover el archivo falla, el log queda EXITOSO
     * con una nota en el detalle — el archivo se moverá en el próximo ciclo (idempotencia).
     */
    private void processSingleFile(File file, Map<String, Integer> userSedeMap,
                                    String hash, boolean triggerReclass) {
        LogCargaAutomatica logEntry = new LogCargaAutomatica();
        logEntry.setFechaInicio(LocalDateTime.now());
        logEntry.setNombreArchivo(file.getName());
        logEntry.setEstado("PROCESANDO");
        logRepository.save(logEntry);  // ID generado desde aquí

        StringBuilder logDetails = new StringBuilder(
                "Iniciando procesamiento de " + file.getName() + " (Hash: " + hash + ")\n");
        List<Map<String, String>> rawData = new ArrayList<>();
        boolean processingOk = false;

        try {
            // ── Lectura del archivo ──────────────────────────────────────────────
            if (file.getName().toLowerCase().endsWith(".csv")) {
                rawData = tryReadAsCsv(file);
            } else if (file.getName().toLowerCase().endsWith(".xlsx") ||
                       file.getName().toLowerCase().endsWith(".xls")) {
                try {
                    DataFormatter dataFormatter = new DataFormatter();
                    try (Workbook workbook = WorkbookFactory.create(file)) {
                        Sheet sheet = workbook.getSheetAt(0);
                        Row headerRow = sheet.getRow(0);
                        if (headerRow == null) throw new RuntimeException("Archivo Excel vacío");

                        List<String> headers = new ArrayList<>();
                        for (Cell cell : headerRow) {
                            headers.add(dataFormatter.formatCellValue(cell).trim());
                        }

                        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                            Row row = sheet.getRow(i);
                            if (row == null) continue;
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

            // ── Validación y carga ───────────────────────────────────────────────
            if (rawData != null && !rawData.isEmpty()) {
                logDetails.append("Registros leídos: ").append(rawData.size()).append("\n");
                List<MedicamentoImportDTO> validItems =
                        medicamentoService.normalizeAndValidate(rawData, userSedeMap, logDetails);
                medicamentoService.importFromExternalData(validItems);

                logEntry.setRegistrosLeidos(rawData.size());
                logEntry.setRegistrosProcesados(validItems.size());

                // Idempotencia: guardar hash DESPUÉS del éxito total de procesamiento
                com.pharmaser.conteociclico.model.CacheIdempotencia cache =
                        new com.pharmaser.conteociclico.model.CacheIdempotencia();
                cache.setHashSha256(hash);
                cache.setNombreArchivo(file.getName());
                cache.setTamanoBytes(file.length());
                cache.setFechaProcesamiento(LocalDateTime.now());
                idempotenciaRepository.save(cache);

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
                processingOk = true;

            } else {
                logEntry.setRegistrosLeidos(rawData != null ? rawData.size() : 0);
                logEntry.setRegistrosProcesados(0);
                logEntry.setEstado("ERROR");
                logDetails.append("No se encontraron registros válidos o el archivo está corrupto/vacío.");
            }

        } catch (Exception e) {
            logger.error("Error procesando archivo automático {}: ", file.getName(), e);
            logEntry.setEstado("FALLIDO");
            logDetails.append("\nError fatal durante procesamiento: ").append(e.getMessage());

        } finally {
            // ── Siempre guardar el log con el estado de PROCESAMIENTO ────────────
            logEntry.setFechaFin(LocalDateTime.now());
            String detailStr = logDetails.toString();
            if (detailStr.length() > 60000) detailStr = detailStr.substring(0, 60000) + "... [Truncado]";
            logEntry.setDetalle(detailStr);
            logRepository.save(logEntry);
        }

        // ── Mover el archivo FUERA del try principal ──────────────────────────────
        // FIX: El fallo al mover no debe cambiar el estado de procesamiento.
        // Si el archivo no puede moverse, en el próximo ciclo se detectará como
        // duplicado (hash ya está en idempotencia) y se intentará mover de nuevo.
        // Captura final para uso dentro del lambda (processingOk no es effectively final
        // porque se reasigna en el bloque try principal).
        final boolean processingOkFinal = processingOk;
        try {
            moveFile(file, processingOkFinal);
        } catch (IOException e) {
            logger.error("No se pudo mover el archivo {} tras procesamiento ({}): {}",
                    file.getName(), processingOkFinal ? "EXITOSO" : "FALLIDO", e.getMessage());
            // Actualizar detalle del log con la advertencia de movimiento fallido
            logRepository.findByNombreArchivo(file.getName()).ifPresent(existingLog -> {
                String d = existingLog.getDetalle() != null ? existingLog.getDetalle() : "";
                existingLog.setDetalle(d + "\n⚠ Advertencia: no se pudo mover el archivo a la carpeta de " +
                        (processingOkFinal ? "procesados" : "fallidos") + ". Se intentará en el próximo ciclo.");
                logRepository.save(existingLog);
            });
        }
    }

    // ── Helpers de log para casos sin archivo ──────────────────────────────────
    private void crearLogEscaneoVacio() {
        // Solo se crea una entrada si la última entrada de escaneo tiene más de 10 minutos
        // para no saturar la tabla con entradas "sin archivos" cada 5 minutos.
        logRepository.findAllByOrderByFechaInicioDesc().stream()
                .filter(l -> "ESCANEO_SIN_ARCHIVOS".equals(l.getNombreArchivo()))
                .findFirst()
                .ifPresentOrElse(ultimo -> {
                    if (ultimo.getFechaInicio() != null &&
                            ultimo.getFechaInicio().plusMinutes(10).isBefore(LocalDateTime.now())) {
                        persistirLogEscaneoVacio();
                    }
                }, this::persistirLogEscaneoVacio);
    }

    private void persistirLogEscaneoVacio() {
        LogCargaAutomatica log = new LogCargaAutomatica();
        log.setNombreArchivo("ESCANEO_SIN_ARCHIVOS");
        log.setFechaInicio(LocalDateTime.now());
        log.setFechaFin(LocalDateTime.now());
        log.setEstado("EXITOSO");
        log.setRegistrosLeidos(0);
        log.setRegistrosProcesados(0);
        log.setDetalle("Escaneo de carpeta completado. No se encontraron archivos nuevos para procesar.");
        logRepository.save(log);
    }

    private void crearLogError(String nombreArchivo, String detalle) {
        LogCargaAutomatica log = new LogCargaAutomatica();
        log.setNombreArchivo(nombreArchivo);
        log.setFechaInicio(LocalDateTime.now());
        log.setFechaFin(LocalDateTime.now());
        log.setEstado("ERROR");
        log.setRegistrosLeidos(0);
        log.setRegistrosProcesados(0);
        log.setDetalle(detalle);
        logRepository.save(log);
    }

    // ── CSV reader con auto-detección de delimitador ─────────────────────────────
    private List<Map<String, String>> tryReadAsCsv(File file) {
        char[] delimiters = {';', ',', '\t'};
        char detectedDelimiter = 0;

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
        if (detectedDelimiter == 0) detectedDelimiter = ';';

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
            logger.error("Error definitivo leyendo CSV con delimitador {}", detectedDelimiter, e);
        }
        return data;
    }

    // ── Mover archivo ────────────────────────────────────────────────────────────
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
            logger.error("No se pudo mover {} a {}: {}", file.getName(), targetPath, e.getMessage());
            // Fallback: copiar + borrar
            Files.copy(file.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(file.toPath());
        }
    }

    // ── Arranque: primera sincronización SFTP ────────────────────────────────────
    @org.springframework.context.event.EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!enabled) {
            logger.info("Carga automática DESHABILITADA (app.auto-import.enabled=false). " +
                        "Cambia APP_AUTO_IMPORT_ENABLED=true para activarla.");
            return;
        }
        logger.info(">>> ARRANQUE: Iniciando primera sincronización automática desde SFTP...");
        scheduledBotSync();
    }

    // ── Sincronización completa SFTP + procesamiento local (cada 30 min) ─────────
    @Scheduled(fixedDelayString = "${app.auto-import.polling-rate:1800000}")
    public void scheduledBotSync() {
        if (!enabled) return;
        if (!isProcessing.compareAndSet(false, true)) {
            logger.info("Sincronización SFTP ya en progreso (otro hilo activo). Saltando esta ejecución.");
            return;
        }

        LogCargaAutomatica sftpLog = new LogCargaAutomatica();
        sftpLog.setNombreArchivo("SFTP_SYNC");
        sftpLog.setFechaInicio(LocalDateTime.now());
        sftpLog.setEstado("PROCESANDO");
        sftpLog.setRegistrosLeidos(0);
        sftpLog.setRegistrosProcesados(0);
        logRepository.save(sftpLog);

        try {
            logger.info("Iniciando sincronización programada desde SFTP...");

            // ── FIX: SFTP ahora devuelve el nº de archivos descargados y lanza excepción si falla
            int descargados = downloadFilesFromSftp();
            sftpLog.setEstado("EXITOSO");
            sftpLog.setDetalle("SFTP completado. Archivos descargados: " + descargados);
            sftpLog.setRegistrosLeidos(descargados);

        } catch (Exception e) {
            // ── FIX: Los errores SFTP ahora quedan registrados en la tabla de logs
            String msg = "Error de conexión/descarga SFTP: " + e.getMessage();
            logger.error(msg, e);
            sftpLog.setEstado("FALLIDO");
            sftpLog.setDetalle(msg);
        } finally {
            sftpLog.setFechaFin(LocalDateTime.now());
            logRepository.save(sftpLog);
        }

        // Procesar archivos locales independientemente del resultado SFTP
        // (puede haber archivos subidos manualmente)
        try {
            doProcessLocalFiles();
            logger.info("Sincronización técnica completada.");
        } finally {
            isProcessing.set(false);
        }
    }

    /**
     * Descarga archivos desde SFTP.
     *
     * FIX: Ahora lanza excepción si la conexión falla (en lugar de tragársela)
     * para que scheduledBotSync() pueda registrarla en el log.
     *
     * @return número de archivos descargados exitosamente
     * @throws Exception si la conexión o autenticación SFTP falla
     */
    private int downloadFilesFromSftp() throws Exception {
        if (sftpHost == null || sftpHost.isBlank()) {
            logger.info("SFTP no configurado (SFTP_HOST vacío). Saltando descarga remota.");
            return 0;
        }

        logger.info("Conectando a SFTP {}:{}...", sftpHost, sftpPort);
        Session session = null;
        ChannelSftp channelSftp = null;
        int descargados = 0;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(sftpUser, sftpHost, sftpPort);
            session.setPassword(sftpPassword);

            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);

            session.connect(15000); // timeout de 15 s
            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect(10000);

            channelSftp.cd(sftpRemotePath);

            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> fileList = channelSftp.ls("*");
            List<ChannelSftp.LsEntry> remoteFiles = new ArrayList<>();

            for (ChannelSftp.LsEntry entry : fileList) {
                if (!entry.getAttrs().isDir()) {
                    String fileName = entry.getFilename();
                    if (fileName.toLowerCase().endsWith(".xls") ||
                        fileName.toLowerCase().endsWith(".xlsx") ||
                        fileName.toLowerCase().endsWith(".csv")) {
                        remoteFiles.add(entry);
                    }
                }
            }

            if (remoteFiles.isEmpty()) {
                logger.info("No se encontraron archivos nuevos en el SFTP.");
                return 0;
            }

            logger.info("Se detectaron {} archivo(s) en SFTP. Iniciando descarga...", remoteFiles.size());

            for (ChannelSftp.LsEntry entry : remoteFiles) {
                String fileName = entry.getFilename();
                File localFile = new File(inputPath, fileName);

                try (FileOutputStream fos = new FileOutputStream(localFile)) {
                    channelSftp.get(fileName, fos);
                    logger.info("Archivo {} descargado exitosamente.", fileName);
                    descargados++;

                    try {
                        channelSftp.rm(fileName);
                    } catch (Exception e) {
                        logger.error("No se pudo eliminar {} del SFTP tras descarga: {}", fileName, e.getMessage());
                    }
                } catch (Exception downloadEx) {
                    logger.error("Error descargando {} desde SFTP: {}", fileName, downloadEx.getMessage());
                }
            }

            return descargados;

        } finally {
            if (channelSftp != null && channelSftp.isConnected()) channelSftp.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }
    }
}
