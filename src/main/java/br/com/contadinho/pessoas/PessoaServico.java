package br.com.contadinho.pessoas;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import br.com.contadinho.comum.ErroApi;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro (HU-16) e arquivamento (HU-42) das pessoas do dono.
 */
@Service
class PessoaServico implements Pessoas {

	static final String PESSOA_NAO_ENCONTRADA = "Pessoa não encontrada.";

	static final String WHATSAPP_INVALIDO = "Informe DDD e número, como (61) 99999-8888.";

	static final String TEM_HISTORICO = "Essa pessoa já tem compras ou valores registrados. Arquive em vez de excluir.";

	private final PessoaRepositorio pessoas;

	private final SaldoDaPessoa saldos;

	private final Clock clock;

	PessoaServico(PessoaRepositorio pessoas, SaldoDaPessoa saldos, Clock clock) {
		this.pessoas = pessoas;
		this.saldos = saldos;
		this.clock = clock;
	}

	record Dados(String nome, String apelido, String whatsapp) {
	}

	@Override
	@Transactional(readOnly = true)
	public boolean podeEscolher(UUID usuarioId, UUID pessoaId) {
		return pessoas.findByIdAndUsuarioId(pessoaId, usuarioId).filter(p -> !p.isArquivada()).isPresent();
	}

	@Override
	@Transactional(readOnly = true)
	public boolean existe(UUID usuarioId, UUID pessoaId) {
		return pessoas.findByIdAndUsuarioId(pessoaId, usuarioId).isPresent();
	}

	List<Pessoa> listar(UUID usuarioId, boolean arquivadas) {
		return arquivadas ? pessoas.findByUsuarioIdAndArquivadaEmIsNotNullOrderByNome(usuarioId)
				: pessoas.findByUsuarioIdAndArquivadaEmIsNullOrderByNome(usuarioId);
	}

	@Transactional
	Pessoa cadastrar(UUID usuarioId, Dados dados) {
		return pessoas.save(new Pessoa(usuarioId, dados.nome(), vazioParaNulo(dados.apelido()),
				whatsapp(dados.whatsapp()), Instant.now(clock)));
	}

	@Transactional
	Pessoa alterar(UUID usuarioId, UUID id, Dados dados) {
		var pessoa = pessoa(usuarioId, id);
		pessoa.alterar(dados.nome(), vazioParaNulo(dados.apelido()), whatsapp(dados.whatsapp()));
		return pessoa;
	}

	/** RN-36: só com saldo zero; senão, 409 com o saldo pendente (HU-42). */
	@Transactional
	Pessoa arquivar(UUID usuarioId, UUID id) {
		var pessoa = pessoa(usuarioId, id);
		var saldo = saldos.saldo(usuarioId, id);
		if (saldo.signum() != 0) {
			throw new ErroApi(HttpStatus.CONFLICT, pessoa.getNome() + " tem saldo de "
					+ String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", saldo)
					+ " a receber. Só dá para arquivar com o saldo zerado.");
		}
		pessoa.arquivar(Instant.now(clock));
		return pessoa;
	}

	@Transactional
	Pessoa desarquivar(UUID usuarioId, UUID id) {
		var pessoa = pessoa(usuarioId, id);
		pessoa.desarquivar();
		return pessoa;
	}

	/**
	 * Sem transação própria: a chave estrangeira de compras, recebíveis e adicionais só falha no commit do
	 * repositório, e aí vira 409 com a sugestão de arquivar (RN-36).
	 */
	void excluir(UUID usuarioId, UUID id) {
		var pessoa = pessoa(usuarioId, id);
		try {
			pessoas.delete(pessoa);
		}
		catch (DataIntegrityViolationException e) {
			throw new ErroApi(HttpStatus.CONFLICT, TEM_HISTORICO);
		}
	}

	/** Pessoa do dono; de outro dono é 404 (RNF-01). */
	private Pessoa pessoa(UUID usuarioId, UUID id) {
		return pessoas.findByIdAndUsuarioId(id, usuarioId)
			.orElseThrow(() -> new ErroApi(HttpStatus.NOT_FOUND, PESSOA_NAO_ENCONTRADA));
	}

	private static String whatsapp(String texto) {
		if (texto == null || texto.isBlank()) {
			return null;
		}
		return Whatsapp.normalizar(texto).orElseThrow(() -> ErroApi.campos(Map.of("whatsapp", WHATSAPP_INVALIDO)));
	}

	private static String vazioParaNulo(String texto) {
		return texto == null || texto.isEmpty() ? null : texto;
	}

}
