package com.hstahlmann.dbuserprovider.persistence;

import com.hstahlmann.dbuserprovider.DBUserStorageException;
import com.hstahlmann.dbuserprovider.model.QueryConfigurations;
import com.hstahlmann.dbuserprovider.util.AuthCredentials;
import com.hstahlmann.dbuserprovider.util.PagingUtil;
import lombok.extern.jbosslog.JBossLog;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.codec.digest.DigestUtils;

import javax.sql.DataSource;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;


@JBossLog
public class UserRepository {
    
    
    private final DataSourceProvider  dataSourceProvider;
    private final QueryConfigurations queryConfigurations;
    
    public UserRepository(DataSourceProvider dataSourceProvider, QueryConfigurations queryConfigurations) {
        this.dataSourceProvider  = dataSourceProvider;
        this.queryConfigurations = queryConfigurations;
    }
    
    
    private <T> T doQuery(String query, PagingUtil.Pageable pageable, Function<ResultSet, T> resultTransformer, Object... params) {
        Optional<DataSource> dataSourceOpt = dataSourceProvider.getDataSource();
        if (dataSourceOpt.isPresent()) {
            DataSource dataSource = dataSourceOpt.get();
            try (Connection c = dataSource.getConnection()) {
                if (pageable != null) {
                    query = PagingUtil.formatScriptWithPageable(query, pageable, queryConfigurations.getRDBMS());
                }
                log.debugv("Query: {0} params: {1} ", query, Arrays.toString(params));
                try (PreparedStatement statement = c.prepareStatement(query)) {
                    if (params != null) {
                        for (int i = 1; i <= params.length; i++) {
                            statement.setObject(i, params[i - 1]);
                        }
                    }
                    try (ResultSet rs = statement.executeQuery()) {
                        return resultTransformer.apply(rs);
                    }
                }
            } catch (SQLException e) {
                log.error(e.getMessage(), e);
            }
            return null;
        }
        return null;
    }

    private List<Map<String, String>> readMap(ResultSet rs) {
        try {
            List<Map<String, String>> data         = new ArrayList<>();
            Set<String>               columnsFound = new HashSet<>();
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                String columnLabel = rs.getMetaData().getColumnLabel(i);
                columnsFound.add(columnLabel);
            }
            while (rs.next()) {
                Map<String, String> result = new HashMap<>();
                for (String col : columnsFound) {
                    result.put(col, rs.getString(col));
                }
                data.add(result);
            }
            return data;
        } catch (Exception e) {
            throw new DBUserStorageException(e.getMessage(), e);
        }
    }
    
    
    private Integer readInt(ResultSet rs) {
        try {
            return rs.next() ? rs.getInt(1) : null;
        } catch (Exception e) {
            throw new DBUserStorageException(e.getMessage(), e);
        }
    }
    
    private Boolean readBoolean(ResultSet rs) {
        try {
            return rs.next() ? rs.getBoolean(1) : null;
        } catch (Exception e) {
            throw new DBUserStorageException(e.getMessage(), e);
        }
    }
    
    private String readString(ResultSet rs) {
        try {
            return rs.next() ? rs.getString(1) : null;
        } catch (Exception e) {
            throw new DBUserStorageException(e.getMessage(), e);
        }
    }
    
    public List<Map<String, String>> getAllUsers() {
        return doQuery(queryConfigurations.getListAll(), null, this::readMap);
    }
    
    public int getUsersCount(String search) {
        if (search == null || search.isEmpty()) {
            return Optional.ofNullable(doQuery(queryConfigurations.getCount(), null, this::readInt)).orElse(0);
        } else {
            String query = String.format("select count(*) from (%s) count", queryConfigurations.getFindBySearchTerm());
            return Optional.ofNullable(doQuery(query, null, this::readInt, search)).orElse(0);
        }
    }
    
    
    public Map<String, String> findUserById(String id) {
        return Optional.ofNullable(doQuery(queryConfigurations.getFindById(), null, this::readMap, id))
                       .orElse(Collections.emptyList())
                       .stream().findFirst().orElse(null);
    }
    
    public Optional<Map<String, String>> findUserByLoginName(String username, boolean isEmailLoginAllowed) {
        if (isEmailLoginAllowed) {
            return Optional.ofNullable(doQuery(queryConfigurations.getFindByUsernameOrEmail(), null, this::readMap, username))
                    .orElse(Collections.emptyList())
                    .stream().findFirst();
        } else {
            return Optional.ofNullable(doQuery(queryConfigurations.getFindByUsername(), null, this::readMap, username))
                    .orElse(Collections.emptyList())
                    .stream().findFirst();
        }

    }

    public List<Map<String, String>> findUsers(String search, PagingUtil.Pageable pageable) {
        if (search == null || search.isEmpty()) {
            return doQuery(queryConfigurations.getListAll(), pageable, this::readMap);
        }
        return doQuery(queryConfigurations.getFindBySearchTerm(), pageable, this::readMap, search);
    }
    
    public boolean validateCredentials(String username, String password, boolean isEmailLoginAllowed) {
        List<Map<String, String>> hashAndSalt;
        String hash, salt;
        boolean userValidated = false;

        if (isEmailLoginAllowed) {
            hashAndSalt = Optional.ofNullable(doQuery(queryConfigurations.getFindPasswordHash(), null, this::readMap, username)).orElse(Collections.emptyList());
        } else {
            hashAndSalt = Optional.ofNullable(doQuery(queryConfigurations.getFindPasswordHashUsernameOnly(), null, this::readMap, username)).orElse(Collections.emptyList());
        }
        hash = hashAndSalt.get(0).get("hash");
        salt = hashAndSalt.get(0).get("salt");

        String hashFunction = queryConfigurations.getHashFunction();

        if (isSHA512(hashFunction)) {
            userValidated = !hash.isEmpty() && AuthCredentials.validate(password, salt, hash, hashFunction);
        } else {
            MessageDigest digest   = DigestUtils.getDigest(hashFunction);
            byte[]        pwdBytes = StringUtils.getBytesUtf8(password + salt);
            userValidated = Objects.equals(Hex.encodeHexString(digest.digest(pwdBytes)), hash);
        }
        log.infov("Validation {0} for user {1}", userValidated, username);
        return userValidated;
    }
    
    public boolean updateCredentials(String username, String password) {
        Map<String, String> hashAndSalt;
        hashAndSalt = AuthCredentials.generate(password, queryConfigurations.getHashFunction());
        log.infov("Updating credentials for user {0}", username);
        // throw new NotImplementedException("Password update not supported");

        Optional<DataSource> dataSourceOpt = dataSourceProvider.getDataSource();
        if (dataSourceOpt.isPresent()) {
            DataSource dataSource = dataSourceOpt.get();
            try (Connection c = dataSource.getConnection()) {
                String query = queryConfigurations.updateCredentials();
                // String query = "update kundenPasswoerter set hash = ?, salt  = ? where strKundenKuerzel = ?";
                log.debugv("Query: {0} hash: {1} salt: {2} username: {3}", query, hashAndSalt.get("hash"), hashAndSalt.get("salt"), username);
                try (PreparedStatement statement = c.prepareStatement(query)) {
                    statement.setString(1, hashAndSalt.get("hash"));
                    statement.setString(2, hashAndSalt.get("salt"));
                    statement.setString(3, username);
                    statement.executeUpdate();
                }
            } catch (SQLException e) {
                log.error(e.getMessage(), e);
                return false;
            }
        }
        return true;
    }

    public boolean updateEmailAddress(String username, String emailAddress) {
        log.debugv("Updating email address for user {0}, new: {1}", username, emailAddress);

        Optional<DataSource> dataSourceOpt = dataSourceProvider.getDataSource();
        if (dataSourceOpt.isPresent()) {
            DataSource dataSource = dataSourceOpt.get();
            try (Connection c = dataSource.getConnection()) {
                String query = queryConfigurations.updateEmailAddress();
                // String query = "update tblKunden set strEmail = ? where strKundenKuerzel = ?";
                log.debugv("Query: {0} username: {1} email address: {2} username: {3}", query, username, emailAddress, username);
                try (PreparedStatement statement = c.prepareStatement(query)) {
                    statement.setString(1, emailAddress);
                    statement.setString(2, username);
                    statement.executeUpdate();
                }
            } catch (SQLException e) {
                log.error(e.getMessage(), e);
                return false;
            }
        }
        return true;
    }
    
    public boolean removeUser() {
        return queryConfigurations.getAllowKeycloakDelete();
    }

    public boolean isSHA512(String hashFunction) {
        return hashFunction.toLowerCase().contains("sha-512");
    }
}
