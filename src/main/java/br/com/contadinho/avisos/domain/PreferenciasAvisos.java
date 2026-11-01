package br.com.contadinho.avisos.domain;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * HU-28: quais avisos o dono quer, com que antecedência e o horário silencioso. Sem linha gravada, valem os padrões
 * do esquema: tudo ligado, contas 2 dias antes, fatura 3 dias antes, silêncio das 22h às 8h.
 */
@Entity
public class PreferenciasAvisos {

	@Id
	private UUID usuarioId;

	private boolean contaAtivo = true;

	private short contaAntecedencia = 2;

	private boolean faturaFechouAtivo = true;

	private boolean faturaReceberAtivo = true;

	private short faturaAntecedencia = 3;

	private boolean silencioAtivo = true;

	private LocalTime silencioInicio = LocalTime.of(22, 0);

	private LocalTime silencioFim = LocalTime.of(8, 0);

	protected PreferenciasAvisos() {
	}

	public PreferenciasAvisos(UUID usuarioId) {
		this.usuarioId = usuarioId;
	}

	public void alterar(boolean contaAtivo, int contaAntecedencia, boolean faturaFechouAtivo, boolean faturaReceberAtivo,
			int faturaAntecedencia, boolean silencioAtivo, LocalTime silencioInicio, LocalTime silencioFim) {
		this.contaAtivo = contaAtivo;
		this.contaAntecedencia = (short) contaAntecedencia;
		this.faturaFechouAtivo = faturaFechouAtivo;
		this.faturaReceberAtivo = faturaReceberAtivo;
		this.faturaAntecedencia = (short) faturaAntecedencia;
		this.silencioAtivo = silencioAtivo;
		this.silencioInicio = silencioInicio;
		this.silencioFim = silencioFim;
	}

	/**
	 * HU-28.2: o aviso que cairia no horário silencioso vai para o fim dele. O silêncio pode virar a noite (22h às 8h)
	 * ou ficar dentro do dia (12h às 14h).
	 */
	public LocalDateTime foraDoSilencio(LocalDateTime quando) {
		if (!silencioAtivo) {
			return quando;
		}
		var hora = quando.toLocalTime();
		if (silencioInicio.isBefore(silencioFim)) {
			return !hora.isBefore(silencioInicio) && hora.isBefore(silencioFim) ? quando.with(silencioFim) : quando;
		}
		if (!hora.isBefore(silencioInicio)) {
			return quando.toLocalDate().plusDays(1).atTime(silencioFim);
		}
		return hora.isBefore(silencioFim) ? quando.with(silencioFim) : quando;
	}

	public UUID getUsuarioId() {
		return usuarioId;
	}

	public boolean isContaAtivo() {
		return contaAtivo;
	}

	public int getContaAntecedencia() {
		return contaAntecedencia;
	}

	public boolean isFaturaFechouAtivo() {
		return faturaFechouAtivo;
	}

	public boolean isFaturaReceberAtivo() {
		return faturaReceberAtivo;
	}

	public int getFaturaAntecedencia() {
		return faturaAntecedencia;
	}

	public boolean isSilencioAtivo() {
		return silencioAtivo;
	}

	public LocalTime getSilencioInicio() {
		return silencioInicio;
	}

	public LocalTime getSilencioFim() {
		return silencioFim;
	}

}
