package me.ultrastrike;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.attribute.Attribute;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class UltraStrike extends JavaPlugin implements Listener {

    private final Map<UUID, Integer> charge = new HashMap<>();
    private final Map<Integer, Integer> gainByDamage = new HashMap<>();

    private double ultraMultiplier;
    private final java.util.Random random = new java.util.Random();
    private int uiUpdateTicks;
    private boolean showWhenZero;
    private boolean showOnlyWithSwordOrAxe;

    private String actionbarFormat;
    private boolean hudShowHp;
    private boolean hudShowMana;
    private boolean hudShowUltraWhenZero;

    private final AuraSkillsHook auraHook = new AuraSkillsHook();
    // Alias kept for older code paths
    private final AuraSkillsHook auraSkillsHook = auraHook;

    private int barLength;
    private String barFilled;
    private String barEmpty;

    private String title;
    private String readyText;
    private String usedText;
    private String formatCharging;
    private String formatReady;
    private String formatUsed;

// Particles
private boolean particlesEnabled;
private java.util.List<org.bukkit.Particle> particlesCharge;
private java.util.List<org.bukkit.Particle> particlesReady;
private java.util.List<org.bukkit.Particle> particlesUse;
private java.util.List<org.bukkit.Particle> particlesKill;
private int particlesChargeCount;
private int particlesReadyCount;
private int particlesUseCount;
private int particlesKillCount;

    private int particlesChargeInterval;
    private int particlesReadyInterval;
    private int uiTickCounter;


    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        Bukkit.getPluginManager().registerEvents(this, this);

        Bukkit.getScheduler().runTaskTimer(this, this::tickUi, 1L, uiUpdateTicks);
        getLogger().info("UltraStrike enabled.");
    }

    @Override
    public void onDisable() {
        charge.clear();
        gainByDamage.clear();
    }

    private void loadSettings() {
        reloadConfig();

        ultraMultiplier = getConfig().getDouble("ultra.damage-multiplier", 1.6);
        uiUpdateTicks = Math.max(1, getConfig().getInt("ui.update-ticks", 5));
        showWhenZero = getConfig().getBoolean("ui.show-when-zero", false);
        showOnlyWithSwordOrAxe = getConfig().getBoolean("ui.show-only-with-sword-or-axe", true);

        barLength = Math.max(5, getConfig().getInt("ui.bar.length", 20));
        barFilled = getConfig().getString("ui.bar.filled", "&a▌");
        barEmpty  = getConfig().getString("ui.bar.empty", "&8▌");

        title = getConfig().getString("ui.title", "&d⚡ &fУльтра удар &d⚡");
        readyText = getConfig().getString("ui.ready-text", "&aГОТОВ!");
        usedText  = getConfig().getString("ui.used-text", "&cУЛЬТА ИСПОЛЬЗОВАНА!");

        formatCharging = getConfig().getString("ui.format-charging", "{title} &e{percent}% &8[{bar}&8]");
        formatReady    = getConfig().getString("ui.format-ready", "{title} {ready} &8[{bar}&8]");
        formatUsed     = getConfig().getString("ui.format-used", "{title} &f{used}");

// Particles (use names from Bukkit Particle enum)
particlesEnabled = getConfig().getBoolean("particles.enabled", true);
particlesChargeCount = getConfig().getInt("particles.charge.count", 6);
particlesReadyCount  = getConfig().getInt("particles.ready.count", 10);
particlesUseCount    = getConfig().getInt("particles.use.count", 18);
particlesKillCount   = getConfig().getInt("particles.kill.count", 22);

        particlesChargeInterval = Math.max(2, getConfig().getInt("particles.charge.interval-ticks", 12));
        particlesReadyInterval  = Math.max(2, getConfig().getInt("particles.ready.interval-ticks", 20));

particlesCharge = parseParticles(getConfig().getStringList("particles.charge.list"),
        java.util.Arrays.asList("ENCHANT", "ELECTRIC_SPARK", "END_ROD"));
particlesReady = parseParticles(getConfig().getStringList("particles.ready.list"),
        java.util.Arrays.asList("TOTEM", "END_ROD", "ELECTRIC_SPARK"));
particlesUse = parseParticles(getConfig().getStringList("particles.use.list"),
        java.util.Arrays.asList("SWEEP_ATTACK", "SOUL_FIRE_FLAME", "CRIT"));
particlesKill = parseParticles(getConfig().getStringList("particles.kill.list"),
        java.util.Arrays.asList("FIREWORK", "END_ROD", "CRIT"));

        actionbarFormat = getConfig().getString("hud.format", "{hp}❤ | {ultra} | {mana}");
        hudShowHp = getConfig().getBoolean("hud.show-hp", true);
        hudShowMana = getConfig().getBoolean("hud.show-mana", true);
        hudShowUltraWhenZero = getConfig().getBoolean("hud.show-ultra-when-zero", true);


        gainByDamage.clear();
        ConfigurationSection sec = getConfig().getConfigurationSection("gain-by-damage");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    int dmg = Integer.parseInt(key.trim());
                    int gain = sec.getInt(key);
                    gainByDamage.put(dmg, gain);
                } catch (NumberFormatException ignored) { }
            }
        }

        if (gainByDamage.isEmpty()) {
            int[] defaults = new int[18];
            defaults[1]=5; defaults[2]=8; defaults[3]=15; defaults[4]=20; defaults[5]=25;
            defaults[6]=33; defaults[7]=39; defaults[8]=46; defaults[9]=51; defaults[10]=60;
            defaults[11]=71; defaults[12]=78; defaults[13]=81; defaults[14]=87; defaults[15]=90;
            defaults[16]=98; defaults[17]=100;
            for (int i=1;i<=17;i++) gainByDamage.put(i, defaults[i]);
        }
    }

    private void tickUi() {
        uiTickCounter++;

    String sep = getConfig().getString("hud.separator", " &7| ");
    for (Player p : Bukkit.getOnlinePlayers()) {

        // Ultra charge (only relevant if holding correct item, but we still show HP/mana)
        int percent = charge.getOrDefault(p.getUniqueId(), 0);
        boolean canUseUltraItem = !showOnlyWithSwordOrAxe || isSwordOrAxe(p.getInventory().getItemInMainHand());

        // HP
        String hpPart = "";
        if (hudShowHp) {
            double rawHp = p.getHealth();

            int fixedMaxHp = getConfig().getInt("hud.hp-fixed-max", 20);
            if (fixedMaxHp <= 0) fixedMaxHp = 20;

            int maxHp = fixedMaxHp;
            int hp = (int) Math.floor(Math.min(rawHp, (double) fixedMaxHp) + 1e-6);
            if (hp < 0) hp = 0;
            if (hp > maxHp) hp = maxHp;

            String hpFmt = getConfig().getString("hud.hp-format", "&c{hp}/{hp_max} ❤");
            hpPart = hpFmt.replace("{hp}", String.valueOf(hp)).replace("{hp_max}", String.valueOf(maxHp));
        }

// Mana (AuraSkills)
        String manaPart = "";
        if (hudShowMana) {
            AuraSkillsHook.ManaSnapshot ms = auraHook.getMana(p);
            if (ms.available) {
                String manaFmt = getConfig().getString("hud.mana-format", "&bМана {mana}/{mana_max}");
                int cap = Math.max(0, getConfig().getInt("hud.mana-cap", 0));
            String manaNow = ms.manaInt;
            String manaMax = ms.maxManaInt;
            if (cap > 0) {
                try {
                    int n = (int) Math.floor(Double.parseDouble(manaNow));
                    int mmax = (int) Math.floor(Double.parseDouble(manaMax));
                    manaNow = String.valueOf(Math.min(n, cap));
                    manaMax = String.valueOf(Math.min(mmax, cap));
                } catch (NumberFormatException ignored) {}
            }
            manaPart = manaFmt.replace("{mana}", manaNow).replace("{mana_max}", manaMax);
            }
        }

        // Ultra
        String ultraPart = "";
        boolean hideUltraNoWeapon = getConfig().getBoolean("hud.hide-ultra-when-no-weapon", false);
        boolean showUltra = (!hideUltraNoWeapon || canUseUltraItem) && (percent > 0 || hudShowUltraWhenZero);
        if (showUltra) {
            if (percent >= 100) {
                String u = getConfig().getString("hud.ultra-format-ready", "&dУльтра удар &aГОТОВ!");
                ultraPart = u.replace("{percent}", "100");
            } else {
                String u = getConfig().getString("hud.ultra-format-charging", "&dУльтра удар &e{percent}%");
                ultraPart = u.replace("{percent}", String.valueOf(percent));
            }
        }

        // If ultra is not shown and showWhenZero is false, optionally skip sending at all
        if (!showWhenZero && percent <= 0 && ultraPart.isEmpty()) {
            // But if HP or mana are shown, we still want the HUD line (we're now the "single HUD" plugin).
            // So we don't skip.
        }

        String hud = joinParts(sep, hpPart, ultraPart, manaPart);
        if (hud.isBlank()) continue;

        p.sendActionBar(legacy.deserialize(hud));
    }
}

private String joinParts(String separator, String... parts) {
    List<String> out = new ArrayList<>();
    for (String p : parts) {
        if (p == null) continue;
        String t = p.trim();
        if (t.isEmpty()) continue;
        out.add(t);
    }
    return String.join(separator, out);
}

    private boolean isSwordOrAxe(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        String n = item.getType().name();
        return n.endsWith("_SWORD") || n.endsWith("_AXE");
    }

    private int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    
    private Component color(String legacyText) {
        if (legacyText == null) legacyText = "";
        return legacy.deserialize(legacyText);
    }

@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)) return;

        Entity victim = e.getEntity();
        if (!(victim instanceof LivingEntity livingVictim)) return;

        ItemStack hand = attacker.getInventory().getItemInMainHand();
        if (!isSwordOrAxe(hand)) return;

        UUID id = attacker.getUniqueId();
        int current = charge.getOrDefault(id, 0);

        // Ultra release on next hit when 100%
        if (current >= 100) {
            int manaCost = getConfig().getInt("ultra.mana-cost", 4);
            if (manaCost < 0) manaCost = 0;
            if (manaCost > 0 && auraSkillsHook.isEnabled()) {
                if (!auraSkillsHook.tryConsumeMana(attacker, manaCost)) {
                    attacker.sendMessage(color(getConfig().getString("messages.not-enough-mana", "&cНедостаточно маны для ульты!")));
                    return; // do not consume charge
                }
            }

            // Multiply base damage
            e.setDamage(e.getDamage() * ultraMultiplier);
            charge.put(id, 0);

            // Ultra visuals
            spawnUseParticles(attacker);

            // 4% lightning chance with extra damage + fire
            double chance = getConfig().getDouble("ultra.lightning.chance-percent", 4.0);
            double roll = random.nextDouble() * 100.0;
            if (chance > 0 && roll < chance) {
                double extra = getConfig().getDouble("ultra.lightning.extra-damage", 3.0);
                int fireTicks = getConfig().getInt("ultra.lightning.fire-ticks", 60);

                victim.getWorld().strikeLightningEffect(victim.getLocation());
                if (fireTicks > 0) livingVictim.setFireTicks(fireTicks);
                if (extra > 0) livingVictim.damage(extra, attacker);
            }

            return;
        }

        // Charge gain on normal hits
        int dmgInt = (int) Math.floor(e.getFinalDamage());
        dmgInt = clamp(dmgInt, 1, 17);

        int gain = gainByDamage.getOrDefault(dmgInt, 0);
        int next = clamp(current + gain, 0, 100);
        charge.put(id, next);

        // Hit particles (only when you deal damage)
        spawnHitParticles(attacker);

        // A small "ready" ping when you reach 100%
        if (next >= 100 && current < 100) {
            spawnReadyParticles(attacker);
            attacker.playSound(attacker.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        charge.remove(e.getPlayer().getUniqueId());
    }

@EventHandler
    public void onQuit(PlayerQuitEvent e) {
        charge.remove(e.getPlayer().getUniqueId());
    }

    private enum UiState { CHARGING, READY, USED }

    private void sendActionbar(Player p, int percent, UiState state) {
        String bar = buildBar(percent);

        String raw;
        if (state == UiState.USED) raw = formatUsed;
        else if (state == UiState.READY) raw = formatReady;
        else raw = formatCharging;

        raw = raw
                .replace("{title}", title)
                .replace("{percent}", (state == UiState.USED ? "" : String.valueOf(percent)))
                .replace("{bar}", bar)
                .replace("{ready}", readyText)
                .replace("{used}", usedText);

        Component c = legacy.deserialize(raw);
        p.sendActionBar(c);
    }

    private String buildBar(int percent) {
        int filled = (int) Math.floor((percent / 100.0) * barLength);
        filled = clamp(filled, 0, barLength);

        StringBuilder sb = new StringBuilder(barLength * 4);
        for (int i = 0; i < barLength; i++) {
            sb.append(i < filled ? barFilled : barEmpty);
        }
        return sb.toString();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("ultrastrike")) return false;

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("ultrastrike.admin")) {
                sender.sendMessage(legacy.deserialize("&cНет прав."));
                return true;
            }
            loadSettings();
            sender.sendMessage(legacy.deserialize("&aUltraStrike: конфиг перезагружен."));
            return true;
        }

        sender.sendMessage(legacy.deserialize("&eИспользование: /ultrastrike reload"));
        return true;
    }


/**
 * Reflection hook to AuraSkills (no compile-time dependency).
 * We only read current mana & max mana if AuraSkills is installed.
 */
private static final class AuraSkillsHook {

    private boolean checked = false;
    private boolean present = false;

    private java.lang.reflect.Method providerGetInstance;
    private java.lang.reflect.Method apiGetUserManager;
    private java.lang.reflect.Method userManagerGetUser;
    private java.lang.reflect.Method userGetMana;
    private java.lang.reflect.Method userGetMaxMana;
	    private java.lang.reflect.Method userSetMana;
	    private java.lang.reflect.Method userAddMana;

    static final class ManaSnapshot {
        final boolean available;
        final String manaInt;
        final String maxManaInt;
        ManaSnapshot(boolean available, String manaInt, String maxManaInt) {
            this.available = available;
            this.manaInt = manaInt;
            this.maxManaInt = maxManaInt;
        }
    }

    ManaSnapshot getMana(Player p) {
        ensure();
        if (!present) return new ManaSnapshot(false, "0", "0");
        try {
            Object api = providerGetInstance.invoke(null);
            Object userManager = apiGetUserManager.invoke(api);
            Object user = userManagerGetUser.invoke(userManager, p.getUniqueId());
            if (user == null) return new ManaSnapshot(false, "0", "0");
            double mana = (double) userGetMana.invoke(user);
            double max = (double) userGetMaxMana.invoke(user);
            return new ManaSnapshot(true, String.valueOf((int) Math.floor(mana)), String.valueOf((int) Math.floor(max)));
        } catch (Throwable t) {
            return new ManaSnapshot(false, "0", "0");
        }
    }


    boolean isEnabled() {
        ensure();
        return present;
    }

    boolean tryConsumeMana(Player p, int cost) {
        ensure();
        if (!present) return true; // if AuraSkills not present, don't block
        if (cost <= 0) return true;
        try {
            Object api = providerGetInstance.invoke(null);
            Object userManager = apiGetUserManager.invoke(api);
            Object user = userManagerGetUser.invoke(userManager, p.getUniqueId());
            if (user == null) return true;

            double mana = (double) userGetMana.invoke(user);
            if (mana + 1e-9 < cost) return false;

            // Prefer addMana(-cost) if available, else setMana(mana - cost)
            if (userAddMana != null) {
                userAddMana.invoke(user, -1.0 * cost);
                return true;
            }
            if (userSetMana != null) {
                userSetMana.invoke(user, Math.max(0.0, mana - cost));
                return true;
            }

            // No setter available -> can't consume, so allow but won't change mana
            return true;
        } catch (Throwable t) {
            return true;
        }
    }

    private void ensure() {
        if (checked) return;
        checked = true;
        try {
            Class<?> provider = Class.forName("dev.aurelium.auraskills.api.AuraSkillsProvider");
            providerGetInstance = provider.getMethod("getInstance");

            Class<?> api = Class.forName("dev.aurelium.auraskills.api.AuraSkillsApi");
            apiGetUserManager = api.getMethod("getUserManager");

            Class<?> userManager = Class.forName("dev.aurelium.auraskills.api.user.UserManager");
            userManagerGetUser = userManager.getMethod("getUser", java.util.UUID.class);

            Class<?> skillsUser = Class.forName("dev.aurelium.auraskills.api.user.SkillsUser");
            userGetMana = skillsUser.getMethod("getMana");

                    try { userSetMana = skillsUser.getMethod("setMana", double.class); } catch (NoSuchMethodException ignored) {}
                    try { userAddMana = skillsUser.getMethod("addMana", double.class); } catch (NoSuchMethodException ignored) {}
            userGetMaxMana = skillsUser.getMethod("getMaxMana");

            present = true;
        } catch (Throwable ignored) {
            present = false;
        }
    }
}

@EventHandler
public void onEntityDeath(org.bukkit.event.entity.EntityDeathEvent e) {
    if (!particlesEnabled) return;
    org.bukkit.entity.Player killer = e.getEntity().getKiller();
    if (killer == null) return;

    // small "celebration" particles on kill
    spawnBurst(killer, particlesKill, particlesKillCount, 1.2, 0.8);
    // simple "attract" illusion: draw a few lines of particles towards the killer
    spawnAttractLines(killer, 6, 2.0);
}

private java.util.List<org.bukkit.Particle> parseParticles(java.util.List<String> cfg, java.util.List<String> defaults) {
    java.util.List<String> src = (cfg == null || cfg.isEmpty()) ? defaults : cfg;
    java.util.List<org.bukkit.Particle> out = new java.util.ArrayList<>();
    for (String name : src) {
        if (name == null) continue;
        String key = name.trim().toUpperCase(java.util.Locale.ROOT);
        if (key.isEmpty()) continue;
        try {
            out.add(org.bukkit.Particle.valueOf(key));
        } catch (IllegalArgumentException ignored) {
            // ignore unknown particle names for the server version
        }
    }
    if (out.isEmpty()) {
        out.add(org.bukkit.Particle.CRIT);
    }
    return out;
}

private void spawnHitParticles(org.bukkit.entity.Player p) {
    if (!particlesEnabled) return;

    // A small burst on each successful hit (charging by damage)
    org.bukkit.Location loc = p.getLocation();
    org.bukkit.Location feet = loc.clone().add(0, 0.10, 0);
    org.bukkit.Location head = loc.clone().add(0, 1.75, 0);

    try {
        p.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, feet, 8, 0.55, 0.08, 0.55, 0.02);
    } catch (Throwable ignored) {}

    try {
        p.getWorld().spawnParticle(org.bukkit.Particle.valueOf("ENCHANT"), head, 10, 0.45, 0.30, 0.45, 0.0);
    } catch (Throwable ignored) {}
}

private void spawnReadyParticles(org.bukkit.entity.Player p) {
    if (!particlesEnabled) return;
    org.bukkit.Location head = p.getLocation().add(0, 1.6, 0);
    try {
        p.getWorld().spawnParticle(org.bukkit.Particle.valueOf("TOTEM_OF_UNDYING"), head, 12, 0.35, 0.45, 0.35, 0.02);
    } catch (Throwable ignored) {}
}

private void spawnUseParticles(org.bukkit.entity.Player p) {
    if (!particlesEnabled) return;

    org.bukkit.Location base = p.getLocation();
    org.bukkit.Location feet = base.clone().add(0, 0.10, 0);
    org.bukkit.Location center = base.clone().add(0, 1.0, 0);

    // Quick impact
    try {
        org.bukkit.util.Vector dir = base.getDirection().normalize();
        org.bukkit.Location front = center.clone().add(dir.multiply(1.2));
        p.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK, front, 2, 0.2, 0.1, 0.2, 0.0);
    } catch (Throwable ignored) {}

    try {
        p.getWorld().spawnParticle(org.bukkit.Particle.CRIT, center, 14, 0.35, 0.25, 0.35, 0.1);
    } catch (Throwable ignored) {}

    // 1-2 seconds burst (no permanent particles)
    new org.bukkit.scheduler.BukkitRunnable() {
        int ticks = 0;
        @Override public void run() {
            if (!p.isOnline()) { cancel(); return; }
            if (ticks >= 30) { cancel(); return; } // ~1.5s

            try {
                p.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, feet, 8, 0.55, 0.08, 0.55, 0.02);
            } catch (Throwable ignored) {}

            try {
                p.getWorld().spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, center, 5, 0.25, 0.25, 0.25, 0.0);
            } catch (Throwable ignored) {}

            ticks += 5;
        }
    }.runTaskTimer(UltraStrike.this, 0L, 5L);
}

private void spawnAroundHead(org.bukkit.entity.Player p, java.util.List<org.bukkit.Particle> list, int count, double radius) {
    org.bukkit.Location head = p.getLocation().add(0, 1.8, 0);
    java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
    for (int i = 0; i < count; i++) {
        org.bukkit.Location loc = head.clone().add(
                r.nextDouble(-radius, radius),
                r.nextDouble(-0.1, 0.25),
                r.nextDouble(-radius, radius)
        );
        spawnOne(p, list, loc);
    }
}

private void spawnRing(org.bukkit.entity.Player p, java.util.List<org.bukkit.Particle> list, int points, double radius) {
    org.bukkit.Location c = p.getLocation().add(0, 0.2, 0);
    for (int i = 0; i < points; i++) {
        double a = (Math.PI * 2.0) * (i / (double) points);
        org.bukkit.Location loc = c.clone().add(Math.cos(a) * radius, 0.0, Math.sin(a) * radius);
        spawnOne(p, list, loc);
    }
}

private void spawnBurst(org.bukkit.entity.Player p, java.util.List<org.bukkit.Particle> list, int count, double radius, double y) {
    org.bukkit.Location c = p.getLocation().add(0, y, 0);
    java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
    for (int i = 0; i < count; i++) {
        org.bukkit.Location loc = c.clone().add(
                r.nextDouble(-radius, radius),
                r.nextDouble(-0.3, 0.7),
                r.nextDouble(-radius, radius)
        );
        spawnOne(p, list, loc);
    }
}

private void spawnAttractLines(org.bukkit.entity.Player p, int lines, double radius) {
    org.bukkit.Location c = p.getLocation().add(0, 1.0, 0);
    java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
    for (int i = 0; i < lines; i++) {
        org.bukkit.Location from = c.clone().add(
                r.nextDouble(-radius, radius),
                r.nextDouble(-0.2, 0.8),
                r.nextDouble(-radius, radius)
        );
        // draw 8 steps towards player center
        for (int s = 0; s <= 8; s++) {
            double t = s / 8.0;
            org.bukkit.Location loc = from.clone().multiply(1 - t).add(c.clone().multiply(t));
            spawnOne(p, particlesCharge, loc);
        }
    }
}

private void spawnOne(org.bukkit.entity.Player p, java.util.List<org.bukkit.Particle> list, org.bukkit.Location loc) {
    if (list == null || list.isEmpty()) return;
    org.bukkit.Particle particle = list.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(list.size()));
    try {
        p.getWorld().spawnParticle(particle, loc, 1, 0.0, 0.0, 0.0, 0.0);
    } catch (Throwable ignored) {
        // some particles may require extra data on certain versions; ignore
    }
}



    /**
     * Draws short "pull" lines (particle dots) from random points around the player towards the player.
     * Designed to be light and to work reasonably on both Java and Bedrock (via Geyser) by using simple particles.
     */
    private void spawnPullLines(org.bukkit.entity.Player player, org.bukkit.Particle particle, int lines) {
        if (player == null || !player.isOnline()) return;
        org.bukkit.Location eye = player.getEyeLocation();
        org.bukkit.World world = eye.getWorld();
        if (world == null) return;

        java.util.Random rnd = new java.util.Random();

        // Radius around player where lines start
        double radius = 2.0;
        for (int i = 0; i < Math.max(1, lines); i++) {
            double angle = rnd.nextDouble() * Math.PI * 2.0;
            double r = rnd.nextDouble() * radius;
            double startX = eye.getX() + Math.cos(angle) * r;
            double startZ = eye.getZ() + Math.sin(angle) * r;
            double startY = eye.getY() - 0.6 + rnd.nextDouble() * 1.2;

            org.bukkit.util.Vector start = new org.bukkit.util.Vector(startX, startY, startZ);
            org.bukkit.util.Vector end = eye.toVector().add(new org.bukkit.util.Vector(0, -0.2, 0));
            org.bukkit.util.Vector dir = end.clone().subtract(start);
            double len = dir.length();
            if (len < 0.001) continue;
            dir.normalize();

            // Step spacing: fewer points = less spam
            double step = 0.35;
            int steps = (int) Math.min(10, Math.max(3, Math.floor(len / step)));
            for (int s = 0; s < steps; s++) {
                org.bukkit.util.Vector pos = start.clone().add(dir.clone().multiply(step * s));
                world.spawnParticle(particle, pos.getX(), pos.getY(), pos.getZ(), 1, 0, 0, 0, 0);
            }
        }
    }

}