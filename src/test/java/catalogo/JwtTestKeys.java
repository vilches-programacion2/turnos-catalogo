package catalogo;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Solo pruebas: la aplicación de catálogo no posee privada ni emite tokens. */
final class JwtTestKeys {
    static final String ISSUER = "https://reservas.test";
    static final String AUDIENCE = "turnos-catalogo";
    private static final Path PUBLIC_KEY;
    private static final JwtEncoder ENCODER;

    static {
        try {
            var pair = generarPar();
            ENCODER = encoder(pair);
            Path directory = Files.createTempDirectory("catalogo-jwt-test-");
            directory.toFile().deleteOnExit();
            PUBLIC_KEY = directory.resolve("public.pem");
            String encoded = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(pair.getPublic().getEncoded());
            Files.writeString(PUBLIC_KEY, "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----\n");
            PUBLIC_KEY.toFile().deleteOnExit();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private JwtTestKeys() {
    }

    static void configurar(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.public-key", () -> PUBLIC_KEY.toUri().toString());
        registry.add("app.jwt.issuer", () -> ISSUER);
        registry.add("app.jwt.audience", () -> AUDIENCE);
    }

    static String[] propiedades() {
        return new String[] {"app.jwt.public-key=" + PUBLIC_KEY.toUri(), "app.jwt.issuer=" + ISSUER,
                "app.jwt.audience=" + AUDIENCE};
    }

    static JwtClaimsSet.Builder claims() {
        Instant ahora = Instant.now();
        return JwtClaimsSet.builder().issuer(ISSUER).audience(List.of(AUDIENCE)).subject("42")
                .issuedAt(ahora.minusSeconds(5)).expiresAt(ahora.plusSeconds(1800))
                .claim("roles", List.of("ROLE_USER"));
    }

    static String token() {
        return firmar(claims(), SignatureAlgorithm.RS256);
    }

    static String firmar(JwtClaimsSet.Builder claims, SignatureAlgorithm algorithm) {
        return ENCODER.encode(JwtEncoderParameters.from(JwsHeader.with(algorithm).type("JWT").build(), claims.build()))
                .getTokenValue();
    }

    static String firmaAjena() throws Exception {
        return encoder(generarPar()).encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims().build())).getTokenValue();
    }

    private static KeyPair generarPar() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static JwtEncoder encoder(KeyPair pair) {
        var key = new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }
}
