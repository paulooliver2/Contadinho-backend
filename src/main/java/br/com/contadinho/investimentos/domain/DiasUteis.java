package br.com.contadinho.investimentos.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Dias de semana, para decidir se um preço está velho (HU-31). Sem lista de feriados: num feriado o servidor só tenta
 * de novo a cada 30 minutos sem achar nada novo. Para o rendimento, o calendário são os dias com CDI guardado.
 */
public final class DiasUteis {

	private DiasUteis() {
	}

	/** O dia de semana mais recente antes de {@code dia}. */
	public static LocalDate anterior(LocalDate dia) {
		var d = dia.minusDays(1);
		while (fimDeSemana(d)) {
			d = d.minusDays(1);
		}
		return d;
	}

	/** {@code dia}, se for dia de semana, ou o anterior. */
	public static LocalDate ultimo(LocalDate dia) {
		return fimDeSemana(dia) ? anterior(dia) : dia;
	}

	private static boolean fimDeSemana(LocalDate d) {
		return d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
	}

}
