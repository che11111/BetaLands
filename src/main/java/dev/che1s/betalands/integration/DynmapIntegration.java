package dev.che1s.betalands.integration;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.Rect2i;

/**
 * Dynmap 0.24 integration.
 *
 * Keep this implementation aligned with the verified alpha2.3.0 drawing path:
 * both marker sets are deleted and rebuilt as persistent sets on every refresh.
 */
public final class DynmapIntegration {
    private static final String MAIN_SET_ID = "betalands";
    private static final String SUB_SET_ID = "betalands_subclaims";
    private static final double OUTLINE_HALF_WIDTH = 0.005D;

    private final BetaLandsPlugin plugin;
    private final DynmapAPI dynmap;
    private int configuredMainColor = MapColor.AUTO;
    private int configuredSubclaimColor = MapColor.AUTO;

    public DynmapIntegration(BetaLandsPlugin plugin, Plugin dynmapPlugin) {
        this.plugin = plugin;
        this.dynmap = (DynmapAPI) dynmapPlugin;
    }

    public boolean refresh() {
        configuredMainColor = MapColor.parse(
                plugin.getSettings().getDynmapMainColor());
        configuredSubclaimColor = MapColor.parse(
                plugin.getSettings().getDynmapSubclaimColor());
        if (!dynmap.markerAPIInitialized()) {
            return false;
        }
        MarkerAPI api = dynmap.getMarkerAPI();
        if (api == null) {
            return false;
        }

        replaceSet(api, MAIN_SET_ID,
                plugin.getMessages().map("dynmap.main-set",
                        "BetaLands Main Claims"), false);
        replaceSet(api, SUB_SET_ID,
                plugin.getMessages().map("dynmap.subclaim-set",
                        "BetaLands 3D Subclaims"), true);

        MarkerSet mainSet = api.getMarkerSet(MAIN_SET_ID);
        MarkerSet subSet = api.getMarkerSet(SUB_SET_ID);
        if (mainSet == null) {
            return false;
        }

        List<Land> lands = plugin.getLandManager().getLands();
        Collections.sort(lands, (first, second) ->
                first.getId().compareTo(second.getId()));
        for (Land land : lands) {
            MarkerSet set = land.isSubclaim() ? subSet : mainSet;
            if (set != null) {
                drawLand(set, land);
            }
        }
        int markerCount = mainSet.getAreaMarkers().size()
                + (subSet == null ? 0 : subSet.getAreaMarkers().size());
        plugin.getMessages().info("maps.dynmap-updated",
                "Dynmap rebuilt {lands} land(s) with {markers} marker(s).",
                "lands", Integer.toString(lands.size()),
                "markers", Integer.toString(markerCount));
        return true;
    }

    private void replaceSet(MarkerAPI api, String id, String label, boolean hidden) {
        MarkerSet existing = api.getMarkerSet(id);
        if (existing != null) {
            existing.deleteMarkerSet();
        }
        Set<MarkerIcon> icons = new HashSet<MarkerIcon>();
        MarkerSet created = api.createMarkerSet(id, label, icons, true);
        if (created != null) {
            created.setHideByDefault(hidden);
            created.setLayerPriority(20);
        }
    }

    private void drawLand(MarkerSet set, Land land) {
        World world = plugin.getServer().getWorld(land.getWorld());
        if (world == null) {
            return;
        }
        int color = configuredColor(land);
        int rectIndex = 0;
        for (Rect2i area : land.getAreas()) {
            String markerId = (land.isSubclaim() ? "sub:" : "main:")
                    + land.getId() + ":" + rectIndex++;
            double[] x = {area.getMinX(), area.getMaxX() + 1,
                    area.getMaxX() + 1, area.getMinX()};
            double[] z = {area.getMinZ(), area.getMinZ(),
                    area.getMaxZ() + 1, area.getMaxZ() + 1};
            String label = rectIndex == 1 ? land.getDisplayName() : "";
            AreaMarker marker = set.createAreaMarker(markerId, label,
                    false, world.getName(), x, z, false);
            if (marker == null) {
                continue;
            }
            marker.setDescription(LandMapInfo.details(plugin, land));
            marker.setLineStyle(0, 0.0D, color);
            marker.setFillStyle(land.isSubclaim() ? 0.32D : 0.20D, color);
            marker.setRangeY(land.isSubclaim() ? land.getMinY() : 0,
                    land.isSubclaim() ? land.getMaxY() + 1 : 128);
        }

        List<ClaimBoundary.Component> components =
                ClaimBoundary.fromAreas(land.getAreas());
        int outlineIndex = 0;
        for (ClaimBoundary.Component component : components) {
            outlineIndex = drawOutlineEdges(set, land, world, color,
                    component.getOuter(), outlineIndex);
            for (ClaimBoundary.Boundary hole : component.getHoles()) {
                outlineIndex = drawOutlineEdges(set, land, world, color,
                        hole, outlineIndex);
            }
        }
    }

    private int drawOutlineEdges(MarkerSet set, Land land, World world,
                                 int color, ClaimBoundary.Boundary boundary,
                                 int startIndex) {
        int markerIndex = startIndex;
        for (int index = 0; index < boundary.size(); index++) {
            int next = (index + 1) % boundary.size();
            double x1 = boundary.getX(index);
            double z1 = boundary.getZ(index);
            double x2 = boundary.getX(next);
            double z2 = boundary.getZ(next);
            double minX = Math.min(x1, x2);
            double maxX = Math.max(x1, x2);
            double minZ = Math.min(z1, z2);
            double maxZ = Math.max(z1, z2);
            if (x1 == x2) {
                minX -= OUTLINE_HALF_WIDTH;
                maxX += OUTLINE_HALF_WIDTH;
            } else {
                minZ -= OUTLINE_HALF_WIDTH;
                maxZ += OUTLINE_HALF_WIDTH;
            }
            double[] x = {minX, maxX, maxX, minX};
            double[] z = {minZ, minZ, maxZ, maxZ};
            String markerId = (land.isSubclaim() ? "sub-outline:" : "main-outline:")
                    + land.getId() + ":" + markerIndex;
            AreaMarker marker = set.createAreaMarker(markerId,
                    index == 0 ? land.getDisplayName() : "",
                    false, world.getName(), x, z, false);
            if (marker == null) {
                continue;
            }
            marker.setDescription(LandMapInfo.details(plugin, land));
            marker.setLineStyle(2, 0.95D, color);
            marker.setFillStyle(0.0D, color);
            marker.setRangeY(land.isSubclaim() ? land.getMinY() : 0,
                    land.isSubclaim() ? land.getMaxY() + 1 : 128);
            markerIndex++;
        }
        return markerIndex;
    }

    private int configuredColor(Land land) {
        int configured = land.isSubclaim()
                ? configuredSubclaimColor : configuredMainColor;
        return configured >= 0 ? configured : colorFor(land.getOwner());
    }

    private int colorFor(String owner) {
        int hash = owner == null ? 0 : owner.toLowerCase(Locale.ROOT).hashCode();
        int red = 64 + Math.abs(hash % 160);
        int green = 64 + Math.abs((hash / 31) % 160);
        int blue = 64 + Math.abs((hash / 127) % 160);
        return (red << 16) | (green << 8) | blue;
    }

    public void disable() {
        // Persistent sets are intentionally left in place until the next rebuild.
    }

    public String diagnostic() {
        if (!dynmap.markerAPIInitialized()) {
            return "api=not-ready";
        }
        MarkerAPI api = dynmap.getMarkerAPI();
        if (api == null) {
            return "api=missing";
        }
        MarkerSet main = api.getMarkerSet(MAIN_SET_ID);
        MarkerSet sub = api.getMarkerSet(SUB_SET_ID);
        List<Land> lands = plugin.getLandManager().getLands();
        int drawableWorlds = 0;
        for (Land land : lands) {
            if (plugin.getServer().getWorld(land.getWorld()) != null) {
                drawableWorlds++;
            }
        }
        return "api=ready, mainAreas="
                + (main == null ? "missing" : Integer.toString(main.getAreaMarkers().size()))
                + ", subAreas="
                + (sub == null ? "missing" : Integer.toString(sub.getAreaMarkers().size()))
                + ", landWorlds=" + drawableWorlds + "/" + lands.size();
    }
}
