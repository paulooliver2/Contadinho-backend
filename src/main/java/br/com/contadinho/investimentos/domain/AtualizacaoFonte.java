package br.com.contadinho.investimentos.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Quando uma fonte diária (tesouro, cdi) foi tentada, se falhou e, no CDI, desde que dia a tabela cobre. */
@Entity
public class AtualizacaoFonte {

	public static final String TESOURO = "tesouro";

	public static final String CDI = "cdi";

	@Id
	private String fonte;

	private Instant tentouEm;

	private boolean falhou;

	private LocalDate cobreDesde;

	protected AtualizacaoFonte() {
	}

	public AtualizacaoFonte(String fonte) {
		this.fonte = fonte;
	}

	public void tentou(Instant quando, boolean falhou) {
		this.tentouEm = quando;
		this.falhou = falhou;
	}

	public void cobreDesde(LocalDate dia) {
		this.cobreDesde = dia;
	}

	public String getFonte() {
		return fonte;
	}

	public Instant getTentouEm() {
		return tentouEm;
	}

	public boolean isFalhou() {
		return falhou;
	}

	public LocalDate getCobreDesde() {
		return cobreDesde;
	}

}
