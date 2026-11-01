package br.com.contadinho.avisos.controller;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import br.com.contadinho.avisos.domain.PreferenciasAvisos;
import br.com.contadinho.avisos.service.AgendaServico;
import br.com.contadinho.avisos.service.PreferenciasServico;
import br.com.contadinho.comum.UsuarioLogado;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Os avisos que o aparelho agenda (HU-26, HU-27, HU-29) e as preferências deles (HU-28). */
@RestController
@RequestMapping("/api/avisos")
public class AvisosController {

	private final AgendaServico servico;

	private final PreferenciasServico preferencias;

	public AvisosController(AgendaServico servico, PreferenciasServico preferencias) {
		this.servico = servico;
		this.preferencias = preferencias;
	}

	/** HU-28. Horários como 22:00. */
	public record PreferenciasPedido(
			@NotNull(message = "Informe se quer os avisos de conta.") Boolean contaAtivo,
			/** 1, 2 ou 3. */
			@NotNull(message = "Escolha a antecedência.") Integer contaAntecedencia,
			@NotNull(message = "Informe se quer o aviso de fechamento.") Boolean faturaFechouAtivo,
			@NotNull(message = "Informe se quer o aviso de quem deve.") Boolean faturaReceberAtivo,
			/** 2, 3 ou 5. */
			@NotNull(message = "Escolha a antecedência.") Integer faturaAntecedencia,
			@NotNull(message = "Informe se quer o horário silencioso.") Boolean silencioAtivo,
			@NotNull(message = "Informe o início.") @JsonFormat(pattern = "HH:mm")
			@Schema(type = "string", example = "22:00") LocalTime silencioInicio,
			@NotNull(message = "Informe o fim.") @JsonFormat(pattern = "HH:mm")
			@Schema(type = "string", example = "08:00") LocalTime silencioFim) {
	}

	public record PreferenciasResposta(@NotNull Boolean contaAtivo, @NotNull Integer contaAntecedencia,
			@NotNull Boolean faturaFechouAtivo, @NotNull Boolean faturaReceberAtivo, @NotNull Integer faturaAntecedencia,
			@NotNull Boolean silencioAtivo,
			@NotNull @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "22:00") LocalTime silencioInicio,
			@NotNull @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "08:00") LocalTime silencioFim) {

		static PreferenciasResposta de(PreferenciasAvisos p) {
			return new PreferenciasResposta(p.isContaAtivo(), p.getContaAntecedencia(), p.isFaturaFechouAtivo(),
					p.isFaturaReceberAtivo(), p.getFaturaAntecedencia(), p.isSilencioAtivo(), p.getSilencioInicio(),
					p.getSilencioFim());
		}
	}

	/** HU-28: sem nada salvo, os padrões. */
	@GetMapping("/preferencias")
	public PreferenciasResposta preferencias(@UsuarioLogado UUID usuarioId) {
		return PreferenciasResposta.de(preferencias.ler(usuarioId));
	}

	/** HU-28. Depois de salvar, o aparelho reagenda com a agenda nova. */
	@PutMapping("/preferencias")
	public PreferenciasResposta salvar(@UsuarioLogado UUID usuarioId, @Valid @RequestBody PreferenciasPedido pedido) {
		return PreferenciasResposta.de(preferencias.salvar(usuarioId,
				new PreferenciasServico.Dados(pedido.contaAtivo(), pedido.contaAntecedencia(), pedido.faturaFechouAtivo(),
						pedido.faturaReceberAtivo(), pedido.faturaAntecedencia(), pedido.silencioAtivo(),
						pedido.silencioInicio(), pedido.silencioFim())));
	}

	public record DestinoResposta(
			@NotNull AgendaServico.Tela tela,
			/** A ocorrência (CONTA), o cartão (FATURA) ou a pessoa (PESSOA). */
			UUID id,
			/** O mês da conta ou da fatura, como 2026-11. */
			String mes) {
	}

	public record AvisoResposta(
			/** Estável entre reagendamentos. */
			@NotNull String chave,
			@NotNull AgendaServico.Tipo tipo,
			/** Horário local do dono, sem fuso, como 2026-11-03T09:00:00. */
			@NotNull LocalDateTime quando,
			@NotNull String titulo,
			@NotNull String texto,
			/** O botão do aviso, como "Cobrar"; sem botão, não vem. */
			String acao,
			/** O que o toque abre. */
			@NotNull DestinoResposta destino) {

		static AvisoResposta de(AgendaServico.Aviso a) {
			var d = a.destino();
			return new AvisoResposta(a.chave(), a.tipo(), a.quando(), a.titulo(), a.texto(), a.acao(),
					new DestinoResposta(d.tela(), d.id(), d.mes() == null ? null : d.mes().toString()));
		}
	}

	/**
	 * HU-29: os avisos dos próximos 60 dias, no máximo 60, do mais próximo para o mais distante. O aparelho cancela os
	 * que tem e agenda estes, ao abrir o app e depois de cada mudança que mexe neles.
	 */
	@GetMapping("/agenda")
	public List<AvisoResposta> agenda(@UsuarioLogado UUID usuarioId) {
		return servico.agenda(usuarioId).stream().map(AvisoResposta::de).toList();
	}

}
