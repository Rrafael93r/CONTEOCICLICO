package com.pharmaser.conteociclico;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ConteociclicoApplication {

	public static void main(String[] args) {
		loadEnv();
		SpringApplication.run(ConteociclicoApplication.class, args);
	}

	private static void loadEnv() {
		java.io.File envFile = new java.io.File(".env");
		if (envFile.exists()) {
			try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(envFile))) {
				String line;
				while ((line = reader.readLine()) != null) {
					line = line.trim();
					if (line.isEmpty() || line.startsWith("#")) continue;
					String[] parts = line.split("=", 2);
					if (parts.length == 2) {
						String key = parts[0].trim();
						String value = parts[1].trim();
						if (System.getProperty(key) == null && System.getenv(key) == null) {
							System.setProperty(key, value);
						}
					}
				}
				System.out.println("✅ Archivo .env cargado exitosamente");
			} catch (java.io.IOException e) {
				System.err.println("❌ Error cargando archivo .env: " + e.getMessage());
			}
		} else {
			System.out.println("ℹ️ No se encontró archivo .env, usando variables de entorno del sistema");
		}
	}

}

