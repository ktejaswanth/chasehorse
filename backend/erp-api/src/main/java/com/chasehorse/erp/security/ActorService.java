package com.chasehorse.erp.security;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.chasehorse.erp.common.ApiException;

@Service
public class ActorService {
    private final JdbcClient jdbc;

    public ActorService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Actor load(Jwt jwt) {
        UUID id;
        try {
            id = UUID.fromString(jwt.getSubject());
        } catch (RuntimeException e) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "unauthorized", "Invalid token subject.");
        }
        String email = jwt.getClaimAsString("email");

        List<Actor> rows = jdbc.sql("""
                SELECT p.account_status::text AS status,
                       coalesce(array_agg(r.role::text) FILTER (WHERE r.role IS NOT NULL), '{}') AS roles
                  FROM app.profiles p
                  LEFT JOIN app.user_roles r ON r.user_id = p.id
                 WHERE p.id = :id
                 GROUP BY p.account_status
                """)
                .param("id", id)
                .query((rs, n) -> {
                    Set<AppRole> roles = EnumSet.noneOf(AppRole.class);
                    for (String r : com.chasehorse.erp.common.Pg.toList(rs.getArray("roles"))) {
                        roles.add(AppRole.fromDb(r));
                    }
                    return new Actor(id, email, AccountStatus.fromDb(rs.getString("status")), roles);
                })
                .list();
        return rows.isEmpty() ? new Actor(id, email, AccountStatus.UNREGISTERED, Set.of()) : rows.get(0);
    }
}
