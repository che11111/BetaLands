package dev.che1s.betalands.command;

import java.io.File;

import org.bukkit.command.CommandSender;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.integration.TownyImportReport;
import dev.che1s.betalands.integration.TownyImporter;
import dev.che1s.betalands.service.LandImportResult;

/**
 * Runs Towny migration without keeping its asynchronous workflow in the main
 * administrative command handler.
 */
final class TownyMigrationCommandHandler extends CommandSupport {
    private boolean running;

    TownyMigrationCommandHandler(BetaLandsPlugin plugin) {
        super(plugin);
    }

    boolean handle(CommandSender sender, String[] args) {
        if (running) {
            msg(sender, "migration.busy",
                    "&eA Towny migration is already running. Wait for it to finish.");
            return true;
        }
        StringBuilder path = new StringBuilder();
        for (int index = 2; index < args.length; index++) {
            if (path.length() > 0) {
                path.append(' ');
            }
            path.append(args[index]);
        }
        File source = path.length() == 0 ? null : new File(path.toString());
        String sourceLabel = source == null ? "auto-detect/live Towny"
                : source.getAbsolutePath();
        running = true;
        msg(sender, "migration.reading",
                "&eReading and converting Towny data in the background...");
        plugin.getMessages().info("towny.migration-start",
                "Towny migration started. Source={source}",
                "source", sourceLabel);
        final TownyImporter importer = new TownyImporter(plugin);
        final TownyImporter.BuildContext context;
        final TownyImporter.PreparedImport live;
        try {
            context = importer.captureBuildContext();
            live = importer.hasLiveTowny()
                    ? importer.prepareLive(context) : null;
        } catch (RuntimeException exception) {
            running = false;
            failMigration(sender, exception);
            return true;
        }
        final File requested = source;
        final CommandSender commandSender = sender;
        boolean scheduled = plugin.runStorageTaskAfterSave(new Runnable() {
            @Override
            public void run() {
                importTowny(commandSender, requested, context, live, importer);
            }
        }, new java.util.function.Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) {
                runOnServerThread(new Runnable() {
                    @Override
                    public void run() {
                        running = false;
                        failMigration(commandSender, throwable);
                    }
                });
            }
        });
        if (!scheduled) {
            running = false;
            failMigration(sender, new IllegalStateException(
                    "The background storage worker is unavailable"));
        }
        return true;
    }

    private void importTowny(CommandSender sender, File requested,
                             TownyImporter.BuildContext context,
                             TownyImporter.PreparedImport live,
                             TownyImporter importer) {
        try {
            File backup = plugin.getDataStore()
                    .backup("before-towny-migration");
            plugin.getMessages().info("towny.migration-backup",
                    "BetaLands data backed up to {path}",
                    "path", backup.getAbsolutePath());
            plugin.getMessages().info("towny.migration-converting",
                    "Reading and converting Towny town blocks in the background...");
            TownyImporter.PreparedImport prepared = live != null
                    ? live : importer.prepareFiles(requested, context);
            TownyImportReport report = importer.build(prepared);
            plugin.getMessages().info("towny.migration-applying",
                    "Converted {count} land entries. Applying them to BetaLands...",
                    "count", Integer.toString(report.getLands().size()));
            final File backupFolder = backup;
            runOnServerThread(new Runnable() {
                @Override
                public void run() {
                    completeMigration(sender, report, backupFolder);
                }
            });
        } catch (Exception exception) {
            runOnServerThread(new Runnable() {
                @Override
                public void run() {
                    running = false;
                    failMigration(sender, exception);
                }
            });
        }
    }

    private void completeMigration(CommandSender sender,
                                   TownyImportReport report, File backup) {
        running = false;
        if (report.getLands().isEmpty()) {
            msg(sender, "migration.empty",
                    "&cNo Towny town lands found. Existing data was not changed.");
            plugin.getMessages().warning("towny.migration-empty",
                    "Towny migration found no town lands. Existing data was not changed.");
            return;
        }
        LandImportResult merge;
        try {
            merge = plugin.getLandManager().mergeImported(report.getLands());
        } catch (RuntimeException exception) {
            failMigration(sender, exception);
            return;
        }
        if (!plugin.requestSaveAsync(true)) {
            msg(sender, "migration.save-failed",
                    "&cTowny import reached memory but background saving could not be queued.");
            plugin.getMessages().severe("towny.migration-save-failed",
                    "Towny data reached memory but background saving could not be queued.");
            return;
        }
        refreshMaps();
        msg(sender, "migration.success",
                "&aSUCCESS: Towny migration complete.");
        msg(sender, "migration.applied",
                "&aLand data was applied immediately. Background saving and map refresh were queued. No plugin reload is required.");
        msg(sender, "migration.source", "&7Source: {source}",
                "source", report.getSource());
        msg(sender, "migration.counts",
                "&7Towns={towns}, blocks={blocks}, 3D-subclaims={subclaims}",
                "towns", Integer.toString(report.getTowns()),
                "blocks", Long.toString(report.getClaimedBlocks()),
                "subclaims", Integer.toString(report.getPlots()));
        msg(sender, "migration.result",
                "&7Added={added}, updated={updated}, conflicts={conflicts}",
                "added", Integer.toString(merge.getAdded()),
                "updated", Integer.toString(merge.getUpdated()),
                "conflicts", Integer.toString(merge.getConflicts()));
        for (String warning : report.getWarnings()) {
            msg(sender, "migration.warning", "&eWARNING: {warning}",
                    "warning", warning);
            plugin.getMessages().warning("towny.migration-warning",
                    "Towny migration warning: {warning}",
                    "warning", warning);
        }
        msg(sender, "migration.backup", "&7Backup: {path}",
                "path", backup.getAbsolutePath());
        plugin.getMessages().info("towny.migration-complete",
                "Towny migration complete. source={source}, towns={towns}, blocks={blocks}, subclaims={subclaims}, added={added}, updated={updated}, conflicts={conflicts}",
                "source", report.getSource(),
                "towns", Integer.toString(report.getTowns()),
                "blocks", Long.toString(report.getClaimedBlocks()),
                "subclaims", Integer.toString(report.getPlots()),
                "added", Integer.toString(merge.getAdded()),
                "updated", Integer.toString(merge.getUpdated()),
                "conflicts", Integer.toString(merge.getConflicts()));
        auditLand(sender, "TOWNY_MIGRATION", null, null,
                "Towns=" + report.getTowns()
                        + ", blocks=" + report.getClaimedBlocks()
                        + ", subclaims=" + report.getPlots());
    }

    private void failMigration(CommandSender sender, Throwable throwable) {
        String error = throwable.getMessage() == null
                ? throwable.toString() : throwable.getMessage();
        msg(sender, "migration.failed",
                "&cERROR: Towny migration failed: {error}", "error", error);
        plugin.getMessages().warning("towny.import-failed",
                "Towny import failed: {error}", "error", error);
    }
}
