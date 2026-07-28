package com.example.fams.aau.keycloak;

import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Keeps a local snapshot of Keycloak users in sync so that pages can read
 * from the database instead of calling Keycloak on every request.
 *
 * Sync runs:
 *   - once on application startup (via the {@link ApplicationRunner} bean), and
 *   - on a schedule (every 5 minutes by default).
 *
 * The sync is resilient: if Keycloak is unreachable or slow, the failure is
 * logged and the previously-synced snapshot is left intact, so pages keep
 * serving the last good data.
 */
@Service
public class UserSyncService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(UserSyncService.class);

    private final KeycloakAdminService keycloakAdminService;
    private final SyncedUserRepository syncedUserRepository;
    private final String syncRealmName;

    private volatile boolean syncedAtLeastOnce = false;
    private volatile LocalDateTime lastSuccessfulSync;
    private volatile LocalDateTime lastAttemptedSync;
    private volatile String lastError;

    public UserSyncService(
            KeycloakAdminService keycloakAdminService,
            SyncedUserRepository syncedUserRepository,
            @Value("${keycloak.sync-realm:${keycloak.realm}}") String syncRealmName
    ) {
        this.keycloakAdminService = keycloakAdminService;
        this.syncedUserRepository = syncedUserRepository;
        this.syncRealmName = syncRealmName;
    }

    /** Runs once when the application has started. */
    @EventListener(ApplicationReadyEvent.class)
    public void userSyncOnStartup() {
        log.info("Triggering initial Keycloak user sync for realm {}", syncRealmName);
        Thread.startVirtualThread(() -> {
            try {
                syncNow();
            } catch (Exception ex) {
                log.error("Initial Keycloak user sync failed for realm {}: {}", syncRealmName, ex.getMessage(), ex);
            }
        });
    }

    /** Scheduled background refresh. */
    @Scheduled(fixedDelayString = "${fams.user-sync.interval-ms:300000}")
    public void scheduledSync() {
        syncNow();
    }

    @Transactional
    public synchronized void syncNow() {
        lastAttemptedSync = LocalDateTime.now();
        log.info("Starting Keycloak user sync for realm {} at {}", syncRealmName, lastAttemptedSync);
        try {
            List<UserRepresentation> users = keycloakAdminService.listAllUsers(syncRealmName);
//            log.info("Fetched {} user(s) from Keycloak realm {}", users.size(), syncRealmName);

            if (users.isEmpty()) {
                log.warn("No users were returned from Keycloak realm {}. Local table will not be updated.", syncRealmName);
            }

            // Pull groups for each user (cheap per-user call; fine in a background job).
            Map<String, String> groupsByUser = new HashMap<>();
            for (UserRepresentation user : users) {
                if (user.getId() == null) {
                    log.warn("Skipping Keycloak user with no ID during sync: username={}, email={}",
                            user.getUsername(), user.getEmail());
                    continue;
                }
                try {
                    List<String> grp = keycloakAdminService.getUserGroups(syncRealmName, user.getId());
                    String delimited = toDelimited(grp);
                    groupsByUser.put(user.getId(), delimited);
//                    log.info("Loaded {} group(s) for Keycloak user {} ({})",
//                            grp == null ? 0 : grp.size(), user.getId(), user.getUsername());
                } catch (Exception ex) {
//                    log.warn("Failed to load groups for Keycloak user {} ({}): {}",
//                            user.getId(), user.getUsername(), ex.getMessage(), ex);
                    groupsByUser.put(user.getId(), ",");
                }
            }

            LocalDateTime now = LocalDateTime.now();
            Set<String> seenIds = new HashSet<>();
            int savedCount = 0;

            for (UserRepresentation user : users) {
                if (user.getId() == null) continue;
                seenIds.add(user.getId());

                SyncedUser entity = syncedUserRepository
                        .findByKeycloakId(user.getId())
                        .orElseGet(SyncedUser::new);

                entity.setKeycloakId(user.getId());
                entity.setUsername(user.getUsername());
                entity.setEmail(user.getEmail());
                entity.setFirstName(user.getFirstName());
                entity.setLastName(user.getLastName());
                entity.setEnabled(Boolean.TRUE.equals(user.isEnabled()));
                entity.setGroups(groupsByUser.getOrDefault(user.getId(), ","));
                entity.setSyncedAt(now);

                syncedUserRepository.save(entity);
                savedCount++;
//                log.info(
//                        "Synced user into database: keycloakId={}, username={}, email={}, enabled={}, groups={}",
//                        entity.getKeycloakId(),
//                        entity.getUsername(),
//                        entity.getEmail(),
//                        entity.isEnabled(),
//                        entity.getGroups()
//                );
            }

            // Remove local rows for users that no longer exist in Keycloak.
            List<SyncedUser> local = syncedUserRepository.findAll();
            List<SyncedUser> toDelete = local.stream()
                    .filter(s -> !seenIds.contains(s.getKeycloakId()))
                    .toList();
            if (!toDelete.isEmpty()) {
                syncedUserRepository.deleteAll(toDelete);
                log.info("Removed {} stale synced user(s) from the local table: {}",
                        toDelete.size(),
                        toDelete.stream().map(SyncedUser::getKeycloakId).toList());
            } else {
                log.info("No stale synced users needed to be removed from the local table.");
            }

            syncedAtLeastOnce = true;
            lastSuccessfulSync = now;
            lastError = null;
            log.info("Keycloak user sync complete for realm {}: {} user(s) processed, {} saved, {} removed.",
                    syncRealmName, users.size(), savedCount, toDelete.size());
        } catch (Exception ex) {
            lastError = ex.getMessage();
            log.error("Keycloak user sync failed for realm {} (serving last snapshot): {}",
                    syncRealmName, ex.getMessage(), ex);
        }
    }

    public boolean isSyncedAtLeastOnce() {
        return syncedAtLeastOnce;
    }

    public LocalDateTime getLastSuccessfulSync() {
        return lastSuccessfulSync;
    }

    public LocalDateTime getLastAttemptedSync() {
        return lastAttemptedSync;
    }

    public String getLastError() {
        return lastError;
    }

    private static String toDelimited(List<String> groups) {
        if (groups == null || groups.isEmpty()) return ",";
        return groups.stream()
                .filter(Objects::nonNull)
                .map(g -> "," + g.trim())
                .collect(Collectors.joining()) + ",";
    }
}
