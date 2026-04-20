package com.pharmaser.conteociclico.service;

import com.pharmaser.conteociclico.model.SedeConfig;
import com.pharmaser.conteociclico.model.Usuario;
import com.pharmaser.conteociclico.repository.SedeConfigRepository;
import com.pharmaser.conteociclico.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SedeConfigService {

    @Autowired
    private SedeConfigRepository sedeConfigRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    public List<SedeConfig> getAllConfigs() {
        return sedeConfigRepository.findAll();
    }

    public SedeConfig saveConfig(SedeConfig config) {
        if (config == null) return null;
        return sedeConfigRepository.save(java.util.Objects.requireNonNull(config));
    }

    public void deleteConfig(Integer id) {
        if (id == null) return;
        sedeConfigRepository.deleteById(java.util.Objects.requireNonNull(id));
    }

    public SedeConfig updateConfig(Integer id, SedeConfig config) {
        if (id == null) throw new RuntimeException("Configuración de sede no encontrada");
        SedeConfig existing = sedeConfigRepository.findById(java.util.Objects.requireNonNull(id))
                .orElseThrow(() -> new RuntimeException("Configuración de sede no encontrada"));
        
        if (config.getNumeroConteo() != null) existing.setNumeroConteo(config.getNumeroConteo());
        if (config.getTipoConteo() != null) existing.setTipoConteo(config.getTipoConteo());
        if (config.getNombre() != null) existing.setNombre(config.getNombre());
        if (config.getOperacionInicio() != null) existing.setOperacionInicio(config.getOperacionInicio());
        if (config.getOperacionFin() != null) existing.setOperacionFin(config.getOperacionFin());
        if (config.getActivo() != null) existing.setActivo(config.getActivo());
        
        // No permitimos cambiar el codigo_sede fácilmente via update parcial
        
        return sedeConfigRepository.save(java.util.Objects.requireNonNull(existing));
    }

    public SedeConfig getConfigBySede(String codigoSede) {
        Optional<SedeConfig> configOpt = sedeConfigRepository.findByCodigoSede(codigoSede);
        
        if (configOpt.isPresent()) {
            return configOpt.get();
        }

        // Return a default config if no specific seat config exists
        SedeConfig fallback = new SedeConfig();
        fallback.setCodigoSede(codigoSede);
        fallback.setNumeroConteo(100);
        fallback.setTipoConteo("ABC");
        return fallback;
    }

    public int syncSedesFromUsers() {
        List<String> userSedes = usuarioRepository.findAll().stream()
                .map(Usuario::getSede)
                .filter(s -> s != null && !s.trim().isEmpty())
                .distinct()
                .collect(Collectors.toList());

        int created = 0;
        for (String sedeCode : userSedes) {
            if (sedeConfigRepository.findByCodigoSede(sedeCode).isEmpty()) {
                SedeConfig newConfig = new SedeConfig();
                newConfig.setCodigoSede(sedeCode);
                newConfig.setNumeroConteo(100);
                newConfig.setTipoConteo("ABC");
                sedeConfigRepository.save(java.util.Objects.requireNonNull(newConfig));
                created++;
            }
        }
        return created;
    }
}
