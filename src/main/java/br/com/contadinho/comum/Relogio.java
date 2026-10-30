package br.com.contadinho.comum;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Datas de negócio (vencimentos, "hoje", mês corrente) sempre no fuso America/Sao_Paulo.
 * Injete o {@link Clock} em vez de chamar {@code LocalDate.now()}, para os testes controlarem o tempo.
 */
@Configuration(proxyBeanMethods = false)
public class Relogio {

	@Bean
	Clock clock(@Value("${contadinho.fuso}") String fuso) {
		return Clock.system(ZoneId.of(fuso));
	}

}
