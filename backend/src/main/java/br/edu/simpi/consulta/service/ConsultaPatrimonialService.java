package br.edu.simpi.consulta.service;

import br.edu.simpi.consulta.dto.BemPatrimonialResponse;
import br.edu.simpi.consulta.dto.DetalheBemPatrimonialResponse;
import br.edu.simpi.consulta.dto.LocalizacaoSelecaoResponse;
import br.edu.simpi.consulta.dto.PaginaBensResponse;
import br.edu.simpi.consulta.dto.PesquisarBensRequest;
import br.edu.simpi.consulta.dto.SetorSelecaoResponse;
import br.edu.simpi.consulta.dto.UsuarioSelecaoResponse;
import br.edu.simpi.consulta.repository.ConsultaBemRepository;
import br.edu.simpi.localizacao.LocalizacaoRepository;
import br.edu.simpi.setor.SetorRepository;
import br.edu.simpi.usuario.Usuario;
import br.edu.simpi.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static br.edu.simpi.consulta.enums.MotivoFalhaConsultaPatrimonial.*;

@Service
@Transactional(readOnly = true)
public class ConsultaPatrimonialService {
    private final SetorRepository setores;
    private final LocalizacaoRepository localizacoes;
    private final UsuarioRepository usuarios;
    private final ConsultaBemRepository bens;
    private final ConsultaPatrimonialMapper mapeador;
    private final EntityManager gerenciador;

    public ConsultaPatrimonialService(SetorRepository setores, LocalizacaoRepository localizacoes,
            UsuarioRepository usuarios, ConsultaBemRepository bens,
            ConsultaPatrimonialMapper mapeador, EntityManager gerenciador) {
        this.setores = setores;
        this.localizacoes = localizacoes;
        this.usuarios = usuarios;
        this.bens = bens;
        this.mapeador = mapeador;
        this.gerenciador = gerenciador;
    }

    public List<SetorSelecaoResponse> listarSetores(Usuario ator) {
        recarregarAtor(ator);
        return setores.listarAtivosParaSelecao();
    }

    public List<LocalizacaoSelecaoResponse> listarLocalizacoes(Usuario ator) {
        recarregarAtor(ator);
        return localizacoes.listarAtivasParaSelecao();
    }

    public List<UsuarioSelecaoResponse> listarUsuarios(Long setorId, Usuario ator) {
        recarregarAtor(ator);
        validarId(setorId, "setorId");
        exigirSetorExistente(setorId);
        // Setor inativo é aceito na consulta; a criação de pedidos mantém suas próprias validações.
        return usuarios.listarAtivosPorSetor(setorId);
    }

    public PaginaBensResponse pesquisarBens(PesquisarBensRequest filtros, Usuario ator) {
        recarregarAtor(ator);
        PesquisarBensRequest normalizados = normalizarFiltros(filtros);
        if (normalizados.setorId() != null) {
            exigirSetorExistente(normalizados.setorId());
        }
        if (normalizados.localizacaoId() != null && !localizacoes.existsById(normalizados.localizacaoId())) {
            throw recursoAusente("localizacaoId");
        }
        long total = bens.contar(normalizados);
        long deslocamento = (long) normalizados.pagina() * normalizados.tamanho();
        long totalPaginas = total / normalizados.tamanho() + (total % normalizados.tamanho() == 0 ? 0 : 1);
        // Evita OFFSET inútil em páginas além do fim, inclusive acima de Integer.MAX_VALUE.
        List<BemPatrimonialResponse> conteudo = deslocamento >= total ? List.of()
                : bens.pesquisar(normalizados, deslocamento).stream().map(mapeador::mapearBem).toList();
        return new PaginaBensResponse(conteudo, normalizados.pagina(), normalizados.tamanho(), total, totalPaginas);
    }

    public DetalheBemPatrimonialResponse detalharBem(Long bemId, Usuario ator) {
        Usuario atual = recarregarAtor(ator);
        validarId(bemId, "bemId");
        var registro = bens.detalhar(bemId).orElseThrow(() -> recursoAusente("bemId"));
        return mapeador.mapearDetalhe(registro, atual);
    }

    private PesquisarBensRequest normalizarFiltros(PesquisarBensRequest filtros) {
        if (filtros == null) {
            return new PesquisarBensRequest(null, null, null, null, 0, 20);
        }
        Map<String, String> erros = new LinkedHashMap<>();
        if (filtros.setorId() != null && filtros.setorId() <= 0) {
            erros.put("setorId", "Informe um identificador positivo.");
        }
        if (filtros.localizacaoId() != null && filtros.localizacaoId() <= 0) {
            erros.put("localizacaoId", "Informe um identificador positivo.");
        }
        int pagina = filtros.pagina() == null ? 0 : filtros.pagina();
        int tamanho = filtros.tamanho() == null ? 20 : filtros.tamanho();
        if (pagina < 0) {
            erros.put("pagina", "Informe uma página a partir de zero.");
        }
        if (tamanho < 1 || tamanho > 100) {
            erros.put("tamanho", "Informe um tamanho entre 1 e 100.");
        }
        if (!erros.isEmpty()) {
            throw new ExcecaoConsultaPatrimonial(DADOS_INVALIDOS, "Revise os campos informados.", erros);
        }
        String busca = filtros.busca() == null ? null : filtros.busca().strip();
        if (busca != null && busca.isEmpty()) {
            busca = null;
        }
        return new PesquisarBensRequest(busca, filtros.setorId(), filtros.localizacaoId(),
                filtros.disponivelParaSolicitacao(), pagina, tamanho);
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

    private void exigirSetorExistente(Long setorId) {
        if (!setores.existsById(setorId)) {
            throw recursoAusente("setorId");
        }
    }

    private void validarId(Long id, String campo) {
        if (id == null || id <= 0) {
            throw new ExcecaoConsultaPatrimonial(DADOS_INVALIDOS, "Revise os campos informados.",
                    Map.of(campo, "Informe um identificador positivo."));
        }
    }

    private ExcecaoConsultaPatrimonial recursoAusente(String campo) {
        return new ExcecaoConsultaPatrimonial(RECURSO_NAO_ENCONTRADO, "Recurso não encontrado.",
                Map.of(campo, "Registro não encontrado."));
    }

    private ExcecaoConsultaPatrimonial naoAutenticado() {
        return new ExcecaoConsultaPatrimonial(NAO_AUTENTICADO, "Usuário interno ausente, inexistente ou inativo.", Map.of());
    }
}
