package io.coredge.health.claims.scheduler;

import io.coredge.health.claims.service.blockchain.WalletServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job for automatic batch wallet updates
 * Runs periodically to ensure all active users have wallets
 */
@Slf4j
@Component
public class WalletUpdateScheduler {

    private final WalletServices walletServices;

    @Autowired
    public WalletUpdateScheduler(WalletServices walletServices) {
        this.walletServices = walletServices;
    }

    /**
     * Run every day at 2:00 AM to update wallets for users without them
     * Cron expression: "0 0 2 * * ?" (second, minute, hour, day, month, day-of-week)
     */
    @Scheduled(cron = "0 0 23 * * ?")
    public void scheduledWalletUpdate() {
        log.info("Starting scheduled batch wallet update job");
        
        try {
            long usersWithoutWallets = walletServices.getUsersWithoutWalletCount();
            log.info("Found {} users without wallets", usersWithoutWallets);
            
            if (usersWithoutWallets > 0) {
                log.info("Initiating batch wallet update for {} users", usersWithoutWallets);
                walletServices.batchUpdateWalletsAsync()
                    .thenAccept(response -> log.info("Scheduled batch wallet update completed: {}", response.getMessage()))
                    .exceptionally(throwable -> {
                        log.error("Scheduled batch wallet update failed", throwable);
                        return null;
                    });
            } else {
                log.info("No users found without wallets. Skipping scheduled update.");
            }
        } catch (Exception e) {
            log.error("Error during scheduled wallet update job", e);
        }
    }

    /**
     * Run every hour to check if there are many users without wallets
     * This can alert administrators if there's a buildup of users needing wallet updates
     * Cron expression: "0 0 * * * ?" (every hour)
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void walletStatusCheck() {
        try {
            long usersWithoutWallets = walletServices.getUsersWithoutWalletCount();
            
            // Log warning if there are many users without wallets
            if (usersWithoutWallets > 100) {
                log.warn("High number of users ({} found) without wallets. Consider running manual batch update.", 
                    usersWithoutWallets);
            } else if (usersWithoutWallets > 10) {
                log.info("Found {} users without wallets. Consider running batch update during off-peak hours.", 
                    usersWithoutWallets);
            } else {
                log.debug("Wallet status check: {} users without wallets", usersWithoutWallets);
            }
        } catch (Exception e) {
            log.error("Error during wallet status check", e);
        }
    }
}