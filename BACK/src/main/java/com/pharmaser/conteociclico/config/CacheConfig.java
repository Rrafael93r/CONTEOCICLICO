package com.pharmaser.conteociclico.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        // Estadísticas globales del dashboard — se invalidan al importar o contar
        manager.registerCustomCache("globalStats",
                Caffeine.newBuilder()
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .maximumSize(10)
                        .build());

        // Resumen por sede — se invalida al importar
        manager.registerCustomCache("sedesSummary",
                Caffeine.newBuilder()
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .maximumSize(200)
                        .build());

        // Mapa PLU → descripción de la maestra — largo TTL, cambia poco
        manager.registerCustomCache("maestraPluMap",
                Caffeine.newBuilder()
                        .expireAfterWrite(30, TimeUnit.MINUTES)
                        .maximumSize(5)
                        .build());

        // Mapa sede → idUsuario
        manager.registerCustomCache("sedeUsuarioMap",
                Caffeine.newBuilder()
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .maximumSize(5)
                        .build());

        // Mapa (centroCosto-plu) → costo_unitario real desde costos_sede
        // TTL de 1 hora; se invalida automáticamente al subir un nuevo archivo de costos
        manager.registerCustomCache("costoSedeMap",
                Caffeine.newBuilder()
                        .expireAfterWrite(60, TimeUnit.MINUTES)
                        .maximumSize(5)
                        .build());

        return manager;
    }
}
