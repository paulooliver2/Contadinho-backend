package br.com.contadinho.pessoas;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Toda consulta recebe o usuário dono (RNF-01). */
interface PessoaRepositorio extends JpaRepository<Pessoa, UUID> {

	List<Pessoa> findByUsuarioIdAndArquivadaEmIsNullOrderByNome(UUID usuarioId);

	List<Pessoa> findByUsuarioIdAndArquivadaEmIsNotNullOrderByNome(UUID usuarioId);

	Optional<Pessoa> findByIdAndUsuarioId(UUID id, UUID usuarioId);

}
