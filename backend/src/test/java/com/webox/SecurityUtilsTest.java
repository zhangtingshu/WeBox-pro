package com.webox;

import com.webox.common.ApiException;
import com.webox.common.Constants;
import com.webox.common.TextUtil;
import com.webox.config.AppProperties;
import com.webox.security.JwtUtil;
import com.webox.security.PasswordHasher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SecurityUtilsTest {

    @Test
    void passwordsAreHashedWithSaltAndNeverStoredInPlaintext() {
        PasswordHasher hasher = new PasswordHasher();
        String first = hasher.hash("Employee@123");
        String second = hasher.hash("Employee@123");
        assertNotEquals(first, second, "each password needs a unique salt");
        assertFalse(first.contains("Employee@123"));
        assertTrue(first.startsWith("pbkdf2$120000$"));
        assertTrue(hasher.matches("Employee@123", first));
        assertFalse(hasher.matches("employee@123", first));
        assertFalse(hasher.matches("Employee@123", "not-a-hash"));
    }

    @Test
    void jwtCarriesIdentityAndRejectsTampering() {
        AppProperties props = new AppProperties();
        props.setJwtSecret("unit-test-secret-with-at-least-32-characters");
        JwtUtil jwt = new JwtUtil(props);
        String token = jwt.issue(7L, "employee@webox.com", "Demo Employee", "EMPLOYEE");
        JwtUtil.Claims claims = jwt.verify(token);
        assertEquals(7L, claims.userId());
        assertEquals("EMPLOYEE", claims.role());
        assertThrows(ApiException.class, () -> jwt.verify(token + "tampered"));
        assertThrows(ApiException.class, () -> jwt.verify(""));
    }

    @Test
    void searchInputIsLengthCappedAndLikeWildcardsAreEscaped() {
        assertEquals("a\\%b\\_c", TextUtil.escapeLike("a%b_c"));
        assertEquals(50, TextUtil.clean("x".repeat(200), Constants.MAX_SEARCH_LENGTH).length());
        assertEquals(List.of("Chinese", "Japanese"),
                TextUtil.whitelist(List.of("Chinese", "DROP TABLE", "Japanese"),
                        Constants.CATEGORY_SET));
    }
}
