package dev.che1s.betalands.permission;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.util.config.Configuration;
import org.junit.Test;

import dev.che1s.betalands.service.LandManager;
import dev.che1s.betalands.service.PermissionService;

public class PermissionNodesTest {
    @Test
    public void granularPlayerPermissionsDefaultToTrue() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/plugin.yml");
        assertNotNull(stream);
        File file = File.createTempFile("betalands-plugin-", ".yml");
        FileOutputStream output = new FileOutputStream(file);
        try {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
        } finally {
            output.close();
            stream.close();
        }
        try {
            Configuration config = new Configuration(file);
            config.load();
            Map<?, ?> nodes = (Map<?, ?>) config.getProperty("permissions");
            assertNotNull(nodes);
            for (String node : Arrays.asList(
                    PermissionNodes.USE,
                    PermissionNodes.CREATE_MAIN,
                    PermissionNodes.CREATE_CLAIM,
                    PermissionNodes.CREATE_SUBCLAIM,
                    PermissionNodes.TELEPORT,
                    PermissionNodes.MEMBER_INVITE,
                    PermissionNodes.MEMBER_KICK,
                    PermissionNodes.MEMBER_PERMISSION,
                    PermissionNodes.LAND_RENAME,
                    PermissionNodes.LAND_SETOWNER,
                    PermissionNodes.LAND_DELETE,
                    PermissionNodes.LAND_FLAGS,
                    PermissionNodes.LAND_SETSPAWN,
                    PermissionNodes.LAND_LOGS,
                    PermissionNodes.BANK_USE,
                    PermissionNodes.RENT_USE)) {
                Map<?, ?> definition = (Map<?, ?>) nodes.get(node);
                assertNotNull(node, definition);
                assertTrue(node, Boolean.TRUE.equals(
                        definition.get("default")));
            }
            assertFalse(nodes.containsKey("betalands.create"));
        } finally {
            file.delete();
        }
    }

    @Test
    public void requiresConcreteCreatePermission() {
        PermissionService permissions =
                new PermissionService(null, new LandManager(null));

        assertTrue(permissions.hasActionPermission(
                player(PermissionNodes.CREATE_SUBCLAIM),
                PermissionNodes.CREATE_SUBCLAIM));
        assertFalse(permissions.hasActionPermission(
                player("other"),
                PermissionNodes.CREATE_SUBCLAIM));
    }

    private Player player(final String granted) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) {
                        return "Builder";
                    }
                    if ("isOp".equals(method.getName())) {
                        return false;
                    }
                    if ("hasPermission".equals(method.getName())) {
                        return granted.equals(args[0]);
                    }
                    return null;
                });
    }
}
