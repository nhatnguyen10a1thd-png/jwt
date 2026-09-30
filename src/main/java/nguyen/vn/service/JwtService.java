package nguyen.vn.service;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {
    @Value("${security.jwt.secret-key}")
    private String secretKey;

    @Value("${security.jwt.expiration-time}")
    private long jwtExpiration;

    public String extractUsername(String token) {
        return extractClaim(token, "sub");
    }

    /**
     * Trích xuất một claim cụ thể từ token.
     */
    public Object extractClaim(String token, String claimName) {
        JWTClaimsSet claims = extractAllClaims(token);
        return claims.getClaim(claimName);
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return buildToken(extraClaims, userDetails, jwtExpiration);
    }

    public long getExpirationTime() {
        return jwtExpiration;
    }

    private String buildToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails,
            long expiration
    ) {
        try {
            // Xây dựng JWT Claims
            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                    .subject(userDetails.getUsername())
                    .issueTime(new Date(System.currentTimeMillis()))
                    // the token will be expired in 30 hours
                    .expirationTime(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 30));

            // Thêm extra claims
            for (Map.Entry<String, Object> entry : extraClaims.entrySet()) {
                claimsBuilder.claim(entry.getKey(), entry.getValue());
            }

            JWTClaimsSet claimsSet = claimsBuilder.build();

            // Tạo JWS Header với thuật toán HS256
            JWSHeader header = new JWSHeader(JWSAlgorithm.HS256);

            // Tạo Signed JWT
            SignedJWT signedJWT = new SignedJWT(header, claimsSet);

            // Ký token bằng secret key
            JWSSigner signer = new MACSigner(getSecretKeyBytes());
            signedJWT.sign(signer);

            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Lỗi khi tạo JWT token", e);
        }
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        Date expiration = extractExpiration(token);
        return expiration.before(new Date());
    }

    private Date extractExpiration(String token) {
        JWTClaimsSet claims = extractAllClaims(token);
        return claims.getExpirationTime();
    }

    /**
     * Parse và xác thực chữ ký của token, trả về toàn bộ claims.
     */
    private JWTClaimsSet extractAllClaims(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);

            // Xác thực chữ ký
            JWSVerifier verifier = new MACVerifier(getSecretKeyBytes());
            if (!signedJWT.verify(verifier)) {
                throw new RuntimeException("Chữ ký JWT không hợp lệ");
            }

            return signedJWT.getJWTClaimsSet();
        } catch (ParseException | JOSEException e) {
            throw new RuntimeException("Lỗi khi parse JWT token", e);
        }
    }

    /**
     * Decode secret key từ Base64 (hex string) thành byte array.
     * Nimbus yêu cầu key tối thiểu 256 bit (32 bytes) cho HS256.
     */
    private byte[] getSecretKeyBytes() {
        // Secret key đang lưu dạng hex string trong application.properties
        // Chuyển hex string thành byte array
        return hexStringToByteArray(secretKey);
    }

    private static byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
