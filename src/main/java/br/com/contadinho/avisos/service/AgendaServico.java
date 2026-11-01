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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.contadinho.avisos.domain.PreferenciasAvisos;
import br.com.contadinho.cartoes.Faturas;
import br.com.contadinho.gastos.Despesas;
import br.com.contadinho.pessoas.Pessoas;
import br.com.contadinho.receber.AReceber;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Os avisos que o aparelho deve deixar agendados (HU-29). O servidor só monta a lista; quem agenda é o celular, sem
 * push. A lista sai sempre dos dados atuais, então reagendar é cancelar tudo e agendar de novo.
 */
@Service
public class AgendaServico {

	public static final LocalTime HORA = LocalTime.of(9, 0);

	/** HU-29.2: o aparelho reagenda ao abrir o app, então basta cobrir os próximos 60 dias. */
	public static final int DIAS_AGENDADOS = 60;

	/** HU-29.3: abaixo do limite de 64 notificações agendadas do iOS. */
	public static final int MAXIMO = 60;

	private final Despesas despesas;

	private final Faturas faturas;

	private final AReceber aReceber;

	private final Pessoas pessoas;

	private final PreferenciasServico preferencias;

	private final Clock clock;

	public AgendaServico(Despesas despesas, Faturas faturas, AReceber aReceber, Pessoas pessoas,
			PreferenciasServico preferencias, Clock clock) {
		this.despesas = despesas;
		this.faturas = faturas;
		this.aReceber = aReceber;
		this.pessoas = pessoas;
		this.preferencias = preferencias;
		this.clock = clock;
	}

	public enum Tipo {
		CONTA_ANTES, CONTA_NO_DIA, FATURA_FECHOU, FATURA_A_RECEBER
	}

	/** A tela que o toque abre. */
	public enum Tela {
		/** A conta do mês em Gastos; {@code id} é a ocorrência. */
		CONTA,
		/** A fatura em Cartões; {@code id} é o cartão. */
		FATURA,
		/** O histórico da pessoa em A receber; {@code id} é a pessoa. */
		PESSOA,
		/** A lista de A receber, quando mais de uma pessoa deve. */
		RECEBER
	}

	/** {@code id} e {@code mes} conforme a {@link Tela}; RECEBER não tem nenhum dos dois. */
	public record Destino(Tela tela, UUID id, YearMonth mes) {
	}

	/**
	 * @param chave estável entre reagendamentos, para o aparelho identificar o aviso
	 * @param quando no horário local do dono
	 * @param acao o botão do aviso, como "Cobrar"; null quando tocar basta
	 */
	public record Aviso(String chave, Tipo tipo, LocalDateTime quando, String titulo, String texto, String acao,
			Destino destino) {
	}

	/** Os próximos avisos, do mais próximo para o mais distante, no máximo {@link #MAXIMO}. */
	@Transactional
	public List<Aviso> agenda(UUID usuarioId) {
		var agora = LocalDateTime.now(clock);
		var hoje = agora.toLocalDate();
		var limite = hoje.plusDays(DIAS_AGENDADOS);
		var prefs = preferencias.ler(usuarioId);
		var avisos = new ArrayList<Aviso>();
		if (prefs.isContaAtivo()) {
			contas(usuarioId, limite, prefs.getContaAntecedencia(), avisos);
		}
		if (prefs.isFaturaFechouAtivo() || prefs.isFaturaReceberAtivo()) {
			faturas(usuarioId, hoje, limite, prefs, avisos);
		}
		return avisos.stream()
			.map(a -> new Aviso(a.chave(), a.tipo(), prefs.foraDoSilencio(a.quando()), a.titulo(), a.texto(), a.acao(),
					a.destino()))
			.filter(a -> a.quando().isAfter(agora) && !a.quando().toLocalDate().isAfter(limite))
			.sorted(Comparator.comparing(Aviso::quando).thenComparing(Aviso::chave))
			.limit(MAXIMO)
			.toList();
	}

	/** HU-26: dias antes e no dia; em débito automático o banco paga sozinho, então não há aviso no dia. */
	private void contas(UUID usuarioId, LocalDate limite, int diasAntes, List<Aviso> avisos) {
		for (var c : despesas.aPagarAte(usuarioId, limite.plusDays(diasAntes))) {
			var valor = reais(c.valor());
			var destino = new Destino(Tela.CONTA, c.ocorrenciaId(), YearMonth.from(c.vencimento()));
			avisos.add(new Aviso("conta-antes:" + c.ocorrenciaId(), Tipo.CONTA_ANTES,
					c.vencimento().minusDays(diasAntes).atTime(HORA),
					c.debitoAutomatico() ? c.descricao() + ": débito automático " + emDias(diasAntes)
							: c.descricao() + " vence " + emDias(diasAntes),
					c.debitoAutomatico() ? "Deixe " + valor + " na conta para o débito." : valor + " · toque para marcar como paga.",
					null, destino));
			if (!c.debitoAutomatico()) {
				avisos.add(new Aviso("conta-dia:" + c.ocorrenciaId(), Tipo.CONTA_NO_DIA, c.vencimento().atTime(HORA),
						c.descricao() + " vence hoje", valor + " · toque para marcar como paga.", null, destino));
			}
		}
	}

	/**
	 * HU-27: no dia seguinte ao último dia de compras, lembra de importar; dias antes do vencimento, se alguém ainda
	 * deve compras daquela fatura, diz quanto e de quem. Cada um pode estar desligado (HU-28).
	 */
	private void faturas(UUID usuarioId, LocalDate hoje, LocalDate limite, PreferenciasAvisos prefs, List<Aviso> avisos) {
		var diasAntes = prefs.getFaturaAntecedencia();
		var ciclos = faturas.ciclos(usuarioId, hoje.minusDays(1), limite.plusDays(diasAntes));
		if (ciclos.isEmpty()) {
			return;
		}
		var emAberto = aReceber.comprasEmAberto(usuarioId).stream()
			.collect(Collectors.groupingBy(c -> c.cartaoId() + "|" + c.fatura(), Collectors.groupingBy(
					AReceber.CompraEmAberto::pessoaId,
					Collectors.reducing(BigDecimal.ZERO, AReceber.CompraEmAberto::emAberto, BigDecimal::add))));
		var nomes = pessoas.rotulos(usuarioId, emAberto.values().stream().flatMap(m -> m.keySet().stream()).toList());
		for (var f : ciclos) {
			var chave = f.cartaoId() + ":" + f.mes();
			if (prefs.isFaturaFechouAtivo()) {
				avisos.add(new Aviso("fatura-fechou:" + chave, Tipo.FATURA_FECHOU,
						f.fechamento().plusDays(1).atTime(HORA), "Fatura " + f.cartao() + " fechou",
						"Importe o arquivo do banco para dividir as compras.", null,
						new Destino(Tela.FATURA, f.cartaoId(), f.mes())));
			}
			if (!prefs.isFaturaReceberAtivo()) {
				continue;
			}
			var devem = emAberto.getOrDefault(f.cartaoId() + "|" + f.mes(), Map.of());
			// HU-27.3: sem saldo a receber da fatura, não há o segundo aviso.
			if (!devem.isEmpty()) {
				var total = devem.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
				var quem = devem.entrySet().stream()
					.sorted(Map.Entry.<UUID, BigDecimal>comparingByValue().reversed())
					.map(e -> nomes.getOrDefault(e.getKey(), "Pessoa"))
					.toList();
				var destino = devem.size() == 1 ? new Destino(Tela.PESSOA, devem.keySet().iterator().next(), null)
						: new Destino(Tela.RECEBER, null, null);
				avisos.add(new Aviso("fatura-receber:" + chave, Tipo.FATURA_A_RECEBER,
						f.vencimento().minusDays(diasAntes).atTime(HORA),
						"Fatura " + f.cartao() + " vence " + emDias(diasAntes),
						"Faltam " + reais(total) + " de " + juntar(quem) + ".", "Cobrar", destino));
			}
		}
	}

	/** "amanhã", "em 3 dias". */
	private static String emDias(int dias) {
		return dias == 1 ? "amanhã" : "em " + dias + " dias";
	}

	/** "Lucas", "Lucas e Ana", "Lucas, Ana e Rafa". */
	private static String juntar(List<String> nomes) {
		if (nomes.size() == 1) {
			return nomes.get(0);
		}
		return String.join(", ", nomes.subList(0, nomes.size() - 1)) + " e " + nomes.get(nomes.size() - 1);
	}

	private static String reais(BigDecimal valor) {
		return NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(valor);
	}

}
