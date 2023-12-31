package com.codestates.stackoverflowbe.global.auth.filter;

import com.codestates.stackoverflowbe.domain.account.entity.Account;
import com.codestates.stackoverflowbe.global.auth.jwt.JwtTokenizer;
import com.codestates.stackoverflowbe.global.auth.login.dto.LoginDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
// 클라이언트 로그인 인증 요청을 처리하는 엔트리포인트 클래스
public class JwtAuthenticationFilter extends UsernamePasswordAuthenticationFilter {
    private final AuthenticationManager authenticationManager;
    private final JwtTokenizer jwtTokenizer;

    public JwtAuthenticationFilter(AuthenticationManager authenticationManager, JwtTokenizer jwtTokenizer) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenizer = jwtTokenizer;
    }
    /*
    *  Spring Security의 인증처리에서 토큰 생성 부분을 가로챈다.
    *  인증 위임을 해당 메서드가 오버라이딩해서 대신 객체를 전달한다.
    * */

    @SneakyThrows // checked 예외를 Runtime 예외로 변경하여 try~catch문을 사용하지 않아도 되게끔 한다.
    @Override
    //인증을 위임하기 위한 메서드
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
        ObjectMapper objectMapper = new ObjectMapper(); //역직렬화 위한 ObjectMapper 인스턴스
        LoginDto loginDto = objectMapper.readValue(request.getInputStream(), LoginDto.class);

//        log.info("# attemptAuthentication : loginDto.getUsername()={}, login.getPassword()={}",loginDto.getEmail(),loginDto.getPassword());

        // Username과 Password 정보를 포함한 미인증 토큰 발행
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(loginDto.getEmail(), loginDto.getPassword());

        // AuthenticationManager에 인증 시도
        return authenticationManager.authenticate(authenticationToken);
    }

    @Override
    // 인증에 성공할 경우 호출 (AccessToken, RefreshToken 생성하여 응답헤더로 반환)
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain, Authentication authResult) throws IOException, ServletException {
        // 인증이 성공되어 Authentication의 principal 필드에 할당된 Account 객체 얻어오기
        Account account = (Account) authResult.getPrincipal();

        String accessToken = delegateAccessToken(account); // AccessToken 생성
        String refreshToken = delegateRefreshToken(account); // RefreshToken 생성

        response.setHeader("Authorization", "Bearer " + accessToken); //응답헤더(Authorization)에 AccessToken을 추가
        response.setHeader("Refresh", refreshToken);


        //AuthenticationSuccessHandler의 onAuthenticationSuccess() 메서드를 호출 -> AccountAuthenticationSuccessHandler의 onAuthenticationSuccess 호출
        this.getSuccessHandler().onAuthenticationSuccess(request, response, authResult);
    }

    private String delegateAccessToken(Account account) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", account.getEmail());
        claims.put("roles", account.getRoles());
        claims.put("displayName", account.getDisplayName());

        String subject = account.getEmail();
        Date expiration = jwtTokenizer.getTokenExpiration(jwtTokenizer.getAccessTokenExpirationMinutes());
        String base64EncodedSecretKey = jwtTokenizer.encodeBase64SecretKey(jwtTokenizer.getSecretKey());
        String acceesToken = jwtTokenizer.generateAccessToken(claims, subject, expiration, base64EncodedSecretKey);

        return acceesToken;
    }

    private String delegateRefreshToken(Account account) {
        String subject = account.getEmail();
        Date expiration = jwtTokenizer.getTokenExpiration(jwtTokenizer.getRefreshTokenExpirationMinutes());
        String base64EncodedSecretKey = jwtTokenizer.encodeBase64SecretKey(jwtTokenizer.getSecretKey());

        String refreshToken = jwtTokenizer.generateRefreshToken(subject, expiration, base64EncodedSecretKey);

        return refreshToken;
    }

}
