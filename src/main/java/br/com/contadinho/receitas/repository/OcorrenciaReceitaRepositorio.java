package br.com.contadinho.receitas.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.contadinho.receitas.domain.OcorrenciaReceita;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Toda consulta recebe o usuário dono (RNF-01). */
public interface OcorrenciaReceitaRepositorio extends JpaRepository<OcorrenciaReceita, UUID> {

	Optional<OcorrenciaReceita> findByIdAndUsuarioId(UUID id, UUID usuarioId);

	List<OcorrenciaReceita> findByUsuarioIdAndCompetencia(UUID usuarioId, LocalDate competencia);

	List<OcorrenciaReceita> findByReceitaId(UUID receitaId);

	/** Cria a ocorrência do mês se ainda não existe; duas telas abrindo o mesmo mês não duplicam. */
	@Modifying(flushAutomatically = true)
	@Query(nativeQuery = true, value = """
			insert into ocorrencia_receita (id, usuario_id, receita_id, competencia, data, valor_previsto)
			values (gen_random_uuid(), :usuarioId, :receitaId, :competencia, :data, :valor)
			on conflict (receita_id, competencia) do nothing""")
	void gerar(UUID usuarioId, UUID receitaId, LocalDate competencia, LocalDate data, BigDecimal valor);

}
