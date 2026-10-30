package br.com.contadinho.conta;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import br.com.contadinho.comum.ErroApi;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro, login e sessões (HU-01).
 */
@Service
class AutenticacaoServico {

	static final String CREDENCIAIS_INVALIDAS = "E-mail ou senha incorretos.";

	static final String SESSAO_ENCERRADA = "Sua sessão terminou. Entre de novo.";

	private final UsuarioRepositorio usuarios;

	private final SessaoRepositorio sessoes;

	private final TentativasLogin tentativas;

	private final EmissorTokens tokens;

	private final PasswordEncoder senhas;

	private final Clock clock;

	/** Comparado quando o e-mail não existe, para a resposta demorar o mesmo e não revelar quem tem conta. */
	private final String hashDeMentira;

	AutenticacaoServico(UsuarioRepositorio usuarios, SessaoRepositorio sessoes, TentativasLogin tentativas,
			EmissorTokens tokens, PasswordEncoder senhas, Clock clock) {
		this.usuarios = usuarios;
		this.sessoes = sessoes;
		this.tentativas = tentativas;
		this.tokens = tokens;
		this.senhas = senhas;
		this.clock = clock;
		this.hashDeMentira = senhas.encode(UUID.randomUUID().toString());
	}

	record Tokens(String tokenAcesso, Instant acessoExpiraEm, String refreshToken) {
	}

	@Transactional
	Tokens cadastrar(String nome, String email, String senha, String dispositivo) {
		var agora = Instant.now(clock);
		var emailNormalizado = normalizar(email);
		if (usuarios.existsByEmail(emailNormalizado)) {
			throw emailEmUso();
		}
		Usuario usuario;
		try {
			usuario = usuarios.saveAndFlush(new Usuario(nome.strip(), emailNormalizado, senhas.encode(senha), agora));
		}
		catch (DataIntegrityViolationException e) {
			throw emailEmUso();
		}
		return abrirSessao(usuario.getId(), dispositivo, agora);
	}

	/**
	 * Sem transação de propósito: a tentativa errada precisa ficar gravada mesmo quando a resposta é um erro.
	 */
	Tokens entrar(String email, String senha, String dispositivo) {
		var agora = Instant.now(clock);
		var emailNormalizado = normalizar(email);
		tentativas.bloqueadoAte(emailNormalizado, agora).ifPresent(ate -> {
			throw bloqueado(ate, agora);
		});

		var usuario = usuarios.findByEmail(emailNormalizado);
		var hash = usuario.map(Usuario::getSenhaHash).orElse(hashDeMentira);
		var acertou = senhas.matches(senha, hash) && usuario.isPresent();
		tentativas.registrar(emailNormalizado, acertou, agora);

		if (!acertou) {
			// O quinto erro já responde com o bloqueio, para o app avisar na hora.
			tentativas.bloqueadoAte(emailNormalizado, agora).ifPresent(ate -> {
				throw bloqueado(ate, agora);
			});
			throw new ErroApi(HttpStatus.UNAUTHORIZED, CREDENCIAIS_INVALIDAS);
		}
		return abrirSessao(usuario.get().getId(), dispositivo, agora);
	}

	/** Troca o refresh token por um novo; o antigo deixa de valer (rotação). */
	@Transactional
	Tokens renovar(String refreshToken) {
		var agora = Instant.now(clock);
		var sessao = sessoes.findByTokenHash(EmissorTokens.hash(refreshToken))
			.filter(s -> s.ativa(agora))
			.orElseThrow(() -> new ErroApi(HttpStatus.UNAUTHORIZED, SESSAO_ENCERRADA));
		if (sessoes.revogar(sessao.getId(), agora) == 0) {
			throw new ErroApi(HttpStatus.UNAUTHORIZED, SESSAO_ENCERRADA);
		}
		return abrirSessao(sessao.getUsuarioId(), sessao.getDispositivo(), agora);
	}

	@Transactional
	void sair(String refreshToken) {
		var agora = Instant.now(clock);
		sessoes.findByTokenHash(EmissorTokens.hash(refreshToken)).ifPresent(s -> sessoes.revogar(s.getId(), agora));
	}

	private Tokens abrirSessao(UUID usuarioId, String dispositivo, Instant agora) {
		var refresh = EmissorTokens.novoRefresh();
		sessoes.save(new Sessao(usuarioId, EmissorTokens.hash(refresh), dispositivo, agora, tokens.refreshExpiraEm(agora)));
		return new Tokens(tokens.acesso(usuarioId, agora), tokens.acessoExpiraEm(agora), refresh);
	}

	static String normalizar(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

	private static ErroApi emailEmUso() {
		return new ErroApi(HttpStatus.CONFLICT, "Já existe uma conta com este e-mail. Entre com ele ou use outro.");
	}

	private static ErroApi bloqueado(Instant ate, Instant agora) {
		var segundos = Math.max(1, Duration.between(agora, ate).toSeconds());
		var minutos = (segundos + 59) / 60;
		var cabecalhos = new HttpHeaders();
		cabecalhos.set(HttpHeaders.RETRY_AFTER, Long.toString(segundos));
		var detalhe = "Muitas tentativas com senha errada. Tente de novo em " + minutos
				+ (minutos == 1 ? " minuto." : " minutos.");
		return new ErroApi(HttpStatus.TOO_MANY_REQUESTS, detalhe, cabecalhos);
	}

}
