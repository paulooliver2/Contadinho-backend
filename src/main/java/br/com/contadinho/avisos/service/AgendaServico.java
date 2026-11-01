package br.com.contadinho.avisos.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import br.com.contadinho.gastos.Despesas;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Os avisos que o aparelho deve deixar agendados (HU-29). O servidor só monta a lista; quem agenda é o celular, sem
 * push. A lista sai sempre dos dados atuais, então reagendar é cancelar tudo e agendar de novo.
 */
@Service
public class AgendaServico {

	/** HU-26: dias antes do vencimento; o padrão até a tela de preferências (HU-28). */
	public static final int DIAS_ANTES = 2;

	public static final LocalTime HORA = LocalTime.of(9, 0);

	/** HU-29.2: o aparelho reagenda ao abrir o app, então basta cobrir os próximos 60 dias. */
	public static final int DIAS_AGENDADOS = 60;

	/** HU-29.3: abaixo do limite de 64 notificações agendadas do iOS. */
	public static final int MAXIMO = 60;

	private final Despesas despesas;

	private final Clock clock;

	public AgendaServico(Despesas despesas, Clock clock) {
		this.despesas = despesas;
		this.clock = clock;
	}

	public enum Tipo {
		CONTA_ANTES, CONTA_NO_DIA
	}

	/**
	 * @param chave estável entre reagendamentos, para o aparelho identificar o aviso
	 * @param quando no horário local do dono
	 * @param mes o mês da conta, para abrir a conta certa ao tocar
	 */
	public record Aviso(String chave, Tipo tipo, LocalDateTime quando, String titulo, String texto, UUID ocorrenciaId,
			YearMonth mes) {
	}

	/** Os próximos avisos, do mais próximo para o mais distante, no máximo {@link #MAXIMO}. */
	@Transactional
	public List<Aviso> agenda(UUID usuarioId) {
		var agora = LocalDateTime.now(clock);
		var limite = agora.toLocalDate().plusDays(DIAS_AGENDADOS);
		var avisos = new ArrayList<Aviso>();
		for (var c : despesas.aPagarAte(usuarioId, limite.plusDays(DIAS_ANTES))) {
			var valor = reais(c.valor());
			var mes = YearMonth.from(c.vencimento());
			avisos.add(new Aviso("conta-antes:" + c.ocorrenciaId(), Tipo.CONTA_ANTES,
					c.vencimento().minusDays(DIAS_ANTES).atTime(HORA),
					c.debitoAutomatico() ? c.descricao() + ": débito automático em " + DIAS_ANTES + " dias"
							: c.descricao() + " vence em " + DIAS_ANTES + " dias",
					c.debitoAutomatico() ? "Deixe " + valor + " na conta para o débito." : valor + " · toque para marcar como paga.",
					c.ocorrenciaId(), mes));
			// HU-26.2: em débito automático o banco paga sozinho; não há aviso no dia.
			if (!c.debitoAutomatico()) {
				avisos.add(new Aviso("conta-dia:" + c.ocorrenciaId(), Tipo.CONTA_NO_DIA, c.vencimento().atTime(HORA),
						c.descricao() + " vence hoje", valor + " · toque para marcar como paga.", c.ocorrenciaId(), mes));
			}
		}
		return avisos.stream()
			.filter(a -> a.quando().isAfter(agora) && !a.quando().toLocalDate().isAfter(limite))
			.sorted(Comparator.comparing(Aviso::quando).thenComparing(Aviso::chave))
			.limit(MAXIMO)
			.toList();
	}

	private static String reais(BigDecimal valor) {
		return NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(valor);
	}

}
