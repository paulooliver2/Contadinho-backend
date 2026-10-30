package br.com.contadinho.conta;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import br.com.contadinho.comum.ErroApi;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-40 e RN-04: o pedido nunca revela se o e-mail existe; o link vale 30 minutos e uma vez; trocar a senha
 * encerra todas as sessões.
 */
@Service
class RecuperacaoSenhaServico {

	static final Duration VALIDADE = Duration.ofMinutes(30);

	/** Um e-mail por conta a cada 2 minutos, para o botão de reenviar não virar spam. */
	static final Duration INTERVALO_ENTRE_ENVIOS = Duration.ofMinutes(2);

	static final String LINK_INVALIDO = "Este link expirou ou já foi usado. Peça um novo.";

	private final UsuarioRepositorio usuarios;

	private final TokenRecuperacaoRepositorio tokens;

	private final SessaoRepositorio sessoes;

	private final TentativasLogin tentativas;

	private final PasswordEncoder senhas;

	private final ApplicationEventPublisher eventos;

	private final Clock clock;

	RecuperacaoSenhaServico(UsuarioRepositorio usuarios, TokenRecuperacaoRepositorio tokens, SessaoRepositorio sessoes,
			TentativasLogin tentativas, PasswordEncoder senhas, ApplicationEventPublisher eventos, Clock clock) {
		this.usuarios = usuarios;
		this.tokens = tokens;
		this.sessoes = sessoes;
		this.tentativas = tentativas;
		this.senhas = senhas;
		this.eventos = eventos;
		this.clock = clock;
	}

	@Transactional
	void pedir(String email) {
		var agora = Instant.now(clock);
		usuarios.findByEmail(AutenticacaoServico.normalizar(email)).ifPresent(usuario -> {
			var pediuHaPouco = tokens.existsByUsuarioIdAndExpiraEmAfter(usuario.getId(),
					agora.plus(VALIDADE).minus(INTERVALO_ENTRE_ENVIOS));
			if (pediuHaPouco) {
				return;
			}
			var token = EmissorTokens.novoSegredo();
			tokens.save(new TokenRecuperacao(usuario.getId(), EmissorTokens.hash(token), agora.plus(VALIDADE)));
			eventos.publishEvent(new EmailRecuperacao.RecuperacaoPedida(usuario.getEmail(), usuario.getNome(), token));
		});
	}

	@Transactional
	void redefinir(String token, String novaSenha) {
		var agora = Instant.now(clock);
		var pedido = tokens.findByTokenHash(EmissorTokens.hash(token))
			.filter(t -> t.valido(agora))
			.orElseThrow(() -> new ErroApi(HttpStatus.BAD_REQUEST, LINK_INVALIDO));
		if (tokens.usar(pedido.getId(), agora) == 0) {
			throw new ErroApi(HttpStatus.BAD_REQUEST, LINK_INVALIDO);
		}
		var usuario = usuarios.findById(pedido.getUsuarioId())
			.orElseThrow(() -> new ErroApi(HttpStatus.BAD_REQUEST, LINK_INVALIDO));

		usuario.trocarSenha(senhas.encode(novaSenha));
		tokens.invalidarTodos(usuario.getId(), agora);
		sessoes.revogarTodas(usuario.getId(), agora);
		// Quem provou ser dono do e-mail não continua bloqueado pelas senhas erradas de antes (RN-03).
		tentativas.registrar(usuario.getEmail(), true, agora);
	}

}
