package br.com.contadinho.conta.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

/**
 * O dono do app (HU-01). O e-mail é guardado em minúsculas.
 */
@Entity
public class Usuario {

	@Id
	private UUID id;

	private String nome;

	private String email;

	private String senhaHash;

	/** HU-03: os dois juntos ou nenhum; sem chave, o app não monta cobrança (RN-06). */
	@Enumerated(EnumType.STRING)
	private ChavePix.Tipo pixTipo;

	private String pixChave;

	private Instant criadoEm;

	protected Usuario() {
	}

	public Usuario(String nome, String email, String senhaHash, Instant agora) {
		this.id = UUID.randomUUID();
		this.nome = nome;
		this.email = email;
		this.senhaHash = senhaHash;
		this.criadoEm = agora;
	}

	public void trocarSenha(String senhaHash) {
		this.senhaHash = senhaHash;
	}

	/** A chave já normalizada por {@link ChavePix#normalizar}. */
	public void definirPix(ChavePix.Tipo tipo, String chave) {
		this.pixTipo = tipo;
		this.pixChave = chave;
	}

	public void removerPix() {
		this.pixTipo = null;
		this.pixChave = null;
	}

	public UUID getId() {
		return id;
	}

	public String getNome() {
		return nome;
	}

	public String getEmail() {
		return email;
	}

	public String getSenhaHash() {
		return senhaHash;
	}

	public ChavePix.Tipo getPixTipo() {
		return pixTipo;
	}

	public String getPixChave() {
		return pixChave;
	}

}
