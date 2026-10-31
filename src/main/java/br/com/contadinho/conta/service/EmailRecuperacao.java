package br.com.contadinho.conta.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Manda o link de recuperação (HU-40). Roda depois do commit e fora da requisição: assim o tempo de resposta
 * não muda se a conta existe ou não (RN-04), e um SMTP lento não segura o app.
 */
@Component
public class EmailRecuperacao {

	private static final Logger log = LoggerFactory.getLogger(EmailRecuperacao.class);

	private final JavaMailSender carteiro;

	private final String remetente;

	private final String link;

	public EmailRecuperacao(JavaMailSender carteiro, @Value("${contadinho.email.remetente}") String remetente,
			@Value("${contadinho.recuperacao.link}") String link) {
		this.carteiro = carteiro;
		this.remetente = remetente;
		this.link = link;
	}

	/** O token vai só em memória até o e-mail; no banco fica o hash. */
	public record RecuperacaoPedida(String email, String nome, String token) {
	}

	@Async
	@TransactionalEventListener
	public void enviar(RecuperacaoPedida pedido) {
		var mensagem = new SimpleMailMessage();
		mensagem.setFrom(remetente);
		mensagem.setTo(pedido.email());
		mensagem.setSubject("Crie uma senha nova no Contadinho");
		mensagem.setText("""
				Olá, %s.

				Recebemos um pedido para trocar a senha da sua conta no Contadinho. Para criar uma senha nova, \
				abra o link abaixo em até 30 minutos:

				%s

				O link só funciona uma vez. Se não foi você quem pediu, ignore este e-mail: sua senha continua a mesma.
				""".formatted(pedido.nome(), UriComponentsBuilder.fromUriString(link)
					.queryParam("token", pedido.token()).build().toUriString()));
		try {
			carteiro.send(mensagem);
		}
		catch (MailException e) {
			// Sem o endereço no log (RNF-14). O usuário pode pedir outro link.
			log.warn("Falha ao enviar o e-mail de recuperação de senha: {}", e.getMessage());
		}
	}

}
