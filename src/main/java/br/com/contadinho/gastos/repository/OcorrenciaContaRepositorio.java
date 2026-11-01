package br.com.contadinho.gastos.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.com.contadinho.gastos.domain.OcorrenciaConta;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Toda consulta recebe o usuário dono (RNF-01). */
public interface OcorrenciaContaRepositorio extends JpaRepository<OcorrenciaConta, UUID> {

	List<OcorrenciaConta> findByUsuarioIdAndCompetencia(UUID usuarioId, LocalDate competencia);

	List<OcorrenciaConta> findByContaFixaIdAndCompetenciaGreaterThanEqual(UUID contaFixaId, LocalDate competencia);

	/** Cria a ocorrência do mês se ainda não existe; duas telas abrindo o mesmo mês não duplicam. */
	@Modifying(flushAutomatically = true)
	@Query(nativeQuery = true, value = """
			insert into ocorrencia_conta (id, usuario_id, conta_fixa_id, competencia, vencimento, valor_previsto)
			values (gen_random_uuid(), :usuarioId, :contaFixaId, :competencia, :vencimento, :valor)
			on conflict (conta_fixa_id, competencia) do nothing""")
	void gerar(UUID usuarioId, UUID contaFixaId, LocalDate competencia, LocalDate vencimento, BigDecimal valor);

}
