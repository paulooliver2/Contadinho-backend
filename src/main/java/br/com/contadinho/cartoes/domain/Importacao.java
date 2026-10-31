package br.com.contadinho.cartoes.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Um arquivo importado numa fatura (HU-06). Guarda a origem de cada lançamento para poder desfazer (HU-44).
 */
@Entity
public class Importacao {

	@Id
	private UUID id;

	private UUID usuarioId;

	private UUID faturaId;

	private String nomeArquivo;

	private String formato;

	/** Modelo do banco, leitor genérico ou mapeamento de CSV (RN-13). */
	private String modelo;

	private BigDecimal totalArquivo;

	private Instant importadaEm;

	protected Importacao() {
	}

	public Importacao(UUID usuarioId, UUID faturaId, String nomeArquivo, String formato, String modelo,
			BigDecimal totalArquivo, Instant agora) {
		this.id = UUID.randomUUID();
		this.usuarioId = usuarioId;
		this.faturaId = faturaId;
		this.nomeArquivo = nomeArquivo.length() > 255 ? nomeArquivo.substring(0, 255) : nomeArquivo;
		this.formato = formato;
		this.modelo = modelo;
		this.totalArquivo = totalArquivo;
		this.importadaEm = agora;
	}

	public UUID getId() {
		return id;
	}

}
