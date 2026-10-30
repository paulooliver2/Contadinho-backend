# Contadinho — API

API do Contadinho, app pessoal de finanças que divide a fatura do cartão com a família e controla quem já pagou.

Java 21, Spring Boot 4, monólito modular (Spring Modulith), PostgreSQL 17 com Flyway, autenticação com JWT.

## Pré-requisitos

- JDK 21 (`JAVA_HOME` apontando para ele).
- Docker em execução: banco local e testes de integração.

## Rodar

```sh
./mvnw spring-boot:run      # sobe o Postgres do compose.yaml e a API em http://localhost:8080, perfil dev
./mvnw verify               # testes com Postgres real (Testcontainers), segurança, contrato e módulos
```

Em produção, `JWT_SECRET` (32 bytes ou mais) e o banco vêm de variáveis de ambiente. O perfil `dev` só é ligado pelo `spring-boot:run`.

## Organização

- Cada pasta em `br.com.contadinho` é um módulo: `conta`, `pessoas`, `categorias`, `cartoes`, `gastos`, `receitas`, `receber`, `resumo` e `avisos`, mais o `comum` com a infraestrutura. O `ModulosTests` falha se um módulo usar as partes internas de outro; a documentação dos módulos sai em `target/spring-modulith-docs`.
- Migrações em `src/main/resources/db/migration`. O esquema só muda por migração nova; nunca edite uma que já rodou.
- Dinheiro em `NUMERIC(12,2)` e `BigDecimal`; datas de negócio no fuso America/Sao_Paulo (injete o `Clock`).
- Totais e saldos nunca são guardados: sempre calculados a partir dos lançamentos.

## Contrato da API

`openapi.json`, na raiz, é o contrato que o app usa para gerar o cliente TypeScript. Ele é regravado pelos testes: mudou a API, rode `./mvnw verify` e faça commit do `openapi.json` junto. O CI falha se ele estiver desatualizado.
