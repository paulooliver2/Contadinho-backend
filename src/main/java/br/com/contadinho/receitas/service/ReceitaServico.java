package br.com.contadinho.receitas.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import br.com.contadinho.comum.ErroApi;
import br.com.contadinho.receitas.domain.OcorrenciaReceita;
import br.com.contadinho.receitas.domain.Receita;
import br.com.contadinho.receitas.repository.OcorrenciaReceitaRepositorio;
import br.com.contadinho.receitas.repository.ReceitaRepositorio;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Receitas (HU-22): de uma vez ou todo mês, com uma ocorrência por mês, criada na primeira vez que o mês é aberto.
 * O dono confirma o que caiu, talvez com outro valor (HU-22.2). A de uma vez com data até hoje já nasce recebida.
 * Pagamento de pessoa não entra aqui (RN-33).
 */
@Service
public class ReceitaServico {

	public static final String RECEITA_NAO_ENCONTRADA = "Receita não encontrada.";

	public static final String OCORRENCIA_NAO_ENCONTRADA = "Receita do mês não encontrada.";

	public static final String NAO_MUDA_REPETICAO = "Para mudar se repete, exclua e cadastre de novo.";

	public static final String SO_RECORRENTE_ENCERRA = "Só receita de todo mês se encerra; esta você pode excluir.";

	public static final String RECEITA_ENCERRADA = "Essa receita já foi encerrada.";

	private final ReceitaRepositorio receitas;

	private final OcorrenciaReceitaRepositorio ocorrencias;

	private final Clock clock;

	public ReceitaServico(ReceitaRepositorio receitas, OcorrenciaReceitaRepositorio ocorrencias, Clock clock) {
		this.receitas = receitas;
		this.ocorrencias = ocorrencias;
		this.clock = clock;
	}

	/** HU-22.1. Sem data, vale hoje; na recorrente, a data diz o dia e o primeiro mês. */
	public record Dados(String descricao, BigDecimal valor, LocalDate data, boolean recorrente) {
	}

	/** Uma receita no mês e o status que ela tem hoje. */
	public record ReceitaDoMes(OcorrenciaReceita ocorrencia, Receita receita, OcorrenciaReceita.Status status) {
	}

	@Transactional
	public Receita cadastrar(UUID usuarioId, Dados dados) {
		var hoje = LocalDate.now(clock);
		var data = dados.data() == null ? hoje : dados.data();
		var nova = new Receita(usuarioId, dados.recorrente());
		nova.alterar(dados.descricao(), dados.valor(), data, YearMonth.from(hoje));
		// Com o id já preenchido, o save faz merge: a instância gerenciada é a que volta.
		var receita = receitas.saveAndFlush(nova);
		if (!receita.isRecorrente()) {
			gerar(usuarioId, receita, YearMonth.from(data));
			ocorrencias.findByReceitaId(receita.getId()).forEach(o -> recebidaSeJaCaiu(o, receita, hoje));
		}
		return receita;
	}

	/**
	 * HU-46.1. Uma vez: a ocorrência segue a receita. Todo mês: o valor e o dia valem do mês atual em diante, nas
	 * ocorrências ainda não confirmadas; os meses anteriores ficam como estavam.
	 */
	@Transactional
	public Receita alterar(UUID usuarioId, UUID id, Dados dados) {
		var receita = receita(usuarioId, id);
		if (dados.recorrente() != receita.isRecorrente()) {
			throw ErroApi.campos(Map.of("recorrente", NAO_MUDA_REPETICAO));
		}
		if (receita.getEncerradaEm() != null) {
			throw new ErroApi(HttpStatus.CONFLICT, RECEITA_ENCERRADA);
		}
		var hoje = LocalDate.now(clock);
		var data = dados.data() == null ? hoje : dados.data();
		var atual = YearMonth.from(hoje);
		receita.alterar(dados.descricao(), dados.valor(), data, atual);
		if (!receita.isRecorrente()) {
			ocorrencias.findByReceitaId(id).forEach(o -> {
				o.acompanhar(receita.getValor(), data);
				o.desfazer();
				recebidaSeJaCaiu(o, receita, hoje);
			});
			return receita;
		}
		var desde = receita.getPrimeiroMes().isAfter(atual) ? receita.getPrimeiroMes() : atual;
		ocorrencias.findByReceitaId(id).stream()
			.filter(o -> !o.getMes().isBefore(desde) && o.emAberto())
			.forEach(o -> o.acompanhar(receita.getValor(), receita.dataEm(o.getMes())));
		// Mudou o primeiro mês de uma que ainda não começou: as ocorrências de antes somem.
		ocorrencias.findByReceitaId(id).stream()
			.filter(o -> o.getMes().isBefore(receita.getPrimeiroMes()))
			.forEach(ocorrencias::delete);
		return receita;
	}

	/** Todo mês: para de gerar a partir do próximo mês; as do mês atual e de antes ficam. */
	@Transactional
	public Receita encerrar(UUID usuarioId, UUID id) {
		var receita = receita(usuarioId, id);
		if (!receita.isRecorrente()) {
			throw new ErroApi(HttpStatus.CONFLICT, SO_RECORRENTE_ENCERRA);
		}
		var hoje = LocalDate.now(clock);
		receita.encerrar(hoje);
		ocorrencias.findByReceitaId(id).stream()
			.filter(o -> o.getMes().isAfter(YearMonth.from(hoje)) && o.emAberto())
			.forEach(ocorrencias::delete);
		return receita;
	}

	/** HU-46.1: some com todos os meses dela. */
	@Transactional
	public void excluir(UUID usuarioId, UUID id) {
		var receita = receita(usuarioId, id);
		ocorrencias.deleteAll(ocorrencias.findByReceitaId(id));
		receitas.delete(receita);
	}

	/** As receitas do mês, pela data. Cria as ocorrências que faltam das receitas ativas no mês. */
	@Transactional
	public List<ReceitaDoMes> doMes(UUID usuarioId, YearMonth mes) {
		var todas = receitas.findByUsuarioId(usuarioId);
		todas.stream().filter(r -> r.ativaEm(mes)).forEach(r -> gerar(usuarioId, r, mes));
		var porId = todas.stream().collect(Collectors.toMap(Receita::getId, Function.identity()));
		var hoje = LocalDate.now(clock);
		return ocorrencias.findByUsuarioIdAndCompetencia(usuarioId, mes.atDay(1)).stream()
			.map(o -> new ReceitaDoMes(o, porId.get(o.getReceitaId()), o.status(hoje)))
			.sorted(Comparator.comparing((ReceitaDoMes r) -> r.ocorrencia().getData())
				.thenComparing(r -> r.receita().getDescricao()))
			.toList();
	}

	/** HU-22.2: caiu; sem valor, o previsto. */
	@Transactional
	public ReceitaDoMes confirmar(UUID usuarioId, UUID ocorrenciaId, BigDecimal valor) {
		var item = item(usuarioId, ocorrenciaId);
		item.ocorrencia().confirmar(valor == null ? item.ocorrencia().getValorPrevisto() : valor);
		return item(usuarioId, ocorrenciaId);
	}

	/** Não caiu, afinal: volta a prevista ou a confirmar. */
	@Transactional
	public ReceitaDoMes desfazer(UUID usuarioId, UUID ocorrenciaId) {
		item(usuarioId, ocorrenciaId).ocorrencia().desfazer();
		return item(usuarioId, ocorrenciaId);
	}

	private void gerar(UUID usuarioId, Receita receita, YearMonth mes) {
		ocorrencias.gerar(usuarioId, receita.getId(), mes.atDay(1), receita.dataEm(mes), receita.getValor());
	}

	/** A de uma vez que já caiu foi recebida: quem registra um freela de ontem não precisa confirmar. */
	private static void recebidaSeJaCaiu(OcorrenciaReceita o, Receita receita, LocalDate hoje) {
		if (!o.getData().isAfter(hoje)) {
			o.confirmar(receita.getValor());
		}
	}

	private ReceitaDoMes item(UUID usuarioId, UUID ocorrenciaId) {
		var o = ocorrencias.findByIdAndUsuarioId(ocorrenciaId, usuarioId)
			.orElseThrow(() -> new ErroApi(HttpStatus.NOT_FOUND, OCORRENCIA_NAO_ENCONTRADA));
		return new ReceitaDoMes(o, receita(usuarioId, o.getReceitaId()), o.status(LocalDate.now(clock)));
	}

	private Receita receita(UUID usuarioId, UUID id) {
		return receitas.findByIdAndUsuarioId(id, usuarioId)
			.orElseThrow(() -> new ErroApi(HttpStatus.NOT_FOUND, RECEITA_NAO_ENCONTRADA));
	}

}
