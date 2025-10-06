package com.hstahlmann.dbuserprovider;

import com.hstahlmann.dbuserprovider.persistence.UserRepository;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.component.ComponentModel;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.UserModel;
import org.keycloak.storage.UserStorageProvider;

import java.util.Objects;

@JBossLog
public class UserProfileUpdateListener implements EventListenerProvider{

    public final static String ID = "profile-update";

    private final KeycloakSession session;
    private final RealmProvider model;

    public UserProfileUpdateListener(KeycloakSession session) {
        this.session = session;
        this.model = session.realms();
    }

    @Override
    public void onEvent(Event event) {
        DBUserStorageProvider provider;
        UserRepository repository;
        log.debugv("Update event received for user {0}", event.getUserId());
        if (event.getType().equals(EventType.UPDATE_PROFILE)) {
            if (event.getDetails() != null && event.getRealmId() != null && event.getUserId() != null) {
                RealmModel realm = model.getRealm(event.getRealmId());
                ComponentModel cp = realm.getStorageProviders(UserStorageProvider.class).findFirst().orElse(null);
                provider = (DBUserStorageProvider) session.getComponentProvider(UserStorageProvider.class, Objects.requireNonNull(cp).getProviderId());
                if(provider != null) {
                    repository = provider.getRepository();
                    for(String key: event.getDetails().keySet()) {
                        if(key.equals("updated_email")) {
                            String updatedEmail = event.getDetails().get("updated_email");
                            String previousEmail = event.getDetails().get("previous_email");
                            UserModel user = session.users().getUserById(session.getContext().getRealm(), event.getUserId());
                            if(repository.updateEmailAddress(user.getUsername(), updatedEmail)) {
                                log.infov("Email changed from {0} to {1} for user {2} [{3}]", previousEmail, updatedEmail, event.getUserId(), user.getUsername());
                            }
                        }
                    }
                } else {
                    log.errorv("No User Storage Provider found");
                }

            }
        }
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
    }

    @Override
    public void close() {
    }
}
