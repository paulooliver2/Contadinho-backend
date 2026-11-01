package br.com.contadinho.avisos.service;

import java.time.LocalTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import br.com.contadinho.avisos.domain.PreferenciasAvisos;
import br.com.contadinho.avisos.repository.PreferenciasAvisosRepositorio;
import br.com.contadinho.comum.ErroApi;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** HU-28: as preferências ficam no servidor, então outro celular reagenda com elas (HU-28.4). */
@Service
public class PreferenciasServico {

	public static final Set<Integer> ANTECEDENCIAS_DA_CONTA = Set.of(1, 2, 3);

	public static final Set<Integer> ANTECEDENCIAS_DA_FATURA = Set.of(2, 3, 5);

	public static final String ANTECEDENCIA_DA_CONTA = "Escolha 1, 2 ou 3 dias.";

	public static final String ANTECEDENCIA_DA_FATURA = "Escolha 2, 3 ou 5 dias.";

	public static final String SILENCIO_VAZIO = "O início e o fim do silêncio precisam ser diferentes.";

	private final PreferenciasAvisosRepositorio preferencias;

	public PreferenciasServico(PreferenciasAvisosRepositorio preferencias) {
		this.preferencias = preferencias;
	}

	public record Dados(boolean contaAtivo, int contaAntecedencia, boolean faturaFechouAtivo,
			boolean faturaReceberAtivo, int faturaAntecedencia, boolean silencioAtivo, LocalTime silencioInicio,
			LocalTime silencioFim) {
	}

	/** Sem nada gravado, os padrões. */
	@Transactional(readOnly = true)
	public PreferenciasAvisos ler(UUID usuarioId) {
		return preferencias.findById(usuarioId).orElseGet(() -> new PreferenciasAvisos(usuarioId));
	}

	@Transactional
	public PreferenciasAvisos salvar(UUID usuarioId, Dados d) {
		if (!ANTECEDENCIAS_DA_CONTA.contains(d.contaAntecedencia())) {
			throw ErroApi.campos(Map.of("contaAntecedencia", ANTECEDENCIA_DA_CONTA));
		}
		if (!ANTECEDENCIAS_DA_FATURA.contains(d.faturaAntecedencia())) {
			throw ErroApi.campos(Map.of("faturaAntecedencia", ANTECEDENCIA_DA_FATURA));
		}
		if (d.silencioInicio().equals(d.silencioFim())) {
			throw ErroApi.campos(Map.of("silencioFim", SILENCIO_VAZIO));
		}
		var atuais = ler(usuarioId);
		atuais.alterar(d.contaAtivo(), d.contaAntecedencia(), d.faturaFechouAtivo(), d.faturaReceberAtivo(),
				d.faturaAntecedencia(), d.silencioAtivo(), d.silencioInicio(), d.silencioFim());
		return preferencias.saveAndFlush(atuais);
	}

}
