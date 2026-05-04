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
			try (java.io.InputStream is = new java.io.FileInputStream(envFile)) {
				java.util.Properties props = new java.util.Properties();
				props.load(is);
				props.forEach((k, v) -> {
					if (System.getProperty(k.toString()) == null && System.getenv(k.toString()) == null) {
						System.setProperty(k.toString(), v.toString());
					}
				});
			} catch (java.io.IOException e) {
			}
		}
	}

}
