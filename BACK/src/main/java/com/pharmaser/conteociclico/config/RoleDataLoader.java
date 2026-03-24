package com.pharmaser.conteociclico.config;

import com.pharmaser.conteociclico.model.Rol;
import com.pharmaser.conteociclico.repository.RolRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class RoleDataLoader implements CommandLineRunner {

    @Autowired
    private RolRepository rolRepository;

    @Override
    public void run(String... args) throws Exception {
        if (rolRepository.count() == 0) {
            rolRepository.saveAll(Arrays.asList(
                new Rol(1, "Farmacia"),
                new Rol(2, "Control de Inventario"),
                new Rol(3, "Administrador")
            ));
        }
    }
}
