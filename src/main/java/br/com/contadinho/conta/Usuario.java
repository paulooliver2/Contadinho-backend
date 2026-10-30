package br.com.contadinho.conta;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * O dono do app (HU-01). O e-mail é guardado em minúsculas.
 */
@Entity
class Usuario {

	@Id
	private UUID id;

	private String nome;

	private String email;

	private String senhaHash;

	private Instant criadoEm;

	protected Usuario() {
	}

	Usuario(String nome, String email, String senhaHash, Instant agora) {
		this.id = UUID.randomUUID();
		this.nome = nome;
		this.email = email;
		this.senhaHash = senhaHash;
		this.criadoEm = agora;
	}

	void trocarSenha(String senhaHash) {
		this.senhaHash = senhaHash;
	}

	UUID getId() {
		return id;
	}

	String getNome() {
		return nome;
	}

	String getEmail() {
		return email;
	}

	String getSenhaHash() {
		return senhaHash;
	}

}
