package br.edu.simpi.persistencia;

import br.edu.simpi.solicitacao.dto.CriarSolicitacaoRequest;
import br.edu.simpi.solicitacao.dto.PaginaSolicitacoesResponse;
import br.edu.simpi.solicitacao.dto.PesquisarSolicitacoesRequest;
import br.edu.simpi.solicitacao.dto.ResumoSolicitacaoResponse;
import br.edu.simpi.solicitacao.enums.MotivoFalhaConsultaSolicitacao;
import br.edu.simpi.solicitacao.enums.StatusSolicitacao;
import br.edu.simpi.solicitacao.enums.VisaoSolicitacao;
import br.edu.simpi.solicitacao.service.ConsultaSolicitacaoService;
import br.edu.simpi.solicitacao.service.ExcecaoConsultaSolicitacao;
import br.edu.simpi.solicitacao.service.SolicitacaoService;
import br.edu.simpi.usuario.Usuario;
import br.edu.simpi.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static br.edu.simpi.solicitacao.enums.MotivoFalhaConsultaSolicitacao.*;
import static br.edu.simpi.solicitacao.enums.VisaoSolicitacao.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(initializers = ProtecaoBancoIntegracao.class)
@Transactional
class ConsultaSolicitacaoServiceIT {
    @Autowired private JdbcTemplate banco;
    @Autowired private EntityManager gerenciador;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private ConsultaSolicitacaoService consultas;
    @Autowired private SolicitacaoService operacoes;
    private DadosSolicitacoesTeste dados;
    private Usuario ator;
    private String prefixo;

    @BeforeEach
    void preparar() {
        dados = DadosSolicitacoesTeste.preparar(banco);
        ator = usuario(dados.solicitanteId());
        prefixo = DadosPatrimoniaisTeste.novoCodigo().substring(0, 12);
    }

    @Test
    void combinaBuscaStatusVisaoEPaginacaoComOrdenacaoPorDataEId() {
        Long antigo = inserir(StatusSolicitacao.CONCLUIDA, "-ANTIGO");
        Long primeiro = inserir(StatusSolicitacao.CONCLUIDA, "-PRIMEIRO");
        Long segundo = inserir(StatusSolicitacao.CONCLUIDA, "-SEGUNDO");
        inserir(StatusSolicitacao.RECUSADA, "-RECUSADO");
        banco.update("UPDATE solicitacao SET criada_em = criada_em - interval '1 day' WHERE id = ?", antigo);
        var pagina = pesquisar(prefixo.toUpperCase(), StatusSolicitacao.CONCLUIDA, CRIADAS_POR_MIM, 0, 2, ator);
        assertEquals(List.of(segundo, primeiro), ids(pagina));
        assertEquals(3, pagina.totalElementos());
        assertEquals(2, pagina.totalPaginas());
        assertEquals(List.of(antigo), ids(pesquisar(prefixo, StatusSolicitacao.CONCLUIDA, CRIADAS_POR_MIM, 1, 2, ator)));
        var alem = pesquisar(prefixo, StatusSolicitacao.CONCLUIDA, CRIADAS_POR_MIM, Integer.MAX_VALUE, 100, ator);
        assertTrue(alem.conteudo().isEmpty());
        assertEquals(3, alem.totalElementos());
        assertEquals(1, alem.totalPaginas());
        assertTrue(pesquisar(prefixo, StatusSolicitacao.APROVADA, DA_MINHA_AREA, 0, 20, ator).conteudo().isEmpty());
    }

    @Test
    void buscaNumeroOuCodigoIgnoraCaixaETrataCaracteresComoLiterais() {
        Long pedido = inserir(StatusSolicitacao.CANCELADA, "_%!'");
        inserir(StatusSolicitacao.RECUSADA, "-CONTROLE");
        assertEquals(List.of(pedido), ids(pesquisar("  " + prefixo.toUpperCase() + "_%!'  ", null, null, 0, 20, ator)));
        String numero = banco.queryForObject("SELECT numero FROM solicitacao WHERE id = ?", String.class, pedido);
        assertEquals(List.of(pedido), ids(pesquisar(numero.toLowerCase(), null, null, 0, 20, ator)));
        banco.update("UPDATE bem_patrimonial SET descricao = ?, plaqueta = ? WHERE codigo = ?",
                "Descricao exclusiva", "Plaqueta exclusiva", prefixo + "_%!'");
        assertEquals(0, pesquisar("Descricao exclusiva", null, null, 0, 20, ator).totalElementos());
        assertEquals(0, pesquisar("Plaqueta exclusiva", null, null, 0, 20, ator).totalElementos());
    }

    @Test
    void padroesValidacoesEPaginaVaziaRespeitamContrato() {
        var padrao = consultas.pesquisar(null, ator);
        assertEquals(0, padrao.pagina());
        assertEquals(20, padrao.tamanho());
        assertEquals(0, padrao.totalElementos());
        assertEquals(0, padrao.totalPaginas());
        assertEquals(padrao, pesquisar("   ", null, null, null, null, ator));
        for (int tamanho : List.of(0, -1, 101)) {
            falha(DADOS_INVALIDOS, () -> pesquisar(null, null, null, 0, tamanho, ator));
        }
        falha(DADOS_INVALIDOS, () -> pesquisar(null, null, null, -1, 20, ator));
        for (int tamanho : List.of(1, 100)) {
            assertEquals(tamanho, pesquisar(null, null, null, 0, tamanho, ator).tamanho());
        }
        for (Long id : Arrays.asList(null, 0L, -1L)) {
            falha(DADOS_INVALIDOS, () -> consultas.detalhar(id, ator));
        }
    }

    @Test
    void visibilidadeUsaTresSetoresPreservadosEAdminTemConsultaGlobal() {
        Long pedido = dados.inserirSolicitacao(banco, "CANCELADA");
        banco.execute("SET CONSTRAINTS fk_setor_gestor DEFERRED");
        var terceiro = DadosPatrimoniaisTeste.preparar(banco);
        banco.execute("SET CONSTRAINTS fk_setor_gestor IMMEDIATE");
        banco.update("UPDATE solicitacao SET setor_solicitante_id = ? WHERE id = ?", terceiro.setorId(), pedido);
        for (Long id : List.of(terceiro.gestorId(), dados.origem().gestorId(), dados.destino().gestorId())) {
            assertEquals(List.of(pedido), ids(consultas.pesquisar(null, usuario(id))));
            assertEquals(pedido, consultas.detalhar(pedido, usuario(id)).id());
        }
        banco.execute("SET CONSTRAINTS fk_setor_gestor DEFERRED");
        var externo = DadosPatrimoniaisTeste.preparar(banco);
        banco.execute("SET CONSTRAINTS fk_setor_gestor IMMEDIATE");
        Usuario administrador = usuario(externo.gestorId());
        banco.update("UPDATE bem_patrimonial SET setor_id = ? WHERE id = ?", externo.setorId(), dados.origem().bemId());
        assertEquals(0, consultas.pesquisar(null, administrador).totalElementos());
        var negada = falha(ACAO_NAO_PERMITIDA, () -> consultas.detalhar(pedido, administrador));
        assertEquals("Ação não permitida.", negada.getMessage());
        assertTrue(negada.getErros().isEmpty());
        falha(SOLICITACAO_NAO_ENCONTRADA, () -> consultas.detalhar(Long.MAX_VALUE, administrador));
        banco.update("UPDATE usuario SET perfil = 'ADMINISTRADOR' WHERE id = ?", administrador.getId());
        assertEquals(0, consultas.pesquisar(null, administrador).totalElementos());
        assertEquals(pedido, consultas.detalhar(pedido, administrador).id());
        String numero = banco.queryForObject("SELECT numero FROM solicitacao WHERE id = ?", String.class, pedido);
        assertEquals(List.of(pedido), ids(pesquisar(numero, null, TODAS, 0, 20, administrador)));
        assertEquals(List.of(), consultas.detalhar(pedido, administrador).acoesDisponiveis());
    }

    @Test
    void autoriaNaoSubstituiAcessoEVisoesExclusivasValidamPerfil() {
        inserir(StatusSolicitacao.CANCELADA, "-AUTORIA");
        falha(ACAO_NAO_PERMITIDA, () -> pesquisar(null, null, TODAS, 0, 20, ator));
        falha(ACAO_NAO_PERMITIDA, () -> pesquisar(null, null, AGUARDANDO_MINHA_APROVACAO, 0, 20, ator));
        banco.execute("SET CONSTRAINTS fk_setor_gestor DEFERRED");
        var externo = DadosPatrimoniaisTeste.preparar(banco);
        banco.execute("SET CONSTRAINTS fk_setor_gestor IMMEDIATE");
        banco.update("UPDATE usuario SET setor_id = ? WHERE id = ?", externo.setorId(), ator.getId());
        assertEquals(0, pesquisar(prefixo, null, CRIADAS_POR_MIM, 0, 20, ator).totalElementos());
        banco.update("UPDATE usuario SET perfil = 'ADMINISTRADOR' WHERE id = ?", ator.getId());
        assertEquals(1, pesquisar(prefixo, null, CRIADAS_POR_MIM, 0, 20, ator).totalElementos());
        falha(ACAO_NAO_PERMITIDA, () -> pesquisar(null, null, AGUARDANDO_MINHA_APROVACAO, 0, 20, ator));
    }

    @Test
    void filaDeAprovacaoEquivaleAEtapaDecidivelSemReatribuirGestorQueMudouDeSetor() {
        Long pedido = dados.inserirSolicitacao(banco, "AGUARDANDO_GESTOR_AREA");
        Long etapa = dados.inserirAprovacao(banco, pedido, "GESTOR_AREA");
        Usuario gestor = usuario(dados.origem().gestorId());
        assertEquals(List.of(pedido), ids(fila(gestor)));
        assertEquals(List.of("APROVAR", "RECUSAR"), consultas.detalhar(pedido, gestor).acoesDisponiveis());
        banco.update("UPDATE usuario SET setor_id = ? WHERE id = ?", dados.destino().setorId(), gestor.getId());
        assertTrue(fila(gestor).conteudo().isEmpty());
        assertEquals(0, fila(gestor).totalElementos());
        var detalhe = consultas.detalhar(pedido, gestor);
        assertEquals(pedido, detalhe.id());
        assertEquals(List.of(), detalhe.acoesDisponiveis());
        assertEquals(gestor.getId(), detalhe.aprovacoes().getFirst().aprovador().id());
        assertEquals(List.of(pedido), ids(consultas.pesquisar(null, gestor)));
        banco.update("UPDATE usuario SET setor_id = ? WHERE id = ?", dados.origem().setorId(), gestor.getId());
        banco.update("UPDATE solicitacao SET status = 'AGUARDANDO_GESTOR_RESPONSAVEL' WHERE id = ?", pedido);
        assertTrue(fila(gestor).conteudo().isEmpty());
        dados.inserirAprovacao(banco, pedido, "GESTOR_RESPONSAVEL");
        assertEquals(List.of(pedido), ids(fila(gestor)));
        banco.update("UPDATE aprovacao SET decisao = 'APROVADA', decidida_em = CURRENT_TIMESTAMP WHERE id = ?", etapa);
        assertEquals(1, fila(gestor).totalElementos());
        banco.update("UPDATE usuario SET perfil = 'COMUM' WHERE id = ?", gestor.getId());
        falha(ACAO_NAO_PERMITIDA, () -> fila(gestor));
    }

    @Test
    void filaExcluiOutraDesignacaoEDecisaoJaRegistrada() {
        Long pedido = dados.inserirSolicitacao(banco, "AGUARDANDO_GESTOR_AREA");
        Long etapa = dados.inserirAprovacao(banco, pedido, "GESTOR_AREA");
        Usuario gestor = usuario(dados.origem().gestorId());
        assertTrue(fila(usuario(dados.destino().gestorId())).conteudo().isEmpty());
        banco.update("UPDATE aprovacao SET decisao = 'APROVADA', decidida_em = CURRENT_TIMESTAMP WHERE id = ?", etapa);
        assertTrue(fila(gestor).conteudo().isEmpty());
        assertFalse(consultas.detalhar(pedido, gestor).acoesDisponiveis().contains("APROVAR"));
    }

    @Test
    void segundaEtapaExigeSetorDeOrigemMesmoQuandoGestorEstaNoSetorSolicitante() {
        Long pedido = dados.inserirSolicitacao(banco, "AGUARDANDO_GESTOR_RESPONSAVEL");
        dados.inserirAprovacao(banco, pedido, "GESTOR_RESPONSAVEL");
        banco.update("UPDATE solicitacao SET setor_solicitante_id = ? WHERE id = ?", dados.destino().setorId(), pedido);
        Usuario gestor = usuario(dados.origem().gestorId());
        assertEquals(List.of(pedido), ids(fila(gestor)));
        banco.update("UPDATE usuario SET setor_id = ? WHERE id = ?", dados.destino().setorId(), gestor.getId());
        assertTrue(fila(gestor).conteudo().isEmpty());
        assertEquals(List.of(pedido), ids(consultas.pesquisar(null, gestor)));
        assertEquals(List.of(), consultas.detalhar(pedido, gestor).acoesDisponiveis());
        banco.update("UPDATE usuario SET perfil = 'ADMINISTRADOR' WHERE id = ?", gestor.getId());
        assertEquals(List.of(), consultas.detalhar(pedido, gestor).acoesDisponiveis());
        falha(ACAO_NAO_PERMITIDA, () -> fila(gestor));
    }

    @Test
    void filaOperacionalCombinaEstadoComSetorDeOrigemOuDestino() {
        Long pedido = dados.inserirSolicitacao(banco, "APROVADA");
        assertEquals(List.of(pedido), ids(operacional(usuario(dados.origem().gestorId()))));
        assertTrue(operacional(usuario(dados.destino().gestorId())).conteudo().isEmpty());
        banco.update("UPDATE solicitacao SET status = 'EM_TRANSITO', enviada_em = CURRENT_TIMESTAMP, enviada_por_id = ? WHERE id = ?",
                dados.origem().gestorId(), pedido);
        assertTrue(operacional(usuario(dados.origem().gestorId())).conteudo().isEmpty());
        assertEquals(List.of(pedido), ids(operacional(usuario(dados.destino().gestorId()))));
        assertEquals(0, pesquisar(null, StatusSolicitacao.APROVADA, ENVIOS_E_RECEBIMENTOS, 0, 20,
                usuario(dados.destino().gestorId())).totalElementos());
    }

    @Test
    void detalheCompletoReutilizaAcoesCumulativasEHistoricoDoFluxoReal() {
        var entrada = new CriarSolicitacaoRequest(dados.origem().bemId(), dados.destino().setorId(),
                dados.destino().localizacaoId(), dados.destino().gestorId(), "Justificativa de consulta");
        var pedido = operacoes.criar(entrada, ator);
        assertEquals(pedido, consultas.detalhar(pedido.id(), ator));
        assertEquals(List.of("CANCELAR"), consultas.detalhar(pedido.id(), ator).acoesDisponiveis());
        assertNull(pedido.envio());
        assertNull(pedido.recebimento());
        Usuario gestor = usuario(dados.origem().gestorId());
        // Autoria e designação no mesmo ator tornam as ações cumulativas.
        banco.update("UPDATE solicitacao SET solicitante_id = ? WHERE id = ?", gestor.getId(), pedido.id());
        assertEquals(List.of("APROVAR", "RECUSAR", "CANCELAR"), consultas.detalhar(pedido.id(), gestor).acoesDisponiveis());
        var primeira = operacoes.aprovar(pedido.id(), pedido.aprovacoes().getFirst().id(), gestor);
        var aprovada = operacoes.aprovar(pedido.id(), primeira.aprovacoes().getLast().id(), gestor);
        assertEquals(aprovada, consultas.detalhar(pedido.id(), gestor));
        assertEquals(List.of("CANCELAR", "ENVIAR"), aprovada.acoesDisponiveis());
        var enviada = operacoes.enviar(pedido.id(), gestor);
        assertEquals(enviada, consultas.detalhar(pedido.id(), gestor));
        Usuario destino = usuario(dados.destino().gestorId());
        assertEquals(List.of("RECEBER"), consultas.detalhar(pedido.id(), destino).acoesDisponiveis());
        var recebida = operacoes.receber(pedido.id(), destino);
        assertEquals(recebida, consultas.detalhar(pedido.id(), destino));
        assertNotNull(recebida.envio());
        assertNotNull(recebida.recebimento());
        assertEquals(List.of(), recebida.acoesDisponiveis());
        banco.update("UPDATE evento_solicitacao SET ocorrido_em = ? WHERE solicitacao_id = ?",
                java.sql.Timestamp.from(pedido.criadaEm()), pedido.id());
        gerenciador.clear();
        var detalhe = consultas.detalhar(pedido.id(), destino);
        var idsEventos = detalhe.eventos().stream().map(evento -> evento.id()).toList();
        assertEquals(idsEventos.stream().sorted().toList(), idsEventos);
        assertEquals(List.of("GESTOR_AREA", "GESTOR_RESPONSAVEL"),
                detalhe.aprovacoes().stream().map(etapa -> etapa.etapa().name()).toList());
        assertEquals(dados.origem().setorId(), detalhe.origem().setor().id());
        assertEquals(dados.destino().setorId(), detalhe.destino().setor().id());
    }

    @Test
    void todosEstadosSaoFiltraveisEFinaisNaoOferecemAcoes() {
        for (var estado : StatusSolicitacao.values()) {
            Long pedido = inserir(estado, "-" + estado.ordinal());
            assertEquals(List.of(pedido), ids(pesquisar(prefixo, estado, DA_MINHA_AREA, 0, 20, ator)));
            if (List.of(StatusSolicitacao.CANCELADA, StatusSolicitacao.RECUSADA, StatusSolicitacao.CONCLUIDA).contains(estado)) {
                assertEquals(List.of(), consultas.detalhar(pedido, ator).acoesDisponiveis());
            }
        }
        banco.execute("SET CONSTRAINTS fk_setor_gestor DEFERRED");
        var externa = DadosSolicitacoesTeste.preparar(banco);
        banco.execute("SET CONSTRAINTS fk_setor_gestor IMMEDIATE");
        externa.inserirSolicitacao(banco, "CANCELADA");
        assertEquals(StatusSolicitacao.values().length,
                pesquisar(prefixo, null, DA_MINHA_AREA, 0, 20, ator).totalElementos());
    }

    @Test
    void resumoPreservaSetorSolicitanteMesmoAposMudancaDeSetorEInativacaoCadastral() {
        Long pedido = inserir(StatusSolicitacao.CANCELADA, "-HISTORICO");
        banco.execute("SET CONSTRAINTS fk_setor_gestor DEFERRED");
        var destinoAtual = DadosPatrimoniaisTeste.preparar(banco);
        banco.execute("SET CONSTRAINTS fk_setor_gestor IMMEDIATE");
        banco.update("UPDATE usuario SET setor_id = ? WHERE id = ?", destinoAtual.setorId(), ator.getId());
        banco.update("UPDATE setor SET ativo = false WHERE id = ?", dados.origem().setorId());
        Usuario gestor = usuario(dados.origem().gestorId());
        var resumo = pesquisar(prefixo, null, DA_MINHA_AREA, 0, 20, gestor).conteudo().getFirst();
        assertEquals(dados.origem().setorId(), resumo.solicitante().setor().id());
        assertEquals(dados.origem().setorId(), consultas.detalhar(pedido, gestor).solicitante().setor().id());
        assertEquals(List.of("id", "numero", "criadaEm", "status", "bem", "solicitante", "setorOrigem", "setorDestino"),
                Arrays.stream(ResumoSolicitacaoResponse.class.getRecordComponents()).map(campo -> campo.getName()).toList());
        assertEquals(List.of("id", "codigo", "descricao"), Arrays.stream(ResumoSolicitacaoResponse.BemResumoResponse.class
                .getRecordComponents()).map(campo -> campo.getName()).toList());
    }

    @Test
    void atorPersistidoAtivoEObrigatorioNasDuasConsultas() {
        Usuario semId = new Usuario("Ator sintético", DadosPatrimoniaisTeste.novoEmail(),
                DadosPatrimoniaisTeste.HASH_SINTETICO, ator.getPerfil(), ator.getSetor());
        Usuario inexistente = new Usuario("Ator inexistente", DadosPatrimoniaisTeste.novoEmail(),
                DadosPatrimoniaisTeste.HASH_SINTETICO, ator.getPerfil(), ator.getSetor());
        ReflectionTestUtils.setField(inexistente, "id", Long.MAX_VALUE);
        for (Usuario invalido : Arrays.asList(null, semId, inexistente)) {
            falha(NAO_AUTENTICADO, () -> consultas.pesquisar(null, invalido));
            falha(NAO_AUTENTICADO, () -> consultas.detalhar(Long.MAX_VALUE, invalido));
        }
        banco.update("UPDATE usuario SET ativo = false WHERE id = ?", ator.getId());
        falha(NAO_AUTENTICADO, () -> consultas.pesquisar(null, ator));
        falha(NAO_AUTENTICADO, () -> consultas.detalhar(Long.MAX_VALUE, ator));
    }

    private Long inserir(StatusSolicitacao estado, String sufixo) {
        banco.execute("SET CONSTRAINTS fk_setor_gestor DEFERRED");
        var novos = DadosSolicitacoesTeste.preparar(banco);
        banco.execute("SET CONSTRAINTS fk_setor_gestor IMMEDIATE");
        banco.update("UPDATE bem_patrimonial SET codigo = ? WHERE id = ?", prefixo + sufixo, novos.origem().bemId());
        Long pedido = novos.inserirSolicitacao(banco, estado.name());
        banco.update("UPDATE solicitacao SET solicitante_id = ?, setor_solicitante_id = ? WHERE id = ?",
                ator.getId(), dados.origem().setorId(), pedido);
        return pedido;
    }

    private Usuario usuario(Long id) { return usuarios.findById(id).orElseThrow(); }

    private PaginaSolicitacoesResponse fila(Usuario usuario) {
        return pesquisar(null, null, AGUARDANDO_MINHA_APROVACAO, 0, 20, usuario);
    }

    private PaginaSolicitacoesResponse operacional(Usuario usuario) {
        return pesquisar(null, null, ENVIOS_E_RECEBIMENTOS, 0, 20, usuario);
    }

    private PaginaSolicitacoesResponse pesquisar(String busca, StatusSolicitacao status,
            VisaoSolicitacao visao, Integer pagina, Integer tamanho, Usuario usuario) {
        return consultas.pesquisar(new PesquisarSolicitacoesRequest(busca, status, visao, pagina, tamanho), usuario);
    }

    private List<Long> ids(PaginaSolicitacoesResponse pagina) {
        return pagina.conteudo().stream().map(ResumoSolicitacaoResponse::id).toList();
    }

    private ExcecaoConsultaSolicitacao falha(MotivoFalhaConsultaSolicitacao motivo, Supplier<?> consulta) {
        var excecao = assertThrows(ExcecaoConsultaSolicitacao.class, consulta::get);
        assertEquals(motivo, excecao.getMotivo());
        return excecao;
    }
}
