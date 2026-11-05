package br.edu.simpi.consulta.service;

import br.edu.simpi.consulta.dto.BemPatrimonialResponse;
import br.edu.simpi.consulta.dto.BemPatrimonialResponse.LocalizacaoResponse;
import br.edu.simpi.consulta.dto.BemPatrimonialResponse.ResponsavelResponse;
import br.edu.simpi.consulta.dto.BemPatrimonialResponse.SetorResponse;
import br.edu.simpi.consulta.dto.DetalheBemPatrimonialResponse;
import br.edu.simpi.consulta.dto.DetalheBemPatrimonialResponse.SolicitacaoEmAndamentoResponse;
import br.edu.simpi.consulta.dto.TransferenciaBemResponse;
import br.edu.simpi.consulta.dto.TransferenciaBemResponse.ReferenciasTransferenciaResponse;
import br.edu.simpi.consulta.repository.ConsultaBemRepository.RegistroBem;
import br.edu.simpi.consulta.repository.ConsultaTransferenciaRepository.RegistroTransferencia;
import br.edu.simpi.solicitacao.service.RegrasVisibilidadeSolicitacao;
import br.edu.simpi.usuario.Usuario;
import org.springframework.stereotype.Component;

@Component
public class ConsultaPatrimonialMapper {
    public TransferenciaBemResponse mapearTransferencia(RegistroTransferencia registro) {
        return new TransferenciaBemResponse(registro.solicitacaoId(), registro.numero(), registro.recebidaEm(),
                new ReferenciasTransferenciaResponse(
                        new SetorResponse(registro.setorOrigemId(), registro.setorOrigemNome()),
                        new LocalizacaoResponse(registro.localizacaoOrigemId(), registro.localizacaoOrigemNome()),
                        new ResponsavelResponse(registro.responsavelOrigemId(), registro.responsavelOrigemNome())),
                new ReferenciasTransferenciaResponse(
                        new SetorResponse(registro.setorDestinoId(), registro.setorDestinoNome()),
                        new LocalizacaoResponse(registro.localizacaoDestinoId(), registro.localizacaoDestinoNome()),
                        new ResponsavelResponse(registro.responsavelDestinoId(), registro.responsavelDestinoNome())));
    }

    public BemPatrimonialResponse mapearBem(RegistroBem registro) {
        return new BemPatrimonialResponse(registro.id(), registro.codigo(), registro.plaqueta(), registro.descricao(),
                new SetorResponse(registro.setorId(), registro.setorNome()),
                new LocalizacaoResponse(registro.localizacaoId(), registro.localizacaoNome()),
                new ResponsavelResponse(registro.responsavelId(), registro.responsavelNome()),
                registro.ativo(), registro.disponivel());
    }

    public DetalheBemPatrimonialResponse mapearDetalhe(RegistroBem registro, Usuario ator) {
        var bem = mapearBem(registro);
        SolicitacaoEmAndamentoResponse pedido = null;
        if (registro.solicitacaoId() != null && RegrasVisibilidadeSolicitacao.temAcesso(ator,
                registro.setorSolicitanteId(), registro.setorOrigemId(), registro.setorDestinoId())) {
            pedido = new SolicitacaoEmAndamentoResponse(registro.solicitacaoId(), registro.solicitacaoNumero());
        }
        return new DetalheBemPatrimonialResponse(bem.id(), bem.codigo(), bem.plaqueta(), bem.descricao(),
                bem.setor(), bem.localizacao(), bem.responsavel(), bem.ativo(), bem.disponivelParaSolicitacao(), pedido);
    }
}
