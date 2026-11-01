package br.com.contadinho.cartoes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** O que os outros módulos leem das faturas do dono, como o resumo do mês (HU-23) e o Início (HU-24). */
public interface Faturas {

	/** As faturas gravadas que vencem no mês (RN-37), de todos os cartões do dono, encerrados inclusive. */
	List<FaturaDoMes> queVencemEm(UUID usuarioId, YearMonth mes);

	/**
	 * @param cartao apelido e final, como "Inter •• 9453"
	 * @param minhaParte as compras sem pessoa, isto é, do dono (RN-15); estornos abatem
	 */
	record FaturaDoMes(UUID cartaoId, String cartao, LocalDate vencimento, BigDecimal total, BigDecimal minhaParte) {
	}

	/**
	 * HU-24: de cada cartão ativo, a próxima fatura a vencer (a que vence hoje ainda conta), na ordem dos cartões.
	 */
	List<FaturaAtual> atuais(UUID usuarioId);

	/**
	 * @param mes mês de vencimento, que identifica a fatura (RN-58)
	 * @param fechada já fechou: só falta pagar
	 * @param partes como na divisão da fatura (HU-10): o dono primeiro, depois as pessoas da maior parte para a menor
	 */
	record FaturaAtual(UUID cartaoId, String cartao, YearMonth mes, LocalDate vencimento, boolean fechada,
			BigDecimal total, List<Parte> partes) {
	}

	/** {@code pessoaId} null é o dono (RN-15). */
	record Parte(UUID pessoaId, BigDecimal total) {
	}

	/** HU-27: as faturas dos cartões ativos que fecham ou vencem entre {@code de} e {@code ate}. */
	List<CicloDoCartao> ciclos(UUID usuarioId, LocalDate de, LocalDate ate);

	/** O último dia de compras é o {@code fechamento}; no dia seguinte a fatura está fechada (RN-58). */
	record CicloDoCartao(UUID cartaoId, String cartao, YearMonth mes, LocalDate fechamento, LocalDate vencimento) {
	}

}
