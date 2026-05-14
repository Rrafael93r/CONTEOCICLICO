package com.pharmaser.conteociclico.service;

import com.pharmaser.conteociclico.model.Medicamento;
import com.pharmaser.conteociclico.model.Usuario;
import com.pharmaser.conteociclico.model.DetalleConteo;
import com.pharmaser.conteociclico.model.SistemaConfiguracion;
import com.pharmaser.conteociclico.repository.MedicamentoRepository;
import com.pharmaser.conteociclico.repository.UsuarioRepository;
import com.pharmaser.conteociclico.repository.DetalleConteoRepository;
import com.pharmaser.conteociclico.repository.SistemaConfiguracionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pharmaser.conteociclico.model.SedeConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CycleGeneratorService {

    @Autowired
    private MedicamentoRepository medicamentoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private DetalleConteoRepository detalleConteoRepository;

    @Autowired
    private SistemaConfiguracionRepository configRepository;

    @Autowired
    private SedeConfigService sedeConfigService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String getConfigValue(String clave, String sede, String defaultValue) {
        if (clave == null) {
            return defaultValue;
        }
        if (sede != null && !sede.trim().isEmpty()) {
            String claveSede = clave + "_" + sede.toUpperCase();
            Optional<SistemaConfiguracion> confSede = configRepository.findById(claveSede);
            if (confSede.isPresent() && confSede.get().getValor() != null
                    && !confSede.get().getValor().trim().isEmpty()) {
                return confSede.get().getValor();
            }
        }
        return configRepository.findById(java.util.Objects.requireNonNull(clave))
                .map(SistemaConfiguracion::getValor)
                .filter(v -> v != null && !v.trim().isEmpty())
                .orElse(defaultValue);
    }

    @Transactional
    public List<Medicamento> obtenerBloqueDiarioDinamico(Integer usuarioId) {
        if (usuarioId == null)
            return java.util.Collections.emptyList();

        Usuario usuario = usuarioRepository.findById(java.util.Objects.requireNonNull(usuarioId))
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        LocalDate fechaHoy = LocalDate.now();
        String sede = usuario.getSede();

        // Obtener configuración de la sede
        SedeConfig config = sedeConfigService.getConfigBySede(sede);
        int cuotaPorSede = (config.getNumeroConteo() != null) ? config.getNumeroConteo() : 10;

        // 1. Obtener registros de hoy para ESTE usuario específicamente
        List<DetalleConteo> detallesHoyPropio = detalleConteoRepository.findByIdUsuarioAndFechaRegistro(usuarioId,
                fechaHoy);

        // 2. RETORNO PRIORITARIO: Si hay algo pendiente, retornarlo.
        List<DetalleConteo> pendientes = detallesHoyPropio.stream()
                .filter(d -> d.getCantidadContada() == null)
                .collect(Collectors.toList());

        if (!pendientes.isEmpty()) {
            List<Integer> ids = pendientes.stream().map(DetalleConteo::getIdMedicamento).collect(Collectors.toList());
            return medicamentoRepository.findAllById(java.util.Objects.requireNonNull(ids));
        }

        // 3. CONTROL DE FLUJO POR SEDE: ¿Ya se generó el bloque para la sede hoy?
        List<DetalleConteo> detallesSedeHoy = detalleConteoRepository.findBySedeAndFecha(sede, fechaHoy);

        boolean yaEmpezoDiarioSede = detallesSedeHoy.stream().anyMatch(d -> "Cíclico".equals(d.getTipoConteo()));
        boolean yaHizoExtraSede = detallesSedeHoy.stream().anyMatch(d -> "Cíclico Adicional".equals(d.getTipoConteo()));

        boolean permisoExtraActivo = usuario.getFechaBloqueExtra() != null
                && usuario.getFechaBloqueExtra().equals(fechaHoy);

        String tipoAAsignar = null;

        if (!yaEmpezoDiarioSede) {
            tipoAAsignar = "Cíclico";
        } else if (permisoExtraActivo && !yaHizoExtraSede) {
            tipoAAsignar = "Cíclico Adicional";
        } else {
            // Si ya se generó para la sede pero este usuario no tiene nada asignado,
            // significa que ya se repartió y él terminó o no le tocó.
            return Collections.emptyList();
        }

        // 4. GENERACIÓN DE SELECCIÓN PARA LA SEDE
        List<Medicamento> totalSedeSelection = generateSedeSelection(sede, cuotaPorSede);

        // 5. REPARTO EQUITATIVO POR FAMILIAS (Round Robin)
        if (!totalSedeSelection.isEmpty()) {
            // Consumir permiso si es extra
            if ("Cíclico Adicional".equals(tipoAAsignar)) {
                usuario.setFechaBloqueExtra(null);
                usuarioRepository.save(usuario);
            }

            // Obtener todos los usuarios de la sede (que no sean admin)
            List<Usuario> operarios = usuarioRepository.findBySedeAndIdRol(sede, 1);
            if (operarios.isEmpty()) {
                operarios = Collections.singletonList(usuario);
            }

            // Agrupar por familia para asegurar que todos los integrantes de un código genérico vayan al mismo usuario
            Map<String, List<Medicamento>> familiesMap = totalSedeSelection.stream()
                    .collect(Collectors.groupingBy(m -> m.getCodigogenerico() != null ? m.getCodigogenerico() : "SIN_FAMILIA"));

            List<String> familyKeys = new ArrayList<>(familiesMap.keySet());
            List<DetalleConteo> nuevosDetalles = new ArrayList<>();

            for (int i = 0; i < familyKeys.size(); i++) {
                String key = familyKeys.get(i);
                List<Medicamento> familyMeds = familiesMap.get(key);
                Usuario targetUser = operarios.get(i % operarios.size());

                for (Medicamento sel : familyMeds) {
                    DetalleConteo det = new DetalleConteo();
                    det.setIdMedicamento(sel.getId());
                    det.setIdUsuario(targetUser.getId());
                    det.setCantidadActual(sel.getInventario() != null ? sel.getInventario() : 0);
                    det.setFechaRegistro(fechaHoy);
                    det.setHoraRegistro(java.time.LocalTime.now());
                    det.setTipoConteo(tipoAAsignar);
                    nuevosDetalles.add(det);

                    sel.setEstadoDelConteo("SÍ");
                    medicamentoRepository.save(sel);
                }
            }
            detalleConteoRepository.saveAll(nuevosDetalles);

            // Retornar solo lo que le tocó a este usuario
            return totalSedeSelection.stream()
                    .filter(m -> nuevosDetalles.stream().anyMatch(
                            d -> d.getIdUsuario().equals(usuarioId) && d.getIdMedicamento().equals(m.getId())))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    private List<Medicamento> generateSedeSelection(String sede, int totalCuotaFamilias) {
        // Métricas basadas en FAMILIAS (codigogenerico)
        long totalFamA = countFamiliesBySedeAndTipo(sede, "A");
        long contadasFamA = countFamiliesBySedeAndTipoAndEstado(sede, "A", 1); // 1 o más conteos
        
        long totalFamB = countFamiliesBySedeAndTipo(sede, "B");
        long contadasFamB = countFamiliesBySedeAndTipoAndEstado(sede, "B", 1);
        
        long totalFamC = countFamiliesBySedeAndTipo(sede, "C");
        long contadasFamC = countFamiliesBySedeAndTipoAndEstado(sede, "C", 1);

        double pctB = totalFamB > 0 ? (double) contadasFamB / totalFamB : 1.0;
        double pctC = totalFamC > 0 ? (double) contadasFamC / totalFamC : 1.0;

        List<Medicamento> selection = new ArrayList<>();

        if (contadasFamA < totalFamA) {
            // Fase 1: Priorizar Familias A no contadas
            selection = limitBloquePorFamilias(sede, "A", 0, totalCuotaFamilias);
        } else if (pctB < 0.60) {
            // Fase 2: Cobertura B
            int cuotaA = (int) Math.ceil(totalCuotaFamilias * 0.20);
            int cuotaB = totalCuotaFamilias - cuotaA;
            selection.addAll(limitBloquePorFamilias(sede, "A", 1, cuotaA));
            selection.addAll(limitBloquePorFamilias(sede, "B", 0, cuotaB));
        } else if (pctC < 0.40) {
            // Fase 3: Cobertura C
            int cuotaA = (int) Math.ceil(totalCuotaFamilias * 0.15);
            int cuotaC = totalCuotaFamilias - cuotaA;
            selection.addAll(limitBloquePorFamilias(sede, "A", 1, cuotaA));
            selection.addAll(limitBloquePorFamilias(sede, "C", 0, cuotaC));
        } else {
            // Fase Final: Mezcla
            selection = limitBloquePorFamilias(sede, "B", 0, totalCuotaFamilias / 2);
            if (selection.size() < totalCuotaFamilias) {
                selection.addAll(limitBloquePorFamilias(sede, "C", 0, totalCuotaFamilias - selection.size()));
            }
        }
        return selection;
    }

    private long countFamiliesBySedeAndTipo(String sede, String tipo) {
        String sql = "SELECT COUNT(DISTINCT m.codigogenerico) FROM medicamento m " +
                     "JOIN usuario u ON m.idusuario = u.id WHERE u.sede = ? AND m.tipomolecula = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, sede, tipo);
        return count != null ? count : 0;
    }

    private long countFamiliesBySedeAndTipoAndEstado(String sede, String tipo, int minEstado) {
        String sql = "SELECT COUNT(DISTINCT m.codigogenerico) FROM medicamento m " +
                     "JOIN usuario u ON m.idusuario = u.id WHERE u.sede = ? AND m.tipomolecula = ? AND m.estado_conteo_mensual >= ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, sede, tipo, minEstado);
        return count != null ? count : 0;
    }

    private List<Medicamento> limitBloquePorFamilias(String sede, String tipo, int estado, int limitFamilias) {
        if (limitFamilias <= 0) return new ArrayList<>();

        // 1. Obtener los códigos genéricos que cumplen la condición, ordenados por valor total de familia
        String sqlFamilias = "SELECT m.codigogenerico FROM medicamento m " +
                            "JOIN usuario u ON m.idusuario = u.id " +
                            "WHERE u.sede = ? AND m.tipomolecula = ? AND m.estado_conteo_mensual = ? " +
                            "GROUP BY m.codigogenerico " +
                            "ORDER BY SUM(m.costototal) DESC LIMIT ?";
        
        List<String> codigosGen = jdbcTemplate.queryForList(sqlFamilias, String.class, sede, tipo, estado, limitFamilias);
        
        if (codigosGen.isEmpty()) return new ArrayList<>();

        // 2. Traer todos los medicamentos que pertenecen a esas familias en esa sede.
        // Se deduplica por PLU para garantizar que un medicamento nunca aparezca
        // dos veces en el bloque aunque queden registros residuales en la BD.
        List<Usuario> usuariosSede = usuarioRepository.findBySede(sede);
        List<Integer> userIds = usuariosSede.stream().map(Usuario::getId).collect(Collectors.toList());

        // LinkedHashMap por PLU: si hay dos registros con el mismo PLU, gana el de menor id
        java.util.LinkedHashMap<String, Medicamento> porPlu = new java.util.LinkedHashMap<>();
        medicamentoRepository.findAll().stream()
                .filter(m -> userIds.contains(m.getIdUsuario()))
                .filter(m -> codigosGen.contains(m.getCodigogenerico()))
                .sorted(Comparator.comparing(Medicamento::getId))
                .forEach(m -> porPlu.putIfAbsent(m.getPlu(), m));

        return new ArrayList<>(porPlu.values());
    }

    public boolean isDailyBlockFinished(Integer idUsuario) {
        LocalDate fechaHoy = LocalDate.now();
        List<DetalleConteo> detalles = detalleConteoRepository.findByIdUsuarioAndFechaRegistro(idUsuario, fechaHoy);

        // Debe existir al menos un registro de tipo "Cíclico"
        boolean existeDiario = detalles.stream().anyMatch(d -> "Cíclico".equals(d.getTipoConteo()));
        if (!existeDiario)
            return false;

        // Todos los de tipo "Cíclico" deben estar contados
        return detalles.stream()
                .filter(d -> "Cíclico".equals(d.getTipoConteo()))
                .allMatch(d -> d.getCantidadContada() != null);
    }



    @Transactional
    public List<Medicamento> generarBloqueCiclico(Integer idUsuario, LocalDate fechaHoy, Boolean manual,
            Integer idAdmin) {
        if (idUsuario == null)
            return Collections.emptyList();

        Usuario usuario = usuarioRepository.findById(java.util.Objects.requireNonNull(idUsuario)).orElse(null);
        if (usuario == null)
            return Collections.emptyList();

        String sede = usuario.getSede();
        SedeConfig config = sedeConfigService.getConfigBySede(sede);

        int totalQuota = config.getNumeroConteo() != null ? config.getNumeroConteo() : 10;
        if (totalQuota <= 0)
            return Collections.emptyList();

        // Verificar si ya existe algo para la sede hoy
        List<DetalleConteo> detallesSedeHoy = detalleConteoRepository.findBySedeAndFecha(sede, fechaHoy);

        boolean isFirstBlockTodaySede = detallesSedeHoy.isEmpty();
        boolean hasExtraBlockToday = usuario.getFechaBloqueExtra() != null
                && usuario.getFechaBloqueExtra().equals(fechaHoy);

        if (!isFirstBlockTodaySede) {
            if (hasExtraBlockToday) {
                boolean alreadyGaveExtra = detallesSedeHoy.stream()
                        .anyMatch(d -> "Cíclico Adicional".equals(d.getTipoConteo()));

                if (alreadyGaveExtra) {
                    return Collections.emptyList();
                }
            } else {
                return Collections.emptyList();
            }
        }

        // Obtener todos los usuarios de la sede (que no sean admin)
        List<Usuario> operariosSede = usuarioRepository.findBySede(sede);

        // GENERACIÓN DE SELECCIÓN PARA LA SEDE (YA BASADA EN FAMILIAS)
        List<Medicamento> totalSelection = generateSedeSelection(sede, totalQuota);

        if (!totalSelection.isEmpty()) {
            // Reparto Round Robin por FAMILIAS
            List<Usuario> operarios = operariosSede.stream()
                    .filter(u -> u.getIdRol() != null && u.getIdRol() == 1)
                    .collect(Collectors.toList());

            if (operarios.isEmpty())
                operarios = Collections.singletonList(usuario);

            // Agrupar por familia para asegurar que todos los integrantes de un código genérico vayan al mismo usuario
            Map<String, List<Medicamento>> familiesMap = totalSelection.stream()
                    .collect(Collectors.groupingBy(m -> m.getCodigogenerico() != null ? m.getCodigogenerico() : "SIN_FAMILIA"));

            List<String> familyKeys = new ArrayList<>(familiesMap.keySet());
            List<DetalleConteo> nuevosDetalles = new ArrayList<>();

            for (int i = 0; i < familyKeys.size(); i++) {
                String key = familyKeys.get(i);
                List<Medicamento> familyMeds = familiesMap.get(key);
                Usuario targetUser = operarios.get(i % operarios.size());

                for (Medicamento sel : familyMeds) {
                    DetalleConteo det = new DetalleConteo();
                    det.setIdMedicamento(sel.getId());
                    det.setIdUsuario(targetUser.getId());
                    det.setCantidadActual(sel.getInventario() != null ? sel.getInventario() : 0);
                    det.setFechaRegistro(fechaHoy);
                    det.setTipoConteo(Boolean.TRUE.equals(manual) ? "Cíclico Adicional" : "Cíclico");
                    nuevosDetalles.add(det);

                    sel.setEstadoDelConteo("sí");
                    medicamentoRepository.save(sel);
                }
            }
            detalleConteoRepository.saveAll(nuevosDetalles);

            // Retornar solo lo del usuario solicitante
            return totalSelection.stream()
                    .filter(m -> nuevosDetalles.stream().anyMatch(
                            d -> d.getIdUsuario().equals(idUsuario) && d.getIdMedicamento().equals(m.getId())))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
