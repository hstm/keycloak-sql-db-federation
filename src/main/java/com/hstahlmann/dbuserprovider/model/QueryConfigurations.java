package com.hstahlmann.dbuserprovider.model;

import com.hstahlmann.dbuserprovider.persistence.RDBMS;
import lombok.Getter;

public class QueryConfigurations {

    @Getter
    private final String count;
    @Getter
    private final String listAll;
    @Getter
    private final String findById;
    @Getter
    private final String findByUsername;
    @Getter
    private final String findByUsernameOrEmail;
    @Getter
    private final String findBySearchTerm;
    @Getter
    private final String findPasswordHash;
    @Getter
    private final String findPasswordHashUsernameOnly;
    @Getter
    private final String hashFunction;
    @Getter
    private final RDBMS  RDBMS;
    private final boolean allowKeycloakDelete;
    private final boolean allowDatabaseToOverwriteKeycloak;
    private final String updateEmailAddress;
    private final String updateCredentials;

    public QueryConfigurations(String count, String listAll, String findById, String findByUsername, String findByUsernameOrEmail, String findBySearchTerm, String findPasswordHash,
                               String findPasswordHashUsernameOnly, String hashFunction, RDBMS RDBMS, boolean allowKeycloakDelete,
                               boolean allowDatabaseToOverwriteKeycloak, String updateEmailAddress, String updateCredentials) {
        this.count = count;
        this.listAll = listAll;
        this.findById = findById;
        this.findByUsername = findByUsername;
        this.findByUsernameOrEmail = findByUsernameOrEmail;
        this.findBySearchTerm = findBySearchTerm;
        this.findPasswordHash = findPasswordHash;
        this.findPasswordHashUsernameOnly = findPasswordHashUsernameOnly;
        this.hashFunction = hashFunction;
        this.RDBMS = RDBMS;
        this.allowKeycloakDelete = allowKeycloakDelete;
        this.allowDatabaseToOverwriteKeycloak = allowDatabaseToOverwriteKeycloak;
        this.updateEmailAddress = updateEmailAddress;
        this.updateCredentials = updateCredentials;
    }

    public boolean getAllowKeycloakDelete() {
        return allowKeycloakDelete;
    }

    public boolean getAllowDatabaseToOverwriteKeycloak() {
        return allowDatabaseToOverwriteKeycloak;
    }

    public String updateEmailAddress() {
        return updateEmailAddress;
    }

    public String updateCredentials() {
        return updateCredentials;
    }
}
