package com.bankx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Clase principal de arranque para el microservicio Transactions Service.
 * <p>
 * Se encarga de inicializar el contexto de Spring Boot y levantar
 * el servicio WebFlux con sus configuraciones.
 * </p>
 */
@SpringBootApplication
public class TransactionsServiceApplication {

    /**
     * Punto de entrada del microservicio.
     * Ejecuta la aplicación de Spring Boot.
     *
     * @param args argumentos de línea de comandos
     */
  public static void main(String[] args) {
    SpringApplication.run(TransactionsServiceApplication.class, args);
  }
}
