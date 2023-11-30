/*
 * This file is part of HuskClaims, licensed under the Apache License 2.0.
 *
 *  Copyright (c) William278 <will27528@gmail.com>
 *  Copyright (c) contributors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.william278.huskclaims;

import net.william278.desertwell.util.Version;
import net.william278.huskclaims.claim.ClaimManager;
import net.william278.huskclaims.claim.ClaimWorld;
import net.william278.huskclaims.config.ConfigProvider;
import net.william278.huskclaims.database.DatabaseProvider;
import net.william278.huskclaims.group.GroupManager;
import net.william278.huskclaims.util.GsonProvider;
import net.william278.huskclaims.util.WorldHeightProvider;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.util.logging.Level;

/**
 * Common interface for the HuskClaims plugin
 *
 * @since 1.0
 */
public interface HuskClaims extends ConfigProvider, DatabaseProvider, GsonProvider, ClaimManager, GroupManager,
        WorldHeightProvider {

    /**
     * Initialize all plugin systems
     *
     * @since 1.0
     */
    default void initialize() {
        log(Level.INFO, String.format("Initializing HuskClaims v%s...", getPluginVersion()));
        try {
            loadSettings();
            loadTrustLevels();
            loadLocales();
            loadDatabase();
            loadClaimWorlds();
            loadUserGroups();
            loadOperationListener();
        } catch (Throwable e) {
            log(Level.SEVERE, "An error occurred whilst initializing HuskClaims", e);
            disablePlugin();
            return;
        }
        log(Level.INFO, String.format("Successfully initialized HuskClaims v%s", getPluginVersion()));
    }

    /**
     * Disable the plugin
     *
     * @since 1.0
     */
    void disablePlugin();

    /**
     * Get a list of all {@link ClaimWorld}s
     *
     * @return A list of all {@link ClaimWorld}s
     * @since 1.0
     */
    @NotNull
    Version getPluginVersion();

    /**
     * Get a plugin resource
     *
     * @param name The name of the resource
     * @return the resource, if found
     * @since 1.0
     */
    InputStream getResource(@NotNull String name);

    /**
     * Log a message to the console.
     *
     * @param level      the level to log at
     * @param message    the message to log
     * @param exceptions any exceptions to log
     * @since 1.0
     */
    void log(@NotNull Level level, @NotNull String message, Throwable... exceptions);

    /**
     * Get the plugin instance
     *
     * @return the plugin instance
     * @since 1.0
     */
    @NotNull
    default HuskClaims getPlugin() {
        return this;
    }

}