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

    private boolean isFeatureEnabled(String clave, String sede, boolean defaultValue) {
        String val = getConfigValue(clave, sede, String.valueOf(defaultValue));
        return "true".equalsIgnoreCase(val) || "1".equals(val);
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

        // 5. REPARTO EQUITATIVO (Round Robin)
        if (!totalSedeSelection.isEmpty()) {
            // Consumir permiso si es extra
            if ("Cíclico Adicional".equals(tipoAAsignar)) {
                usuario.setFechaBloqueExtra(null);
                usuarioRepository.save(usuario);
            }

            // Obtener todos los usuarios de la sede (que no sean admin)
            List<Usuario> operarios = usuarioRepository.findBySedeAndIdRol(sede, 1);

            if (operarios.isEmpty()) {
                operarios = Collections.singletonList(usuario); // Fallback al usuario actual
            }

            List<DetalleConteo> nuevosDetalles = new ArrayList<>();
            for (int i = 0; i < totalSedeSelection.size(); i++) {
                Medicamento sel = totalSedeSelection.get(i);
                Usuario targetUser = operarios.get(i % operarios.size());

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
            detalleConteoRepository.saveAll(nuevosDetalles);

            // Retornar solo lo que le tocó a este usuario
            return totalSedeSelection.stream()
                    .filter(m -> nuevosDetalles.stream().anyMatch(
                            d -> d.getIdUsuario().equals(usuarioId) && d.getIdMedicamento().equals(m.getId())))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    private List<Medicamento> generateSedeSelection(String sede, int totalCuota) {
        long totalA = medicamentoRepository.countByUsuarioSedeAndTipomolecula(sede, "A");
        long contadasA1 = medicamentoRepository.countByUsuarioSedeAndTipomoleculaAndEstadoConteoMensualGreaterThan(sede,
                "A", 0);
        long totalB = medicamentoRepository.countByUsuarioSedeAndTipomolecula(sede, "B");
        long contadasB = medicamentoRepository.countByUsuarioSedeAndTipomoleculaAndEstadoConteoMensualGreaterThan(sede,
                "B", 0);
        long totalC = medicamentoRepository.countByUsuarioSedeAndTipomolecula(sede, "C");
        long contadasC = medicamentoRepository.countByUsuarioSedeAndTipomoleculaAndEstadoConteoMensualGreaterThan(sede,
                "C", 0);

        double pctB = totalB > 0 ? (double) contadasB / totalB : 1.0;
        double pctC = totalC > 0 ? (double) contadasC / totalC : 1.0;

        List<Medicamento> selection = new ArrayList<>();

        if (contadasA1 < totalA) {
            selection = limitBloque(
                    medicamentoRepository.findByUsuarioSedeAndEstadoConteoMensualAndTipomolecula(sede, 0, "A"),
                    totalCuota);
        } else if (pctB < 0.60) {
            int cuotaA = (int) Math.ceil(totalCuota * 0.20);
            int cuotaB = totalCuota - cuotaA;
            selection.addAll(limitBloque(
                    medicamentoRepository.findByUsuarioSedeAndEstadoConteoMensualAndTipomolecula(sede, 1, "A"),
                    cuotaA));
            selection.addAll(limitBloque(
                    medicamentoRepository.findByUsuarioSedeAndEstadoConteoMensualAndTipomolecula(sede, 0, "B"),
                    cuotaB));
        } else if (pctC < 0.40) {
            int cuotaA = (int) Math.ceil(totalCuota * 0.15);
            int cuotaC = totalCuota - cuotaA;
            selection.addAll(limitBloque(
                    medicamentoRepository.findByUsuarioSedeAndEstadoConteoMensualAndTipomolecula(sede, 1, "A"),
                    cuotaA));
            selection.addAll(limitBloque(
                    medicamentoRepository.findByUsuarioSedeAndEstadoConteoMensualAndTipomolecula(sede, 0, "C"),
                    cuotaC));
        } else {
            List<Medicamento> tempBloque = new ArrayList<>();
            tempBloque
                    .addAll(medicamentoRepository.findByUsuarioSedeAndEstadoConteoMensualAndTipomolecula(sede, 0, "B"));
            if (tempBloque.size() < totalCuota) {
                tempBloque.addAll(
                        medicamentoRepository.findByUsuarioSedeAndEstadoConteoMensualAndTipomolecula(sede, 0, "C"));
            }
            selection = limitBloque(tempBloque, totalCuota);
        }
        return selection;
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

    private List<Medicamento> limitBloque(List<Medicamento> meds, int limit) {
        // Priorizar por contadoMesAnterior = FALSE y mayor costo
        return meds.stream()
                .sorted(Comparator.comparing(Medicamento::getContadoMesAnterior)
                        .thenComparing(Comparator.comparing(Medicamento::getCosto).reversed()))
                .limit(limit)
                .collect(Collectors.toList());
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

        String modo = "ABC";
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
        List<Integer> userIdsSede = operariosSede.stream().map(Usuario::getId).collect(Collectors.toList());

        // Obtener todos los medicamentos de la sede (basado en idUsuario de
        // medicamentos)
        List<Medicamento> baseMedsForSede = medicamentoRepository.findAll().stream()
                .filter(m -> m.getIdUsuario() != null && userIdsSede.contains(m.getIdUsuario()))
                .collect(Collectors.toList());

        Set<Integer> currentIdsInTable = detallesSedeHoy.stream()
                .map(d -> d.getIdMedicamento())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<Medicamento> totalSelection = new ArrayList<>();

        if ("ABC".equalsIgnoreCase(modo)) {
            boolean faseCoberturaActiva = isFeatureEnabled("fase_cobertura_activa", sede, true);

            if (faseCoberturaActiva) {
                // Prioridad 1: Moléculas A no contadas hoy ni previamente en el mes
                List<Medicamento> medsA = baseMedsForSede.stream()
                        .filter(m -> "A".equalsIgnoreCase(m.getTipomolecula()))
                        .filter(m -> "no".equalsIgnoreCase(m.getEstadoDelConteo())
                                && !currentIdsInTable.contains(m.getId()))
                        .sorted(Comparator
                                .comparing((Medicamento m) -> m.getCostoTotal() == null ? 0.0 : m.getCostoTotal())
                                .reversed())
                        .collect(Collectors.toList());

                if (!medsA.isEmpty()) {
                    totalSelection = medsA.stream().limit(totalQuota).collect(Collectors.toList());
                } else {
                    // Prioridad 2: Moléculas B/C pendientes por costo total
                    List<Medicamento> medsBC = baseMedsForSede.stream()
                            .filter(m -> !"A".equalsIgnoreCase(m.getTipomolecula()))
                            .filter(m -> "no".equalsIgnoreCase(m.getEstadoDelConteo())
                                    && !currentIdsInTable.contains(m.getId()))
                            .sorted(Comparator
                                    .comparing((Medicamento m) -> m.getCostoTotal() == null ? 0.0 : m.getCostoTotal())
                                    .reversed())
                            .collect(Collectors.toList());

                    if (!medsBC.isEmpty()) {
                        totalSelection = medsBC.stream().limit(totalQuota).collect(Collectors.toList());
                    }
                }
            } else {
                totalSelection = baseMedsForSede.stream()
                        .filter(m -> "no".equalsIgnoreCase(m.getEstadoDelConteo())
                                && !currentIdsInTable.contains(m.getId()))
                        .sorted(Comparator
                                .comparing((Medicamento m) -> m.getCostoTotal() == null ? 0.0 : m.getCostoTotal())
                                .reversed())
                        .limit(totalQuota)
                        .collect(Collectors.toList());
            }
        } else {
            // Modo TRADICIONAL
            totalSelection = baseMedsForSede.stream()
                    .filter(m -> "no".equalsIgnoreCase(m.getEstadoDelConteo())
                            && !currentIdsInTable.contains(m.getId()))
                    .sorted(Comparator.comparing((Medicamento m) -> m.getCostoTotal() == null ? 0.0 : m.getCostoTotal())
                            .reversed())
                    .limit(totalQuota)
                    .collect(Collectors.toList());
        }

        if (!totalSelection.isEmpty()) {
            // Reparto Round Robin
            List<Usuario> operarios = operariosSede.stream()
                    .filter(u -> u.getIdRol() != null && u.getIdRol() == 1)
                    .collect(Collectors.toList());

            if (operarios.isEmpty())
                operarios = Collections.singletonList(usuario);

            List<DetalleConteo> nuevosDetalles = new ArrayList<>();
            for (int i = 0; i < totalSelection.size(); i++) {
                Medicamento sel = totalSelection.get(i);
                Usuario targetUser = operarios.get(i % operarios.size());

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
