package br.edu.simpi.persistencia;

import br.edu.simpi.consulta.dto.BemPatrimonialResponse;
import br.edu.simpi.consulta.dto.DetalheBemPatrimonialResponse;
import br.edu.simpi.consulta.dto.PaginaBensResponse;
import br.edu.simpi.consulta.dto.PesquisarBensRequest;
import br.edu.simpi.consulta.enums.MotivoFalhaConsultaPatrimonial;
import br.edu.simpi.consulta.repository.ConsultaBemRepository;
import br.edu.simpi.consulta.service.ConsultaPatrimonialService;
import br.edu.simpi.consulta.service.ExcecaoConsultaPatrimonial;
import br.edu.simpi.solicitacao.enums.StatusSolicitacao;
import br.edu.simpi.solicitacao.repository.SolicitacaoRepository;
import br.edu.simpi.solicitacao.service.RegrasVisibilidadeSolicitacao;
import br.edu.simpi.usuario.PerfilUsuario;
import br.edu.simpi.usuario.Usuario;
import br.edu.simpi.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static br.edu.simpi.consulta.enums.MotivoFalhaConsultaPatrimonial.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.cache.use_second_level_cache=false",
        "spring.jpa.properties.hibernate.cache.use_query_cache=false"
})
@ContextConfiguration(initializers = ProtecaoBancoIntegracao.class)
@Transactional
class ConsultaBensServiceIT {
    private final JdbcTemplate banco;
    private final EntityManager gerenciador;
    private final SessionFactory fabrica;
    private final ConsultaPatrimonialService consultas;
    private final ConsultaBemRepository bens;
    private final UsuarioRepository usuarios;
    private final SolicitacaoRepository solicitacoes;
    private DadosSolicitacoesTeste dados;
    private Usuario ator;
    private String prefixo;

    @Autowired
    ConsultaBensServiceIT(JdbcTemplate banco, EntityManager gerenciador, EntityManagerFactory fabrica,
            ConsultaPatrimonialService consultas, ConsultaBemRepository bens,
            UsuarioRepository usuarios, SolicitacaoRepository solicitacoes) {
        this.banco = banco;
        this.gerenciador = gerenciador;
        this.fabrica = fabrica.unwrap(SessionFactory.class);
        this.consultas = consultas;
        this.bens = bens;
        this.usuarios = usuarios;
        this.solicitacoes = solicitacoes;
    }

    @BeforeEach
    void preparar() {
        dados = DadosSolicitacoesTeste.preparar(banco);
        ator = usuarios.findById(dados.solicitanteId()).orElseThrow();
        prefixo = DadosPatrimoniaisTeste.novoCodigo().substring(0, 12);
        banco.update("UPDATE bem_patrimonial SET codigo = ?, descricao = 'Descrição base', plaqueta = NULL WHERE id = ?",
                prefixo + "-00", dados.origem().bemId());
        banco.update("UPDATE bem_patrimonial SET codigo = ? WHERE id = ?", prefixo + "-99", dados.destino().bemId());
    }

    @Test
    void pesquisaCombinaBuscaSetorLocalizacaoEDisponibilidadeComTotaisCoerentes() {
        Long selecionado = inserirBem("-01", "Placa alvo", "Texto procurado", dados.origem().setorId(), dados.destino().localizacaoId(), true);
        inserirBem("-02", null, "Texto procurado", dados.origem().setorId(), dados.origem().localizacaoId(), true);
        inserirBem("-03", null, "Texto procurado", dados.destino().setorId(), dados.destino().localizacaoId(), true);
        inserirBem("-04", null, "Texto procurado", dados.origem().setorId(), dados.destino().localizacaoId(), false);
        var resposta = consultas.pesquisarBens(new PesquisarBensRequest("  TEXTO PROCURADO  ", dados.origem().setorId(),
                dados.destino().localizacaoId(), true, 0, 1), ator);
        assertEquals(1, resposta.totalElementos());
        assertEquals(1, resposta.totalPaginas());
        assertEquals(List.of(selecionado), resposta.conteudo().stream().map(BemPatrimonialResponse::id).toList());
        var indisponivel = consultas.pesquisarBens(new PesquisarBensRequest("Texto procurado", dados.origem().setorId(),
                dados.destino().localizacaoId(), false, 0, 20), ator);
        assertEquals(1, indisponivel.totalElementos());
        assertFalse(indisponivel.conteudo().getFirst().ativo());
        assertFalse(indisponivel.conteudo().getFirst().disponivelParaSolicitacao());
    }

    @Test
    void buscaParcialIgnoraCaixaNosTresCamposETrataCaracteresLiteralmente() {
        Long especial = inserirBem("-CODIGOALVO", "TAG_%!'", "Descrição Singular", dados.origem().setorId(), dados.origem().localizacaoId(), true);
        inserirBem("-CONTROLE", "TAGxyz", "Descrição comum", dados.origem().setorId(), dados.origem().localizacaoId(), true);
        for (String busca : List.of("codigoalvo", "singular", "TAG_%!'", "%", "_", "!", "'")) {
            var resposta = consultas.pesquisarBens(new PesquisarBensRequest("  " + busca + "  ", dados.origem().setorId(),
                    null, null, null, null), ator);
            assertEquals(List.of(especial), resposta.conteudo().stream().map(BemPatrimonialResponse::id).toList());
            assertEquals(1, resposta.totalElementos());
        }
        var vazia = consultas.pesquisarBens(new PesquisarBensRequest("' OR 1=1 --", dados.origem().setorId(), null, null, 0, 20), ator);
        assertEquals(List.of(), vazia.conteudo());
        assertEquals(0, vazia.totalElementos());
    }

    @Test
    void buscaVaziaEquivaleAAusenciaEPlaquetaNulaNaoImpedeConsulta() {
        var semBusca = consultas.pesquisarBens(new PesquisarBensRequest(null, dados.origem().setorId(), null, null, null, null), ator);
        var vazia = consultas.pesquisarBens(new PesquisarBensRequest(" \t\n ", dados.origem().setorId(), null, null, null, null), ator);
        assertEquals(semBusca, vazia);
        assertNull(vazia.conteudo().getFirst().plaqueta());
        assertEquals(0, vazia.pagina());
        assertEquals(20, vazia.tamanho());
        var padrao = consultas.pesquisarBens(null, ator);
        assertEquals(0, padrao.pagina());
        assertEquals(20, padrao.tamanho());
    }

    @Test
    void paginacaoOrdenaPorCodigoEIdEPreservaTotaisAlemDoFim() {
        Long segundo = inserirBem("-20", null, "Segundo", dados.origem().setorId(), dados.origem().localizacaoId(), true);
        Long primeiro = inserirBem("-10", null, "Primeiro", dados.origem().setorId(), dados.origem().localizacaoId(), true);
        var pagina = consultas.pesquisarBens(filtros(1, 2), ator);
        assertEquals(3, pagina.totalElementos());
        assertEquals(2, pagina.totalPaginas());
        assertEquals(List.of(segundo), pagina.conteudo().stream().map(BemPatrimonialResponse::id).toList());
        var inicio = consultas.pesquisarBens(filtros(0, 2), ator);
        assertEquals(List.of(dados.origem().bemId(), primeiro), inicio.conteudo().stream().map(BemPatrimonialResponse::id).toList());
        for (int numeroPagina : List.of(2, Integer.MAX_VALUE)) {
            var fim = consultas.pesquisarBens(filtros(numeroPagina, 100), ator);
            assertEquals(numeroPagina, fim.pagina());
            assertEquals(3, fim.totalElementos());
            assertEquals(1, fim.totalPaginas());
            assertEquals(List.of(), fim.conteudo());
        }
        // Exercita também a vinculação SQL de um OFFSET maior que int, sem o atalho do service.
        assertEquals(List.of(), bens.pesquisar(filtros(0, 100), (long) Integer.MAX_VALUE + 1));
    }

    @Test
    void validaIdsPaginacaoEReferenciasInexistentesMesmoSemResultados() {
        for (Long id : new Long[] {0L, -1L}) {
            verificarFalha(DADOS_INVALIDOS, () -> consultas.pesquisarBens(new PesquisarBensRequest(null, id, null, null, 0, 20), ator));
            verificarFalha(DADOS_INVALIDOS, () -> consultas.pesquisarBens(new PesquisarBensRequest(null, null, id, null, 0, 20), ator));
        }
        verificarFalha(DADOS_INVALIDOS, () -> consultas.pesquisarBens(filtros(-1, 20), ator));
        for (int tamanho : List.of(-1, 0, 101)) {
            verificarFalha(DADOS_INVALIDOS, () -> consultas.pesquisarBens(filtros(0, tamanho), ator));
        }
        verificarFalha(RECURSO_NAO_ENCONTRADO, () -> consultas.pesquisarBens(
                new PesquisarBensRequest("Inexistente", Long.MAX_VALUE, null, null, 0, 20), ator));
        verificarFalha(RECURSO_NAO_ENCONTRADO, () -> consultas.pesquisarBens(
                new PesquisarBensRequest("Inexistente", null, Long.MAX_VALUE, null, 0, 20), ator));
        for (Long id : new Long[] {null, 0L, -1L}) {
            verificarFalha(DADOS_INVALIDOS, () -> consultas.detalharBem(id, ator));
        }
        verificarFalha(RECURSO_NAO_ENCONTRADO, () -> consultas.detalharBem(Long.MAX_VALUE, ator));
    }

    @Test
    void filtrosEDetalheAceitamCadastrosInativosSemOcultarBem() {
        banco.update("UPDATE setor SET ativo = false WHERE id = ?", dados.origem().setorId());
        banco.update("UPDATE localizacao SET ativo = false WHERE id = ?", dados.origem().localizacaoId());
        banco.update("UPDATE usuario SET ativo = false WHERE id = ?", dados.origem().gestorId());
        banco.update("UPDATE bem_patrimonial SET ativo = false WHERE id = ?", dados.origem().bemId());
        var resposta = consultas.pesquisarBens(new PesquisarBensRequest(prefixo, dados.origem().setorId(),
                dados.origem().localizacaoId(), false, 0, 20), ator);
        assertEquals(1, resposta.totalElementos());
        var detalhe = consultas.detalharBem(dados.origem().bemId(), ator);
        assertFalse(detalhe.ativo());
        assertFalse(detalhe.disponivelParaSolicitacao());
        assertEquals(dados.origem().setorId(), detalhe.setor().id());
        assertEquals(dados.origem().localizacaoId(), detalhe.localizacao().id());
        assertEquals(dados.origem().gestorId(), detalhe.responsavel().id());
    }

    @Test
    void disponibilidadeUsaTodosEstadosAtivosEFinaisEASituacaoDoBem() {
        Long pedidoId = dados.inserirSolicitacao(banco, "AGUARDANDO_GESTOR_AREA");
        List<StatusSolicitacao> ativos = List.of(StatusSolicitacao.AGUARDANDO_GESTOR_AREA,
                StatusSolicitacao.AGUARDANDO_GESTOR_RESPONSAVEL, StatusSolicitacao.APROVADA, StatusSolicitacao.EM_TRANSITO);
        for (StatusSolicitacao estado : StatusSolicitacao.values()) {
            banco.update("UPDATE solicitacao SET status = ? WHERE id = ?", estado.name(), pedidoId);
            boolean bloqueado = ativos.contains(estado);
            var detalhe = consultas.detalharBem(dados.origem().bemId(), ator);
            assertEquals(!bloqueado, detalhe.disponivelParaSolicitacao());
            assertEquals(bloqueado, detalhe.solicitacaoEmAndamento() != null);
            assertEquals(bloqueado ? 1 : 0, pesquisarDisponibilidade(false).totalElementos());
            assertEquals(bloqueado ? 0 : 1, pesquisarDisponibilidade(true).totalElementos());
            banco.update("UPDATE bem_patrimonial SET ativo = false WHERE id = ?", dados.origem().bemId());
            assertFalse(consultas.detalharBem(dados.origem().bemId(), ator).disponivelParaSolicitacao());
            assertEquals(1, pesquisarDisponibilidade(false).totalElementos());
            banco.update("UPDATE bem_patrimonial SET ativo = true WHERE id = ?", dados.origem().bemId());
        }
    }

    @Test
    void pedidoOcultoNaoExpoeDadosMasContinuaBloqueandoDisponibilidade() {
        Long pedido = dados.inserirSolicitacao(banco, "APROVADA");
        String numero = banco.queryForObject("SELECT numero FROM solicitacao WHERE id = ?", String.class, pedido);
        var externo = prepararOutroSetor();
        Usuario outro = usuarios.findById(externo.gestorId()).orElseThrow();
        // Alterar o cadastro atual do bem não concede acesso ao pedido histórico.
        banco.update("UPDATE bem_patrimonial SET setor_id = ? WHERE id = ?", externo.setorId(), dados.origem().bemId());
        var detalhe = consultas.detalharBem(dados.origem().bemId(), outro);
        assertNull(detalhe.solicitacaoEmAndamento());
        assertFalse(detalhe.disponivelParaSolicitacao());
        assertFalse(detalhe.toString().contains(numero));
        var lista = consultas.pesquisarBens(new PesquisarBensRequest(prefixo, externo.setorId(), null, false, 0, 20), outro);
        assertEquals(1, lista.totalElementos());
        assertFalse(lista.toString().contains(numero));
        assertFalse(campos(BemPatrimonialResponse.class).contains("solicitacaoEmAndamento"));
        assertEquals(0, consultas.pesquisarBens(new PesquisarBensRequest(prefixo, externo.setorId(), null, true, 0, 20), outro).totalElementos());
        banco.update("UPDATE usuario SET perfil = 'ADMINISTRADOR' WHERE id = ?", outro.getId());
        var administrador = consultas.detalharBem(dados.origem().bemId(), outro);
        assertEquals(pedido, administrador.solicitacaoEmAndamento().id());
        assertEquals(numero, administrador.solicitacaoEmAndamento().numero());
    }

    @Test
    void visibilidadePorIdsProjetadosPreservaRegraDosTresSetoresHistoricos() {
        var terceiro = prepararOutroSetor();
        Long pedido = dados.inserirSolicitacao(banco, "EM_TRANSITO");
        banco.update("UPDATE solicitacao SET setor_solicitante_id = ? WHERE id = ?", terceiro.setorId(), pedido);
        var solicitacao = solicitacoes.findById(pedido).orElseThrow();
        for (Long usuarioId : List.of(dados.origem().gestorId(), dados.destino().gestorId(), terceiro.gestorId())) {
            Usuario participante = usuarios.findById(usuarioId).orElseThrow();
            assertTrue(RegrasVisibilidadeSolicitacao.temAcesso(solicitacao, participante));
            assertEquals(RegrasVisibilidadeSolicitacao.temAcesso(solicitacao, participante),
                    RegrasVisibilidadeSolicitacao.temAcesso(participante, terceiro.setorId(), dados.origem().setorId(), dados.destino().setorId()));
            assertEquals(pedido, consultas.detalharBem(dados.origem().bemId(), participante).solicitacaoEmAndamento().id());
        }
        banco.update("UPDATE usuario SET setor_id = ? WHERE id = ?", prepararOutroSetor().setorId(), ator.getId());
        assertNull(consultas.detalharBem(dados.origem().bemId(), ator).solicitacaoEmAndamento());
        assertFalse(RegrasVisibilidadeSolicitacao.temAcesso(solicitacao, ator));
        assertFalse(RegrasVisibilidadeSolicitacao.temAcesso(ator, terceiro.setorId(), dados.origem().setorId(), dados.destino().setorId()));
    }

    @Test
    void dtoDetalhadoMantemFormatoPlanoSemCamposDeAutenticacao() {
        var detalhe = consultas.detalharBem(dados.origem().bemId(), ator);
        assertEquals(Set.of("id", "codigo", "plaqueta", "descricao", "setor", "localizacao", "responsavel", "ativo",
                "disponivelParaSolicitacao", "solicitacaoEmAndamento"), campos(DetalheBemPatrimonialResponse.class));
        assertEquals(Set.of("id", "nome"), campos(detalhe.setor().getClass()));
        assertEquals(Set.of("id", "nome"), campos(detalhe.localizacao().getClass()));
        assertEquals(Set.of("id", "nome"), campos(detalhe.responsavel().getClass()));
        assertEquals(Set.of("id", "numero"), campos(DetalheBemPatrimonialResponse.SolicitacaoEmAndamentoResponse.class));
        assertNull(detalhe.solicitacaoEmAndamento());
    }

    @Test
    void atorPersistidoAtivoEObrigatorioTambemNaPesquisaEDetalhe() {
        verificarAtorRejeitado(null);
        Usuario inexistente = new Usuario("Ator sintético", "ator@example.invalid", DadosPatrimoniaisTeste.HASH_SINTETICO,
                PerfilUsuario.COMUM, ator.getSetor());
        verificarAtorRejeitado(inexistente);
        ReflectionTestUtils.setField(inexistente, "id", Long.MAX_VALUE);
        verificarAtorRejeitado(inexistente);
        banco.update("UPDATE usuario SET ativo = false WHERE id = ?", ator.getId());
        verificarAtorRejeitado(ator);
    }

    @Test
    void pesquisaNaoAumentaConsultasPorItemComContextoFrioECachesDesativados() {
        for (int indice = 1; indice <= 24; indice++) {
            inserirBem("-" + String.format("%02d", indice), null, "Item sintético", dados.origem().setorId(), dados.origem().localizacaoId(), true);
        }
        long uma = medirConsultas(() -> {
            var resposta = consultas.pesquisarBens(filtros(0, 1), ator);
            assertEquals(1, resposta.conteudo().size());
            assertEquals(25, resposta.totalElementos());
            return resposta;
        });
        long varias = medirConsultas(() -> {
            var resposta = consultas.pesquisarBens(filtros(0, 25), ator);
            assertEquals(25, resposta.conteudo().size());
            assertEquals(25, resposta.totalElementos());
            return resposta;
        });
        assertTrue(uma > 0);
        assertEquals(uma, varias);
    }

    @Test
    void selecaoDeUsuariosNaoAumentaConsultasPorItemComContextoFrio() {
        long uma = medirConsultas(() -> {
            var resposta = consultas.listarUsuarios(dados.destino().setorId(), ator);
            assertEquals(1, resposta.size());
            return resposta;
        });
        for (int indice = 0; indice < 20; indice++) {
            banco.update("""
                    INSERT INTO usuario (nome, email, senha_hash, perfil, setor_id)
                    VALUES ('Usuário sintético', ?, ?, 'COMUM', ?)
                    """, DadosPatrimoniaisTeste.novoEmail(), DadosPatrimoniaisTeste.HASH_SINTETICO, dados.destino().setorId());
        }
        long varias = medirConsultas(() -> {
            var resposta = consultas.listarUsuarios(dados.destino().setorId(), ator);
            assertEquals(21, resposta.size());
            return resposta;
        });
        assertTrue(uma > 0);
        assertEquals(uma, varias);
    }

    private long medirConsultas(Supplier<?> operacao) {
        gerenciador.flush();
        gerenciador.clear();
        fabrica.getCache().evictAll();
        var estatisticas = fabrica.getStatistics();
        assertTrue(estatisticas.isStatisticsEnabled());
        long antes = estatisticas.getPrepareStatementCount();
        operacao.get();
        return estatisticas.getPrepareStatementCount() - antes;
    }

    private PaginaBensResponse pesquisarDisponibilidade(boolean disponivel) {
        return consultas.pesquisarBens(new PesquisarBensRequest(prefixo, dados.origem().setorId(), null, disponivel, 0, 20), ator);
    }

    private PesquisarBensRequest filtros(int pagina, int tamanho) {
        return new PesquisarBensRequest(prefixo, dados.origem().setorId(), null, null, pagina, tamanho);
    }

    private Long inserirBem(String sufixo, String plaqueta, String descricao, Long setor, Long localizacao, boolean ativo) {
        return banco.queryForObject("""
                INSERT INTO bem_patrimonial (codigo, plaqueta, descricao, setor_id, localizacao_id, responsavel_id, ativo)
                VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, prefixo + sufixo, plaqueta, descricao, setor, localizacao, dados.origem().gestorId(), ativo);
    }

    private DadosPatrimoniaisTeste prepararOutroSetor() {
        banco.execute("SET CONSTRAINTS fk_setor_gestor DEFERRED");
        var outro = DadosPatrimoniaisTeste.preparar(banco);
        banco.execute("SET CONSTRAINTS fk_setor_gestor IMMEDIATE");
        return outro;
    }

    private void verificarAtorRejeitado(Usuario usuario) {
        verificarFalha(NAO_AUTENTICADO, () -> consultas.pesquisarBens(filtros(0, 20), usuario));
        verificarFalha(NAO_AUTENTICADO, () -> consultas.detalharBem(dados.origem().bemId(), usuario));
    }

    private ExcecaoConsultaPatrimonial verificarFalha(MotivoFalhaConsultaPatrimonial motivo, Runnable operacao) {
        var falha = assertThrows(ExcecaoConsultaPatrimonial.class, operacao::run);
        assertEquals(motivo, falha.getMotivo());
        return falha;
    }

    private Set<String> campos(Class<?> tipo) {
        return Arrays.stream(tipo.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).collect(Collectors.toSet());
    }
}
