package br.com.contadinho.cartoes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Fatura em PDF (HU-39). O PDF não tem formato padrão: o texto é extraído e um modelo por banco o interpreta
 * (RN-13). A senha, quando o PDF tem, só abre o arquivo; não é guardada nem registrada em log.
 */
final class LeitorPdf {

	/** Lê o texto do PDF de um banco. */
	interface Modelo {

		boolean reconhece(String texto);

		ArquivoFatura ler(String texto) throws ArquivoInvalido;

	}

	static final String PEDE_SENHA = "Este PDF tem senha. Digite a senha para o app abrir a fatura.";

	static final String SENHA_INCORRETA = "Senha incorreta. Confira e tente de novo.";

	static final String SEM_TEXTO = "Este PDF não tem texto, parece uma imagem. Baixe a fatura direto no app do banco.";

	static final String BANCO_SEM_MODELO = "Ainda não sei ler a fatura em PDF deste banco. Por enquanto, o app lê o PDF "
			+ "do Inter; dos outros bancos, importe o OFX da fatura do cartão.";

	/** Uma fatura tem poucas páginas; isto barra arquivos que não são fatura antes de extrair tudo. */
	private static final int MAXIMO_PAGINAS = 60;

	private static final List<Modelo> MODELOS = List.of(new ModeloPdfInter());

	private LeitorPdf() {
	}

	static boolean ehPdf(byte[] conteudo) {
		var inicio = new String(conteudo, 0, Math.min(conteudo.length, 1024), StandardCharsets.ISO_8859_1);
		return inicio.contains("%PDF-");
	}

	static ArquivoFatura ler(byte[] conteudo, String senha) throws ArquivoInvalido {
		var texto = texto(conteudo, senha);
		for (var modelo : MODELOS) {
			if (modelo.reconhece(texto)) {
				return modelo.ler(texto);
			}
		}
		throw new ArquivoInvalido(BANCO_SEM_MODELO);
	}

	private static String texto(byte[] conteudo, String senha) throws ArquivoInvalido {
		var semSenha = senha == null || senha.isEmpty();
		try (var documento = Loader.loadPDF(conteudo, semSenha ? "" : senha)) {
			if (documento.getNumberOfPages() > MAXIMO_PAGINAS) {
				throw new ArquivoInvalido("Este PDF tem páginas demais para ser uma fatura.");
			}
			var extrator = new PDFTextStripper();
			extrator.setSortByPosition(true);
			var texto = extrator.getText(documento);
			if (texto.isBlank()) {
				throw new ArquivoInvalido(SEM_TEXTO);
			}
			return texto;
		}
		catch (InvalidPasswordException e) {
			throw semSenha ? new ArquivoInvalido(PEDE_SENHA, ArquivoInvalido.Codigo.SENHA_PDF)
					: new ArquivoInvalido(SENHA_INCORRETA, ArquivoInvalido.Codigo.SENHA_PDF_INCORRETA);
		}
		catch (IOException e) {
			throw new ArquivoInvalido("Não deu para abrir este PDF. Baixe a fatura de novo no app do banco.");
		}
	}

}
