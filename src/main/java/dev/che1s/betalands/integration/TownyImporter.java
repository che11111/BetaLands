package dev.che1s.betalands.integration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownyUniverse;
import com.palmergames.bukkit.towny.object.TownyWorld;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.model.HomePoint;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.LandFlag;
import dev.che1s.betalands.model.LandIdentity;
import dev.che1s.betalands.model.LandPermission;
import dev.che1s.betalands.model.Rect2i;
import dev.che1s.betalands.util.Text;

public final class TownyImporter {
    private static final int TOWNY_CELL_SIZE = 16;

    private final BetaLandsPlugin plugin;

    public TownyImporter(BetaLandsPlugin plugin) {
        this.plugin = plugin;
    }

    public TownyImportReport importData(File requestedSource) throws IOException {
        BuildContext context = captureBuildContext();
        PreparedImport prepared = hasLiveTowny()
                ? prepareLive(context)
                : prepareFiles(requestedSource, context);
        return build(prepared);
    }

    public boolean hasLiveTowny() {
        Plugin townyPlugin = plugin.getServer().getPluginManager().getPlugin("Towny");
        return townyPlugin != null && townyPlugin.isEnabled();
    }

    /**
     * Captures mutable plugin state on the server thread before an asynchronous build.
     */
    public BuildContext captureBuildContext() {
        Set<String> occupiedMainNames = new HashSet<String>();
        for (Land existing : plugin.getLandManager().getLands()) {
            if (!existing.isSubclaim() && !existing.getSource().startsWith("towny:")) {
                occupiedMainNames.add(Text.key(existing.getName()));
            }
        }
        return new BuildContext(occupiedMainNames,
                plugin.getSettings().getMinNameLength(),
                plugin.getSettings().getMaxNameLength(),
                plugin.getSettings().shouldImportResidentPlots(),
                plugin.getSettings().getDefaultMainRent());
    }

    /**
     * Reads Towny's live object graph. This must be called on the server thread.
     */
    public PreparedImport prepareLive(BuildContext context) {
        Plugin townyPlugin = plugin.getServer().getPluginManager().getPlugin("Towny");
        if (townyPlugin == null || !townyPlugin.isEnabled()) {
            throw new IllegalStateException("Towny is not enabled");
        }
        return new PreparedImport(readLive((Towny) townyPlugin),
                "loaded Towny plugin", context);
    }

    /**
     * Reads Towny flat-file data. This is safe to call from an asynchronous worker.
     */
    public PreparedImport prepareFiles(File requestedSource, BuildContext context)
            throws IOException {
        File source = requestedSource;
        if (source == null) {
            source = new File(plugin.getDataFolder().getParentFile(), "Towny");
        }
        if (!source.exists()) {
            throw new IOException("Towny folder not found: " + source.getAbsolutePath());
        }
        return new PreparedImport(readFiles(source, context.importResidentPlots),
                source.getAbsolutePath(), context);
    }

    private TownyData readLive(Towny towny) {
        TownyData data = new TownyData();
        TownyUniverse universe = towny.getTownyUniverse();
        for (TownyWorld world : universe.getWorlds()) {
            Collection<TownBlock> blocks = world.getTownBlocks();
            for (TownBlock block : blocks) {
                if (!block.hasTown()) {
                    continue;
                }
                Town town;
                try {
                    town = block.getTown();
                } catch (Exception exception) {
                    data.warnings.add("Skipped a town block without a readable town.");
                    continue;
                }
                String worldName = block.getWorld().getName();
                Draft draft = data.draft(town.getName(), worldName);
                draft.name = town.getName();
                draft.world = worldName;
                draft.pvp = town.isPVP();
                draft.explosionAllowed = town.isBANG();
                draft.fireAllowed = town.isFire();
                draft.mobsAllowed = town.hasMobs();
                draft.tramplingAllowed = !(world.isDisablePlayerTrample()
                        || world.isDisableCreatureTrample());
                try {
                    if (town.hasMayor()) {
                        draft.mayor = town.getMayor().getName();
                    }
                } catch (Exception exception) {
                    data.warnings.add("Town " + town.getName() + " has no readable mayor.");
                }
                for (Resident resident : town.getResidents()) {
                    if (!resident.isNPC()) {
                        draft.residents.add(resident.getName());
                    }
                }
                try {
                    if (town.hasSpawn()) {
                        draft.home = homeFromLocation(town.getSpawn());
                        if (draft.home != null && draft.world == null) {
                            draft.world = town.getSpawn().getWorld().getName();
                        }
                    }
                } catch (Exception exception) {
                    // The first claimed block is a safe fallback home.
                }
                draft.cells.add(pack(block.getX(), block.getZ()));
                if (block.hasResident()) {
                    try {
                        Resident resident = block.getResident();
                        if (!resident.isNPC()) {
                            draft.plotOwners.put(pack(block.getX(), block.getZ()), resident.getName());
                        }
                    } catch (Exception exception) {
                        data.warnings.add("Skipped an unreadable plot owner in " + town.getName() + ".");
                    }
                }
            }
        }
        return data;
    }

    private TownyData readFiles(File source, boolean importResidentPlots) throws IOException {
        File dataFolder = new File(source, "data");
        File townsFolder = new File(dataFolder, "towns");
        if (!townsFolder.isDirectory() && source.getName().equalsIgnoreCase("towns")) {
            townsFolder = source;
            dataFolder = source.getParentFile();
        }
        if (!townsFolder.isDirectory()) {
            throw new IOException("Towny towns folder not found under " + source.getAbsolutePath());
        }
        TownyData data = new TownyData();
        Map<String, Boolean> worldTrampling = readWorldTrampling(new File(dataFolder, "worlds"));
        File[] files = townsFolder.listFiles();
        if (files == null) {
            return data;
        }
        Arrays.sort(files);
        for (File file : files) {
            if (!file.isFile() || !file.getName().toLowerCase(Locale.ROOT).endsWith(".txt")) {
                continue;
            }
            Map<String, String> values = readKeyValues(file);
            String townName = removeExtension(file.getName());
            String mayor = first(values, "mayor", "");
            String residents = first(values, "residents", mayor);
            String townBlocks = first(values, "townBlocks", "");
            if (townBlocks.length() == 0) {
                data.warnings.add("Town " + townName + " has no townBlocks data.");
                continue;
            }
            String[] worldGroups = townBlocks.split("\\|");
            for (String worldGroup : worldGroups) {
                int separator = worldGroup.indexOf(':');
                if (separator <= 0) {
                    continue;
                }
                String worldName = worldGroup.substring(0, separator);
                Draft draft = data.draft(townName, worldName);
                draft.name = townName;
                draft.world = worldName;
                draft.mayor = mayor;
                draft.pvp = parseBoolean(first(values, "pvp", "false"));
                if (values.containsKey("bang")) {
                    draft.explosionAllowed = parseBoolean(values.get("bang"));
                }
                if (values.containsKey("fire")) {
                    draft.fireAllowed = parseBoolean(values.get("fire"));
                }
                if (values.containsKey("mobs")) {
                    draft.mobsAllowed = parseBoolean(values.get("mobs"));
                }
                if (worldTrampling.containsKey(worldName)) {
                    draft.tramplingAllowed = worldTrampling.get(worldName);
                }
                for (String resident : residents.split(",")) {
                    if (resident.trim().length() > 0) {
                        draft.residents.add(resident.trim());
                    }
                }
                String spawn = first(values, "spawn", "");
                draft.home = parseFileHome(spawn, worldName);
                parseTownBlockValue(worldGroup.substring(separator + 1), draft);
            }
        }
        if (importResidentPlots) {
            loadFilePlotOwners(new File(dataFolder, "residents"), data);
        }
        return data;
    }

    private Map<String, Boolean> readWorldTrampling(File worldsFolder) throws IOException {
        Map<String, Boolean> result = new LinkedHashMap<String, Boolean>();
        if (!worldsFolder.isDirectory()) {
            return result;
        }
        File[] files = worldsFolder.listFiles();
        if (files == null) {
            return result;
        }
        for (File file : files) {
            if (!file.isFile() || !file.getName().toLowerCase(Locale.ROOT).endsWith(".txt")) {
                continue;
            }
            Map<String, String> values = readKeyValues(file);
            String playerTrample = values.get("disableplayertrample");
            String creatureTrample = values.get("disablecreaturetrample");
            if (playerTrample != null || creatureTrample != null) {
                boolean disabled = parseBoolean(playerTrample) || parseBoolean(creatureTrample);
                result.put(removeExtension(file.getName()), Boolean.valueOf(!disabled));
            }
        }
        return result;
    }

    private void loadFilePlotOwners(File residentsFolder, TownyData data) throws IOException {
        if (!residentsFolder.isDirectory()) {
            return;
        }
        File[] files = residentsFolder.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!file.isFile() || !file.getName().toLowerCase(Locale.ROOT).endsWith(".txt")) {
                continue;
            }
            String resident = removeExtension(file.getName());
            Map<String, String> values = readKeyValues(file);
            String townBlocks = first(values, "townBlocks", "");
            for (String worldGroup : townBlocks.split("\\|")) {
                int separator = worldGroup.indexOf(':');
                if (separator <= 0) {
                    continue;
                }
                String worldName = worldGroup.substring(0, separator);
                for (long cell : parseCellList(worldGroup.substring(separator + 1))) {
                    PlotAssignment assignment =
                            new PlotAssignment(resident, worldName, (int) (cell >> 32), (int) cell);
                    data.filePlots.put(assignment.key(), assignment);
                }
            }
        }
    }

    public TownyImportReport build(PreparedImport prepared) {
        TownyData data = prepared.data;
        BuildContext context = prepared.context;
        List<Land> result = new ArrayList<Land>();
        Map<String, Land> mainById = new HashMap<String, Land>();
        Map<String, Draft> draftByClaimedCell = new HashMap<String, Draft>();
        Set<String> occupiedMainNames = new HashSet<String>(context.occupiedMainNames);
        long totalBlocks = 0L;
        int plotCount = 0;
        for (Draft draft : data.towns.values()) {
            if (draft.cells.isEmpty()) {
                continue;
            }
            String owner = draft.mayor == null || draft.mayor.length() == 0
                    ? "towny-" + Text.slug(draft.name) : draft.mayor;
            String mainName = chooseUniqueMainName(draft.name, occupiedMainNames,
                    context.minNameLength, context.maxNameLength);
            String id = stableTownyId(draft.world, "towny-" + Text.slug(draft.name));
            List<Rect2i> areas = decompose(draft.cells);
            long blocks = 0L;
            for (Rect2i area : areas) {
                blocks += area.area();
            }
            HomePoint home = draft.home;
            if (home == null) {
                long first = draft.cells.iterator().next();
                int chunkX = (int) (first >> 32);
                int chunkZ = (int) first;
                home = new HomePoint(chunkX * TOWNY_CELL_SIZE + 8.5D, 65.0D,
                        chunkZ * TOWNY_CELL_SIZE + 8.5D, 0.0F, 0.0F);
            }
            Land main = new Land(id, owner, mainName, draft.world,
                    null, areas, 0, 127, true,
                    System.currentTimeMillis(), "towny:" + draft.name, home);
            mainById.put(Text.key(id), main);
            for (long cell : draft.cells) {
                draftByClaimedCell.put(worldCellKey(draft.world, cell), draft);
            }
            main.setRentAmount(context.defaultMainRent);
            main.setFlagOverride(LandFlag.PVP, Boolean.valueOf(draft.pvp));
            if (draft.explosionAllowed != null) {
                main.setFlagOverride(LandFlag.EXPLOSION, draft.explosionAllowed);
            }
            if (draft.fireAllowed != null) {
                main.setFlagOverride(LandFlag.FIRE, draft.fireAllowed);
            }
            if (draft.mobsAllowed != null) {
                main.setFlagOverride(LandFlag.MOBS, draft.mobsAllowed);
            }
            if (draft.tramplingAllowed != null) {
                main.setFlagOverride(LandFlag.TRAMPLING, draft.tramplingAllowed);
            }
            for (String resident : draft.residents) {
                EnumSet<LandPermission> permissions =
                        EnumSet.copyOf(LandPermission.defaultsForMember(draft.pvp));
                main.setMemberPermissions(resident, permissions);
            }
            result.add(main);
            totalBlocks += blocks;

            if (context.importResidentPlots) {
                for (Map.Entry<Long, String> plot : draft.plotOwners.entrySet()) {
                    Land sub = buildPlot(main, plot.getKey(), plot.getValue());
                    if (sub != null) {
                        result.add(sub);
                        plotCount++;
                    }
                }
            }
        }

        if (context.importResidentPlots) {
            for (PlotAssignment assignment : data.filePlots.values()) {
                Draft matching = draftByClaimedCell.get(assignment.key());
                if (matching == null) {
                    continue;
                }
                String mainId = stableTownyId(matching.world,
                        "towny-" + Text.slug(matching.name));
                Land main = mainById.get(Text.key(mainId));
                if (main == null) {
                    continue;
                }
                long cell = pack(assignment.x, assignment.z);
                if (matching.plotOwners.containsKey(cell)) {
                    continue;
                }
                Land sub = buildPlot(main, cell, assignment.owner);
                if (sub != null) {
                    result.add(sub);
                    plotCount++;
                }
            }
        }

        return new TownyImportReport(prepared.source, result, data.towns.size(), plotCount,
                totalBlocks, data.warnings);
    }

    private static String worldCellKey(String world, long cell) {
        return Text.key(world) + ":" + (int) (cell >> 32) + ":" + (int) cell;
    }

    private Land buildPlot(Land main, long packedCell, String plotOwner) {
        int chunkX = (int) (packedCell >> 32);
        int chunkZ = (int) packedCell;
        String name = "plot-" + chunkX + "-" + chunkZ;
        String id = stableTownyId(main.getId(), name);
        List<Rect2i> areas = new ArrayList<Rect2i>();
        areas.add(new Rect2i(chunkX * TOWNY_CELL_SIZE, chunkZ * TOWNY_CELL_SIZE,
                chunkX * TOWNY_CELL_SIZE + TOWNY_CELL_SIZE - 1,
                chunkZ * TOWNY_CELL_SIZE + TOWNY_CELL_SIZE - 1));
        Land plot = new Land(id, plotOwner, name, main.getWorld(), main.getId(),
                areas, 0, 127, false, System.currentTimeMillis(),
                main.getSource() + ":plot", null);
        return plot;
    }

    private String stableTownyId(String scope, String name) {
        return LandIdentity.stableTownyId(scope, name);
    }

    static String chooseUniqueMainName(String townName, Set<String> occupiedNames,
                                       int minLength, int maxLength) {
        String base = Text.slugPreservingCase(townName);
        if (base.length() < minLength) {
            base = fitName(base + "-town", maxLength);
        }
        if (base.length() > maxLength) {
            base = fitName(base, maxLength);
        }
        if (occupiedNames.add(Text.key(base))) {
            return base;
        }
        String prefix = "towny-";
        String candidate = fitName(prefix + base, maxLength);
        int suffix = 2;
        while (!occupiedNames.add(Text.key(candidate))) {
            candidate = fitName(prefix + base + "-" + suffix, maxLength);
            suffix++;
        }
        return candidate;
    }

    private static String fitName(String value, int maxLength) {
        int safeLength = Math.max(1, maxLength);
        return value.length() <= safeLength ? value : value.substring(0, safeLength);
    }

    private void parseTownBlockValue(String value, Draft draft) {
        for (long cell : parseCellList(value)) {
            draft.cells.add(cell);
        }
    }

    private Set<Long> parseCellList(String value) {
        Set<Long> cells = new LinkedHashSet<Long>();
        for (String entry : value.split(";")) {
            String clean = entry.trim();
            int closingBracket = clean.indexOf(']');
            if (clean.startsWith("[") && closingBracket >= 0) {
                clean = clean.substring(closingBracket + 1);
            }
            String[] fields = clean.split(",");
            if (fields.length < 2) {
                continue;
            }
            try {
                cells.add(pack(Integer.parseInt(fields[0].trim()), Integer.parseInt(fields[1].trim())));
            } catch (NumberFormatException ignored) {
            }
        }
        return cells;
    }

    static List<Rect2i> decompose(Set<Long> chunks) {
        Set<Long> remaining = new HashSet<Long>(chunks);
        List<Rect2i> result = new ArrayList<Rect2i>();
        List<Long> sorted = new ArrayList<Long>(chunks);
        sorted.sort((first, second) -> {
            int firstZ = (int) first.longValue();
            int secondZ = (int) second.longValue();
            if (firstZ != secondZ) {
                return firstZ < secondZ ? -1 : 1;
            }
            int firstX = (int) (first.longValue() >> 32);
            int secondX = (int) (second.longValue() >> 32);
            return firstX < secondX ? -1 : (firstX == secondX ? 0 : 1);
        });
        for (long start : sorted) {
            if (!remaining.contains(start)) {
                continue;
            }
            int startX = (int) (start >> 32);
            int startZ = (int) start;
            int endX = startX;
            while (remaining.contains(pack(endX + 1, startZ))) {
                endX++;
            }
            int endZ = startZ;
            while (hasFullRow(remaining, startX, endX, endZ + 1)) {
                endZ++;
            }
            for (int x = startX; x <= endX; x++) {
                for (int z = startZ; z <= endZ; z++) {
                    remaining.remove(pack(x, z));
                }
            }
            result.add(new Rect2i(startX * TOWNY_CELL_SIZE, startZ * TOWNY_CELL_SIZE,
                    endX * TOWNY_CELL_SIZE + TOWNY_CELL_SIZE - 1,
                    endZ * TOWNY_CELL_SIZE + TOWNY_CELL_SIZE - 1));
        }
        return result;
    }

    private static boolean hasFullRow(Set<Long> cells, int startX, int endX, int z) {
        for (int x = startX; x <= endX; x++) {
            if (!cells.contains(pack(x, z))) {
                return false;
            }
        }
        return true;
    }

    static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private HomePoint homeFromLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return new HomePoint(location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    private HomePoint parseFileHome(String value, String fallbackWorld) {
        String[] fields = value.split(",");
        if (fields.length < 4) {
            return null;
        }
        try {
            return new HomePoint(Double.parseDouble(fields[1]), Double.parseDouble(fields[2]),
                    Double.parseDouble(fields[3]), 0.0F, 0.0F);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Map<String, String> readKeyValues(File file) throws IOException {
        Map<String, String> values = new LinkedHashMap<String, String>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                int separator = line.indexOf('=');
                if (separator > 0) {
                    values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
                }
            }
        } finally {
            reader.close();
        }
        return values;
    }

    private String first(Map<String, String> values, String key, String fallback) {
        String result = values.get(key);
        return result == null ? fallback : result;
    }

    private boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
    }

    private String removeExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    public static final class BuildContext {
        private final Set<String> occupiedMainNames;
        private final int minNameLength;
        private final int maxNameLength;
        private final boolean importResidentPlots;
        private final double defaultMainRent;

        private BuildContext(Set<String> occupiedMainNames, int minNameLength,
                             int maxNameLength, boolean importResidentPlots,
                             double defaultMainRent) {
            this.occupiedMainNames = new HashSet<String>(occupiedMainNames);
            this.minNameLength = minNameLength;
            this.maxNameLength = maxNameLength;
            this.importResidentPlots = importResidentPlots;
            this.defaultMainRent = defaultMainRent;
        }
    }

    public static final class PreparedImport {
        private final TownyData data;
        private final String source;
        private final BuildContext context;

        private PreparedImport(TownyData data, String source, BuildContext context) {
            this.data = data;
            this.source = source;
            this.context = context;
        }

        public String getSource() {
            return source;
        }
    }

    private static final class TownyData {
        private final Map<String, Draft> towns = new LinkedHashMap<String, Draft>();
        private final Map<String, PlotAssignment> filePlots = new LinkedHashMap<String, PlotAssignment>();
        private final List<String> warnings = new ArrayList<String>();

        private Draft draft(String town, String world) {
            String key = Text.key(town) + "|" + Text.key(world);
            Draft draft = towns.get(key);
            if (draft == null) {
                draft = new Draft();
                towns.put(key, draft);
            }
            return draft;
        }
    }

    private static final class Draft {
        private String name;
        private String world;
        private String mayor;
        private boolean pvp;
        private Boolean explosionAllowed;
        private Boolean fireAllowed;
        private Boolean mobsAllowed;
        private Boolean tramplingAllowed;
        private HomePoint home;
        private final Set<Long> cells = new LinkedHashSet<Long>();
        private final Set<String> residents = new LinkedHashSet<String>();
        private final Map<Long, String> plotOwners = new HashMap<Long, String>();
    }

    private static final class PlotAssignment {
        private final String owner;
        private final String world;
        private final int x;
        private final int z;

        private PlotAssignment(String owner, String world, int x, int z) {
            this.owner = owner;
            this.world = world;
            this.x = x;
            this.z = z;
        }

        private String key() {
            return Text.key(world) + ":" + x + ":" + z;
        }
    }
}
