package com.pharmaser.conteociclico.service;

import com.pharmaser.conteociclico.model.Personalizado;
import com.pharmaser.conteociclico.repository.PersonalizadoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class PersonalizadoService {

    @Autowired
    private PersonalizadoRepository personalizadoRepository;

    public List<Personalizado> getPersonalizadosByUsuario(Integer idUsuario) {
        return personalizadoRepository.findByIdUsuario(idUsuario);
    }

    public List<Personalizado> getPersonalizadosByUsuarioYFechaProgramacion(Integer idUsuario, java.time.LocalDate fecha) {
        return personalizadoRepository.findByIdUsuarioAndFechaProgramacion(idUsuario, fecha);
    }
    public List<Personalizado> getPersonalizadosByFechaProgramacion(java.time.LocalDate fecha) {
        return personalizadoRepository.findByFechaProgramacion(fecha);
    }
    public List<Personalizado> getAllPersonalizados() {
        return personalizadoRepository.findAll();
    }

    public Optional<Personalizado> getPersonalizadoById(Integer id) {
        return personalizadoRepository.findById(id);
    }

    public Personalizado savePersonalizado(Personalizado personalizado) {
        return personalizadoRepository.save(personalizado);
    }

    public void deletePersonalizado(Integer id) {
        personalizadoRepository.deleteById(id);
    }
}
