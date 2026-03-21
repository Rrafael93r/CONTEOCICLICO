package com.pharmaser.conteociclico.controller;

import com.pharmaser.conteociclico.model.Personalizado;
import com.pharmaser.conteociclico.service.PersonalizadoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/personalizado")
public class PersonalizadoController {

    @Autowired
    private PersonalizadoService personalizadoService;

    @GetMapping
    public List<Personalizado> getAll(@RequestParam(required = false) Integer idUsuario) {
        if (idUsuario != null) {
            return personalizadoService.getPersonalizadosByUsuario(idUsuario);
        }
        return personalizadoService.getAllPersonalizados();
    }

    @PostMapping
    public Personalizado create(@RequestBody Personalizado personalizado) {
        return personalizadoService.savePersonalizado(personalizado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        personalizadoService.deletePersonalizado(id);
        return ResponseEntity.noContent().build();
    }
}
