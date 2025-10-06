package com.hstahlmann.dbuserprovider;

import com.google.auto.service.AutoService;
import com.hstahlmann.dbuserprovider.model.QueryConfigurations;
import com.hstahlmann.dbuserprovider.persistence.DataSourceProvider;
import com.hstahlmann.dbuserprovider.persistence.RDBMS;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.component.ComponentModel;
import org.keycloak.component.ComponentValidationException;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.cache.UserCache;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.UserStorageProviderFactory;
import org.keycloak.storage.UserStorageProviderModel;
import org.keycloak.storage.user.ImportSynchronization;
import org.keycloak.storage.user.SynchronizationResult;

import java.util.*;

@JBossLog
@AutoService(UserStorageProviderFactory.class)
public class DBUserStorageProviderFactory implements UserStorageProviderFactory<DBUserStorageProvider>, ImportSynchronization {
    
    private static final String PARAMETER_PLACEHOLDER_HELP = "Use '?' as parameter placeholder character (replaced only once). ";
    private static final String DEFAULT_HELP_TEXT          = "Select to query all users you must return at least: \"id\". " +
                                                             "            \"username\"," +
                                                             "            \"email\" (optional)," +
                                                             "            \"firstName\" (optional)," +
                                                             "            \"lastName\" (optional). Any other parameter can be mapped by aliases to a realm scope";
    private static final String PARAMETER_HELP             = " The %s is passed as query parameter.";
    
    
    private final Map<String, ProviderConfig> providerConfigPerInstance = new HashMap<>();
    
    @Override
    public void init(Config.Scope config) {
    }
    
    @Override
    public void close() {
        for (Map.Entry<String, ProviderConfig> pc : providerConfigPerInstance.entrySet()) {
            pc.getValue().dataSourceProvider.close();
        }
    }
    
    @Override
    public DBUserStorageProvider create(KeycloakSession session, ComponentModel model) {
        ProviderConfig providerConfig = providerConfigPerInstance.computeIfAbsent(model.getId(), s -> configure(model));
        return new DBUserStorageProvider(session, model, providerConfig.dataSourceProvider, providerConfig.queryConfigurations);
    }
    
    private synchronized ProviderConfig configure(ComponentModel model) {
        log.debugv("Creating configuration for model: id={0} name={1}", model.getId(), model.getName());
        ProviderConfig providerConfig = new ProviderConfig();
        String         user           = model.get("user");
        String         password       = model.get("password");
        String         url            = model.get("url");
        RDBMS          rdbms          = RDBMS.getByDescription(model.get("rdbms"));
        providerConfig.dataSourceProvider.configure(url, Objects.requireNonNull(rdbms), user, password, model.getName());
        providerConfig.queryConfigurations = new QueryConfigurations(
                model.get("count"),
                model.get("listAll"),
                model.get("findById"),
                model.get("findByUsername"),
                model.get("findByUsernameOrEmail"),
                model.get("findBySearchTerm"),
                model.get("findPasswordHash"),
                model.get("findPasswordHashUsernameOnly"),
                model.get("hashFunction"),
                rdbms,
                model.get("allowKeycloakDelete", false),
                model.get("allowDatabaseToOverwriteKeycloak", false),
                model.get("updateEmailAddress"),
                model.get("updateCredentials")
        );
        return providerConfig;
    }
    
    @Override
    public void validateConfiguration(KeycloakSession session, RealmModel realm, ComponentModel model) throws ComponentValidationException {
        try {
            ProviderConfig old = providerConfigPerInstance.put(model.getId(), configure(model));
            if (old != null) {
                old.dataSourceProvider.close();
            }
        } catch (Exception e) {
            throw new ComponentValidationException(e.getMessage(), e);
        }
    }
    
    @Override
    public String getId() {
        return "sql-db-user-provider";
    }
    
    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()
                                                //DATABASE
                                                .property()
                                                .name("url")
                                                .label("JDBC URL")
                                                .helpText("JDBC Connection String")
                                                .type(ProviderConfigProperty.STRING_TYPE)
                                                .defaultValue("jdbc:sqlserver://192.168.1.89;databaseName=TestDB;trustServerCertificate=true")
                                                .add()
                                                .property()
                                                .name("user")
                                                .label("JDBC Connection User")
                                                .helpText("JDBC Connection User")
                                                .type(ProviderConfigProperty.STRING_TYPE)
                                                .defaultValue("user")
                                                .add()
                                                .property()
                                                .name("password")
                                                .label("JDBC Connection Password")
                                                .helpText("JDBC Connection Password")
                                                .type(ProviderConfigProperty.PASSWORD)
                                                .defaultValue("password")
                                                .add()
                                                .property()
                                                .name("rdbms")
                                                .label("RDBMS")
                                                .helpText("Relational Database Management System")
                                                .type(ProviderConfigProperty.LIST_TYPE)
                                                .options(RDBMS.getAllDescriptions())
                                                .defaultValue(RDBMS.MSSQL.getDesc())
                                                .add()
                                                .property()
                                                .name("allowKeycloakDelete")
                                                .label("Allow Keycloak's User Delete")
                                                .helpText("By default, clicking Delete on a user in Keycloak is not allowed.  Activate this option to allow to Delete Keycloak's version of the user (does not touch the user record in the linked RDBMS), e.g. to clear synching issues and allow the user to be synced from scratch from the RDBMS on next use, in Production or for testing.")
                                                .type(ProviderConfigProperty.BOOLEAN_TYPE)
                                                .defaultValue("false")
                                                .add()
                                                .property()
                                                .name("allowDatabaseToOverwriteKeycloak")
                                                .label("Allow DB Attributes to Overwrite Keycloak")
                                                // Technical details for the following comment: we aggregate both the existing Keycloak version and the DB version of an attribute in a Set, but since e.g. email is not a list of values on the Keycloak User, the new email is never set on it.
                                                .helpText("By default, once a user is loaded in Keycloak, its attributes (e.g. 'email') stay as they are in Keycloak even if an attribute of the same name now returns a different value through the query.  Activate this option to have all attributes set in the SQL query to always overwrite the existing user attributes in Keycloak (e.g. if Keycloak user has email 'test@test.com' but the query fetches a field named 'email' that has a value 'example@exemple.com', the Keycloak user will now have email attribute = 'example@exemple.com'). This behavior works with NO_CACHE configuration. In case you set this flag under a cached configuration, the user attributes will be reload if: 1) the cached value is older than 500ms and 2) username or e-mail does not match cached values.")
                                                .type(ProviderConfigProperty.BOOLEAN_TYPE)
                                                .defaultValue("false")
                                                .add()

                                                //QUERIES

                                                .property()
                                                .name("count")
                                                .label("User count SQL query")
                                                .helpText("SQL query returning the total count of users")
                                                .type(ProviderConfigProperty.STRING_TYPE)
                                                .defaultValue("select count(*) from tblKundenPasswoerter")
                                                .add()

                                                .property()
                                                .name("listAll")
                                                .label("List All Users SQL query")
                                                .helpText(DEFAULT_HELP_TEXT)
                                                .type(ProviderConfigProperty.STRING_TYPE)
                                                .defaultValue("select guiKundenId as id, " +
                                                            "kd.strKundenkuerzel as username, " +
                                                            "strEmail as email, " +
                                                            "name as lastName, " +
                                                            "vorname as firstName " +
                                                            "from tblKundenPasswoerter kd join tblKunden k on kd.strKundenkuerzel = k.strKundenkuerzel")
                                                .add()

                                                .property()
                                                .name("findById")
                                                .label("Find user by id SQL query")
                                                .helpText(DEFAULT_HELP_TEXT + String.format(PARAMETER_HELP, "user id") + PARAMETER_PLACEHOLDER_HELP)
                                                .type(ProviderConfigProperty.STRING_TYPE)
                                                .defaultValue("select guiKundenId as id, " +
                                                            "kd.strKundenkuerzel as username, " +
                                                            "strEmail as email, " +
                                                            "name as lastName," +
                                                            "vorname as firstName " +
                                                            "v.locale " +
                                                            "from tblKundenPasswoerter kd join tblKunden k on kd.strKundenkuerzel = k.strKundenkuerzel " +
                                                            "join (values (0, 'de'), (1, 'en'), (2, 'fr')) v(id, locale) on k.lngSpracheId = v.id where guiKundenId = ?")
                                                .add()

                                                .property()
                                                .name("findByUsernameOrEmail")
                                                .label("Find user by username or email SQL query")
                                                .helpText(DEFAULT_HELP_TEXT + String.format(PARAMETER_HELP, "login name") + PARAMETER_PLACEHOLDER_HELP)
                                                .type(ProviderConfigProperty.STRING_TYPE)
                                                .defaultValue("select guiKundenId as id, " +
                                                            "kd.strKundenkuerzel as username, " +
                                                            "strEmail as email, " +
                                                            "name as lastName, " +
                                                            "vorname as firstName " +
                                                            "v.locale " +
                                                            "from tblKundenPasswoerter kd join tblKunden k on kd.strKundenkuerzel = k.strKundenkuerzel " +
                                                            "join (values (0, 'de'), (1, 'en'), (2, 'fr')) v(id, locale) on k.lngSpracheId = v.id " +
                                                            "cross join (select ? as login_name) const where kd.strKundenkuerzel = login_name or k.strEmail = login_name")
                                                .add()

                                                .property()
                                                .name("findByUsername")
                                                .label("Find user by username SQL query")
                                                .helpText(DEFAULT_HELP_TEXT + String.format(PARAMETER_HELP, "username") + PARAMETER_PLACEHOLDER_HELP)
                                                .type(ProviderConfigProperty.STRING_TYPE)
                                                .defaultValue("select guiKundenId as id, " +
                                                            "kd.strKundenkuerzel as username, " +
                                                            "strEmail as email, " +
                                                            "name as lastName, " +
                                                            "vorname as firstName " +
                                                            "v.locale " +
                                                            "from tblKundenPasswoerter kd join tblKunden k on kd.strKundenkuerzel = k.strKundenkuerzel " +
                                                            "join (values (0, 'de'), (1, 'en'), (2, 'fr')) v(id, locale) on k.lngSpracheId = v.id where kd.strKundenkuerzel  = ?")
                                                .add()

                                                .property()
                                                .name("findBySearchTerm")
                                                .label("Find user by search term SQL query")
                                                .helpText(DEFAULT_HELP_TEXT + String.format(PARAMETER_HELP, "search term") + PARAMETER_PLACEHOLDER_HELP)
                                                .type(ProviderConfigProperty.STRING_TYPE)
                                                .defaultValue("select guiKundenId as id, " +
                                                            "kd.strKundenkuerzel as username, " +
                                                            "strEmail as email, " +
                                                            "name as lastName, " +
                                                            "vorname as firstName " +
                                                            "v.locale " +
                                                            "from tblKundenPasswoerter kd join tblKunden k on kd.strKundenkuerzel = k.strKundenkuerzel " +
                                                            "join (values (0, 'de'), (1, 'en'), (2, 'fr')) v(id, locale) on k.lngSpracheId = v.id " +
                                                            "cross join (select ? as login_name) const where kd.strKundenkuerzel = login_name or name = login_name or strEmail = login_name")
                                                .add()

                                                .property()
                                                .name("updateCredentials")
                                                .label("Update a user's credentials")
                                                .helpText(DEFAULT_HELP_TEXT + String.format(PARAMETER_HELP, "search term") + PARAMETER_PLACEHOLDER_HELP)
                                                .type(ProviderConfigProperty.STRING_TYPE)
                                                .defaultValue("update kundenPasswoerter set hash = ?, salt  = ? where strKundenKuerzel = ?")
                                                .add()

                                                .property()
                                                .name("updateEmailAddress")
                                                .label("Update a user's email address")
                                                .helpText(DEFAULT_HELP_TEXT + String.format(PARAMETER_HELP, "search term") + PARAMETER_PLACEHOLDER_HELP)
                                                .type(ProviderConfigProperty.STRING_TYPE)
                                                .defaultValue("update tblKunden set strEmail = ? where strKundenKuerzel = ?")
                                                .add()

                                                .property()
                                                .name("findPasswordHash")
                                                .label("Find password hash for username or email SQL query")
                                                .helpText(DEFAULT_HELP_TEXT + String.format(PARAMETER_HELP, "login_name") + " It is used to check against the username or email address.")
                                                .type(ProviderConfigProperty.STRING_TYPE)
                                                .defaultValue("select hash, salt from tblKundenPasswoerter kd join tblKunden k on kd.strKundenkuerzel = k.strKundenkuerzel " +
                                                            "cross join (select ? as login_name) const where kd.strKundenkuerzel = login_name or strEmail = login_name")
                                                .add()

                                                .property()
                                                .name("findPasswordHashUsernameOnly")
                                                .label("Find password hash SQL query (username only)")
                                                .helpText(DEFAULT_HELP_TEXT + String.format(PARAMETER_HELP, "login_name") + PARAMETER_PLACEHOLDER_HELP)
                                                .type(ProviderConfigProperty.STRING_TYPE)
                                                .defaultValue("select hash, salt from kunden_details where kunden_nr = ?")
                                                .add()
                                                .property()
                                                .name("hashFunction")
                                                .label("Password hash function")
                                                .helpText("Hash type used to match password (md* e sha* uses hex hash digest)")
                                                .type(ProviderConfigProperty.LIST_TYPE)
                                                .options("Blowfish (bcrypt)", "MD2", "MD5", "SHA-1", "SHA-256", "SHA3-224", "SHA3-256", "SHA3-384", "SHA3-512", "SHA-384", "SHA-512/224", "SHA-512/256", "SHA-512")
                                                .defaultValue("SHA-512")
                                                .add()
                                                .build();
    }

    @Override
    public SynchronizationResult sync(KeycloakSessionFactory keycloakSessionFactory, String realmId, UserStorageProviderModel userStorageProviderModel) {

//        return new SynchronizationResult();

        SynchronizationResult synchronizationResult = new SynchronizationResult();

        KeycloakSession session = keycloakSessionFactory.create();
        try {
            DBUserStorageProvider provider;
            session.getTransactionManager().begin();
            log.infov("Syncing federated users...");
            RealmModel realm = session.realms().getRealm(realmId);
            session.getContext().setRealm(realm);
            boolean updated = false;
            int userCounter = 0;

            provider = (DBUserStorageProvider) session.getComponentProvider(UserStorageProvider.class, userStorageProviderModel.getProviderId());

            List<Map<String, String>> users = provider.getAllUsers();
            Iterator<Map<String, String>> userIterator = users.iterator();
            log.infov("Number of users to sync: " + (long) users.size());

            while (userIterator.hasNext()) {
                userCounter++;
                final Map<String, String> federatedUser = userIterator.next();
                final String fedUsername = federatedUser.get("username");
                final Optional<String> fedLastName = Optional.ofNullable(federatedUser.get("lastName"));
                final Optional<String> fedFirstName = Optional.ofNullable(federatedUser.get("firstName"));
                final Optional<String> fedEmail = Optional.ofNullable(federatedUser.get("email"));

//                if(fedUsername.equals("1888")) {
//                    UserModel existingLocalUser = session.users().getUserByUsername(realm, fedUsername);

                    UserModel existingLocalUser = provider.getUserByUsername(realm, fedUsername);

                    if (fedEmail.isPresent() && !fedEmail.get().trim().equals(existingLocalUser.getEmail())) {
                        log.infov("SYNC local:" + existingLocalUser.getEmail() + " new from federation: " + fedEmail.get());
                        existingLocalUser.setEmail(fedEmail.get().trim());
                        updated = true;
                    }
                    if (fedLastName.isPresent() && !fedLastName.get().trim().equals(existingLocalUser.getLastName())) {
                        log.infov("SYNC local:" + existingLocalUser.getLastName() + " new from federation: " + fedLastName.get());
                        existingLocalUser.setLastName(fedLastName.get().trim());
                        updated = true;
                    }
                    if (fedFirstName.isPresent() && !fedFirstName.get().trim().equals(existingLocalUser.getFirstName())) {
                        log.infov("SYNC local:" + existingLocalUser.getFirstName() + " new from federation: " + fedFirstName.get());
                        existingLocalUser.setFirstName(fedFirstName.get().trim());
                        updated = true;
                    }
                    if (updated) {
                        synchronizationResult.increaseUpdated();
                        updated = false;
                    }
                }
//            }
            log.infov("Syncing of " + userCounter + " users completed.");
            UserCache cache = session.getProvider(UserCache.class);
            if (cache != null) cache.clear();
            session.getTransactionManager().commit();
        } catch (RuntimeException ex) {
            log.errorv(String.valueOf(ex));
            session.getTransactionManager().rollback();
        } finally {
            session.close();
        }

        log.infov(synchronizationResult.getStatus());
        return synchronizationResult;
    }

    @Override
    public SynchronizationResult syncSince(Date date, KeycloakSessionFactory keycloakSessionFactory, String realmId, UserStorageProviderModel userStorageProviderModel) {
        log.infov("Syncing federated users...");
        return sync(keycloakSessionFactory, realmId, userStorageProviderModel);
        // return SynchronizationResult.ignored();
    }

    private static class ProviderConfig {
        private final DataSourceProvider  dataSourceProvider = new DataSourceProvider();
        private QueryConfigurations queryConfigurations;
    }
}
