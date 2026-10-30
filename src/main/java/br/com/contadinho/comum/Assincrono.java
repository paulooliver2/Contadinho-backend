package br.com.contadinho.comum;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Liga o {@code @Async}, para trabalho que não deve segurar a requisição (ex.: enviar e-mail). Usa o executor
 * que o Spring Boot já configura.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class Assincrono {

}
