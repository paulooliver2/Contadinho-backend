package br.com.contadinho.cartoes.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Cartão de crédito do dono (HU-05). Do número, só os 4 últimos dígitos (RN-07).
 */
@Entity
public class Cartao {

	@Id
	private UUID id;

	private UUID usuarioId;

	private String apelido;

	@Enumerated(EnumType.STRING)
	private Banco banco;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "ultimos4", length = 4)
	private String ultimos4;

	/** RN-08: 1 a 31; em mês mais curto vale o último dia. */
	private short diaFechamento;

	private short diaVencimento;

	private Instant encerradoEm;

	private Instant criadoEm;

	protected Cartao() {
	}

	public Cartao(UUID usuarioId, String apelido, Banco banco, String ultimos4, int diaFechamento, int diaVencimento,
			Instant agora) {
		this.id = UUID.randomUUID();
		this.usuarioId = usuarioId;
		this.apelido = apelido;
		this.banco = banco;
		this.ultimos4 = ultimos4;
		this.diaFechamento = (short) diaFechamento;
		this.diaVencimento = (short) diaVencimento;
		this.criadoEm = agora;
	}

	public UUID getId() {
		return id;
	}

	public String getApelido() {
		return apelido;
	}

	public Banco getBanco() {
		return banco;
	}

	public String getUltimos4() {
		return ultimos4;
	}

	public int getDiaFechamento() {
		return diaFechamento;
	}

	public int getDiaVencimento() {
		return diaVencimento;
	}

	/** HU-45: as faturas já gravadas guardam as datas delas; os dias novos valem para as próximas (RN-55). */
	public void alterar(String apelido, int diaFechamento, int diaVencimento) {
		this.apelido = apelido;
		this.diaFechamento = (short) diaFechamento;
		this.diaVencimento = (short) diaVencimento;
	}

	/** HU-45.3: some das listas, e o histórico continua. */
	public void encerrar(Instant agora) {
		this.encerradoEm = agora;
	}

	/** Datas de uma fatura, identificada pelo mês de vencimento (RN-58). */
	public record Ciclo(YearMonth mes, LocalDate fechamento, LocalDate vencimento) {
	}

	/**
	 * RN-58: vence no dia de vencimento do mês e fecha no dia de fechamento do mesmo mês, se ele vier antes;
	 * senão, no mês anterior.
	 */
	public Ciclo ciclo(YearMonth mes) {
		var mesFechamento = diaFechamento < diaVencimento ? mes : mes.minusMonths(1);
		return new Ciclo(mes, dia(mesFechamento, diaFechamento), dia(mes, diaVencimento));
	}

	/** RN-58: a primeira fatura cujo fechamento ainda não passou. */
	public YearMonth mesAberto(LocalDate hoje) {
		var mes = YearMonth.from(hoje);
		while (ciclo(mes).fechamento().isBefore(hoje)) {
			mes = mes.plusMonths(1);
		}
		return mes;
	}

	/** RN-08: em mês mais curto vale o último dia. */
	private static LocalDate dia(YearMonth mes, int dia) {
		return mes.atDay(Math.min(dia, mes.lengthOfMonth()));
	}

}
