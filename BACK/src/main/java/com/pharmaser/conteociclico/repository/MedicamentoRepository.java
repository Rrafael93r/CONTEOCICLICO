package com.pharmaser.conteociclico.repository;

import com.pharmaser.conteociclico.model.Medicamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface MedicamentoRepository extends JpaRepository<Medicamento, Integer> {
    Optional<Medicamento> findByPluAndIdUsuario(String plu, Integer idUsuario);
    List<Medicamento> findAllByPlu(String plu);
    List<Medicamento> findByIdUsuario(Integer idUsuario);

    @Query(value = "SELECT m.idusuario, m.tipomolecula, COUNT(*) as total, " +
                   "SUM(CASE WHEN m.estado_conteo_mensual >= 1 THEN 1 ELSE 0 END) as contados, " +
                   "SUM(CASE WHEN m.tipomolecula = 'A' AND m.estado_conteo_mensual = 1 THEN 1 ELSE 0 END) as a_una, " +
                   "SUM(CASE WHEN m.tipomolecula = 'A' AND m.estado_conteo_mensual >= 2 THEN 1 ELSE 0 END) as a_dos " +
                   "FROM medicamento m GROUP BY m.idusuario, m.tipomolecula", nativeQuery = true)
    List<Object[]> getMedicamentoStatsPerUserAndType();

    @Query(value = "SELECT u.id, COALESCE(u.usuario, u.sede, 'SIN NOMBRE') as nombre_final, u.sede, u.fecha_bloque_extra, " +
                   "COALESCE(UPPER(m.tipomolecula), 'C') as tipo, COUNT(*) as total, " +
                   "SUM(CASE WHEN m.estado_conteo_mensual >= 1 THEN 1 ELSE 0 END) as contados, " +
                   "SUM(CASE WHEN UPPER(m.tipomolecula) = 'A' AND m.estado_conteo_mensual = 1 THEN 1 ELSE 0 END) as a_una, " +
                   "SUM(CASE WHEN UPPER(m.tipomolecula) = 'A' AND m.estado_conteo_mensual >= 2 THEN 1 ELSE 0 END) as a_dos " +
                   "FROM medicamento m " +
                   "JOIN usuario u ON m.idusuario = u.id " +
                   "GROUP BY u.id, COALESCE(u.usuario, u.sede, 'SIN NOMBRE'), u.sede, u.fecha_bloque_extra, COALESCE(UPPER(m.tipomolecula), 'C')", nativeQuery = true)
    List<Object[]> getMedicamentoStatsPerSedeAndType();

    List<Medicamento> findByUsuarioSedeAndEstadoConteoMensualAndTipomolecula(String sede, Integer estado, String tipo);
    
    long countByUsuarioSedeAndTipomolecula(String sede, String tipomolecula);
    
    long countByUsuarioSedeAndTipomoleculaAndEstadoConteoMensualGreaterThan(String sede, String tipomolecula, Integer estado);

    @Query("SELECT m FROM Medicamento m JOIN m.usuario u WHERE u.sede = :sede AND " +
           "(m.estadoConteoMensual = 0 OR (m.tipomolecula = 'A' AND m.estadoConteoMensual = 1)) " +
           "ORDER BY m.tipomolecula ASC, m.contadoMesAnterior ASC, m.costo DESC")
    List<Medicamento> findPrioritizedForSede(String sede, org.springframework.data.domain.Pageable pageable);
}
