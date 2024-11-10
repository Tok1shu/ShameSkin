package net.tokishu;

// LuckPerms
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;

// Skinsrestorer
import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.property.SkinIdentifier;
import net.skinsrestorer.api.property.SkinVariant;

// Bukkit
import net.skinsrestorer.api.storage.PlayerStorage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

// Java
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.URL;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.concurrent.CompletableFuture;

public final class ShameSkin extends JavaPlugin implements CommandExecutor, Listener {

    private FileConfiguration config;
    private LuckPerms luckPerms;
    private Map<String, Long> punishedPlayers;
    private File punishmentsFile;
    private FileConfiguration punishmentsConfig;
    public String ConfigMessageError = "Please, check plugin config!";
    private boolean useLegacySkinChange;
    private SkinsRestorer skinsRestorer;
    private PlayerStorage playerStorage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        punishedPlayers = new HashMap<>();
        punishmentsFile = new File(getDataFolder(), "punishments.yml");
        if (!punishmentsFile.exists()) {
            saveResource("punishments.yml", false);
        }
        punishmentsConfig = YamlConfiguration.loadConfiguration(punishmentsFile);
        getCommand("shameskin").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        luckPerms = LuckPermsProvider.get();
        loadPunishments();
        startExpirationChecker();
        useLegacySkinChange = config.getBoolean("useLegacySkinChange", false);

        try {
            skinsRestorer = SkinsRestorerProvider.get();
            playerStorage = skinsRestorer.getPlayerStorage();
            getLogger().info("Successfully connected to SkinsRestorer API!");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize SkinsRestorer API! Falling back to legacy method.");
            getLogger().severe(e.getMessage());
            useLegacySkinChange = true;
        }
    }

    @Override
    public void onDisable() {
        savePunishments();
    }

    private void loadPunishments() {
        if (punishmentsConfig.contains("punishments")) {
            for (String username : punishmentsConfig.getConfigurationSection("punishments").getKeys(false)) {
                long expiration = punishmentsConfig.getLong("punishments." + username);
                if (expiration > System.currentTimeMillis()) {
                    punishedPlayers.put(username.toLowerCase(), expiration);
                }
            }
        }
    }

    private void savePunishments() {
        punishmentsConfig.set("punishments", null);
        for (Map.Entry<String, Long> entry : punishedPlayers.entrySet()) {
            punishmentsConfig.set("punishments." + entry.getKey(), entry.getValue());
        }
        try {
            punishmentsConfig.save(punishmentsFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not save punishments data!", e);
        }
    }

    private void startExpirationChecker() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            long currentTime = System.currentTimeMillis();
            boolean changed = punishedPlayers.entrySet().removeIf(entry -> {
                if (entry.getValue() <= currentTime) {
                    restoreOriginalSkin(entry.getKey());
                    return true;
                }
                return false;
            });
            if (changed) {
                savePunishments();
            }
        }, 20L * 10, 20L * 10); // Check every 10 seconds
    }

    private void restoreOriginalSkin(String playerName) {
        Player player = Bukkit.getPlayer(playerName);
        if (player != null && player.isOnline()) {
            try {
                if (!useLegacySkinChange && skinsRestorer != null) {
                    playerStorage.removeSkinIdOfPlayer(player.getUniqueId());
                    Bukkit.getScheduler().runTask(this, () -> {
                        try {
                            skinsRestorer.getSkinApplier(Player.class).applySkin(player);
                        } catch (Exception e) {
                            getLogger().severe("Failed to refresh skin after expiration: " + e.getMessage());
                        }
                    });
                } else {
                    useLegacyRestoreSkin(player);
                }
            } catch (Exception e) {
                getLogger().severe("Error restoring original skin: " + e.getMessage());
            }
            player.sendMessage(config.getString("messages.punishmentExpired", ConfigMessageError));
        }
    }

    private void useLegacyRestoreSkin(Player player) {
        Bukkit.getScheduler().runTask(this, () -> {
            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + player.getName() + " permission set skinsrestorer.bypasscooldown");
            player.performCommand("skin clear");
            Bukkit.getScheduler().runTaskLater(this, () -> {
                Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(),
                        "lp user " + player.getName() + " permission unset skinsrestorer.bypasscooldown");
            }, 20L);
        });
    }


    private String formatTime(String timeStr) {
        if (timeStr.equalsIgnoreCase("forever")) {
            return " "+ config.getString("time.forever");
        }

        Pattern pattern = Pattern.compile("(\\d+)([dmwyh])");
        Matcher matcher = pattern.matcher(timeStr.toLowerCase());

        if (!matcher.matches()) {
            return timeStr;
        }

        int amount = Integer.parseInt(matcher.group(1));
        String unit = matcher.group(2);

        switch (unit) {
            case "m":
                return amount + " "+ config.getString("time.minutes");
            case "h":
                return amount + " "+ config.getString("time.hours");
            case "d":
                return amount + " "+ config.getString("time.days");
            case "w":
                return amount + " "+ config.getString("time.weeks");
            case "y":
                return amount + " "+ config.getString("time.years");
            default:
                return timeStr;
        }
    }

    private long parseTime(String timeStr) {
        if (timeStr.equalsIgnoreCase("forever")) {
            return Long.MAX_VALUE;
        }

        Pattern pattern = Pattern.compile("(\\d+)([dmwyh])");
        Matcher matcher = pattern.matcher(timeStr.toLowerCase());

        if (!matcher.matches()) {
            return -1;
        }

        int amount = Integer.parseInt(matcher.group(1));
        String unit = matcher.group(2);
        long milliseconds;
        switch (unit) {
            case "m":
                milliseconds = TimeUnit.MINUTES.toMillis(amount);
                break;
            case "h":
                milliseconds = TimeUnit.HOURS.toMillis(amount);
                break;
            case "d":
                milliseconds = TimeUnit.DAYS.toMillis(amount);
                break;
            case "w":
                milliseconds = TimeUnit.DAYS.toMillis(amount * 7L);
                break;
            case "y":
                milliseconds = TimeUnit.DAYS.toMillis(amount * 365L);
                break;
            default:
                return -1;
        }
        return System.currentTimeMillis() + milliseconds;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("shameskin")) {
            return false;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(config.getString("messages.playerOnly", ConfigMessageError));
            return true;
        }

        Player player = (Player) sender;
        User user = luckPerms.getUserManager().getUser(player.getName());

        if (user == null || !user.getCachedData().getPermissionData().checkPermission("shameskin.mod").asBoolean()) {
            sender.sendMessage(config.getString("messages.noPermission", ConfigMessageError));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(config.getString("messages.usage", ConfigMessageError));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                if (!user.getCachedData().getPermissionData().checkPermission("shameskin.reload").asBoolean()) {
                    sender.sendMessage(config.getString("messages.noPermission", ConfigMessageError));
                    return true;
                }
                reloadPluginConfig();
                sender.sendMessage(config.getString("messages.configReloaded", ConfigMessageError));
                return true;

            case "remove":
                if (!user.getCachedData().getPermissionData().checkPermission("shameskin.remove").asBoolean()) {
                    sender.sendMessage(config.getString("messages.noPermission", ConfigMessageError));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(config.getString("messages.specifyPlayer", ConfigMessageError));
                    return true;
                }
                removePunishment(sender, args[1]);
                return true;

            default:
                if (args.length < 2) {
                    sender.sendMessage(config.getString("messages.usage", ConfigMessageError));
                    return true;
                }

                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    sender.sendMessage(config.getString("messages.playerNotFound", ConfigMessageError));
                    return true;
                }

                long expirationTime = parseTime(args[1]);
                if (expirationTime == -1) {
                    sender.sendMessage(config.getString("messages.invalidTimeFormat", ConfigMessageError));
                    return true;
                }

                String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                        : config.getString("messages.noReason", ConfigMessageError);

                applyPunishment(target, expirationTime, reason, args[1]);
                return true;
        }
    }

    private void reloadPluginConfig() {
        reloadConfig();
        config = getConfig();
        useLegacySkinChange = config.getBoolean("useLegacySkinChange", false);
        punishmentsConfig = YamlConfiguration.loadConfiguration(punishmentsFile);
        loadPunishments();
    }

    private void removePunishment(CommandSender sender, String playerName) {
        String lowercasePlayerName = playerName.toLowerCase();
        if (!punishedPlayers.containsKey(lowercasePlayerName)) {
            sender.sendMessage(config.getString("messages.playerNotPunished", ConfigMessageError)
                    .replace("[player]", playerName));
            return;
        }

        Player target = Bukkit.getPlayer(playerName);
        if (target != null && target.isOnline()) {
            try {
                if (!useLegacySkinChange && skinsRestorer != null) {
                    playerStorage.removeSkinIdOfPlayer(target.getUniqueId());
                    Bukkit.getScheduler().runTask(this, () -> {
                        try {
                            skinsRestorer.getSkinApplier(Player.class).applySkin(target);
                        } catch (Exception e) {
                            getLogger().severe("Failed to refresh skin after removal: " + e.getMessage());
                        }
                    });
                }

                target.sendMessage(config.getString("messages.punishmentRemovedNotify", ConfigMessageError));
            } catch (Exception e) {
                getLogger().severe("Error removing skin: " + e.getMessage());
            }
        }

        punishedPlayers.remove(lowercasePlayerName);
        savePunishments();

        String message = config.getString("messages.punishmentRemoved", ConfigMessageError)
                .replace("[player]", playerName);
        Bukkit.broadcastMessage(message);
    }

    public static CompletableFuture<SkinVariant> detectVariant(String skinUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL(skinUrl);
                BufferedImage skin = ImageIO.read(url);
                boolean isSlim = isSlimArm(skin);

                return isSlim ? SkinVariant.SLIM : SkinVariant.CLASSIC;
            } catch (IOException e) {
                e.printStackTrace();
                return SkinVariant.CLASSIC;
            }
        });
    }

    private static boolean isSlimArm(BufferedImage skin) {
        int[] checkPoints = {
                skin.getRGB(54, 20),
                skin.getRGB(54, 21),
                skin.getRGB(54, 22)
        };

        for (int pixel : checkPoints) {
            int alpha = (pixel >> 24) & 0xff;
            if (alpha != 0) {
                return false;
            }
        }

        return true;
    }



    private void applyPunishment(Player target, long expirationTime, String reason, String originalTime) {
        punishedPlayers.put(target.getName().toLowerCase(), expirationTime);
        savePunishments();

        List<String> skins = config.getStringList("skins");
        if (!skins.isEmpty()) {
            String selectedSkin = skins.get(new Random().nextInt(skins.size()));
            if (!useLegacySkinChange && skinsRestorer != null) {
                detectVariant(selectedSkin)
                        .thenAccept(variant -> {
                            SkinIdentifier skinIdentifier = SkinIdentifier.ofURL(selectedSkin, variant);
                            Bukkit.getScheduler().runTask(this, () -> {
                                try {
                                    playerStorage.setSkinIdOfPlayer(target.getUniqueId(), skinIdentifier);
                                    skinsRestorer.getSkinApplier(Player.class).applySkin(target);
                                    handlePostPunishment(target);
                                } catch (Exception e) {
                                    getLogger().severe("Failed to apply skin using SkinsRestorer API: " + e.getMessage());
                                    if (!useLegacySkinChange) {
                                        getLogger().warning("Falling back to legacy method...");
                                        useLegacyApplyPunishment(target, selectedSkin);
                                    }
                                }
                            });
                        })
                        .exceptionally(throwable -> {
                            getLogger().severe("Failed to detect skin variant: " + throwable.getMessage());
                            return null;
                        });
            } else {
                useLegacyApplyPunishment(target, selectedSkin);
            }
        }

        String message = config.getString("messages.appliedSkinMessage", ConfigMessageError)
                .replace("[player]", target.getName())
                .replace("[time]", formatTime(originalTime))
                .replace("[reason]", reason);
        Bukkit.broadcastMessage(message);
    }

    private void useLegacyApplyPunishment(Player target, String selectedSkinUrl) {
        Bukkit.getScheduler().runTask(this, () -> {
            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + target.getName() + " permission set skinsrestorer.bypasscooldown");
            target.performCommand("skin url '" + selectedSkinUrl + "'");
            Bukkit.getScheduler().runTaskLater(this, () -> {
                Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(),
                        "lp user " + target.getName() + " permission unset skinsrestorer.bypasscooldown");
                handlePostPunishment(target);
            }, 20L);
        });
    }

    private void handlePostPunishment(Player target) {
        if (config.getBoolean("kickOnPunishment", false)) {
            String kickMessage = config.getString("messages.kickMessage", ConfigMessageError);
            Bukkit.getScheduler().runTask(this, () -> target.kickPlayer(kickMessage));
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (punishedPlayers.containsKey(event.getPlayer().getName().toLowerCase())) {
            String command = event.getMessage().toLowerCase();
            if (command.startsWith("/skin") || command.startsWith("/skinsrestorer:skin")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(config.getString("messages.skinCommandBlocked", ConfigMessageError));
            }
        }
    }
}