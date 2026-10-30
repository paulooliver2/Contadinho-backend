package br.com.contadinho.cartoes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import br.com.contadinho.comum.ErroApi;
import br.com.contadinho.comum.UsuarioLogado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Importar a fatura em OFX (HU-06, HU-07, RN-59): primeiro a prévia, depois a confirmação com o mesmo arquivo.
 * O arquivo vai em base64 dentro do JSON, para o servidor receber os bytes como estão (o OFX 1 costuma vir em
 * windows-1252).
 */
@RestController
class ImportacoesController {

	/** 2 MB de arquivo; uma fatura em OFX tem poucos KB. */
	private static final int LIMITE_BASE64 = 2_800_000;

	private static final String MES = "\\d{4}-(0[1-9]|1[0-2])";

	private final ImportacaoServico servico;

	ImportacoesController(ImportacaoServico servico) {
		this.servico = servico;
	}

	record PreviaPedido(
			@NotBlank(message = "Escolha o arquivo.") @Size(max = 255) String nomeArquivo,
			@NotBlank(message = "Escolha o arquivo.") @Size(max = LIMITE_BASE64, message = "O arquivo passa de 2 MB.") String conteudo,
			/** Fatura de destino escolhida na prévia; vazio, o servidor sugere (RN-09). */
			@Pattern(regexp = MES, message = "Mês inválido.") String mes) {
	}

	/** A compra do arquivo é a mesma digitada que a prévia apontou (RN-59). */
	record MesmaCompra(@NotBlank String idExterno, @NotNull UUID lancamentoId) {
	}

	record ConfirmacaoPedido(
			@NotBlank(message = "Escolha o arquivo.") @Size(max = 255) String nomeArquivo,
			@NotBlank(message = "Escolha o arquivo.") @Size(max = LIMITE_BASE64, message = "O arquivo passa de 2 MB.") String conteudo,
			@NotNull(message = "Escolha a fatura.") @Pattern(regexp = MES, message = "Mês inválido.") String mes,
			@NotNull List<@Valid MesmaCompra> mesmas) {
	}

	record ParecidaResposta(@NotNull UUID id, @NotNull String descricao) {
	}

	record ItemPreviaResposta(
			@NotNull String idExterno,
			@NotNull LocalDate data,
			@NotNull String descricao,
			@NotNull BigDecimal valor,
			Integer parcelaAtual,
			Integer parcelaTotal,
			@NotNull Boolean jaExiste,
			ParecidaResposta parecida) {
	}

	record PreviaResposta(
			@NotNull String mes,
			@NotNull LocalDate fechamento,
			@NotNull LocalDate vencimento,
			@NotNull Integer novos,
			@NotNull Integer jaExistiam,
			/** Pagamentos da fatura anterior, que não são compras (RN-61). */
			@NotNull Integer ignorados,
			@NotNull BigDecimal totalLido,
			/** Total informado pelo banco no arquivo, quando houver (RN-12). */
			BigDecimal totalArquivo,
			@NotNull List<ItemPreviaResposta> itens) {
	}

	record ImportacaoResposta(@NotNull UUID id, @NotNull String mes, @NotNull Integer novos,
			@NotNull Integer jaExistiam, @NotNull Integer casadas) {
	}

	@PostMapping("/api/cartoes/{cartaoId}/importacoes/previa")
	PreviaResposta previa(@UsuarioLogado UUID usuarioId, @PathVariable UUID cartaoId,
			@Valid @RequestBody PreviaPedido pedido) {
		var mes = pedido.mes() == null ? null : YearMonth.parse(pedido.mes());
		var previa = servico.previa(usuarioId, cartaoId, pedido.nomeArquivo(), bytes(pedido.conteudo()), mes);
		var itens = previa.itens().stream().map(i -> {
			var item = i.item();
			var parecida = i.parecida() == null ? null : new ParecidaResposta(i.parecida().getId(), i.parecida().getDescricao());
			return new ItemPreviaResposta(item.idExterno(), item.data(), item.descricao(), item.valor(),
					item.parcelaAtual(), item.parcelaTotal(), i.jaExiste(), parecida);
		}).toList();
		var ciclo = previa.ciclo();
		int jaExistiam = (int) previa.itens().stream().filter(ImportacaoServico.ItemPrevia::jaExiste).count();
		return new PreviaResposta(ciclo.mes().toString(), ciclo.fechamento(), ciclo.vencimento(),
				itens.size() - jaExistiam, jaExistiam, previa.arquivo().ignorados(), previa.totalLido(),
				previa.arquivo().totalArquivo(), itens);
	}

	@PostMapping("/api/cartoes/{cartaoId}/importacoes")
	@ResponseStatus(HttpStatus.CREATED)
	ImportacaoResposta importar(@UsuarioLogado UUID usuarioId, @PathVariable UUID cartaoId,
			@Valid @RequestBody ConfirmacaoPedido pedido) {
		var mesmas = new LinkedHashMap<String, UUID>();
		pedido.mesmas().forEach(m -> mesmas.put(m.idExterno(), m.lancamentoId()));
		var resultado = servico.importar(usuarioId, cartaoId, pedido.nomeArquivo(), bytes(pedido.conteudo()),
				YearMonth.parse(pedido.mes()), mesmas);
		return new ImportacaoResposta(resultado.importacaoId(), resultado.mes().toString(), resultado.novos(),
				resultado.jaExistiam(), resultado.casadas());
	}

	private static byte[] bytes(String base64) {
		try {
			return Base64.getDecoder().decode(base64);
		}
		catch (IllegalArgumentException e) {
			throw new ErroApi(HttpStatus.BAD_REQUEST, "Não deu para ler o arquivo enviado. Escolha de novo.");
		}
	}

}
