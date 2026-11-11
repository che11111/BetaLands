package dev.che1s.betalands.integration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import com.flowpowered.math.vector.Vector2d;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.ExtrudeMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.Rect2i;
import dev.che1s.betalands.service.LandManager;

public final class BlueMapIntegration {
    private static final String MARKER_SET_ID = "betalands";

    private final BetaLandsPlugin plugin;
    private final LandManager lands;
    private final Consumer<BlueMapAPI> enableConsumer;
    private final Consumer<BlueMapAPI> disableConsumer;
    private final Map<String, MapMarkers> renderedMaps =
            new HashMap<String, MapMarkers>();
    private BlueMapAPI currentApi;
    private int configuredMainColor = MapColor.AUTO;
    private int configuredSubclaimColor = MapColor.AUTO;

    public BlueMapIntegration(BetaLandsPlugin plugin) {
        this.plugin = plugin;
        this.lands = plugin.getLandManager();
        this.enableConsumer = new Consumer<BlueMapAPI>() {
            @Override
            public void accept(BlueMapAPI ignored) {
                plugin.scheduleBluemapRefresh();
            }
        };
        this.disableConsumer = new Consumer<BlueMapAPI>() {
            @Override
            public void accept(BlueMapAPI ignored) {
                renderedMaps.clear();
                currentApi = null;
            }
        };
        BlueMapAPI.onEnable(enableConsumer);
        BlueMapAPI.onDisable(disableConsumer);
    }

    public void refresh() {
        Optional<BlueMapAPI> optional = BlueMapAPI.getInstance();
        if (optional.isPresent()) {
            refresh(optional.get());
        }
    }

    public String diagnostic() {
        return BlueMapAPI.getInstance().isPresent()
                ? "hooked, api=ready" : "hooked, api=not-ready";
    }

    private void refresh(BlueMapAPI api) {
        configuredMainColor = MapColor.parse(
                plugin.getSettings().getBluemapMainColor());
        configuredSubclaimColor = MapColor.parse(
                plugin.getSettings().getBluemapSubclaimColor());
        if (api != currentApi) {
            renderedMaps.clear();
            currentApi = api;
        }
        String markerSetLabel = plugin.getMessages().map("bluemap.marker-set",
                "BetaLands Claims");
        Set<String> seenMaps = new HashSet<String>();
        Collection<BlueMapMap> maps = api.getMaps();
        for (BlueMapMap map : maps) {
            String mapKey = mapKey(map);
            seenMaps.add(mapKey);
            MarkerSet markerSet = map.getMarkerSets().get(MARKER_SET_ID);
            MapMarkers mapMarkers = renderedMaps.get(mapKey);
            if (mapMarkers == null || mapMarkers.markerSet != markerSet) {
                markerSet = ensureMarkerSet(map, markerSet, markerSetLabel);
                if (markerSet == null) {
                    continue;
                }
                mapMarkers = new MapMarkers(markerSet);
                renderedMaps.put(mapKey, mapMarkers);
            } else {
                configureMarkerSet(markerSet, markerSetLabel);
            }

            Set<String> seenLands = new HashSet<String>();
            for (Land land : lands.getLands()) {
                if (!belongsToMap(api, map, land)) {
                    continue;
                }
                if (land.isSubclaim() && !plugin.getSettings().shouldShowSubclaims()) {
                    continue;
                }
                String details = LandMapInfo.details(plugin, land);
                int color = configuredColor(land);
                long fingerprint = MapRenderFingerprint.of(land, details, color);
                LandMarkers previous = mapMarkers.lands.get(land.getId());
                seenLands.add(land.getId());
                if (previous != null && previous.fingerprint == fingerprint
                        && previous.existsIn(markerSet)) {
                    continue;
                }
                if (previous != null) {
                    previous.delete(markerSet);
                }
                LandMarkers updated = drawLand(markerSet, land, details, color);
                updated.fingerprint = fingerprint;
                mapMarkers.lands.put(land.getId(), updated);
            }
            Iterator<Map.Entry<String, LandMarkers>> iterator =
                    mapMarkers.lands.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, LandMarkers> entry = iterator.next();
                if (seenLands.contains(entry.getKey())) {
                    continue;
                }
                entry.getValue().delete(markerSet);
                iterator.remove();
            }
        }
        Iterator<String> mapIterator = renderedMaps.keySet().iterator();
        while (mapIterator.hasNext()) {
            if (!seenMaps.contains(mapIterator.next())) {
                mapIterator.remove();
            }
        }
    }

    private MarkerSet ensureMarkerSet(BlueMapMap map, MarkerSet existing, String label) {
        MarkerSet markerSet = existing;
        if (markerSet == null) {
            markerSet = new MarkerSet(label, true, false);
            configureMarkerSet(markerSet, label);
            map.getMarkerSets().put(MARKER_SET_ID, markerSet);
        } else {
            for (String markerId : new ArrayList<String>(
                    markerSet.getMarkers().keySet())) {
                markerSet.remove(markerId);
            }
            configureMarkerSet(markerSet, label);
        }
        return markerSet;
    }

    private void configureMarkerSet(MarkerSet markerSet, String label) {
        markerSet.setLabel(label);
        markerSet.setToggleable(true);
        markerSet.setDefaultHidden(false);
        markerSet.setSorting(40);
    }

    private String mapKey(BlueMapMap map) {
        return map.getId() + "|" + map.getWorld().getId();
    }

    private boolean belongsToMap(BlueMapAPI api, BlueMapMap map, Land land) {
        String mapWorldId = map.getWorld().getId();
        Optional<BlueMapWorld> landWorld = api.getWorld(land.getWorld());
        String resolvedWorldId = landWorld.isPresent() ? landWorld.get().getId() : null;
        return matchesWorld(land.getWorld(), mapWorldId, resolvedWorldId);
    }

    static boolean matchesWorld(String bukkitWorldName, String mapWorldId,
                                String resolvedWorldId) {
        return bukkitWorldName != null
                && (bukkitWorldName.equalsIgnoreCase(mapWorldId)
                || (resolvedWorldId != null
                && resolvedWorldId.equalsIgnoreCase(mapWorldId)));
    }

    private LandMarkers drawLand(MarkerSet markerSet, Land land,
                                 String details, int color) {
        LandMarkers result = new LandMarkers();
        int index = 0;
        List<ClaimBoundary.Component> components = ClaimBoundary.fromAreas(land.getAreas());
        for (ClaimBoundary.Component component : components) {
            Shape shape = shape(component.getOuter());
            Shape[] holes = new Shape[component.getHoles().size()];
            for (int holeIndex = 0; holeIndex < holes.length; holeIndex++) {
                holes[holeIndex] = shape(component.getHoles().get(holeIndex));
            }
            String markerId = (land.isSubclaim() ? "sub:" : "main:")
                    + land.getId() + ":" + index++;
            if (land.isSubclaim()) {
                ExtrudeMarker marker = ExtrudeMarker.builder()
                        .shape(shape, land.getMinY(), land.getMaxY() + 1)
                        .holes(holes)
                        .label(land.getDisplayName())
                        .detail(details)
                        .lineColor(color(color, 0.95F))
                        .fillColor(color(color, 0.24F))
                        .lineWidth(2)
                        .depthTestEnabled(true)
                        .build();
                markerSet.put(markerId, marker);
            } else {
                float y = land.getHome() == null
                        ? 127.0F : (float) land.getHome().getY() + 1.0F;
                ShapeMarker marker = ShapeMarker.builder()
                        .shape(shape, y)
                        .holes(holes)
                        .label(land.getDisplayName())
                        .detail(details)
                        .lineColor(color(color, 0.95F))
                        .fillColor(color(color, 0.20F))
                        .lineWidth(2)
                        .depthTestEnabled(false)
                        .build();
                markerSet.put(markerId, marker);
            }
            result.markerIds.add(markerId);
        }
        return result;
    }

    private int configuredColor(Land land) {
        int configured = land.isSubclaim()
                ? configuredSubclaimColor : configuredMainColor;
        if (configured >= 0) {
            return configured;
        }
        return land.isSubclaim() ? 0xffa640 : 0x40b4ff;
    }

    private Shape shape(ClaimBoundary.Boundary boundary) {
        Vector2d[] points = new Vector2d[boundary.size()];
        for (int index = 0; index < points.length; index++) {
            points[index] = new Vector2d(boundary.getX(index), boundary.getZ(index));
        }
        return new Shape(points);
    }

    private Color color(int rgb, float opacity) {
        return new Color(MapColor.red(rgb), MapColor.green(rgb), MapColor.blue(rgb), opacity);
    }

    public void disable() {
        BlueMapAPI.unregisterListener(enableConsumer);
        BlueMapAPI.unregisterListener(disableConsumer);
        renderedMaps.clear();
        currentApi = null;
    }

    private static final class MapMarkers {
        private final MarkerSet markerSet;
        private final Map<String, LandMarkers> lands =
                new HashMap<String, LandMarkers>();

        private MapMarkers(MarkerSet markerSet) {
            this.markerSet = markerSet;
        }
    }

    private static final class LandMarkers {
        private final List<String> markerIds = new ArrayList<String>();
        private long fingerprint;

        private boolean existsIn(MarkerSet markerSet) {
            if (markerIds.isEmpty()) {
                return false;
            }
            for (String markerId : markerIds) {
                if (markerSet.get(markerId) == null) {
                    return false;
                }
            }
            return true;
        }

        private void delete(MarkerSet markerSet) {
            for (String markerId : markerIds) {
                markerSet.remove(markerId);
            }
            markerIds.clear();
        }
    }
}
