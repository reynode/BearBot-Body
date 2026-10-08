package id.beruang.bearbotbody;

import id.beruang.bearbotbody.body.BearBotBody;
import net.kyori.adventure.text.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

public final class BearBotBodyPlugin extends JavaPlugin implements Listener {

    private BearBotBody body;

    @Override
    public void onEnable() {
        getCommand("bot").setExecutor(this::handleCommand);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("BearBot Body enabled.");
    }

    @Override
    public void onDisable() {
        despawnBody();
        getLogger().info("BearBot Body disabled.");
    }

    private boolean handleCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("spawn")) {
            return spawn(sender);
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("despawn")) {
            return despawn(sender);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("look")) {
            return lookAt(sender, args[1]);
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("debug")) {
            return debug(sender);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("move")) {
            return move(sender, args[1]);
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("stop")) {
            return stop(sender);
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("jump")) {
            return jump(sender);
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("sprint")) {
            return sprint(sender, true);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sprint")) {
            return setToggle(sender, args[1], true);
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("sneak")) {
            return sneak(sender, true);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sneak")) {
            return setToggle(sender, args[1], false);
        }
        sender.sendMessage("Usage: /bot spawn | /bot despawn | /bot look <player> | /bot move <forward|backward|left|right> | /bot stop | /bot jump | /bot sprint [on|off] | /bot sneak [on|off] | /bot debug");
        return true;
    }

    private boolean spawn(CommandSender sender) {
        if (body != null && body.isSpawned()) {
            sender.sendMessage("BearBot is already spawned.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can spawn BearBot.");
            return true;
        }

        Location location = player.getLocation();
        ServerLevel level = ((CraftWorld) location.getWorld()).getHandle();
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        BearBotBody newBody = new BearBotBody(server, level);
        try {
            newBody.spawn(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
            body = newBody;
            sender.sendMessage("Spawned BearBot.");
        } catch (RuntimeException exception) {
            getLogger().severe("Could not add BearBot to the server entity lifecycle: " + exception.getMessage());
            sender.sendMessage("Could not spawn BearBot; check the server log.");
        }
        return true;
    }

    private boolean despawn(CommandSender sender) {
        if (body == null || !body.isSpawned()) {
            sender.sendMessage("BearBot is not spawned.");
            return true;
        }
        despawnBody();
        sender.sendMessage("Despawned BearBot.");
        return true;
    }

    private boolean lookAt(CommandSender sender, String playerName) {
        if (body == null || !body.isSpawned()) {
            sender.sendMessage("Spawn BearBot first with /bot spawn.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null || !target.isOnline()) {
            sender.sendMessage("Player is not online: " + playerName);
            return true;
        }
        body.getController().lookAt(target);
        sender.sendMessage("BearBot is now looking at " + target.getName() + ".");
        return true;
    }

    private boolean move(CommandSender sender, String direction) {
        if (!requireSpawned(sender)) {
            return true;
        }
        switch (direction.toLowerCase(Locale.ROOT)) {
            case "forward" -> body.getController().moveForward(1.0F);
            case "backward", "back" -> body.getController().moveBackward(1.0F);
            case "left" -> body.getController().strafeLeft(1.0F);
            case "right" -> body.getController().strafeRight(1.0F);
            default -> {
                sender.sendMessage("Use /bot move <forward|backward|left|right>.");
                return true;
            }
        }
        sender.sendMessage("BearBot movement set to " + direction.toLowerCase(Locale.ROOT) + ". Use /bot stop to stop.");
        return true;
    }

    private boolean stop(CommandSender sender) {
        if (!requireSpawned(sender)) {
            return true;
        }
        body.getController().stop();
        sender.sendMessage("BearBot movement stopped.");
        return true;
    }

    private boolean jump(CommandSender sender) {
        if (!requireSpawned(sender)) {
            return true;
        }
        body.getController().jump();
        sender.sendMessage("BearBot jump requested.");
        return true;
    }

    private boolean sprint(CommandSender sender, boolean enabled) {
        if (!requireSpawned(sender)) {
            return true;
        }
        body.getController().sprint(enabled);
        sender.sendMessage("BearBot sprint " + (enabled ? "enabled." : "disabled."));
        return true;
    }

    private boolean sneak(CommandSender sender, boolean enabled) {
        if (!requireSpawned(sender)) {
            return true;
        }
        body.getController().sneak(enabled);
        sender.sendMessage("BearBot sneak " + (enabled ? "enabled." : "disabled."));
        return true;
    }

    private boolean setToggle(CommandSender sender, String value, boolean sprint) {
        if (!value.equalsIgnoreCase("on") && !value.equalsIgnoreCase("off")) {
            sender.sendMessage("Use on or off.");
            return true;
        }
        boolean enabled = value.equalsIgnoreCase("on");
        return sprint ? sprint(sender, enabled) : sneak(sender, enabled);
    }

    private boolean debug(CommandSender sender) {
        if (body == null) {
            sender.sendMessage("Spawned: false");
            sender.sendMessage("Connection: false");
            sender.sendMessage("Channel: false");
            return true;
        }

        Entity bukkitEntity = body.getEntity().getBukkitEntity();
        var controller = body.getController();
        sender.sendMessage("Spawned: " + body.isSpawned());
        sender.sendMessage("Entity ID: " + body.getEntity().getId());
        sender.sendMessage("UUID: " + body.getEntity().getUUID());
        sender.sendMessage("World: " + bukkitEntity.getWorld().getName());
        sender.sendMessage("Position: " + format(controller.getPosition()));
        sender.sendMessage("Velocity: " + format(controller.getVelocity()));
        sender.sendMessage("OnGround: " + controller.isOnGround());
        sender.sendMessage("InWater: " + controller.isInWater());
        sender.sendMessage("InLava: " + controller.isInLava());
        sender.sendMessage("Sprinting: " + controller.isSprinting());
        sender.sendMessage("Sneaking: " + controller.isSneaking());
        sender.sendMessage("FallDistance: " + controller.getFallDistance());
        sender.sendMessage("Tick status: " + (body.isSpawned() ? "active" : "inactive")
                + ", entity ticks=" + body.getEntity().getPhysicalTickCount());
        var diagnostics = body.getEntity().getLastTickDiagnostics();
        sender.sendMessage("Movement input: xxa before=" + diagnostics.strafeInputBefore()
                + ", zza before=" + diagnostics.forwardInputBefore()
                + ", xxa after=" + diagnostics.strafeInputAfter()
                + ", zza after=" + diagnostics.forwardInputAfter());
        sender.sendMessage("Movement gates: immobile=" + diagnostics.immobile()
                + ", canSimulateMovement=" + diagnostics.canSimulateMovement()
                + ", effectiveAi=" + diagnostics.effectiveAi()
                + ", removed=" + diagnostics.removed()
                + ", spectator=" + diagnostics.spectator());
        sender.sendMessage("Last tick position: before=" + format(diagnostics.positionBefore())
                + ", after=" + format(diagnostics.positionAfter()));
        sender.sendMessage("Last tick delta: before=" + format(diagnostics.deltaBefore())
                + ", after=" + format(diagnostics.deltaAfter()));
        sender.sendMessage("Controller state: forward=" + controller.getConfiguredForwardInput()
                + ", strafe=" + controller.getConfiguredStrafeInput()
                + ", sprint=" + controller.isConfiguredSprinting()
                + ", sneak=" + controller.isConfiguredSneaking());
        sender.sendMessage("Connection: " + body.hasConnection()
                + ", lifecycle=" + body.getConnectionLifecycleState());
        sender.sendMessage("Channel: " + body.hasChannel()
                + ", active=" + body.isChannelActive()
                + ", open=" + body.isChannelOpen());
        return true;
    }

    private boolean requireSpawned(CommandSender sender) {
        if (body == null || !body.isSpawned()) {
            sender.sendMessage("Spawn BearBot first with /bot spawn.");
            return false;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBearBotRightClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isBearBot(event.getRightClicked())) {
            return;
        }
        event.getPlayer().sendMessage("BearBot Body: RIGHT CLICK detected");
        event.getPlayer().openInventory(createDebugInventory());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBearBotLeftClick(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !isBearBot(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage("BearBot Body: LEFT CLICK detected");
    }

    private boolean isBearBot(Entity target) {
        return body != null
                && body.isSpawned()
                && target.getUniqueId().equals(body.getEntity().getUUID());
    }

    private Inventory createDebugInventory() {
        var controller = body.getController();
        Entity entity = body.getEntity().getBukkitEntity();
        Inventory inventory = Bukkit.createInventory(null, 27, Component.text("BearBot Body"));
        addInfo(inventory, 10, Material.PLAYER_HEAD, "Spawned", Boolean.toString(body.isSpawned()));
        addInfo(inventory, 11, Material.NAME_TAG, "Entity ID", Integer.toString(body.getEntity().getId()));
        addInfo(inventory, 12, Material.PAPER, "UUID", body.getEntity().getUUID().toString());
        addInfo(inventory, 13, Material.COMPASS, "Position", format(controller.getPosition()));
        addInfo(inventory, 14, Material.FEATHER, "Velocity", format(controller.getVelocity()));
        addInfo(inventory, 15, Material.REDSTONE, "Health",
                String.format(Locale.ROOT, "%.1f", ((org.bukkit.entity.Damageable) entity).getHealth()));
        addInfo(inventory, 16, Material.IRON_BOOTS, "On ground", Boolean.toString(controller.isOnGround()));
        addInfo(inventory, 21, Material.WATER_BUCKET, "In water", Boolean.toString(controller.isInWater()));
        addInfo(inventory, 22, Material.LAVA_BUCKET, "In lava", Boolean.toString(controller.isInLava()));
        return inventory;
    }

    private static void addInfo(Inventory inventory, int slot, Material material, String title, String value) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(title));
        meta.lore(List.of(Component.text(value)));
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    private static String format(net.minecraft.world.phys.Vec3 vector) {
        return String.format(Locale.ROOT, "%.2f, %.2f, %.2f", vector.x, vector.y, vector.z);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (body != null) {
            body.showPlayerInfoTo(event.getPlayer());
        }
    }

    private void despawnBody() {
        if (body != null) {
            body.despawn();
            body = null;
        }
    }
}
