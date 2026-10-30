package br.com.contadinho.cartoes;

/**
 * Porta de entrada da importação: decide pelo conteúdo se o arquivo é PDF (HU-39) ou OFX (HU-06).
 */
final class LeitorFatura {

	private LeitorFatura() {
	}

	/** {@code senha} só é usada quando o arquivo é um PDF protegido. */
	static ArquivoFatura ler(String nomeArquivo, byte[] conteudo, String senha) throws ArquivoInvalido {
		return LeitorPdf.ehPdf(conteudo) ? LeitorPdf.ler(conteudo, senha) : LeitorOfx.ler(nomeArquivo, conteudo);
	}

}
