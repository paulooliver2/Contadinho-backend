package br.com.contadinho.avisos.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import br.com.contadinho.avisos.service.AgendaServico;
import br.com.contadinho.comum.UsuarioLogado;
import jakarta.validation.constraints.NotNull;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Os avisos que o aparelho agenda (HU-26, HU-29). */
@RestController
@RequestMapping("/api/avisos")
public class AvisosController {

	private final AgendaServico servico;

	public AvisosController(AgendaServico servico) {
		this.servico = servico;
	}

	public record AvisoResposta(
			/** Estável entre reagendamentos. */
			@NotNull String chave,
			@NotNull AgendaServico.Tipo tipo,
			/** Horário local do dono, sem fuso, como 2026-11-03T09:00:00. */
			@NotNull LocalDateTime quando,
			@NotNull String titulo,
			@NotNull String texto,
			/** A conta do mês que o aviso abre ao ser tocado. */
			@NotNull UUID ocorrenciaId,
			/** O mês dessa conta, como 2026-11. */
			@NotNull String mes) {

		static AvisoResposta de(AgendaServico.Aviso a) {
			return new AvisoResposta(a.chave(), a.tipo(), a.quando(), a.titulo(), a.texto(), a.ocorrenciaId(),
					a.mes().toString());
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
