package com.artemis.the.gr8.playerstats.core.gui;

import com.artemis.the.gr8.playerstats.api.RequestGenerator;
import com.artemis.the.gr8.playerstats.api.StatRequest;
import com.artemis.the.gr8.playerstats.core.config.ConfigHandler;
import com.artemis.the.gr8.playerstats.core.multithreading.ThreadManager;
import com.artemis.the.gr8.playerstats.core.msg.msgutils.LanguageKeyHandler;
import com.artemis.the.gr8.playerstats.core.statistic.PlayerStatRequest;
import com.artemis.the.gr8.playerstats.core.statistic.TopStatRequest;
import com.artemis.the.gr8.playerstats.core.utils.EnumHandler;
import com.artemis.the.gr8.playerstats.core.utils.OfflinePlayerHandler;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.property.SkinProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class StatsGuiManager {

    private static final int PAGE_SIZE = 45;
    private final ThreadManager threadManager;
    private final ConfigHandler config;
    private final EnumHandler enumHandler;
    private final OfflinePlayerHandler offlinePlayerHandler;
    private final LanguageKeyHandler languageKeyHandler;

    public StatsGuiManager(@NotNull ThreadManager threadManager) {
        this.threadManager = threadManager;
        this.config = ConfigHandler.getInstance();
        this.enumHandler = EnumHandler.getInstance();
        this.offlinePlayerHandler = OfflinePlayerHandler.getInstance();
        this.languageKeyHandler = LanguageKeyHandler.getInstance();
    }

    public void openMainMenu(@NotNull Player player) {
        new StatisticListMenu(player, 0).open();
    }

    public boolean isManagedInventory(@NotNull Inventory inventory) {
        return inventory.getHolder() instanceof Menu;
    }

    public void handleInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof Menu menu)) {
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == null) {
            return;
        }
        menu.handleClick(event);
    }

    private void startLookup(@NotNull Player sender, @NotNull StatContext context, @NotNull LookupAction action, @Nullable String targetPlayer) {
        RequestGenerator<?> requestGenerator;
        requestGenerator = switch (action) {
            case TOP_10 -> new TopStatRequest(sender, config.getTopListMaxSize());
            case MY_STAT -> new PlayerStatRequest(sender, sender.getName());
            case PLAYER_STAT -> new PlayerStatRequest(sender, targetPlayer);
        };

        StatRequest<?> request = switch (context.statistic().getType()) {
            case UNTYPED -> requestGenerator.untyped(context.statistic());
            case BLOCK, ITEM -> requestGenerator.blockOrItemType(context.statistic(), context.material());
            case ENTITY -> requestGenerator.entityType(context.statistic(), context.entityType());
        };
        threadManager.startStatThread(request);
        sender.closeInventory();
    }

    private @NotNull List<Statistic> getSortedStatistics() {
        return Arrays.stream(Statistic.values())
                .sorted(Comparator
                        .comparingInt((Statistic stat) -> stat.getType().ordinal())
                        .thenComparing(Enum::name))
                .toList();
    }

    private @NotNull List<EntityType> getEntitiesForStatistic(@NotNull Statistic statistic) {
        Collection<String> names = enumHandler.getAllEntitiesThatCanDie();
        return names.stream()
                .map(enumHandler::getEntityEnum)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Enum::name))
                .toList();
    }

    private @NotNull List<Material> getMaterialsForStatistic(@NotNull Statistic statistic) {
        Collection<String> names;
        if (statistic.getType() == Statistic.Type.BLOCK) {
            names = enumHandler.getAllBlockNames();
        } else if (statistic == Statistic.BREAK_ITEM) {
            names = enumHandler.getAllItemsThatCanBreak();
        } else {
            names = enumHandler.getAllItemNames();
        }

        return names.stream()
                .map(Material::matchMaterial)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Enum::name))
                .toList();
    }

    private @NotNull List<String> getPlayerNames() {
        return offlinePlayerHandler.getIncludedOfflinePlayerNames().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private @NotNull Component decorate(@NotNull Component component, @NotNull NamedTextColor color) {
        return component.color(color).decoration(TextDecoration.ITALIC, false);
    }

    private @NotNull Component statDisplayName(@NotNull Statistic statistic) {
        return decorate(Component.translatable(languageKeyHandler.getStatKey(statistic)), NamedTextColor.GOLD);
    }

    private @NotNull Component entityDisplayName(@NotNull EntityType entityType) {
        String key = languageKeyHandler.getEntityKey(entityType);
        if (key == null) {
            return decorate(Component.text(prettify(entityType.name())), NamedTextColor.YELLOW);
        }
        return decorate(Component.translatable(key), NamedTextColor.YELLOW);
    }

    private @NotNull Component materialDisplayName(@NotNull Material material, boolean blockType) {
        String key = blockType ? languageKeyHandler.getBlockKey(material) : languageKeyHandler.getItemKey(material);
        if (key == null) {
            return decorate(Component.text(prettify(material.name())), NamedTextColor.YELLOW);
        }
        return decorate(Component.translatable(key), NamedTextColor.YELLOW);
    }

    private @NotNull String prettify(@NotNull String raw) {
        String lower = raw.toLowerCase(Locale.ENGLISH).replace('_', ' ');
        if (lower.isEmpty()) {
            return raw;
        }
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private @NotNull ItemStack createNavItem(@NotNull Material material, @NotNull String name, @NotNull String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(decorate(Component.text(name), NamedTextColor.AQUA));
        if (loreLines.length > 0) {
            meta.lore(Arrays.stream(loreLines)
                    .map(line -> decorate(Component.text(line), NamedTextColor.GRAY))
                    .toList());
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private @NotNull ItemStack createStatItem(@NotNull Statistic statistic) {
        ItemStack item = new ItemStack(resolveStatisticIcon(statistic));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(statDisplayName(statistic));

        List<Component> lore = new ArrayList<>();
        lore.add(decorate(Component.text("Тип: " + getRussianTypeName(statistic.getType())), NamedTextColor.GRAY));
        if (statistic.getType() == Statistic.Type.UNTYPED) {
            lore.add(decorate(Component.text("Нажмите, чтобы выбрать действие."), NamedTextColor.DARK_GRAY));
        } else {
            lore.add(decorate(Component.text("Сначала откроется выбор категории и объекта."), NamedTextColor.DARK_GRAY));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private @NotNull ItemStack createEntityItem(@NotNull EntityType entityType) {
        ItemStack item = new ItemStack(resolveEntityIcon(entityType));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(entityDisplayName(entityType));
        meta.lore(List.of(decorate(Component.text("Нажмите, чтобы выбрать действие."), NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    private @NotNull ItemStack createMaterialItem(@NotNull Material material, boolean blockType) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(materialDisplayName(material, blockType));
        meta.lore(List.of(decorate(Component.text("Нажмите, чтобы выбрать действие."), NamedTextColor.GRAY)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private @NotNull ItemStack createPlayerHead(@NotNull String playerName) {
        OfflinePlayer offlinePlayer = offlinePlayerHandler.getIncludedOfflinePlayer(playerName);
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        Player onlinePlayer = Bukkit.getPlayerExact(playerName);
        if (onlinePlayer != null) {
            meta.setPlayerProfile(onlinePlayer.getPlayerProfile());
        } else {
            applySkinRestorerProfile(meta, offlinePlayer);
            if (meta.getPlayerProfile() == null) {
                meta.setOwningPlayer(offlinePlayer);
            }
        }

        meta.displayName(decorate(Component.text(playerName), NamedTextColor.GOLD));
        meta.lore(List.of(
                decorate(Component.text("Нажмите, чтобы посмотреть статистику игрока."), NamedTextColor.GRAY),
                decorate(Component.text("Голова подгружается через профиль игрока и SkinRestorer, если он установлен."), NamedTextColor.DARK_GRAY)
        ));
        head.setItemMeta(meta);
        return head;
    }

    private void applySkinRestorerProfile(@NotNull SkullMeta meta, @NotNull OfflinePlayer offlinePlayer) {
        if (!Bukkit.getPluginManager().isPluginEnabled("SkinsRestorer") || offlinePlayer.getName() == null) {
            return;
        }
        try {
            Optional<SkinProperty> property = SkinsRestorerProvider.get()
                    .getPlayerStorage()
                    .getSkinOfPlayer(offlinePlayer.getUniqueId());
            if (property.isEmpty()) {
                return;
            }

            PlayerProfile profile = Bukkit.createPlayerProfile(offlinePlayer.getUniqueId(), offlinePlayer.getName());
            SkinProperty skinProperty = property.get();
            profile.setProperty(new ProfileProperty("textures", skinProperty.getValue(), skinProperty.getSignature()));
            meta.setPlayerProfile(profile);
        } catch (Throwable ignored) {
            // graceful fallback to the normal owning-player head
        }
    }

    private @NotNull Material resolveStatisticIcon(@NotNull Statistic statistic) {
        String name = statistic.name();
        return switch (statistic) {
            case PLAY_ONE_MINUTE, TOTAL_WORLD_TIME, TIME_SINCE_DEATH, TIME_SINCE_REST -> Material.CLOCK;
            case WALK_ONE_CM, CROUCH_ONE_CM, SPRINT_ONE_CM, JUMP, WALK_UNDER_WATER_ONE_CM, WALK_ON_WATER_ONE_CM -> Material.LEATHER_BOOTS;
            case FLY_ONE_CM, AVIATE_ONE_CM -> Material.ELYTRA;
            case SWIM_ONE_CM -> Material.TURTLE_HELMET;
            case CLIMB_ONE_CM -> Material.LADDER;
            case FALL_ONE_CM -> Material.FEATHER;
            case DAMAGE_DEALT, DAMAGE_DEALT_ABSORBED, DAMAGE_DEALT_RESISTED -> Material.DIAMOND_SWORD;
            case DAMAGE_TAKEN, DAMAGE_BLOCKED_BY_SHIELD, DAMAGE_ABSORBED, DAMAGE_RESISTED -> Material.SHIELD;
            case DEATHS -> Material.SKELETON_SKULL;
            case MOB_KILLS, PLAYER_KILLS, KILL_ENTITY -> Material.IRON_SWORD;
            case ENTITY_KILLED_BY -> Material.TOTEM_OF_UNDYING;
            case MINE_BLOCK -> Material.DIAMOND_PICKAXE;
            case USE_ITEM -> Material.CHEST;
            case BREAK_ITEM -> Material.ANVIL;
            case CRAFT_ITEM -> Material.CRAFTING_TABLE;
            case DROP, PICKUP, DROP_COUNT -> Material.HOPPER;
            case CHEST_OPENED -> Material.CHEST;
            case ENDERCHEST_OPENED -> Material.ENDER_CHEST;
            case SHULKER_BOX_OPENED -> Material.SHULKER_BOX;
            case SLEEP_IN_BED -> Material.RED_BED;
            case FISH_CAUGHT -> Material.FISHING_ROD;
            case TALKED_TO_VILLAGER, TRADED_WITH_VILLAGER -> Material.EMERALD;
            case CAKE_SLICES_EATEN -> Material.CAKE;
            case ANIMALS_BRED -> Material.WHEAT;
            case LEAVE_GAME -> Material.OAK_DOOR;
            default -> {
                if (name.contains("MINE") || name.contains("BLOCK")) {
                    yield Material.STONE_PICKAXE;
                }
                if (name.contains("KILL") || name.contains("DAMAGE")) {
                    yield Material.IRON_SWORD;
                }
                if (name.contains("FISH")) {
                    yield Material.FISHING_ROD;
                }
                if (name.contains("VILLAGER") || name.contains("TRADE")) {
                    yield Material.EMERALD;
                }
                if (name.contains("OPEN") || name.contains("INTERACTION") || name.contains("INSPECTED")) {
                    yield Material.BOOK;
                }
                if (name.contains("TIME") || name.contains("PLAY")) {
                    yield Material.CLOCK;
                }
                if (name.contains("WALK") || name.contains("SPRINT") || name.contains("JUMP") || name.contains("CLIMB")) {
                    yield Material.LEATHER_BOOTS;
                }
                if (statistic.getType() == Statistic.Type.ENTITY) {
                    yield Material.ZOMBIE_HEAD;
                }
                if (statistic.getType() == Statistic.Type.BLOCK) {
                    yield Material.GRASS_BLOCK;
                }
                if (statistic.getType() == Statistic.Type.ITEM) {
                    yield Material.CHEST;
                }
                yield Material.PAPER;
            }
        };
    }

    private @NotNull Material resolveEntityIcon(@NotNull EntityType entityType) {
        return switch (categorizeEntity(entityType)) {
            case PASSIVE -> Material.SHEEP_SPAWN_EGG;
            case HOSTILE -> Material.ZOMBIE_SPAWN_EGG;
            case WATER -> Material.COD_BUCKET;
            case OTHER -> Material.ARMOR_STAND;
        };
    }

    private @NotNull String getRussianTypeName(@NotNull Statistic.Type type) {
        return switch (type) {
            case UNTYPED -> "без доп. выбора";
            case BLOCK -> "блок";
            case ITEM -> "предмет";
            case ENTITY -> "моб/сущность";
        };
    }

    private @NotNull EntityCategory categorizeEntity(@NotNull EntityType entityType) {
        if (AQUATIC_ENTITIES.contains(entityType.name())) {
            return EntityCategory.WATER;
        }
        if (HOSTILE_ENTITIES.contains(entityType.name())) {
            return EntityCategory.HOSTILE;
        }
        if (PASSIVE_ENTITIES.contains(entityType.name())) {
            return EntityCategory.PASSIVE;
        }
        return EntityCategory.OTHER;
    }

    private @NotNull MaterialCategory categorizeMaterial(@NotNull Material material, boolean blockType) {
        String name = material.name();

        if (name.contains("NETHER") || name.contains("CRIMSON") || name.contains("WARPED") || name.contains("BLACKSTONE") ||
                name.contains("BASALT") || name.contains("SOUL_") || name.contains("MAGMA") || name.contains("QUARTZ") ||
                name.contains("GHAST") || name.contains("BLAZE") || name.contains("PIGLIN") || name.contains("HOGLIN")) {
            return MaterialCategory.NETHER_END;
        }
        if (name.contains("END_") || name.contains("PURPUR") || name.contains("CHORUS") || name.contains("SHULKER")) {
            return MaterialCategory.NETHER_END;
        }
        if (name.contains("WATER") || name.contains("KELP") || name.contains("CORAL") || name.contains("SEAGRASS") ||
                name.contains("PRISMARINE") || name.contains("SPONGE") || name.contains("NAUTILUS") || name.contains("SCUTE") ||
                name.contains("SALMON") || name.contains("COD") || name.contains("TROPICAL_FISH") || name.contains("PUFFERFISH")) {
            return MaterialCategory.AQUATIC;
        }
        if (name.contains("REDSTONE") || name.contains("REPEATER") || name.contains("COMPARATOR") || name.contains("OBSERVER") ||
                name.contains("PISTON") || name.contains("LEVER") || name.contains("BUTTON") || name.contains("PRESSURE_PLATE") ||
                name.contains("HOPPER") || name.contains("DISPENSER") || name.contains("DROPPER") || name.contains("SCULK_SENSOR") ||
                name.contains("LIGHTNING_ROD") || name.contains("TRIPWIRE") || name.contains("TARGET")) {
            return MaterialCategory.REDSTONE;
        }
        if (material.isEdible() || name.contains("STEW") || name.contains("BREAD") || name.contains("PIE") || name.contains("CAKE")) {
            return MaterialCategory.FOOD_FARM;
        }
        if (name.contains("SAPLING") || name.contains("SEEDS") || name.contains("WHEAT") || name.contains("CARROT") ||
                name.contains("POTATO") || name.contains("BEETROOT") || name.contains("NETHER_WART") || name.contains("MELON") ||
                name.contains("PUMPKIN") || name.contains("CANE") || name.contains("BAMBOO") || name.contains("FLOWER") ||
                name.contains("LEAVES") || name.contains("MOSS") || name.contains("VINE") || name.contains("GRASS") ||
                name.contains("BUSH") || name.contains("TULIP") || name.contains("DANDELION") || name.contains("ROSE")) {
            return MaterialCategory.FOOD_FARM;
        }
        if (name.contains("SWORD") || name.contains("AXE") || name.contains("BOW") || name.contains("CROSSBOW") ||
                name.contains("TRIDENT") || name.contains("MACE") || name.contains("SHIELD") || name.contains("ARROW") ||
                name.contains("TOTEM") || name.contains("ELYTRA")) {
            return MaterialCategory.COMBAT_TOOLS;
        }
        if (name.contains("PICKAXE") || name.contains("SHOVEL") || name.contains("HOE") || name.contains("FISHING_ROD") ||
                name.contains("FLINT_AND_STEEL") || name.contains("SHEARS") || name.contains("BRUSH") || name.contains("COMPASS") ||
                name.contains("CLOCK") || name.contains("SPYGLASS")) {
            return MaterialCategory.COMBAT_TOOLS;
        }
        if (name.contains("HELMET") || name.contains("CHESTPLATE") || name.contains("LEGGINGS") || name.contains("BOOTS") || name.contains("HORSE_ARMOR")) {
            return MaterialCategory.COMBAT_TOOLS;
        }
        if (name.contains("INGOT") || name.contains("NUGGET") || name.contains("RAW_") || name.contains("DIAMOND") ||
                name.contains("EMERALD") || name.contains("AMETHYST") || name.contains("SHARD") || name.contains("CRYSTAL") ||
                name.contains("LAPIS") || name.contains("COAL") || name.contains("COPPER") || name.contains("IRON") ||
                name.contains("GOLD") || name.contains("STRING") || name.contains("FEATHER") || name.contains("LEATHER") ||
                name.contains("GUNPOWDER") || name.contains("BONE") || name.contains("SLIME") || name.contains("QUARTZ")) {
            return MaterialCategory.RESOURCES;
        }

        if (blockType) {
            if (name.contains("STAIRS") || name.contains("SLAB") || name.contains("WALL") || name.contains("FENCE") ||
                    name.contains("DOOR") || name.contains("TRAPDOOR") || name.contains("PLANKS") || name.contains("BRICKS") ||
                    name.contains("GLASS") || name.contains("CONCRETE") || name.contains("TERRACOTTA") || name.contains("WOOL")) {
                return MaterialCategory.BUILDING_DECOR;
            }
            if (name.contains("BANNER") || name.contains("CARPET") || name.contains("BED") || name.contains("LANTERN") ||
                    name.contains("CANDLE") || name.contains("PAINTING") || name.contains("POT") || name.contains("SIGN") ||
                    name.contains("BOOKSHELF") || name.contains("CHAIN") || name.contains("SKULL") || name.contains("HEAD")) {
                return MaterialCategory.BUILDING_DECOR;
            }
            if (name.contains("STONE") || name.contains("DEEPSLATE") || name.contains("DIRT") || name.contains("SAND") ||
                    name.contains("GRAVEL") || name.contains("ORE") || name.contains("LOG") || name.contains("ICE") ||
                    name.contains("SNOW") || name.contains("CLAY") || name.contains("OBSIDIAN") || name.contains("TUFF") ||
                    name.contains("COBBLESTONE") || name.contains("MUD")) {
                return MaterialCategory.NATURAL;
            }
        }

        return MaterialCategory.OTHER;
    }

    private static final List<String> AQUATIC_ENTITIES = List.of(
            "AXOLOTL", "COD", "DOLPHIN", "DROWNED", "ELDER_GUARDIAN",
            "GLOW_SQUID", "GUARDIAN", "PUFFERFISH", "SALMON", "SQUID",
            "TADPOLE", "TROPICAL_FISH", "TURTLE"
    );

    private static final List<String> HOSTILE_ENTITIES = List.of(
            "BLAZE", "BOGGED", "BREEZE", "CAVE_SPIDER", "CREAKING", "CREEPER",
            "DROWNED", "ELDER_GUARDIAN", "ENDER_DRAGON", "ENDERMAN", "ENDERMITE",
            "EVOKER", "GHAST", "GUARDIAN", "HOGLIN", "HUSK", "ILLUSIONER",
            "MAGMA_CUBE", "PHANTOM", "PIGLIN_BRUTE", "PILLAGER", "RAVAGER",
            "SHULKER", "SILVERFISH", "SKELETON", "SLIME", "SPIDER", "STRAY",
            "VEX", "VINDICATOR", "WARDEN", "WITCH", "WITHER", "WITHER_SKELETON",
            "ZOGLIN", "ZOMBIE", "ZOMBIE_VILLAGER", "ZOMBIFIED_PIGLIN"
    );

    private static final List<String> PASSIVE_ENTITIES = List.of(
            "ALLAY", "ARMADILLO", "AXOLOTL", "BAT", "BEE", "CAMEL", "CAT",
            "CHICKEN", "COD", "COW", "DONKEY", "FOX", "FROG", "GLOW_SQUID",
            "GOAT", "HORSE", "IRON_GOLEM", "LLAMA", "MOOSHROOM", "MULE",
            "OCELOT", "PANDA", "PARROT", "PIG", "POLAR_BEAR", "PUFFERFISH",
            "RABBIT", "SALMON", "SHEEP", "SNIFFER", "SNOW_GOLEM", "SQUID",
            "STRIDER", "TADPOLE", "TRADER_LLAMA", "TROPICAL_FISH", "TURTLE",
            "VILLAGER", "WANDERING_TRADER", "WOLF"
    );

    private enum LookupAction {
        TOP_10,
        MY_STAT,
        PLAYER_STAT
    }

    private enum EntityCategory {
        PASSIVE("Мирные мобы", Material.SHEEP_SPAWN_EGG),
        HOSTILE("Агрессивные мобы", Material.ZOMBIE_SPAWN_EGG),
        WATER("Водные сущности", Material.COD_BUCKET),
        OTHER("Прочие сущности", Material.ARMOR_STAND);

        private final String title;
        private final Material icon;

        EntityCategory(String title, Material icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    private enum MaterialCategory {
        NATURAL("Природные", Material.GRASS_BLOCK),
        BUILDING_DECOR("Строительство и декор", Material.BRICKS),
        REDSTONE("Механизмы и редстоун", Material.REDSTONE),
        FOOD_FARM("Еда и ферма", Material.GOLDEN_CARROT),
        COMBAT_TOOLS("Бой, броня и инструменты", Material.IRON_SWORD),
        RESOURCES("Ресурсы", Material.DIAMOND),
        NETHER_END("Нижний мир и Энд", Material.NETHER_BRICKS),
        AQUATIC("Водные", Material.PRISMARINE),
        OTHER("Прочее", Material.CHEST);

        private final String title;
        private final Material icon;

        MaterialCategory(String title, Material icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    private record StatContext(@NotNull Statistic statistic, @Nullable Material material, @Nullable EntityType entityType) {
    }

    private abstract class Menu implements InventoryHolder {
        protected final Player viewer;
        protected final Inventory inventory;
        private final Map<Integer, Consumer<InventoryClickEvent>> clickHandlers = new HashMap<>();

        protected Menu(@NotNull Player viewer, int size, @NotNull String title) {
            this.viewer = viewer;
            this.inventory = Bukkit.createInventory(this, size, title);
            fillNavigationBar();
        }

        protected abstract void build();

        public void open() {
            build();
            viewer.openInventory(inventory);
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }

        protected void setButton(int slot, @NotNull ItemStack item, @Nullable Consumer<InventoryClickEvent> handler) {
            inventory.setItem(slot, item);
            if (handler != null) {
                clickHandlers.put(slot, handler);
            }
        }

        private void fillNavigationBar() {
            ItemStack filler = createNavItem(Material.GRAY_STAINED_GLASS_PANE, " ");
            int start = Math.max(0, inventory.getSize() - 9);
            for (int slot = start; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, filler);
            }
        }

        void handleClick(@NotNull InventoryClickEvent event) {
            if (event.getClickedInventory() == null || event.getClickedInventory() != inventory) {
                return;
            }
            Consumer<InventoryClickEvent> handler = clickHandlers.get(event.getRawSlot());
            if (handler != null) {
                handler.accept(event);
            }
        }
    }

    private final class StatisticListMenu extends Menu {
        private final int page;
        private final List<Statistic> statistics;

        private StatisticListMenu(@NotNull Player viewer, int page) {
            super(viewer, 54, "Статистика игроков");
            this.page = page;
            this.statistics = getSortedStatistics();
        }

        @Override
        protected void build() {
            int start = page * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, statistics.size());

            for (int slot = 0; slot < PAGE_SIZE && (start + slot) < end; slot++) {
                Statistic statistic = statistics.get(start + slot);
                setButton(slot, createStatItem(statistic), click -> {
                    Runnable back = () -> new StatisticListMenu(viewer, page).open();
                    if (statistic.getType() == Statistic.Type.UNTYPED) {
                        new LookupActionMenu(viewer, new StatContext(statistic, null, null), back).open();
                    } else if (statistic.getType() == Statistic.Type.ENTITY) {
                        new EntityCategoryMenu(viewer, statistic, back).open();
                    } else {
                        new MaterialCategoryMenu(viewer, statistic, back).open();
                    }
                });
            }

            int maxPage = Math.max(0, (statistics.size() - 1) / PAGE_SIZE);
            setButton(45, createNavItem(Material.ARROW, "Назад", "Предыдущая страница"), click -> {
                if (page > 0) {
                    new StatisticListMenu(viewer, page - 1).open();
                }
            });
            setButton(49, createNavItem(Material.COMPASS, "Главное меню", "Открыт список всех статистик", "Страница " + (page + 1) + " из " + (maxPage + 1)), null);
            setButton(53, createNavItem(Material.ARROW, "Вперёд", "Следующая страница"), click -> {
                if (page < maxPage) {
                    new StatisticListMenu(viewer, page + 1).open();
                }
            });
        }
    }

    private final class LookupActionMenu extends Menu {
        private final StatContext statContext;
        private final Runnable backAction;

        private LookupActionMenu(@NotNull Player viewer, @NotNull StatContext statContext, @NotNull Runnable backAction) {
            super(viewer, 27, "Действия со статистикой");
            this.statContext = statContext;
            this.backAction = backAction;
        }

        @Override
        protected void build() {
            setButton(11, createNavItem(Material.GOLD_INGOT, "Посмотреть топ 10", "Откроется обычный вывод плагина в чат."),
                    click -> startLookup(viewer, statContext, LookupAction.TOP_10, null));
            setButton(13, createNavItem(Material.PLAYER_HEAD, "Посмотреть мою статистику", "Статистика текущего игрока."),
                    click -> startLookup(viewer, statContext, LookupAction.MY_STAT, viewer.getName()));
            setButton(15, createNavItem(Material.SPYGLASS, "Посмотреть статистику игрока", "Откроется список игроков с головами."),
                    click -> new PlayerListMenu(viewer, statContext, () -> new LookupActionMenu(viewer, statContext, backAction).open(), 0).open());
            setButton(18, createNavItem(Material.ARROW, "Назад", "Вернуться к предыдущему меню"), click -> backAction.run());
            setButton(22, createNavItem(resolveStatisticIcon(statContext.statistic()), "Выбранная статистика", "Нажмите слева или справа, чтобы выполнить действие."), null);
            if (statContext.material() != null) {
                setButton(26, createMaterialItem(statContext.material(), statContext.statistic().getType() == Statistic.Type.BLOCK), null);
            } else if (statContext.entityType() != null) {
                setButton(26, createEntityItem(statContext.entityType()), null);
            }
        }
    }

    private final class EntityCategoryMenu extends Menu {
        private final Statistic statistic;
        private final Runnable backAction;

        private EntityCategoryMenu(@NotNull Player viewer, @NotNull Statistic statistic, @NotNull Runnable backAction) {
            super(viewer, 27, "Категория сущности");
            this.statistic = statistic;
            this.backAction = backAction;
        }

        @Override
        protected void build() {
            int slot = 10;
            for (EntityCategory category : EntityCategory.values()) {
                setButton(slot, createNavItem(category.icon, category.title, "Открыть список сущностей"),
                        click -> new EntityListMenu(viewer, statistic, category, () -> new EntityCategoryMenu(viewer, statistic, backAction).open(), 0).open());
                slot += 2;
            }
            setButton(18, createNavItem(Material.ARROW, "Назад", "Вернуться к списку статистик"), click -> backAction.run());
            setButton(22, createNavItem(resolveStatisticIcon(statistic), "Выбранная статистика", "Сначала выберите категорию мобов."), null);
        }
    }

    private final class EntityListMenu extends Menu {
        private final Statistic statistic;
        private final EntityCategory category;
        private final Runnable backAction;
        private final int page;
        private final List<EntityType> filteredEntities;

        private EntityListMenu(@NotNull Player viewer, @NotNull Statistic statistic, @NotNull EntityCategory category,
                               @NotNull Runnable backAction, int page) {
            super(viewer, 54, "Выбор сущности");
            this.statistic = statistic;
            this.category = category;
            this.backAction = backAction;
            this.page = page;
            this.filteredEntities = getEntitiesForStatistic(statistic).stream()
                    .filter(entityType -> categorizeEntity(entityType) == category)
                    .toList();
        }

        @Override
        protected void build() {
            int start = page * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, filteredEntities.size());
            for (int slot = 0; slot < PAGE_SIZE && (start + slot) < end; slot++) {
                EntityType entityType = filteredEntities.get(start + slot);
                setButton(slot, createEntityItem(entityType), click -> {
                    StatContext context = new StatContext(statistic, null, entityType);
                    new LookupActionMenu(viewer, context, () -> new EntityListMenu(viewer, statistic, category, backAction, page).open()).open();
                });
            }

            int maxPage = Math.max(0, (filteredEntities.size() - 1) / PAGE_SIZE);
            setButton(45, createNavItem(Material.ARROW, "Назад", "Предыдущая страница"), click -> {
                if (page > 0) {
                    new EntityListMenu(viewer, statistic, category, backAction, page - 1).open();
                }
            });
            setButton(49, createNavItem(category.icon, category.title, "Страница " + (page + 1) + " из " + (maxPage + 1)), null);
            setButton(52, createNavItem(Material.BARRIER, "К выбору категории", "Вернуться к категориям сущностей"), click -> backAction.run());
            setButton(53, createNavItem(Material.ARROW, "Вперёд", "Следующая страница"), click -> {
                if (page < maxPage) {
                    new EntityListMenu(viewer, statistic, category, backAction, page + 1).open();
                }
            });
        }
    }

    private final class MaterialCategoryMenu extends Menu {
        private final Statistic statistic;
        private final Runnable backAction;

        private MaterialCategoryMenu(@NotNull Player viewer, @NotNull Statistic statistic, @NotNull Runnable backAction) {
            super(viewer, 36, statistic.getType() == Statistic.Type.BLOCK ? "Категория блока" : "Категория предмета");
            this.statistic = statistic;
            this.backAction = backAction;
        }

        @Override
        protected void build() {
            int slot = 10;
            for (MaterialCategory category : MaterialCategory.values()) {
                setButton(slot, createNavItem(category.icon, category.title, "Открыть список значений"),
                        click -> new MaterialListMenu(viewer, statistic, category,
                                () -> new MaterialCategoryMenu(viewer, statistic, backAction).open(), 0).open());
                slot++;
                if (slot == 17) {
                    slot = 19;
                }
            }
            setButton(27, createNavItem(Material.ARROW, "Назад", "Вернуться к списку статистик"), click -> backAction.run());
            setButton(31, createNavItem(resolveStatisticIcon(statistic), "Выбранная статистика", "Сначала выберите категорию блока или предмета."), null);
        }
    }

    private final class MaterialListMenu extends Menu {
        private final Statistic statistic;
        private final MaterialCategory category;
        private final Runnable backAction;
        private final int page;
        private final List<Material> filteredMaterials;

        private MaterialListMenu(@NotNull Player viewer, @NotNull Statistic statistic, @NotNull MaterialCategory category,
                                 @NotNull Runnable backAction, int page) {
            super(viewer, 54, statistic.getType() == Statistic.Type.BLOCK ? "Выбор блока" : "Выбор предмета");
            this.statistic = statistic;
            this.category = category;
            this.backAction = backAction;
            this.page = page;
            boolean blockType = statistic.getType() == Statistic.Type.BLOCK;
            this.filteredMaterials = getMaterialsForStatistic(statistic).stream()
                    .filter(material -> categorizeMaterial(material, blockType) == category)
                    .toList();
        }

        @Override
        protected void build() {
            int start = page * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, filteredMaterials.size());
            boolean blockType = statistic.getType() == Statistic.Type.BLOCK;

            for (int slot = 0; slot < PAGE_SIZE && (start + slot) < end; slot++) {
                Material material = filteredMaterials.get(start + slot);
                setButton(slot, createMaterialItem(material, blockType), click -> {
                    StatContext context = new StatContext(statistic, material, null);
                    new LookupActionMenu(viewer, context, () -> new MaterialListMenu(viewer, statistic, category, backAction, page).open()).open();
                });
            }

            int maxPage = Math.max(0, (filteredMaterials.size() - 1) / PAGE_SIZE);
            setButton(45, createNavItem(Material.ARROW, "Назад", "Предыдущая страница"), click -> {
                if (page > 0) {
                    new MaterialListMenu(viewer, statistic, category, backAction, page - 1).open();
                }
            });
            setButton(49, createNavItem(category.icon, category.title, "Страница " + (page + 1) + " из " + (maxPage + 1)), null);
            setButton(52, createNavItem(Material.BARRIER, "К выбору категории", "Вернуться к категориям"), click -> backAction.run());
            setButton(53, createNavItem(Material.ARROW, "Вперёд", "Следующая страница"), click -> {
                if (page < maxPage) {
                    new MaterialListMenu(viewer, statistic, category, backAction, page + 1).open();
                }
            });
        }
    }

    private final class PlayerListMenu extends Menu {
        private final StatContext statContext;
        private final Runnable backAction;
        private final int page;
        private final List<String> playerNames;

        private PlayerListMenu(@NotNull Player viewer, @NotNull StatContext statContext, @NotNull Runnable backAction, int page) {
            super(viewer, 54, "Выбор игрока");
            this.statContext = statContext;
            this.backAction = backAction;
            this.page = page;
            this.playerNames = getPlayerNames();
        }

        @Override
        protected void build() {
            int start = page * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, playerNames.size());
            for (int slot = 0; slot < PAGE_SIZE && (start + slot) < end; slot++) {
                String playerName = playerNames.get(start + slot);
                setButton(slot, createPlayerHead(playerName), click -> startLookup(viewer, statContext, LookupAction.PLAYER_STAT, playerName));
            }

            int maxPage = Math.max(0, (playerNames.size() - 1) / PAGE_SIZE);
            setButton(45, createNavItem(Material.ARROW, "Назад", "Предыдущая страница"), click -> {
                if (page > 0) {
                    new PlayerListMenu(viewer, statContext, backAction, page - 1).open();
                }
            });
            setButton(49, createNavItem(Material.PLAYER_HEAD, "Игроки", "Страница " + (page + 1) + " из " + (maxPage + 1)), null);
            setButton(52, createNavItem(Material.BARRIER, "К действиям", "Вернуться к выбору действия"), click -> backAction.run());
            setButton(53, createNavItem(Material.ARROW, "Вперёд", "Следующая страница"), click -> {
                if (page < maxPage) {
                    new PlayerListMenu(viewer, statContext, backAction, page + 1).open();
                }
            });
        }
    }
}
