package dev.che1s.betalands.integration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import dev.che1s.betalands.model.Rect2i;

/**
 * Converts a union of rectangles into connected components with simplified
 * rectilinear outer and hole boundaries.
 */
public final class ClaimBoundary {
    private static final int EAST = 0;
    private static final int NORTH = 1;
    private static final int WEST = 2;
    private static final int SOUTH = 3;

    private ClaimBoundary() {
    }

    public static List<Component> fromAreas(List<Rect2i> areas) {
        List<Component> result = new ArrayList<Component>();
        if (areas == null || areas.isEmpty()) {
            return result;
        }

        TreeSet<Integer> xCoordinates = new TreeSet<Integer>();
        TreeSet<Integer> zCoordinates = new TreeSet<Integer>();
        for (Rect2i area : areas) {
            xCoordinates.add(Integer.valueOf(area.getMinX()));
            xCoordinates.add(Integer.valueOf(area.getMaxX() + 1));
            zCoordinates.add(Integer.valueOf(area.getMinZ()));
            zCoordinates.add(Integer.valueOf(area.getMaxZ() + 1));
        }
        int[] xs = toArray(xCoordinates);
        int[] zs = toArray(zCoordinates);
        boolean[][] covered = new boolean[Math.max(0, xs.length - 1)][Math.max(0, zs.length - 1)];

        for (Rect2i area : areas) {
            int minX = Arrays.binarySearch(xs, area.getMinX());
            int maxX = Arrays.binarySearch(xs, area.getMaxX() + 1);
            int minZ = Arrays.binarySearch(zs, area.getMinZ());
            int maxZ = Arrays.binarySearch(zs, area.getMaxZ() + 1);
            for (int x = minX; x < maxX; x++) {
                for (int z = minZ; z < maxZ; z++) {
                    covered[x][z] = true;
                }
            }
        }

        List<Edge> edges = boundaryEdges(covered, xs, zs);
        Map<Vertex, List<Edge>> outgoing = new LinkedHashMap<Vertex, List<Edge>>();
        for (Edge edge : edges) {
            List<Edge> list = outgoing.get(edge.start);
            if (list == null) {
                list = new ArrayList<Edge>();
                outgoing.put(edge.start, list);
            }
            list.add(edge);
        }

        List<Boundary> outers = new ArrayList<Boundary>();
        List<Boundary> holes = new ArrayList<Boundary>();
        Set<Edge> used = new HashSet<Edge>();
        for (Edge first : edges) {
            if (used.contains(first)) {
                continue;
            }
            List<Vertex> points = trace(first, outgoing, used, edges.size());
            if (points.size() < 4) {
                continue;
            }
            Boundary boundary = new Boundary(simplify(points));
            if (boundary.signedArea > 0.0D) {
                outers.add(boundary);
            } else if (boundary.signedArea < 0.0D) {
                holes.add(boundary);
            }
        }

        outers.sort((first, second) -> Double.compare(second.signedArea, first.signedArea));
        for (Boundary outer : outers) {
            result.add(new Component(outer));
        }
        for (Boundary hole : holes) {
            Component owner = findHoleOwner(result, hole);
            if (owner != null) {
                owner.holes.add(hole);
            }
        }
        return result;
    }

    private static int[] toArray(TreeSet<Integer> values) {
        int[] result = new int[values.size()];
        int index = 0;
        for (Integer value : values) {
            result[index++] = value.intValue();
        }
        return result;
    }

    private static List<Edge> boundaryEdges(boolean[][] covered, int[] xs, int[] zs) {
        List<Edge> result = new ArrayList<Edge>();
        for (int x = 0; x < covered.length; x++) {
            for (int z = 0; z < covered[x].length; z++) {
                if (!covered[x][z]) {
                    continue;
                }
                if (z == 0 || !covered[x][z - 1]) {
                    result.add(new Edge(new Vertex(x, z), new Vertex(x + 1, z),
                            EAST, xs, zs));
                }
                if (x == covered.length - 1 || !covered[x + 1][z]) {
                    result.add(new Edge(new Vertex(x + 1, z), new Vertex(x + 1, z + 1),
                            NORTH, xs, zs));
                }
                if (z == covered[x].length - 1 || !covered[x][z + 1]) {
                    result.add(new Edge(new Vertex(x + 1, z + 1), new Vertex(x, z + 1),
                            WEST, xs, zs));
                }
                if (x == 0 || !covered[x - 1][z]) {
                    result.add(new Edge(new Vertex(x, z + 1), new Vertex(x, z),
                            SOUTH, xs, zs));
                }
            }
        }
        return result;
    }

    private static List<Vertex> trace(Edge first, Map<Vertex, List<Edge>> outgoing,
                                      Set<Edge> used, int maximumEdges) {
        List<Vertex> points = new ArrayList<Vertex>();
        points.add(first.start);
        used.add(first);
        Edge current = first;

        for (int count = 0; count < maximumEdges; count++) {
            if (current.end.equals(first.start)) {
                return points;
            }
            points.add(current.end);
            List<Edge> candidates = outgoing.get(current.end);
            Edge next = chooseNext(current, candidates, used);
            if (next == null) {
                throw new IllegalStateException("Could not close claim boundary at "
                        + current.end.x + "," + current.end.z);
            }
            used.add(next);
            current = next;
        }
        throw new IllegalStateException("Claim boundary did not close.");
    }

    private static Edge chooseNext(Edge incoming, List<Edge> candidates, Set<Edge> used) {
        Edge best = null;
        int bestTurn = Integer.MAX_VALUE;
        if (candidates == null) {
            return null;
        }
        for (Edge candidate : candidates) {
            if (used.contains(candidate)) {
                continue;
            }
            int turn = (candidate.direction - incoming.direction + 4) % 4;
            if (turn < bestTurn) {
                bestTurn = turn;
                best = candidate;
            }
        }
        return best;
    }

    private static List<Vertex> simplify(List<Vertex> points) {
        List<Vertex> result = new ArrayList<Vertex>(points);
        boolean changed = true;
        while (changed && result.size() > 4) {
            changed = false;
            for (int index = 0; index < result.size(); index++) {
                Vertex previous = result.get((index + result.size() - 1) % result.size());
                Vertex current = result.get(index);
                Vertex next = result.get((index + 1) % result.size());
                long firstX = current.x - previous.x;
                long firstZ = current.z - previous.z;
                long secondX = next.x - current.x;
                long secondZ = next.z - current.z;
                if (firstX * secondZ - firstZ * secondX == 0L
                        && firstX * secondX + firstZ * secondZ > 0L) {
                    result.remove(index);
                    changed = true;
                    break;
                }
            }
        }
        return result;
    }

    private static Component findHoleOwner(List<Component> components, Boundary hole) {
        Vertex first = hole.points.get(0);
        Vertex second = hole.points.get(1);
        double dx = second.x - first.x;
        double dz = second.z - first.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        double testX = (first.x + second.x) / 2.0D + dz * 1.0E-5D / length;
        double testZ = (first.z + second.z) / 2.0D - dx * 1.0E-5D / length;

        Component best = null;
        double smallestArea = Double.MAX_VALUE;
        for (Component component : components) {
            if (component.outer.contains(testX, testZ)
                    && component.outer.signedArea < smallestArea) {
                best = component;
                smallestArea = component.outer.signedArea;
            }
        }
        return best;
    }

    public static final class Component {
        private final Boundary outer;
        private final List<Boundary> holes = new ArrayList<Boundary>();

        private Component(Boundary outer) {
            this.outer = outer;
        }

        public Boundary getOuter() {
            return outer;
        }

        public List<Boundary> getHoles() {
            return new ArrayList<Boundary>(holes);
        }
    }

    public static final class Boundary {
        private final List<Vertex> points;
        private final double signedArea;

        private Boundary(List<Vertex> points) {
            this.points = new ArrayList<Vertex>(points);
            this.signedArea = signedArea(points);
        }

        public int size() {
            return points.size();
        }

        public double getX(int index) {
            return points.get(index).x;
        }

        public double getZ(int index) {
            return points.get(index).z;
        }

        public double[] getXs() {
            double[] result = new double[points.size()];
            for (int index = 0; index < points.size(); index++) {
                result[index] = points.get(index).x;
            }
            return result;
        }

        public double[] getZs() {
            double[] result = new double[points.size()];
            for (int index = 0; index < points.size(); index++) {
                result[index] = points.get(index).z;
            }
            return result;
        }

        public double getSignedArea() {
            return signedArea;
        }

        private boolean contains(double x, double z) {
            boolean inside = false;
            for (int current = 0, previous = points.size() - 1;
                 current < points.size(); previous = current++) {
                Vertex a = points.get(previous);
                Vertex b = points.get(current);
                if ((a.z > z) != (b.z > z)
                        && x < (b.x - a.x) * (z - a.z) / (b.z - a.z) + a.x) {
                    inside = !inside;
                }
            }
            return inside;
        }

        private static double signedArea(List<Vertex> points) {
            double result = 0.0D;
            for (int index = 0; index < points.size(); index++) {
                Vertex current = points.get(index);
                Vertex next = points.get((index + 1) % points.size());
                result += current.x * next.z - next.x * current.z;
            }
            return result / 2.0D;
        }
    }

    private static final class Vertex {
        private final int x;
        private final int z;

        private Vertex(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Vertex)) {
                return false;
            }
            Vertex vertex = (Vertex) other;
            return x == vertex.x && z == vertex.z;
        }

        @Override
        public int hashCode() {
            return 31 * x + z;
        }

        @Override
        public String toString() {
            return x + "," + z;
        }
    }

    private static final class Edge {
        private final Vertex start;
        private final Vertex end;
        private final int direction;

        private Edge(Vertex start, Vertex end, int direction, int[] xs, int[] zs) {
            this.start = new Vertex(xs[start.x], zs[start.z]);
            this.end = new Vertex(xs[end.x], zs[end.z]);
            this.direction = direction;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Edge)) {
                return false;
            }
            Edge edge = (Edge) other;
            return start.equals(edge.start) && end.equals(edge.end);
        }

        @Override
        public int hashCode() {
            return 31 * start.hashCode() + end.hashCode();
        }
    }
}
