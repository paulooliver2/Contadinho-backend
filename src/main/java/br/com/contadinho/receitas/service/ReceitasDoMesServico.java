package br.com.contadinho.receitas.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

import br.com.contadinho.receitas.Receitas;
import br.com.contadinho.receitas.domain.OcorrenciaReceita;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link Receitas} para os outros módulos. */
@Service
public class ReceitasDoMesServico implements Receitas {

	private final ReceitaServico receitas;

	public ReceitasDoMesServico(ReceitaServico receitas) {
		this.receitas = receitas;
	}

	@Override
	@Transactional
	public ReceitasDoMes doMes(UUID usuarioId, YearMonth mes) {
		var recebido = BigDecimal.ZERO;
		var previsto = BigDecimal.ZERO;
		for (var r : receitas.doMes(usuarioId, mes)) {
			if (r.status() == OcorrenciaReceita.Status.RECEBIDA) {
				recebido = recebido.add(r.ocorrencia().valor());
			}
			else {
				previsto = previsto.add(r.ocorrencia().valor());
			}
		}
		return new ReceitasDoMes(recebido, previsto);
	}

}
