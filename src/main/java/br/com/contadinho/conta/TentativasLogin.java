package br.com.contadinho.conta;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * RN-03: 5 senhas erradas em 15 minutos bloqueiam novas tentativas por 15 minutos. Conta por e-mail,
 * exista a conta ou não, para o bloqueio não revelar quais e-mails estão cadastrados.
 */
@Component
class TentativasLogin {

	/*
	 * Só contam os erros depois do último acerto. Para cada erro, lag(…, 4) acha o quarto erro anterior:
	 * se os dois estão a até 15 minutos um do outro, são 5 erros nessa janela, e o bloqueio vai até 15
	 * minutos depois do quinto. Tentativas feitas durante o bloqueio não são gravadas, então não o prolongam.
	 */
	private static final String BLOQUEADO_ATE = """
		select max(ocorrida_em) + interval '15 minutes'
		from (
		    select ocorrida_em, lag(ocorrida_em, 4) over (order by ocorrida_em) as quarto_anterior
		    from tentativa_login
		    where lower(email) = :email
		      and not sucesso
		      and ocorrida_em > :agora - interval '30 minutes'
		      and ocorrida_em > coalesce(
		          (select max(ocorrida_em) from tentativa_login where lower(email) = :email and sucesso),
		          '-infinity')
		) erros
		where ocorrida_em - quarto_anterior <= interval '15 minutes'
		""";

	private final JdbcClient jdbc;

	TentativasLogin(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	Optional<Instant> bloqueadoAte(String email, Instant agora) {
		return jdbc.sql(BLOQUEADO_ATE)
			.param("email", email)
			.param("agora", utc(agora))
			.query(OffsetDateTime.class)
			.list().stream()
			.filter(Objects::nonNull)
			.map(OffsetDateTime::toInstant)
			.filter(agora::isBefore)
			.findFirst();
	}

	void registrar(String email, boolean sucesso, Instant agora) {
		jdbc.sql("insert into tentativa_login (email, sucesso, ocorrida_em) values (:email, :sucesso, :agora)")
			.param("email", email)
			.param("sucesso", sucesso)
			.param("agora", utc(agora))
			.update();
	}

	private static OffsetDateTime utc(Instant instante) {
		return instante.atOffset(ZoneOffset.UTC);
	}

}
