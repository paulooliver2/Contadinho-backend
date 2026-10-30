package br.com.contadinho.pessoas;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Quem usa o cartão do dono ou pega dinheiro emprestado (HU-16). Não tem login: só recebe cobranças pelo WhatsApp.
 */
@Entity
class Pessoa {

	@Id
	private UUID id;

	private UUID usuarioId;

	private String nome;

	private String apelido;

	/** +55, DDD e número (RN-35); null quando não informado. */
	private String whatsapp;

	/** Arquivada some das listas de escolha e mantém o histórico (RN-36). */
	private Instant arquivadaEm;

	private Instant criadaEm;

	protected Pessoa() {
	}

	Pessoa(UUID usuarioId, String nome, String apelido, String whatsapp, Instant agora) {
		this.id = UUID.randomUUID();
		this.usuarioId = usuarioId;
		this.criadaEm = agora;
		alterar(nome, apelido, whatsapp);
	}

	void alterar(String nome, String apelido, String whatsapp) {
		this.nome = nome;
		this.apelido = apelido;
		this.whatsapp = whatsapp;
	}

	void arquivar(Instant agora) {
		if (arquivadaEm == null) {
			arquivadaEm = agora;
		}
	}

	void desarquivar() {
		arquivadaEm = null;
	}

	UUID getId() {
		return id;
	}

	String getNome() {
		return nome;
	}

	String getApelido() {
		return apelido;
	}

	String getWhatsapp() {
		return whatsapp;
	}

	boolean isArquivada() {
		return arquivadaEm != null;
	}

}
