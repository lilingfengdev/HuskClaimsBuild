package net.william278.huskclaims.database;

import net.william278.huskclaims.HuskClaims;
import net.william278.huskclaims.user.Preferences;
import net.william278.huskclaims.user.SavedUser;
import net.william278.huskclaims.user.User;
import org.jetbrains.annotations.NotNull;
import org.sqlite.SQLiteConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public class SqLiteDatabase extends Database {

    /**
     * Path to the SQLite HuskHomesData.db file.
     */
    private final Path databaseFile;

    /**
     * The name of the database file.
     */
    private static final String DATABASE_FILE_NAME = "HuskClaimsData.db";

    /**
     * The persistent SQLite database connection.
     */
    private Connection connection;

    public SqLiteDatabase(@NotNull HuskClaims plugin) {
        super(plugin);
        this.databaseFile = plugin.getConfigDirectory().resolve(DATABASE_FILE_NAME);
    }

    private Connection getConnection() throws SQLException {
        if (connection == null) {
            setConnection();
        } else if (connection.isClosed()) {
            setConnection();
        }
        return connection;
    }

    private void setConnection() {
        try {
            // Ensure that the database file exists
            if (databaseFile.toFile().createNewFile()) {
                plugin.log(Level.INFO, "Created the SQLite database file");
            }

            // Specify use of the JDBC SQLite driver
            Class.forName("org.sqlite.JDBC");

            // Set SQLite database properties
            SQLiteConfig config = new SQLiteConfig();
            config.enforceForeignKeys(true);
            config.setEncoding(SQLiteConfig.Encoding.UTF8);
            config.setSynchronous(SQLiteConfig.SynchronousMode.FULL);

            // Establish the connection
            connection = DriverManager.getConnection(
                    String.format("jdbc:sqlite:%s", databaseFile.toAbsolutePath()),
                    config.toProperties()
            );
        } catch (IOException e) {
            plugin.log(Level.SEVERE, "An exception occurred creating the database file", e);
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "An SQL exception occurred initializing the SQLite database", e);
        } catch (ClassNotFoundException e) {
            plugin.log(Level.SEVERE, "Failed to load the necessary SQLite driver", e);
        }
    }

    @Override
    protected void executeScript(@NotNull Connection connection, @NotNull String name) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String schemaStatement : getScript(name)) {
                statement.execute(schemaStatement);
            }
        }
    }

    @Override
    public void initialize() throws RuntimeException {
        // Establish connection
        this.setConnection();

        // Backup database file
        this.backupFlatFile(databaseFile);

        // Create tables
        if (!isCreated()) {
            plugin.log(Level.INFO, "Creating SQLite database tables");
            try {
                executeScript(getConnection(), "sqlite_schema.sql");
            } catch (SQLException e) {
                plugin.log(Level.SEVERE, "Failed to create SQLite database tables");
                setLoaded(false);
                return;
            }
            setSchemaVersion(Migration.getLatestVersion());
            plugin.log(Level.INFO, "SQLite database tables created!");
            setLoaded(true);
            return;
        }

        // Perform migrations
        try {
            performMigrations(getConnection(), Type.SQLITE);
            setLoaded(true);
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to perform SQLite database migrations");
            setLoaded(false);
        }
    }

    @Override
    public boolean isCreated() {
        if (!databaseFile.toFile().exists()) {
            return false;
        }
        try (PreparedStatement statement = getConnection().prepareStatement(format("""
                SELECT `uuid`
                FROM `%user_data%`
                LIMIT 1;"""))) {
            statement.executeQuery();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public int getSchemaVersion() {
        try (PreparedStatement statement = getConnection().prepareStatement(format("""
                SELECT `schema_version`
                FROM `%meta_data%`
                LIMIT 1;"""))) {
            final ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt("schema_version");
            }
        } catch (SQLException e) {
            plugin.log(Level.WARNING, "The database schema version could not be fetched; migrations will be carried out.");
        }
        return -1;
    }

    @Override
    public void setSchemaVersion(int version) {
        if (getSchemaVersion() == -1) {
            try (PreparedStatement insertStatement = getConnection().prepareStatement(format("""
                    INSERT INTO `%meta_data%` (`schema_version`)
                    VALUES (?);"""))) {
                insertStatement.setInt(1, version);
                insertStatement.executeUpdate();
            } catch (SQLException e) {
                plugin.log(Level.SEVERE, "Failed to insert schema version in table", e);
            }
            return;
        }

        try (PreparedStatement statement = getConnection().prepareStatement(format("""
                UPDATE `%meta_data%`
                SET `schema_version` = ?;"""))) {
            statement.setInt(1, version);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to update schema version in table", e);
        }
    }

    @Override
    public Optional<SavedUser> getUser(@NotNull UUID uuid) {
        try (PreparedStatement statement = getConnection().prepareStatement(format("""
                SELECT `uuid`, `username`, `last_login`, `claim_blocks`, `preferences`
                FROM `%user_data%`
                WHERE uuid = ?"""))) {
            statement.setString(1, uuid.toString());
            final ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                final String name = resultSet.getString("username");
                final String preferences = new String(resultSet.getBytes("preferences"), StandardCharsets.UTF_8);
                return Optional.of(new SavedUser(
                        User.of(uuid, name),
                        resultSet.getTimestamp("last_login").toLocalDateTime()
                                .atOffset(OffsetDateTime.now().getOffset()),
                        resultSet.getLong("claim_blocks"),
                        plugin.getPreferencesFromJson(preferences)
                ));
            }
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to fetch user data from table by UUID", e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<SavedUser> getUser(@NotNull String username) {
        try (PreparedStatement statement = getConnection().prepareStatement(format("""
                SELECT `uuid`, `username`, `last_login`, `claim_blocks`, `claim_blocks`, `preferences`
                FROM `%user_data%`
                WHERE `username` = ?"""))) {
            statement.setString(1, username);
            final ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                final UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                final String name = resultSet.getString("username");
                final String preferences = new String(resultSet.getBytes("preferences"), StandardCharsets.UTF_8);
                return Optional.of(new SavedUser(
                        User.of(uuid, name),
                        resultSet.getTimestamp("last_login").toLocalDateTime()
                                .atOffset(OffsetDateTime.now().getOffset()),
                        resultSet.getLong("claim_blocks"),
                        plugin.getPreferencesFromJson(preferences)
                ));
            }
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to fetch user data from table by username", e);
        }
        return Optional.empty();
    }

    @Override
    public List<SavedUser> getInactiveUsers(long daysInactive) {
        final List<SavedUser> inactiveUsers = new ArrayList<>();
        try (PreparedStatement statement = getConnection().prepareStatement(format("""
                SELECT `uuid`, `username`, `last_login`, `preferences`, `claim_blocks`
                FROM `%user_data%`
                WHERE datetime(`last_login` / 1000, 'unixepoch') < datetime('now', ?);"""))) {
            statement.setString(1, String.format("-%d days", daysInactive));
            final ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                final UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                final String name = resultSet.getString("username");
                final String preferences = new String(resultSet.getBytes("preferences"), StandardCharsets.UTF_8);
                inactiveUsers.add(new SavedUser(
                        User.of(uuid, name),
                        resultSet.getTimestamp("last_login").toLocalDateTime()
                                .atOffset(OffsetDateTime.now().getOffset()),
                        resultSet.getLong("claim_blocks"),
                        plugin.getPreferencesFromJson(preferences)
                ));
            }
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to fetch list of inactive users", e);
            inactiveUsers.clear(); // Clear for safety to prevent any accidental data being returned
        }
        return inactiveUsers;
    }

    @Override
    public void createUser(@NotNull User user, long claimBlocks, @NotNull Preferences preferences) {
        try (PreparedStatement statement = getConnection().prepareStatement(format("""
                INSERT INTO `%user_data%` (`uuid`, `username`, `last_login`, `claim_blocks`, `preferences`)
                VALUES (?, ?, ?, ?)"""))) {
            statement.setString(1, user.getUuid().toString());
            statement.setString(2, user.getUsername());
            statement.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            statement.setLong(4, claimBlocks);
            statement.setBytes(5, plugin.getGson().toJson(preferences).getBytes(StandardCharsets.UTF_8));
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to create user in table", e);
        }
    }

    @Override
    public void updateUser(@NotNull User user, @NotNull OffsetDateTime lastLogin, long claimBlocks, @NotNull Preferences preferences) {
        try (PreparedStatement statement = getConnection().prepareStatement(format("""
                UPDATE `%user_data%`
                SET `username` = ?, `last_login` = ?, `claim_blocks` = ?, `preferences` = ?
                WHERE `uuid` = ?"""))) {
            statement.setString(1, user.getUsername());
            statement.setTimestamp(2, Timestamp.valueOf(lastLogin.toLocalDateTime()));
            statement.setLong(3, claimBlocks);
            statement.setBytes(4, plugin.getGson().toJson(preferences).getBytes(StandardCharsets.UTF_8));
            statement.setString(5, user.getUuid().toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to update user in table", e);
        }
    }

    @Override
    public void deleteAllUsers() {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute(format("DELETE FROM `%user_data%`"));
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to delete all users from table", e);
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to close connection", e);
        }
    }

}