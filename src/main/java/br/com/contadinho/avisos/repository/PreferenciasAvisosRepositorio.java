package br.com.contadinho.avisos.repository;

import java.util.UUID;

import br.com.contadinho.avisos.domain.PreferenciasAvisos;

import org.springframework.data.jpa.repository.JpaRepository;

/** A chave é o próprio usuário dono (RNF-01). */
public interface PreferenciasAvisosRepositorio extends JpaRepository<PreferenciasAvisos, UUID> {

}
