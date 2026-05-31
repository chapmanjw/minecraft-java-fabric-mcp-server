package com.chapmanjw.minecraft.fabric.mcp.adapter.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import com.chapmanjw.minecraft.fabric.mcp.adapter.AdapterException;
import com.chapmanjw.minecraft.fabric.mcp.adapter.MinecraftAdapter;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.CompostableInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.FlammableBlockInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.FluidStackInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.ItemStackInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.LootDropInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.RecipeInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3d;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3i;

/**
 * Loot/recipe/tag/resource registries, Fabric content registries, resource
 * conditions, fluid storage, and player screen handlers.
 */
final class RegistryOps {

    private final AdapterContext ctx;

    RegistryOps(AdapterContext ctx) {
        this.ctx = ctx;
    }

    // =====================================================================
    // Loot / Recipe / Tag / Resource — v0.2.0 targets
    // =====================================================================

    List<String> lootTableList() {
        var lootKey =
                net.minecraft.world.level.storage.loot.LootDataType.TABLE.registryKey();
        var registry =
                ctx.requireServer().reloadableRegistries().lookup().lookup(lootKey).orElse(null);
        if (registry == null) {
            return List.of();
        }
        return registry.listElements().map(e -> e.key().identifier().toString()).sorted().toList();
    }

    Optional<String> lootTableGetDefinition(String id) {
        Identifier rl = AdapterContext.parseIdentifier(id);
        MinecraftServer s = ctx.requireServer();
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, rl);
        var lookup = s.reloadableRegistries().lookup();
        try {
            LootTable table = s.reloadableRegistries().getLootTable(key);
            if (table == LootTable.EMPTY) {
                return Optional.empty();
            }
            var result = LootTable.DIRECT_CODEC.encodeStart(
                    com.mojang.serialization.JsonOps.INSTANCE, table);
            return result.result().map(Object::toString);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    LootDropInfo lootTableGenerate(String id, Vec3d position, UUID killer, UUID lootingEntity) {
        MinecraftServer s = ctx.requireServer();
        Identifier rl = AdapterContext.parseIdentifier(id);
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, rl);
        LootTable table;
        try {
            table = s.reloadableRegistries().getLootTable(key);
        } catch (Exception e) {
            throw new AdapterException("Loot table not found: " + id);
        }
        if (table == LootTable.EMPTY) {
            return new LootDropInfo(List.of());
        }
        ServerLevel level = s.overworld();
        if (level == null) {
            throw new AdapterException("No overworld loaded; cannot evaluate loot table");
        }
        LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, new Vec3(position.x(), position.y(), position.z()));
        if (killer != null) {
            Entity ke = ctx.findEntityAcrossLevels(killer);
            if (ke != null) {
                builder.withOptionalParameter(LootContextParams.ATTACKING_ENTITY, ke);
                builder.withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, ke);
                if (ke instanceof net.minecraft.world.entity.player.Player p) {
                    builder.withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, p);
                }
            }
        }
        if (lootingEntity != null) {
            Entity le = ctx.findEntityAcrossLevels(lootingEntity);
            if (le != null) {
                builder.withOptionalParameter(LootContextParams.THIS_ENTITY, le);
            }
        }
        LootParams params = builder.create(table.getParamSet());
        List<net.minecraft.world.item.ItemStack> drops = table.getRandomItems(params);
        List<ItemStackInfo> out = new ArrayList<>();
        for (var stack : drops) {
            out.add(ctx.toItemStackInfo(stack));
        }
        return new LootDropInfo(out);
    }

    List<RecipeInfo> recipeList(String type) {
        RecipeManager mgr = ctx.requireServer().getRecipeManager();
        List<RecipeInfo> out = new ArrayList<>();
        Identifier typeFilter = (type == null || type.isBlank()) ? null : AdapterContext.parseIdentifier(type);
        var typeRegistry = ctx.requireServer().registryAccess().lookupOrThrow(Registries.RECIPE_TYPE);
        for (RecipeHolder<?> holder : mgr.getRecipes()) {
            RecipeInfo info = toRecipeInfo(holder, typeRegistry);
            if (typeFilter != null && !typeFilter.toString().equals(info.type())) {
                continue;
            }
            out.add(info);
        }
        out.sort((a, b) -> a.id().compareTo(b.id()));
        return out;
    }

    Optional<RecipeInfo> recipeGetDefinition(String id) {
        Identifier rl = AdapterContext.parseIdentifier(id);
        ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, rl);
        Optional<RecipeHolder<?>> holder = ctx.requireServer().getRecipeManager().byKey(key);
        if (holder.isEmpty()) {
            return Optional.empty();
        }
        var typeRegistry = ctx.requireServer().registryAccess().lookupOrThrow(Registries.RECIPE_TYPE);
        return Optional.of(toRecipeInfo(holder.get(), typeRegistry));
    }

    List<RecipeInfo> recipeFindByResult(String itemId) {
        Identifier match = AdapterContext.parseIdentifier(itemId);
        String matchStr = match.toString();
        List<RecipeInfo> out = new ArrayList<>();
        var typeRegistry = ctx.requireServer().registryAccess().lookupOrThrow(Registries.RECIPE_TYPE);
        for (RecipeHolder<?> holder : ctx.requireServer().getRecipeManager().getRecipes()) {
            RecipeInfo info = toRecipeInfo(holder, typeRegistry);
            if (matchStr.equals(info.result())) {
                out.add(info);
            }
        }
        return out;
    }

    List<RecipeInfo> recipeFindByIngredient(String itemId) {
        Identifier match = AdapterContext.parseIdentifier(itemId);
        String matchStr = match.toString();
        List<RecipeInfo> out = new ArrayList<>();
        var typeRegistry = ctx.requireServer().registryAccess().lookupOrThrow(Registries.RECIPE_TYPE);
        for (RecipeHolder<?> holder : ctx.requireServer().getRecipeManager().getRecipes()) {
            RecipeInfo info = toRecipeInfo(holder, typeRegistry);
            if (info.ingredients().contains(matchStr)) {
                out.add(info);
            }
        }
        return out;
    }

    /**
     * Maps a {@link RecipeHolder} to a generic {@link RecipeInfo}. Ingredient list is
     * derived from {@link Recipe#placementInfo()}; result is derived from the first
     * available {@link RecipeDisplay} (falls back to an empty string when no display
     * is published — e.g. for special/dynamic recipes).
     */
    private RecipeInfo toRecipeInfo(
            RecipeHolder<?> holder,
            net.minecraft.core.Registry<RecipeType<?>> typeRegistry) {
        Recipe<?> recipe = holder.value();
        Identifier id = holder.id().identifier();
        Identifier typeId = typeRegistry.getKey(recipe.getType());
        List<String> ingredients = new ArrayList<>();
        try {
            for (Ingredient ing : recipe.placementInfo().ingredients()) {
                String repr = ing.items()
                        .findFirst()
                        .flatMap(Holder::unwrapKey)
                        .map(rk -> rk.identifier().toString())
                        .orElse("minecraft:air");
                ingredients.add(repr);
            }
        } catch (Throwable t) {
            // placementInfo can throw for special recipes; leave ingredients empty.
        }
        String resultId = "";
        int resultCount = 0;
        try {
            List<RecipeDisplay> displays = recipe.display();
            if (!displays.isEmpty()) {
                ContextMap emptyContext = new ContextMap.Builder().create(new ContextKeySet.Builder().build());
                var stack = displays.get(0).result().resolveForFirstStack(emptyContext);
                if (stack != null && !stack.isEmpty()) {
                    Identifier itemId = ctx.requireServer()
                            .registryAccess()
                            .lookupOrThrow(Registries.ITEM)
                            .getKey(stack.getItem());
                    resultId = itemId == null ? "" : itemId.toString();
                    resultCount = stack.getCount();
                }
            }
        } catch (Throwable t) {
            // Best-effort; some recipes don't surface a single concrete result.
        }
        return new RecipeInfo(
                id.toString(),
                typeId == null ? "" : typeId.toString(),
                recipe.group(),
                ingredients,
                resultId,
                resultCount);
    }

    List<String> tagListInRegistry(String registry) {
        Identifier regId = AdapterContext.parseIdentifier(registry);
        ResourceKey<net.minecraft.core.Registry<Object>> regKey =
                ResourceKey.createRegistryKey(regId);
        var maybeRegistry = ctx.requireServer().registryAccess().lookup(regKey);
        if (maybeRegistry.isEmpty()) {
            return List.of();
        }
        return maybeRegistry.get().getTags()
                .map(named -> named.key().location().toString())
                .sorted()
                .toList();
    }

    List<String> tagGetMembers(String registry, String tag) {
        Identifier regId = AdapterContext.parseIdentifier(registry);
        Identifier tagId = AdapterContext.parseIdentifier(tag);
        ResourceKey<net.minecraft.core.Registry<Object>> regKey =
                ResourceKey.createRegistryKey(regId);
        var maybeRegistry = ctx.requireServer().registryAccess().lookup(regKey);
        if (maybeRegistry.isEmpty()) {
            return List.of();
        }
        var reg = maybeRegistry.get();
        var tagKey = net.minecraft.tags.TagKey.create(regKey, tagId);
        var holderSet = reg.get(tagKey);
        if (holderSet.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        holderSet.get().forEach(holder ->
                holder.unwrapKey()
                        .ifPresent(k -> out.add(k.identifier().toString())));
        out.sort(String::compareTo);
        return out;
    }

    boolean tagCheckMembership(String registry, String tag, String member) {
        Identifier regId = AdapterContext.parseIdentifier(registry);
        Identifier tagId = AdapterContext.parseIdentifier(tag);
        Identifier memberId = AdapterContext.parseIdentifier(member);
        ResourceKey<net.minecraft.core.Registry<Object>> regKey =
                ResourceKey.createRegistryKey(regId);
        var maybeRegistry = ctx.requireServer().registryAccess().lookup(regKey);
        if (maybeRegistry.isEmpty()) {
            return false;
        }
        var reg = maybeRegistry.get();
        var memberKey = ResourceKey.create(regKey, memberId);
        var holder = reg.get(memberKey);
        if (holder.isEmpty()) {
            return false;
        }
        var tagKey = net.minecraft.tags.TagKey.create(regKey, tagId);
        return holder.get().is(tagKey);
    }

    List<String> resourceLoaderListNamespaces() {
        var manager = ctx.requireServer().getResourceManager();
        return new ArrayList<>(manager.getNamespaces());
    }

    Optional<byte[]> resourceLoaderGetResource(String namespace, String path) {
        try {
            var manager = ctx.requireServer().getResourceManager();
            var location = Identifier.fromNamespaceAndPath(namespace, path);
            var resource = manager.getResource(location);
            if (resource.isEmpty()) {
                return Optional.empty();
            }
            try (var stream = resource.get().open()) {
                return Optional.of(stream.readAllBytes());
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // =====================================================================
    // Content Registry (Fabric) — fuel / flammable / compostable
    // =====================================================================

    int contentRegistryGetFuel(String itemId) {
        Identifier rl = AdapterContext.parseIdentifier(itemId);
        var itemRegistry = ctx.requireServer().registryAccess().lookupOrThrow(Registries.ITEM);
        var item = itemRegistry.getValue(rl);
        if (item == null) {
            return 0;
        }
        // FuelValues is part of vanilla; the Fabric module's BuildCallback merely
        // injects entries during build, so reading goes through the vanilla map.
        return ctx.requireServer().fuelValues().burnDuration(new net.minecraft.world.item.ItemStack(item));
    }

    boolean contentRegistrySetFuel(String itemId, int burnTimeTicks) {
        // The Fabric FuelValueEvents.BUILD / FuelRegistryEvents.BUILD callbacks only
        // fire during resource reload — vanilla has no runtime mutator for fuel burn
        // times. Returning false matches the tool description's "no-op" contract and
        // lets clients distinguish "not supported in this version" from a genuine
        // server error.
        return false;
    }

    FlammableBlockInfo contentRegistryGetFlammableBlock(String blockId) {
        Identifier rl = AdapterContext.parseIdentifier(blockId);
        var blockRegistry = ctx.requireServer().registryAccess().lookupOrThrow(Registries.BLOCK);
        var block = blockRegistry.getValue(rl);
        if (block == null) {
            return FlammableBlockInfo.notFlammable();
        }
        var entry =
                net.fabricmc.fabric.api.registry.FlammableBlockRegistry.getDefaultInstance().get(block);
        if (entry == null) {
            return FlammableBlockInfo.notFlammable();
        }
        //? if mc_gte_26 {
        return new FlammableBlockInfo(true, entry.getIgniteOdds(), entry.getBurnOdds());
        //?} else {
        /*return new FlammableBlockInfo(true, entry.getSpreadChance(), entry.getBurnChance());
        *///?}
    }

    boolean contentRegistrySetFlammableBlock(String blockId, int burnChance, int spreadChance) {
        Identifier rl = AdapterContext.parseIdentifier(blockId);
        var blockRegistry = ctx.requireServer().registryAccess().lookupOrThrow(Registries.BLOCK);
        var block = blockRegistry.getValue(rl);
        if (block == null) {
            return false;
        }
        // Fabric's add(Block, igniteOdds, burnOdds) takes ignite/spread odds first,
        // burn odds second. Map spread_chance -> igniteOdds, burn_chance -> burnOdds
        // so a value written here reads back unchanged via getIgniteOdds()/getBurnOdds().
        net.fabricmc.fabric.api.registry.FlammableBlockRegistry.getDefaultInstance()
                .add(block, spreadChance, burnChance);
        return true;
    }

    CompostableInfo contentRegistryGetCompostable(String itemId) {
        Identifier rl = AdapterContext.parseIdentifier(itemId);
        var itemRegistry = ctx.requireServer().registryAccess().lookupOrThrow(Registries.ITEM);
        var item = itemRegistry.getValue(rl);
        if (item == null) {
            return CompostableInfo.notCompostable();
        }
        //? if mc_gte_26 {
        Float chance =
                net.fabricmc.fabric.api.registry.CompostableRegistry.INSTANCE.get(item);
        //?} else {
        /*Float chance =
                net.fabricmc.fabric.api.registry.CompostingChanceRegistry.INSTANCE.get(item);
        *///?}
        if (chance == null) {
            return CompostableInfo.notCompostable();
        }
        return new CompostableInfo(true, chance);
    }

    boolean contentRegistrySetCompostable(String itemId, float chance) {
        Identifier rl = AdapterContext.parseIdentifier(itemId);
        var itemRegistry = ctx.requireServer().registryAccess().lookupOrThrow(Registries.ITEM);
        var item = itemRegistry.getValue(rl);
        if (item == null) {
            return false;
        }
        //? if mc_gte_26 {
        net.fabricmc.fabric.api.registry.CompostableRegistry.INSTANCE.add(item, chance);
        //?} else {
        /*net.fabricmc.fabric.api.registry.CompostingChanceRegistry.INSTANCE.add(item, chance);
        *///?}
        return true;
    }

    // =====================================================================
    // Resource Conditions (Fabric)
    // =====================================================================

    MinecraftAdapter.ResourceConditionResult resourceConditionEvaluate(String conditionJson) {
        try {
            com.fasterxml.jackson.databind.JsonNode jacksonNode =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(conditionJson);
            // Convert the parsed Jackson node back to a Gson JsonElement — Mojang's
            // serialization stack speaks JsonOps over Gson trees.
            com.google.gson.JsonElement gson = com.google.gson.JsonParser.parseString(jacksonNode.toString());
            var decoded =
                    net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition.CODEC
                            .parse(com.mojang.serialization.JsonOps.INSTANCE, gson)
                            .resultOrPartial(err -> { });
            if (decoded.isEmpty()) {
                throw new AdapterException("Failed to decode ResourceCondition JSON");
            }
            var cond = decoded.get();
            String typeId = cond.getType().id().toString();
            net.minecraft.resources.RegistryOps.RegistryInfoLookup lookup =
                    buildRegistryInfoLookup();
            boolean matches = cond.test(lookup);
            return new MinecraftAdapter.ResourceConditionResult(matches, typeId);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new AdapterException("Failed to evaluate ResourceCondition: " + e.getMessage(), e);
        }
    }

    /**
     * Build a {@link net.minecraft.resources.RegistryOps.RegistryInfoLookup} backed by
     * the server's full registry access. ResourceCondition implementations consult this
     * for {@code tags_populated} / {@code registry_contains} style checks.
     */
    private net.minecraft.resources.RegistryOps.RegistryInfoLookup buildRegistryInfoLookup() {
        var access = ctx.requireServer().registryAccess();
        return new net.minecraft.resources.RegistryOps.RegistryInfoLookup() {
            @Override
            public <T> java.util.Optional<net.minecraft.resources.RegistryOps.RegistryInfo<T>> lookup(
                    ResourceKey<? extends net.minecraft.core.Registry<? extends T>> registryKey) {
                return access.lookup(registryKey)
                        .map(net.minecraft.resources.RegistryOps.RegistryInfo::fromRegistryLookup);
            }
        };
    }

    // =====================================================================
    // Fluid Storage (Fabric Transfer API)
    // =====================================================================

    Optional<FluidStackInfo> fluidStorageGet(
            String dimensionId, Vec3i position, String direction) {
        ServerLevel level = ctx.requireLevel(dimensionId);
        BlockPos pos = new BlockPos(position.x(), position.y(), position.z());
        net.minecraft.core.Direction dir = parseDirection(direction);
        var storage =
                net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.find(level, pos, dir);
        if (storage == null) {
            return Optional.empty();
        }
        try (var tx =
                net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
            var iterator = storage.iterator();
            if (!iterator.hasNext()) {
                return Optional.of(FluidStackInfo.emptyTank());
            }
            var view = iterator.next();
            FluidStackInfo info = toFluidStackInfo(view);
            tx.abort();
            return Optional.of(info);
        }
    }

    List<FluidStackInfo> fluidStorageListAt(String dimensionId, Vec3i position) {
        ServerLevel level = ctx.requireLevel(dimensionId);
        BlockPos pos = new BlockPos(position.x(), position.y(), position.z());
        // Try every face plus the side-agnostic null direction; keep the first
        // matching storage (most blocks publish the same instance on every side).
        net.fabricmc.fabric.api.transfer.v1.storage.Storage<
                        net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant>
                storage =
                        net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.find(
                                level, pos, null);
        if (storage == null) {
            for (var d : net.minecraft.core.Direction.values()) {
                storage =
                        net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.find(
                                level, pos, d);
                if (storage != null) {
                    break;
                }
            }
        }
        List<FluidStackInfo> out = new ArrayList<>();
        if (storage == null) {
            return out;
        }
        try (var tx = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
            for (var view : storage) {
                out.add(toFluidStackInfo(view));
            }
            tx.abort();
        }
        return out;
    }

    private FluidStackInfo toFluidStackInfo(
            net.fabricmc.fabric.api.transfer.v1.storage.StorageView<
                            net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant>
                    view) {
        var variant = view.getResource();
        if (view.isResourceBlank()) {
            return new FluidStackInfo(true, "minecraft:empty", 0L, view.getCapacity());
        }
        Identifier id =
                ctx.requireServer()
                        .registryAccess()
                        .lookupOrThrow(Registries.FLUID)
                        .getKey(variant.getFluid());
        String idStr = id == null ? "minecraft:empty" : id.toString();
        return new FluidStackInfo(false, idStr, view.getAmount(), view.getCapacity());
    }

    private static net.minecraft.core.Direction parseDirection(String direction) {
        if (direction == null || direction.isBlank() || "none".equalsIgnoreCase(direction)) {
            return null;
        }
        return switch (direction.toLowerCase(Locale.ROOT)) {
            case "up" -> net.minecraft.core.Direction.UP;
            case "down" -> net.minecraft.core.Direction.DOWN;
            case "north" -> net.minecraft.core.Direction.NORTH;
            case "south" -> net.minecraft.core.Direction.SOUTH;
            case "east" -> net.minecraft.core.Direction.EAST;
            case "west" -> net.minecraft.core.Direction.WEST;
            default -> throw new AdapterException("Unknown direction: " + direction);
        };
    }

    // =====================================================================
    // Player Screen Handlers (Fabric — uses vanilla menu providers underneath)
    // =====================================================================

    boolean playerScreenOpenMenu(UUID uuid, String menuType, String title) {
        ServerPlayer player = ctx.requireServer().getPlayerList().getPlayer(uuid);
        if (player == null) {
            return false;
        }
        Component name =
                (title == null || title.isBlank())
                        ? Component.literal(menuType)
                        : Component.literal(title);
        net.minecraft.world.SimpleMenuProvider provider =
                switch (menuType.toLowerCase(Locale.ROOT)) {
                    case "anvil" ->
                            new net.minecraft.world.SimpleMenuProvider(
                                    (id, inv, p) ->
                                            new net.minecraft.world.inventory.AnvilMenu(
                                                    id,
                                                    inv,
                                                    net.minecraft.world.inventory.ContainerLevelAccess
                                                            .create(player.level(), player.blockPosition())),
                                    name);
                    case "crafting_table" ->
                            new net.minecraft.world.SimpleMenuProvider(
                                    (id, inv, p) ->
                                            new net.minecraft.world.inventory.CraftingMenu(
                                                    id,
                                                    inv,
                                                    net.minecraft.world.inventory.ContainerLevelAccess
                                                            .create(player.level(), player.blockPosition())),
                                    name);
                    case "enchanting_table" ->
                            new net.minecraft.world.SimpleMenuProvider(
                                    (id, inv, p) ->
                                            new net.minecraft.world.inventory.EnchantmentMenu(
                                                    id,
                                                    inv,
                                                    net.minecraft.world.inventory.ContainerLevelAccess
                                                            .create(player.level(), player.blockPosition())),
                                    name);
                    case "loom" ->
                            new net.minecraft.world.SimpleMenuProvider(
                                    (id, inv, p) ->
                                            new net.minecraft.world.inventory.LoomMenu(
                                                    id,
                                                    inv,
                                                    net.minecraft.world.inventory.ContainerLevelAccess
                                                            .create(player.level(), player.blockPosition())),
                                    name);
                    case "stonecutter" ->
                            new net.minecraft.world.SimpleMenuProvider(
                                    (id, inv, p) ->
                                            new net.minecraft.world.inventory.StonecutterMenu(
                                                    id,
                                                    inv,
                                                    net.minecraft.world.inventory.ContainerLevelAccess
                                                            .create(player.level(), player.blockPosition())),
                                    name);
                    case "grindstone" ->
                            new net.minecraft.world.SimpleMenuProvider(
                                    (id, inv, p) ->
                                            new net.minecraft.world.inventory.GrindstoneMenu(
                                                    id,
                                                    inv,
                                                    net.minecraft.world.inventory.ContainerLevelAccess
                                                            .create(player.level(), player.blockPosition())),
                                    name);
                    case "smithing_table" ->
                            new net.minecraft.world.SimpleMenuProvider(
                                    (id, inv, p) ->
                                            new net.minecraft.world.inventory.SmithingMenu(
                                                    id,
                                                    inv,
                                                    net.minecraft.world.inventory.ContainerLevelAccess
                                                            .create(player.level(), player.blockPosition())),
                                    name);
                    case "cartography_table" ->
                            new net.minecraft.world.SimpleMenuProvider(
                                    (id, inv, p) ->
                                            new net.minecraft.world.inventory.CartographyTableMenu(
                                                    id,
                                                    inv,
                                                    net.minecraft.world.inventory.ContainerLevelAccess
                                                            .create(player.level(), player.blockPosition())),
                                    name);
                    default ->
                            throw new AdapterException("Unknown menu type: " + menuType);
                };
        return player.openMenu(provider).isPresent();
    }

    boolean playerScreenOpenContainer(UUID uuid, String dimensionId, Vec3i position) {
        ServerPlayer player = ctx.requireServer().getPlayerList().getPlayer(uuid);
        if (player == null) {
            return false;
        }
        ServerLevel level = ctx.requireLevel(dimensionId);
        BlockPos pos = new BlockPos(position.x(), position.y(), position.z());
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof net.minecraft.world.MenuProvider mp)) {
            return false;
        }
        return player.openMenu(mp).isPresent();
    }

    boolean playerScreenClose(UUID uuid) {
        ServerPlayer player = ctx.requireServer().getPlayerList().getPlayer(uuid);
        if (player == null) {
            return false;
        }
        player.closeContainer();
        return true;
    }
}
