package br.com.contadinho.cartoes.repository;

import java.util.UUID;

import br.com.contadinho.cartoes.domain.Importacao;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportacaoRepositorio extends JpaRepository<Importacao, UUID> {

}
