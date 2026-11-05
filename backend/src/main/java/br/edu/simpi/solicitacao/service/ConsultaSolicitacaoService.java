package br.edu.simpi.solicitacao.service;

import br.edu.simpi.solicitacao.dto.PaginaSolicitacoesResponse;
import br.edu.simpi.solicitacao.dto.PesquisarSolicitacoesRequest;
import br.edu.simpi.solicitacao.dto.ResumoSolicitacaoResponse;
import br.edu.simpi.solicitacao.dto.SolicitacaoResponse;
import br.edu.simpi.solicitacao.enums.VisaoSolicitacao;
import br.edu.simpi.solicitacao.repository.AprovacaoRepository;
import br.edu.simpi.solicitacao.repository.ConsultaSolicitacaoRepository;
import br.edu.simpi.solicitacao.repository.EventoSolicitacaoRepository;
import br.edu.simpi.solicitacao.repository.SolicitacaoRepository;
import br.edu.simpi.usuario.PerfilUsuario;
import br.edu.simpi.usuario.Usuario;
import br.edu.simpi.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static br.edu.simpi.solicitacao.enums.MotivoFalhaConsultaSolicitacao.*;

@Service
@Transactional(readOnly = true)
public class ConsultaSolicitacaoService {
    private final UsuarioRepository usuarios;
    private final SolicitacaoRepository solicitacoes;
    private final ConsultaSolicitacaoRepository consultas;
    private final AprovacaoRepository aprovacoes;
    private final EventoSolicitacaoRepository eventos;
    private final SolicitacaoMapper mapeador;
    private final EntityManager gerenciador;

    public ConsultaSolicitacaoService(UsuarioRepository usuarios, SolicitacaoRepository solicitacoes,
            ConsultaSolicitacaoRepository consultas, AprovacaoRepository aprovacoes,
            EventoSolicitacaoRepository eventos, SolicitacaoMapper mapeador, EntityManager gerenciador) {
        this.usuarios = usuarios;
        this.solicitacoes = solicitacoes;
        this.consultas = consultas;
        this.aprovacoes = aprovacoes;
        this.eventos = eventos;
        this.mapeador = mapeador;
        this.gerenciador = gerenciador;
    }

    public PaginaSolicitacoesResponse pesquisar(PesquisarSolicitacoesRequest filtros, Usuario ator) {
        Usuario atual = recarregarAtor(ator);
        var normalizados = normalizar(filtros);
        if ((normalizados.visao() == VisaoSolicitacao.TODAS && atual.getPerfil() != PerfilUsuario.ADMINISTRADOR)
                || (normalizados.visao() == VisaoSolicitacao.AGUARDANDO_MINHA_APROVACAO
                    && atual.getPerfil() != PerfilUsuario.GESTOR)) {
            throw acessoNegado();
        }
        long total = consultas.contar(normalizados, atual);
        long deslocamento = (long) normalizados.pagina() * normalizados.tamanho();
        List<ResumoSolicitacaoResponse> conteudo = deslocamento >= total ? List.of()
                : consultas.pesquisar(normalizados, atual, deslocamento).stream().map(mapeador::mapearResumo).toList();
        long totalPaginas = total / normalizados.tamanho() + (total % normalizados.tamanho() == 0 ? 0 : 1);
        return new PaginaSolicitacoesResponse(conteudo, normalizados.pagina(), normalizados.tamanho(), total, totalPaginas);
    }

    public SolicitacaoResponse detalhar(Long solicitacaoId, Usuario ator) {
        Usuario atual = recarregarAtor(ator);
        if (solicitacaoId == null || solicitacaoId <= 0) {
            throw new ExcecaoConsultaSolicitacao(DADOS_INVALIDOS, "Revise os campos informados.",
                    Map.of("solicitacaoId", "Informe um identificador positivo."));
        }
        var solicitacao = solicitacoes.findById(solicitacaoId).orElseThrow(() ->
                new ExcecaoConsultaSolicitacao(SOLICITACAO_NAO_ENCONTRADA, "Solicitação não encontrada.", Map.of()));
        gerenciador.refresh(solicitacao);
        if (!RegrasVisibilidadeSolicitacao.temAcesso(solicitacao, atual)) {
            throw acessoNegado();
        }
        var etapas = aprovacoes.buscarPorSolicitacao(solicitacaoId);
        etapas.forEach(gerenciador::refresh);
        return mapeador.mapear(solicitacao, etapas, eventos.buscarPorSolicitacao(solicitacaoId), atual);
    }

    private PesquisarSolicitacoesRequest normalizar(PesquisarSolicitacoesRequest filtros) {
        if (filtros == null) {
            return new PesquisarSolicitacoesRequest(null, null, VisaoSolicitacao.DA_MINHA_AREA, 0, 20);
        }
        int pagina = filtros.pagina() == null ? 0 : filtros.pagina();
        int tamanho = filtros.tamanho() == null ? 20 : filtros.tamanho();
        Map<String, String> erros = new LinkedHashMap<>();
        if (pagina < 0) {
            erros.put("pagina", "Informe uma página a partir de zero.");
        }
        if (tamanho < 1 || tamanho > 100) {
            erros.put("tamanho", "Informe um tamanho entre 1 e 100.");
        }
        if (!erros.isEmpty()) {
            throw new ExcecaoConsultaSolicitacao(DADOS_INVALIDOS, "Revise os campos informados.", erros);
        }
        String busca = filtros.busca() == null ? null : filtros.busca().strip();
        if (busca != null && busca.isEmpty()) {
            busca = null;
        }
        return new PesquisarSolicitacoesRequest(busca, filtros.status(),
                filtros.visao() == null ? VisaoSolicitacao.DA_MINHA_AREA : filtros.visao(), pagina, tamanho);
    }

    private Usuario recarregarAtor(Usuario ator) {
        if (ator == null || ator.getId() == null) {
            throw naoAutenticado();
        }
        Usuario atual = usuarios.findById(ator.getId()).orElseThrow(this::naoAutenticado);
        gerenciador.refresh(atual);
        if (!atual.isAtivo()) {
            throw naoAutenticado();
        }
        return atual;
    }

    private ExcecaoConsultaSolicitacao naoAutenticado() {
        return new ExcecaoConsultaSolicitacao(NAO_AUTENTICADO,
                "Usuário interno ausente, inexistente ou inativo.", Map.of());
    }

    private ExcecaoConsultaSolicitacao acessoNegado() {
        return new ExcecaoConsultaSolicitacao(ACAO_NAO_PERMITIDA, "Ação não permitida.", Map.of());
    }
}
