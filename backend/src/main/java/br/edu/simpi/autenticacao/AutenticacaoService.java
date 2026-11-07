package br.edu.simpi.autenticacao;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import br.edu.simpi.autenticacao.dto.AutenticacaoRequest;
import br.edu.simpi.autenticacao.dto.AutenticacaoResponse;
import br.edu.simpi.autenticacao.dto.UsuarioResponse;
import br.edu.simpi.usuario.Usuario;

@Service
public class AutenticacaoService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AutenticacaoService(
            AuthenticationManager authenticationManager,
            JwtService jwtService) {

        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public AutenticacaoResponse autenticar(AutenticacaoRequest request) {

        var authenticationToken = new UsernamePasswordAuthenticationToken(
                request.email(),
                request.senha());

        var authentication = authenticationManager.authenticate(authenticationToken);

        Usuario usuario = (Usuario) authentication.getPrincipal();

        String token = jwtService.gerarToken(usuario.getId());

        return new AutenticacaoResponse(
                token,
                "Bearer",
                jwtService.getExpirationSeconds(),
                UsuarioResponse.from(usuario));
    }
}