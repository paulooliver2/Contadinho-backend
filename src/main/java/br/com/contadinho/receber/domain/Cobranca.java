package br.com.contadinho.receber.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Uma cobrança que o dono abriu no WhatsApp (HU-20.4), para mostrar "cobrado há X dias". O envio é sempre dele
 * (RN-34): aqui fica só quando ele abriu a conversa e o saldo daquele momento.
 */
@Entity
public class Cobranca {

	@Id
	private UUID id;

	private UUID usuarioId;

	private UUID pessoaId;

	private BigDecimal valor;

	private Instant enviadaEm;

	protected Cobranca() {
	}

	public Cobranca(UUID usuarioId, UUID pessoaId, BigDecimal valor, Instant agora) {
		this.id = UUID.randomUUID();
		this.usuarioId = usuarioId;
		this.pessoaId = pessoaId;
		this.valor = valor;
		// Em segundos: basta para "cobrado há X dias" e o horário devolvido é o mesmo que fica no banco.
		this.enviadaEm = agora.truncatedTo(ChronoUnit.SECONDS);
	}

	public UUID getPessoaId() {
		return pessoaId;
	}

	public BigDecimal getValor() {
		return valor;
	}

	public Instant getEnviadaEm() {
		return enviadaEm;
	}

}
