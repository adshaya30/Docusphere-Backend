package com.docusphere.backend.documentAction.service;

import com.docusphere.backend.Common.exception.InvalidRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class HttpTeamAccessValidator implements TeamAccessValidator {
    // TODO(team-module): Temporary validator used until Team module is integrated.
    // Replace this class with TeamModuleAccessValidator (service/repository-backed)
    // and remove HttpTeamAccessValidator after real membership checks are available.

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    private final JdbcTemplate jdbcTemplate;

    @Value("${team.validation.mode:EXISTS_ONLY}")
    private String validationMode;

    @Value("${team.validation.schema:public}")
    private String schemaName;

    @Value("${team.validation.teams-table:team}")
    private String teamsTable;

    @Value("${team.validation.team-id-column:id}")
    private String teamIdColumn;

    @Value("${team.validation.membership-table:team_members}")
    private String membershipTable;

    @Value("${team.validation.membership-team-id-column:team_id}")
    private String membershipTeamIdColumn;

    @Value("${team.validation.membership-user-id-column:user_id}")
    private String membershipUserIdColumn;

    public HttpTeamAccessValidator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean isMember(Long userId, UUID teamId) {
        // TODO(team-module): Replace table-based fallback checks with canonical
        // Team module membership API/repository check.
        try {
            if ("MEMBERSHIP".equalsIgnoreCase(validationMode)) {
                String membershipSql = "SELECT COUNT(1) FROM " + qualifiedTable(membershipTable)
                        + " WHERE " + membershipTeamIdColumn + " = ? AND " + membershipUserIdColumn + " = ?";
                Integer count = jdbcTemplate.queryForObject(membershipSql, Integer.class, teamId, userId);
                return count != null && count > 0;
            }

            Integer configuredCount = queryTeamCount(teamsTable, teamIdColumn, teamId);
            if (configuredCount != null) {
                return configuredCount > 0;
            }

            // Fallback for common naming mismatch: team <-> teams
            String fallbackTable = "team".equalsIgnoreCase(teamsTable) ? "teams" : "team";
            Integer fallbackCount = queryTeamCount(fallbackTable, teamIdColumn, teamId);
            if (fallbackCount != null) {
                return fallbackCount > 0;
            }

            // Final fallback: auto-detect any table in schema containing "team"
            String discoveredTable = discoverTeamTable();
            if (discoveredTable != null) {
                Integer discoveredCount = queryTeamCount(discoveredTable, teamIdColumn, teamId);
                if (discoveredCount != null) {
                    return discoveredCount > 0;
                }
            }

            throw new InvalidRequestException(
                    "Team validation failed. Team table not found. Set team.validation.teams-table correctly."
            );
        } catch (DataAccessException ex) {
            throw new InvalidRequestException(
                    "Team validation failed. Check team table/membership configuration in application.properties."
            );
        }
    }

    private Integer queryTeamCount(String table, String idColumn, UUID teamId) {
        try {
            String sql = "SELECT COUNT(1) FROM " + qualifiedTable(table) + " WHERE " + idColumn + " = ?";
            return jdbcTemplate.queryForObject(sql, Integer.class, teamId);
        } catch (DataAccessException ex) {
            return null;
        }
    }

    private String discoverTeamTable() {
        try {
            String sql = "SELECT table_name FROM information_schema.tables " +
                    "WHERE table_schema = ? AND table_type = 'BASE TABLE' AND table_name ILIKE ? " +
                    "ORDER BY table_name ASC";
            List<String> tables = jdbcTemplate.queryForList(sql, String.class, schemaName, "%team%");
            return tables.isEmpty() ? null : tables.get(0);
        } catch (DataAccessException ex) {
            return null;
        }
    }

    private String qualifiedTable(String table) {
        if (!isSafeIdentifier(schemaName) || !isSafeIdentifier(table)) {
            throw new InvalidRequestException("Unsafe team validation table/schema configuration.");
        }
        return schemaName + "." + table;
    }

    private boolean isSafeIdentifier(String identifier) {
        return identifier != null && SAFE_IDENTIFIER.matcher(identifier).matches();
    }
}
