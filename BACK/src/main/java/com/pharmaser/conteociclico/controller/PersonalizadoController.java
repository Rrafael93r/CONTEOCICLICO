package com.pharmaser.conteociclico.controller;

import com.pharmaser.conteociclico.model.Personalizado;
import com.pharmaser.conteociclico.service.PersonalizadoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.time.LocalDate;
import org.springframework.lang.NonNull;

@RestController
@RequestMapping("/api/personalizado")
public class PersonalizadoController {

    @Autowired
    private PersonalizadoService personalizadoService;

    @GetMapping
    public List<Personalizado> getAll(@RequestParam(required = false) Integer idUsuario,
                                     @RequestParam(required = false) String fechaProgramacion) {
        
        LocalDate date = (fechaProgramacion != null) ? java.time.LocalDate.parse(fechaProgramacion) : java.time.LocalDate.now();
        
        if (idUsuario != null) {
            return personalizadoService.getPersonalizadosByUsuarioYFechaProgramacion(idUsuario, date);
        }
        
        return personalizadoService.getPersonalizadosByFechaProgramacion(date);
    }

    @PostMapping
    public Personalizado create(@RequestBody @NonNull Personalizado personalizado) {
        return personalizadoService.savePersonalizado(personalizado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @NonNull Integer id) {
        personalizadoService.deletePersonalizado(id);
        return ResponseEntity.noContent().build();
    }
}
