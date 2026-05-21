package com.pharmaser.conteociclico.repository;

import com.pharmaser.conteociclico.model.Medicamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface MedicamentoRepository extends JpaRepository<Medicamento, Integer> {

    Optional<Medicamento> findByPluAndSede(String plu, String sede);

    List<Medicamento> findAllByPlu(String plu);

    List<Medicamento> findBySede(String sede);

    // ── Estadísticas agrupadas por sede y tipo de molécula (dashboard) ──────────
    @Query(value =
        "SELECT m.sede, m.tipomolecula, COUNT(*) AS total, " +
        "SUM(CASE WHEN m.estado_conteo_mensual >= 1 THEN 1 ELSE 0 END) AS contados, " +
        "SUM(CASE WHEN m.tipomolecula = 'A' AND m.estado_conteo_mensual = 1  THEN 1 ELSE 0 END) AS a_una, " +
        "SUM(CASE WHEN m.tipomolecula = 'A' AND m.estado_conteo_mensual >= 2 THEN 1 ELSE 0 END) AS a_dos " +
        "FROM medicamento m GROUP BY m.sede, m.tipomolecula",
        nativeQuery = true)
    List<Object[]> getMedicamentoStatsPerSedeAndType();

    // ── Stats completos por sede incluyendo usuario canónico (compat. frontend) ─
    @Query(value =
        "SELECT COALESCE(" +
        "  (SELECT MIN(u2.id) FROM usuario u2 WHERE u2.sede = m.sede AND u2.idrol = 1)," +
        "  (SELECT MIN(u3.id) FROM usuario u3 WHERE u3.sede = m.sede)" +
        ") AS idusuario, " +
        "m.sede, " +
        "(SELECT MAX(u4.fecha_bloque_extra) FROM usuario u4 WHERE u4.sede = m.sede) AS fecha_bloque_extra, " +
        "COALESCE(UPPER(m.tipomolecula), 'C') AS tipo, COUNT(*) AS total, " +
        "SUM(CASE WHEN m.estado_conteo_mensual >= 1 THEN 1 ELSE 0 END) AS contados, " +
        "SUM(CASE WHEN UPPER(m.tipomolecula) = 'A' AND m.estado_conteo_mensual = 1  THEN 1 ELSE 0 END) AS a_una, " +
        "SUM(CASE WHEN UPPER(m.tipomolecula) = 'A' AND m.estado_conteo_mensual >= 2 THEN 1 ELSE 0 END) AS a_dos " +
        "FROM medicamento m " +
        "GROUP BY m.sede, COALESCE(UPPER(m.tipomolecula), 'C')",
        nativeQuery = true)
    List<Object[]> getMedicamentoStatsWithCanonicalUser();

    List<Medicamento> findBySedeAndEstadoConteoMensualAndTipomolecula(
            String sede, Integer estadoConteoMensual, String tipomolecula);

    long countBySedeAndTipomolecula(String sede, String tipomolecula);

    long countBySedeAndTipomoleculaAndEstadoConteoMensualGreaterThan(
            String sede, String tipomolecula, Integer estadoConteoMensual);

    @Query("SELECT m FROM Medicamento m WHERE m.sede = :sede AND " +
           "(m.estadoConteoMensual = 0 OR (m.tipomolecula = 'A' AND m.estadoConteoMensual = 1)) " +
           "ORDER BY m.tipomolecula ASC, m.contadoMesAnterior ASC, m.costo DESC")
    List<Medicamento> findPrioritizedForSede(
            @org.springframework.data.repository.query.Param("sede") String sede,
            org.springframework.data.domain.Pageable pageable);
}
