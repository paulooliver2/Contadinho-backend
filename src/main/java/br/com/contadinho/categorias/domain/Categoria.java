package br.com.contadinho.categorias.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Categoria de gasto (HU-15). Arquivada some das opções para gastos novos, e os antigos continuam com ela (RN-27).
 */
@Entity
public class Categoria {

	@Id
	private UUID id;

	private UUID usuarioId;

	private String nome;

	/** O "essencial" de quem não marcou no próprio gasto (RN-28). */
	private boolean essencial;

	private Instant arquivadaEm;

	protected Categoria() {
	}

	public Categoria(UUID usuarioId, String nome, boolean essencial) {
		this.id = UUID.randomUUID();
		this.usuarioId = usuarioId;
		this.nome = nome;
		this.essencial = essencial;
	}

	public boolean isArquivada() {
		return arquivadaEm != null;
	}

	public UUID getId() {
		return id;
	}

	public String getNome() {
		return nome;
	}

	public boolean isEssencial() {
		return essencial;
	}

}
