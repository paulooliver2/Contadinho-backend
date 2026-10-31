package br.com.contadinho.conta.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
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

}
