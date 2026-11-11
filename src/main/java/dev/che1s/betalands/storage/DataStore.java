package dev.che1s.betalands.storage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.model.HomePoint;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.LandFlag;
import dev.che1s.betalands.model.LandPermission;
import dev.che1s.betalands.model.PlayerData;
import dev.che1s.betalands.model.Rect2i;
import dev.che1s.betalands.model.RentAccount;
import dev.che1s.betalands.service.LandManager;
import dev.che1s.betalands.util.Text;

public final class DataStore implements LandDataStore {
    private static final String VERSION = "betalands-v2";

    private final BetaLandsPlugin plugin;
    private final File landsFile;
    private final File membersFile;
    private final File playersFile;
    private final File rentsFile;
    private final File serverBankFile;
    private int loadErrors;

    public DataStore(BetaLandsPlugin plugin) {
        this.plugin = plugin;
        File folder = plugin.getDataFolder();
        this.landsFile = new File(folder, "lands.tsv");
        this.membersFile = new File(folder, "members.tsv");
        this.playersFile = new File(folder, "players.tsv");
        this.rentsFile = new File(folder, "rents.tsv");
        this.serverBankFile = new File(folder, "server-bank.tsv");
    }

    @Override
    public void load(LandManager manager, Map<String, PlayerData> players) throws IOException {
        manager.clear();
        players.clear();
        loadErrors = 0;
        if (landsFile.isFile()) {
            loadLands(manager);
        }
        if (membersFile.isFile()) {
            loadMembers(manager);
        }
        if (rentsFile.isFile()) {
            loadRentAccounts(manager);
        }
        if (playersFile.isFile()) {
            loadPlayers(players);
        }
        if (serverBankFile.isFile()) {
            loadServerBank(manager);
        }
        manager.rebuildIndexes();
        if (loadErrors > 0) {
            throw new IOException("Found " + loadErrors
                    + " invalid data line(s); automatic saving is disabled until they are fixed");
        }
    }

    private void loadLands(LandManager manager) throws IOException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(landsFile), StandardCharsets.UTF_8));
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.length() == 0 || line.charAt(0) == '#') {
                    continue;
                }
                try {
                    String[] fields = line.split("\t", -1);
                    if (fields.length < 22) {
                        throw new IllegalArgumentException("expected 22 fields, got " + fields.length);
                    }
                    List<Rect2i> areas = parseAreas(fields[11]);
                    if (areas.isEmpty()) {
                        throw new IllegalArgumentException("no areas");
                    }
                    Land land = new Land(
                            Text.unescape(fields[0]),
                            Text.unescape(fields[2]),
                            Text.unescape(fields[1]),
                            Text.unescape(fields[3]),
                            emptyToNull(Text.unescape(fields[4])),
                            areas,
                            Integer.parseInt(fields[5]),
                            Integer.parseInt(fields[6]),
                            Boolean.parseBoolean(fields[8]),
                            Long.parseLong(fields[9]),
                            Text.unescape(fields[10]),
                            HomePoint.parse(Text.unescape(fields[12]))
                    );
                    land.setFlagOverrides(parseFlags(fields[13]));
                    if (fields[14].length() > 0) {
                        land.setLevelId(Text.unescape(fields[14]));
                    }
                    if (fields[15].length() > 0) {
                        land.setBankBalance(Double.parseDouble(fields[15]));
                    }
                    if (fields[16].length() > 0) {
                        land.setLastMaintenanceAt(Long.parseLong(fields[16]));
                    }
                    land.setRentEnabled(Boolean.parseBoolean(fields[17]));
                    if (fields[19].length() > 0) {
                        land.setRentAmount(Double.parseDouble(fields[19]));
                    }
                    if (fields[21].length() > 0) {
                        land.setLastRentAt(Long.parseLong(fields[21]));
                    }
                    if (fields.length >= 23) {
                        land.setMaintenanceExempt(Boolean.parseBoolean(fields[22]));
                    }
                    if (fields.length >= 24
                            && fields[23].length() > 0) {
                        land.markDeleted(fields.length >= 25
                                        ? Text.unescape(fields[24]) : null,
                                Long.parseLong(fields[23]));
                    }
                    manager.addLoadedLand(land);
                } catch (RuntimeException exception) {
                    loadErrors++;
                    plugin.getMessages().warning("data.invalid-line",
                            "Skipped invalid {file} line {line}: {error}",
                            "file", "lands.tsv",
                            "line", Integer.toString(lineNumber),
                            "error", exception.getMessage());
                }
            }
        } finally {
            close(reader);
        }
    }

    private void loadMembers(LandManager manager) throws IOException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(membersFile), StandardCharsets.UTF_8));
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.length() == 0 || line.charAt(0) == '#') {
                    continue;
                }
                String[] fields = line.split("\t", -1);
                if (fields.length < 3) {
                    loadErrors++;
                    plugin.getMessages().warning("data.invalid-line",
                            "Skipped invalid {file} line {line}",
                            "file", "members.tsv",
                            "line", Integer.toString(lineNumber),
                            "error", "invalid field count");
                    continue;
                }
                Land land = manager.getByIdIncludingDeleted(
                        Text.unescape(fields[0]));
                if (land == null) {
                    continue;
                }
                EnumSet<LandPermission> permissions =
                        StorageCodec.parsePermissions(fields[2]);
                land.setMemberPermissions(Text.unescape(fields[1]), permissions);
            }
        } finally {
            close(reader);
        }
    }

    private void loadPlayers(Map<String, PlayerData> players) throws IOException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(playersFile), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() == 0 || line.charAt(0) == '#') {
                    continue;
                }
                String[] fields = line.split("\t", -1);
                if (fields.length < 6) {
                    loadErrors++;
                    continue;
                }
                try {
                    String key = Text.unescape(fields[0]);
                    PlayerData data = new PlayerData(
                            key,
                            Text.unescape(fields[1]),
                            Long.parseLong(fields[2])
                    );
                    data.setLevelId(Text.unescape(fields[3]));
                    if (Boolean.parseBoolean(fields[4])) {
                        data.setManagedLevel(Text.unescape(fields[3]),
                                Long.parseLong(fields[5]));
                    }
                    players.put(key, data);
                } catch (RuntimeException exception) {
                    loadErrors++;
                }
            }
        } finally {
            close(reader);
        }
    }

    private void loadServerBank(LandManager manager) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(serverBankFile), StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                String value = line.trim();
                if (value.length() == 0 || value.charAt(0) == '#') {
                    continue;
                }
                try {
                    manager.setServerMaintenanceBank(Double.parseDouble(value));
                } catch (NumberFormatException exception) {
                    loadErrors++;
                }
            }
        } finally {
            close(reader);
        }
    }

    private void loadRentAccounts(LandManager manager) throws IOException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(rentsFile), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() == 0 || line.charAt(0) == '#') {
                    continue;
                }
                String[] fields = line.split("\t", -1);
                if (fields.length < 3) {
                    loadErrors++;
                    continue;
                }
                if (fields.length >= 4) {
                    String playerName = Text.unescape(fields[1]);
                    RentAccount account =
                            manager.getOrCreateRentAccount(playerName);
                    account.setBalance(Double.parseDouble(fields[2]));
                    account.setDebtLandId(blankToNull(Text.unescape(fields[3])));
                    continue;
                }
                Land legacyLand = manager.getByIdIncludingDeleted(
                        Text.unescape(fields[0]));
                String playerName = Text.unescape(fields[1]);
                double balance = Double.parseDouble(fields[2]);
                RentAccount account = manager.getOrCreateRentAccount(playerName);
                account.addBalance(balance);
                if (balance < 0.0D && account.getDebtLandId() == null
                        && legacyLand != null) {
                    account.setDebtLandId(legacyLand.getId());
                }
            }
        } finally {
            close(reader);
        }
    }

    @Override
    public void save(LandManager manager, Map<String, PlayerData> players) throws IOException {
        saveSnapshot(LandDataSnapshot.capture(manager, System.currentTimeMillis()));
    }

    @Override
    public void saveSnapshot(LandDataSnapshot snapshot) throws IOException {
        ensureDirectory();
        saveLands(snapshot.getLands());
        saveMembers(snapshot.getLands());
        saveRentAccounts(snapshot.getRentAccounts());
        savePlayers(snapshot.getPlayers());
        saveServerBank(snapshot.getServerMaintenanceBank());
    }

    private void saveLands(List<Land> lands) throws IOException {
        File temporary = new File(landsFile.getParentFile(), landsFile.getName() + ".tmp");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(temporary), StandardCharsets.UTF_8));
        try {
            writer.write("# " + VERSION);
            writer.newLine();
            writer.write("# id\tname\towner\tworld\tparent\tminY\tmaxY\tpublic\tinherit\tcreated\tsource\tareas\thome\tflags\tlevel\tbank\tlast-maintenance\trent-enabled\trent-tenant\trent-amount\trent-balance\tlast-rent\tmaintenance-exempt\tdeleted-at\tdeleted-by\tis-deleted");
            writer.newLine();
            for (Land land : lands) {
                String areas = StorageCodec.areas(land.getAreas());
                writer.write(Text.escape(land.getId()));
                writer.write('\t');
                writer.write(Text.escape(land.getName()));
                writer.write('\t');
                writer.write(Text.escape(land.getOwner()));
                writer.write('\t');
                writer.write(Text.escape(land.getWorld()));
                writer.write('\t');
                writer.write(Text.escape(land.getParentId() == null ? "" : land.getParentId()));
                writer.write('\t');
                writer.write(Integer.toString(land.getMinY()));
                writer.write('\t');
                writer.write(Integer.toString(land.getMaxY()));
                writer.write('\t');
                writer.write("false");
                writer.write('\t');
                writer.write(Boolean.toString(land.inheritsParentPermissions()));
                writer.write('\t');
                writer.write(Long.toString(land.getCreatedAt()));
                writer.write('\t');
                writer.write(Text.escape(land.getSource()));
                writer.write('\t');
                writer.write(areas);
                writer.write('\t');
                writer.write(Text.escape(land.getHome() == null ? "-" : land.getHome().serialize()));
                writer.write('\t');
                writer.write(serializeFlags(land));
                writer.write('\t');
                writer.write(Text.escape(land.getLevelId()));
                writer.write('\t');
                writer.write(Double.toString(land.getBankBalance()));
                writer.write('\t');
                writer.write(Long.toString(land.getLastMaintenanceAt()));
                writer.write('\t');
                writer.write(Boolean.toString(land.isRentEnabled()));
                writer.write('\t');
                writer.write("");
                writer.write('\t');
                writer.write(Double.toString(land.getRentAmount()));
                writer.write('\t');
                writer.write("0.0");
                writer.write('\t');
                writer.write(Long.toString(land.getLastRentAt()));
                writer.write('\t');
                writer.write(Boolean.toString(land.isMaintenanceExempt()));
                writer.write('\t');
                writer.write(Long.toString(land.getDeletedAt()));
                writer.write('\t');
                writer.write(Text.escape(land.getDeletedBy() == null
                        ? "" : land.getDeletedBy()));
                writer.write('\t');
                writer.write(Boolean.toString(land.isDeleted()));
                writer.newLine();
            }
        } finally {
            close(writer);
        }
        replace(temporary, landsFile);
    }

    private void saveMembers(List<Land> lands) throws IOException {
        File temporary = new File(membersFile.getParentFile(), membersFile.getName() + ".tmp");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(temporary), StandardCharsets.UTF_8));
        try {
            writer.write("# " + VERSION + " members");
            writer.newLine();
            for (Land land : lands) {
                for (Map.Entry<String, EnumSet<LandPermission>> entry : land.getMembers().entrySet()) {
                    writer.write(Text.escape(land.getId()));
                    writer.write('\t');
                    writer.write(Text.escape(entry.getKey()));
                    writer.write('\t');
                    writer.write(StorageCodec.permissions(entry.getValue()));
                    writer.newLine();
                }
            }
        } finally {
            close(writer);
        }
        replace(temporary, membersFile);
    }

    private void saveRentAccounts(Map<String, RentAccount> accounts) throws IOException {
        File temporary = new File(rentsFile.getParentFile(), rentsFile.getName() + ".tmp");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(temporary), StandardCharsets.UTF_8));
        try {
            writer.write("# " + VERSION + " rent accounts");
            writer.newLine();
            writer.write("# player-key\tplayer-name\tbalance\tdebt-land-id");
            writer.newLine();
            for (Map.Entry<String, RentAccount> entry : accounts.entrySet()) {
                RentAccount account = entry.getValue();
                writer.write(Text.escape(entry.getKey()));
                writer.write('\t');
                writer.write(Text.escape(account.getPlayerName()));
                writer.write('\t');
                writer.write(Double.toString(account.getBalance()));
                writer.write('\t');
                writer.write(Text.escape(account.getDebtLandId() == null
                        ? "" : account.getDebtLandId()));
                writer.newLine();
            }
        } finally {
            close(writer);
        }
        replace(temporary, rentsFile);
    }

    private void savePlayers(Map<String, PlayerData> players) throws IOException {
        File temporary = new File(playersFile.getParentFile(), playersFile.getName() + ".tmp");
        long now = System.currentTimeMillis();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(temporary), StandardCharsets.UTF_8));
        try {
            writer.write("# " + VERSION + " players");
            writer.newLine();
            writer.write("# key\tlast-name\taccumulated-seconds\tlevel\tmanaged\tlevel-expires-at");
            writer.newLine();
            for (PlayerData data : players.values()) {
                writer.write(Text.escape(data.getKey()));
                writer.write('\t');
                writer.write(Text.escape(data.getLastName()));
                writer.write('\t');
                writer.write(Long.toString(data.getTotalSeconds(now)));
                writer.write('\t');
                writer.write(Text.escape(data.getLevelId()));
                writer.write('\t');
                writer.write(Boolean.toString(data.isLevelManaged()));
                writer.write('\t');
                writer.write(Long.toString(data.getLevelExpiresAt()));
                writer.newLine();
            }
        } finally {
            close(writer);
        }
        replace(temporary, playersFile);
    }

    private void saveServerBank(double balance) throws IOException {
        File temporary = new File(serverBankFile.getParentFile(),
                serverBankFile.getName() + ".tmp");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(temporary), StandardCharsets.UTF_8));
        try {
            writer.write("# BetaLands server maintenance bank");
            writer.newLine();
            writer.write(Double.toString(Math.max(0.0D, balance)));
            writer.newLine();
        } finally {
            close(writer);
        }
        replace(temporary, serverBankFile);
    }

    @Override
    public File backup(String label) throws IOException {
        File backupRoot = new File(plugin.getDataFolder(), "backups");
        if (!backupRoot.exists() && !backupRoot.mkdirs()) {
            throw new IOException("Could not create backup directory");
        }
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        File target = new File(backupRoot, label + "-" + timestamp);
        if (!target.exists() && !target.mkdirs()) {
            throw new IOException("Could not create backup directory: " + target);
        }
        copyIfPresent(landsFile, target);
        copyIfPresent(membersFile, target);
        copyIfPresent(playersFile, target);
        copyIfPresent(rentsFile, target);
        copyIfPresent(serverBankFile, target);
        return target;
    }

    private void copyIfPresent(File source, File targetDirectory) throws IOException {
        if (!source.isFile()) {
            return;
        }
        FileInputStream input = new FileInputStream(source);
        FileOutputStream output = new FileOutputStream(new File(targetDirectory, source.getName()));
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
        } finally {
            close(input);
            close(output);
        }
    }

    private void ensureDirectory() throws IOException {
        File folder = landsFile.getParentFile();
        if (folder != null && !folder.exists() && !folder.mkdirs()) {
            throw new IOException("Could not create data directory: " + folder);
        }
    }

    private void replace(File temporary, File target) throws IOException {
        try {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private List<Rect2i> parseAreas(String value) {
        return StorageCodec.parseAreas(value);
    }

    static Map<LandFlag, Boolean> parseFlags(String value) {
        return StorageCodec.parseFlags(value);
    }

    static String serializeFlags(Land land) {
        return StorageCodec.serializeFlags(land);
    }

    private String emptyToNull(String value) {
        return value == null || value.length() == 0 ? null : value;
    }

    private String blankToNull(String value) {
        return value == null || value.trim().length() == 0 ? null : value;
    }

    private void close(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }
}
