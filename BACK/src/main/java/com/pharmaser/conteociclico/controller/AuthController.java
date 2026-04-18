package com.pharmaser.conteociclico.controller;

import com.pharmaser.conteociclico.config.JwtUtil;
import com.pharmaser.conteociclico.model.Usuario;
import com.pharmaser.conteociclico.service.UsuarioService;
import com.pharmaser.conteociclico.service.UserDetailsServiceImpl;
import com.pharmaser.conteociclico.service.SedeConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SedeConfigService sedeConfigService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        if (username == null || password == null) {
            return ResponseEntity.badRequest().body("Username and password are required");
        }

        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body("Invalid credentials");
        } catch (Exception e) {
            return ResponseEntity.status(401).body("Error en la autenticación");
        }

        final UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        Usuario user = usuarioService.getUsuarioByUsername(username).orElse(null);

        if (user != null && user.getIdRol() == 1) {
            com.pharmaser.conteociclico.model.SedeConfig config = sedeConfigService.getConfigBySede(user.getSede());
            LocalTime apertura = config.getOperacionInicio();
            LocalTime cierre = config.getOperacionFin();

            if (apertura != null && cierre != null) {
                try {
                    LocalTime now = LocalTime.now(ZoneId.of("America/Bogota"));

                    boolean enOperacion;
                    if (apertura.isBefore(cierre)) {
                        enOperacion = !now.isBefore(apertura) && !now.isAfter(cierre);
                    } else {
                        enOperacion = !now.isBefore(apertura) || !now.isAfter(cierre);
                    }

                    if (enOperacion) {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body("No puede iniciar sesión porque en este horario no puede usar la herramienta. La sede se encuentra en operación (desde las "
                                        + apertura.format(formatter) + " hasta las " + cierre.format(formatter) + ").");
                    }
                } catch (Exception e) {
                    // Log silencioso o ignorado para evitar ruido en consola
                }
            }
        }

        final String jwt = jwtUtil.generateToken(userDetails.getUsername());

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwt);
        response.put("user", user);

        return ResponseEntity.ok(response);
    }
}
