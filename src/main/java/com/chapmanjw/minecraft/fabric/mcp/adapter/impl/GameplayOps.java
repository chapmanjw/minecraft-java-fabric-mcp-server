package com.chapmanjw.minecraft.fabric.mcp.adapter.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import com.chapmanjw.minecraft.fabric.mcp.adapter.AdapterException;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.AdvancementProgressInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.BossbarInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.CommandResult;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.ScheduledFunctionInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.ScoreboardObjectiveInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.TeamInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3i;

/**
 * Command dispatch, scoreboard/team, bossbar, advancement, function, schedule,
 * and item-modify operations.
 */
final class GameplayOps {

    private final AdapterContext ctx;

    GameplayOps(AdapterContext ctx) {
        this.ctx = ctx;
    }

    // =====================================================================
    // Command — direct via Brigadier
    // =====================================================================

    CommandResult commandExecute(String command) {
        return ctx.commandExecute(command);
    }

    CommandResult commandExecuteAs(String command, UUID actor) {
        return ctx.commandExecute(
                "execute as " + AdapterContext.entitySelector(actor) + " run " + command);
    }

    // =====================================================================
    // Scoreboard — delegate to commands
    // =====================================================================

    List<ScoreboardObjectiveInfo> scoreboardListObjectives() {
        MinecraftServer s = ctx.requireServer();
        List<ScoreboardObjectiveInfo> out = new ArrayList<>();
        s.getScoreboard()
                .getObjectives()
                .forEach(
                        obj ->
                                out.add(
                                        new ScoreboardObjectiveInfo(
                                                obj.getName(),
                                                obj.getDisplayName().getString(),
                                                obj.getCriteria().getName(),
                                                "")));
        return out;
    }

    Optional<ScoreboardObjectiveInfo> scoreboardGetObjective(String name) {
        return scoreboardListObjectives().stream().filter(o -> o.name().equals(name)).findFirst();
    }

    boolean scoreboardAddObjective(String name, String criterion, String displayName) {
        StringBuilder sb = new StringBuilder("scoreboard objectives add ");
        sb.append(name).append(' ').append(criterion);
        if (displayName != null && !displayName.isBlank()) {
            sb.append(' ').append(AdapterContext.asJsonText(displayName));
        }
        // /scoreboard objectives add is a void setter.
        return AdapterContext.commandOk(ctx.commandExecute(sb.toString()));
    }

    boolean scoreboardRemoveObjective(String name) {
        // /scoreboard objectives remove is a void setter; it returns successCount=0 even
        // on the happy path, so the previous > 0 check always reported "failed".
        return AdapterContext.commandOk(
                ctx.commandExecute("scoreboard objectives remove " + name));
    }

    boolean scoreboardSetDisplaySlot(String slot, String objectiveName) {
        // /scoreboard objectives setdisplay is a void setter.
        return AdapterContext.commandOk(
                ctx.commandExecute(
                        "scoreboard objectives setdisplay " + slot + " " + objectiveName));
    }

    int scoreboardGetScore(String participant, String objectiveName) {
        MinecraftServer s = ctx.requireServer();
        var obj = s.getScoreboard().getObjective(objectiveName);
        if (obj == null) {
            return 0;
        }
        try {
            var scoreHolder =
                    net.minecraft.world.scores.ScoreHolder.forNameOnly(participant);
            var holder = s.getScoreboard().getOrCreatePlayerScore(scoreHolder, obj);
            return holder.get();
        } catch (Throwable t) {
            return 0;
        }
    }

    boolean scoreboardSetScore(String participant, String objectiveName, int score) {
        // /scoreboard players set is a void setter.
        return AdapterContext.commandOk(
                ctx.commandExecute(
                        "scoreboard players set "
                                + participant
                                + " "
                                + objectiveName
                                + " "
                                + score));
    }

    boolean scoreboardAddScore(String participant, String objectiveName, int delta) {
        // /scoreboard players add is a void setter.
        return AdapterContext.commandOk(
                ctx.commandExecute(
                        "scoreboard players add "
                                + participant
                                + " "
                                + objectiveName
                                + " "
                                + delta));
    }

    boolean scoreboardResetParticipant(String participant, String objectiveName) {
        // /scoreboard players reset is a void setter.
        return AdapterContext.commandOk(
                ctx.commandExecute(
                        "scoreboard players reset " + participant + " " + objectiveName));
    }

    List<TeamInfo> scoreboardListTeams() {
        MinecraftServer s = ctx.requireServer();
        List<TeamInfo> out = new ArrayList<>();
        s.getScoreboard()
                .getPlayerTeams()
                .forEach(
                        team ->
                                out.add(
                                        new TeamInfo(
                                                team.getName(),
                                                team.getDisplayName().getString(),
                                                team.getColor() == null ? "" : team.getColor().getName(),
                                                team.isAllowFriendlyFire(),
                                                team.canSeeFriendlyInvisibles(),
                                                new ArrayList<>(team.getPlayers()))));
        return out;
    }

    boolean scoreboardAddTeam(String name, String displayName) {
        StringBuilder sb = new StringBuilder("team add ").append(name);
        if (displayName != null && !displayName.isBlank()) {
            sb.append(' ').append(AdapterContext.asJsonText(displayName));
        }
        // /team add is a void setter.
        return AdapterContext.commandOk(ctx.commandExecute(sb.toString()));
    }

    boolean scoreboardRemoveTeam(String name) {
        // /team remove is a void setter (successCount=0 even on success).
        return AdapterContext.commandOk(ctx.commandExecute("team remove " + name));
    }

    boolean scoreboardTeamAddMember(String teamName, String participant) {
        // /team join is a void setter; returning successCount > 0 reported "failed" on
        // an already-joined member.
        return AdapterContext.commandOk(
                ctx.commandExecute("team join " + teamName + " " + participant));
    }

    boolean scoreboardTeamRemoveMember(String teamName, String participant) {
        // Vanilla `/team leave <targets>` removes the participant from whichever team
        // they're currently on — there is no `/team leave <teamName> <targets>` form.
        // We can't safely scope the removal to a specific team without knowing whether
        // `participant` is a player name or a UUID (predicates like `[team=X]` only
        // attach to @-prefixed selectors). If the caller needs scoped semantics, they
        // should query the current team first via `scoreboard_get_team_members` and
        // only call this if the participant is actually on the named team.
        return AdapterContext.commandOk(ctx.commandExecute("team leave " + participant));
    }

    // =====================================================================
    // Bossbar (vanilla)
    // =====================================================================

    List<BossbarInfo> bossbarList() {
        var bars = ctx.requireServer().getCustomBossEvents();
        List<BossbarInfo> out = new ArrayList<>();
        for (var ev : bars.getEvents()) {
            out.add(toBossbarInfo(ev));
        }
        return out;
    }

    Optional<BossbarInfo> bossbarGet(String id) {
        Identifier rl = AdapterContext.parseIdentifier(id);
        var ev = ctx.requireServer().getCustomBossEvents().get(rl);
        if (ev == null) {
            return Optional.empty();
        }
        return Optional.of(toBossbarInfo(ev));
    }

    private static BossbarInfo toBossbarInfo(net.minecraft.server.bossevents.CustomBossEvent ev) {
        List<UUID> uuids = new ArrayList<>();
        for (ServerPlayer p : ev.getPlayers()) {
            uuids.add(p.getUUID());
        }
        //? if mc_gte_26 {
        String id = ev.customId().toString();
        int value = ev.value();
        int max = ev.max();
        //?} else {
        /*String id = ev.getTextId().toString();
        int value = ev.getValue();
        int max = ev.getMax();
        *///?}
        return new BossbarInfo(
                id,
                ev.getDisplayName().getString(),
                value,
                max,
                ev.getColor().getName(),
                ev.getOverlay().getName(),
                ev.isVisible(),
                uuids);
    }

    boolean bossbarAdd(String id, String name) {
        // /bossbar add is a void setter.
        return AdapterContext.commandOk(
                ctx.commandExecute(
                        "bossbar add " + id + " " + AdapterContext.asJsonText(name)));
    }

    boolean bossbarRemove(String id) {
        // /bossbar remove is a void setter (successCount=0 on success).
        return AdapterContext.commandOk(ctx.commandExecute("bossbar remove " + id));
    }

    boolean bossbarSetValue(String id, int value) {
        // /bossbar set value is a void setter.
        return AdapterContext.commandOk(
                ctx.commandExecute("bossbar set " + id + " value " + value));
    }

    boolean bossbarSetMax(String id, int max) {
        // /bossbar set max is a void setter.
        return AdapterContext.commandOk(
                ctx.commandExecute("bossbar set " + id + " max " + max));
    }

    boolean bossbarSetName(String id, String name) {
        // /bossbar set name is a void setter.
        return AdapterContext.commandOk(
                ctx.commandExecute(
                        "bossbar set " + id + " name " + AdapterContext.asJsonText(name)));
    }

    boolean bossbarSetColor(String id, String color) {
        // /bossbar set color is a void setter.
        return AdapterContext.commandOk(
                ctx.commandExecute("bossbar set " + id + " color " + color));
    }

    boolean bossbarSetStyle(String id, String style) {
        // /bossbar set style is a void setter.
        return AdapterContext.commandOk(
                ctx.commandExecute("bossbar set " + id + " style " + style));
    }

    boolean bossbarSetVisible(String id, boolean visible) {
        // /bossbar set visible is a void setter.
        return AdapterContext.commandOk(
                ctx.commandExecute("bossbar set " + id + " visible " + visible));
    }

    boolean bossbarSetPlayers(String id, List<UUID> playerUuids) {
        // /bossbar set players is a void setter. Each UUID must be wrapped in an
        // @a[uuid=...] selector -- vanilla rejects bare UUIDs for player-target args.
        if (playerUuids == null || playerUuids.isEmpty()) {
            return AdapterContext.commandOk(
                    ctx.commandExecute("bossbar set " + id + " players"));
        }
        StringBuilder sb = new StringBuilder("bossbar set ").append(id).append(" players");
        for (UUID u : playerUuids) {
            sb.append(' ').append(AdapterContext.playerSelector(u));
        }
        return AdapterContext.commandOk(ctx.commandExecute(sb.toString()));
    }

    // =====================================================================
    // Advancement (vanilla)
    // =====================================================================

    boolean advancementGrant(
            UUID playerUuid, String advancementId, String mode, String criterion) {
        return advancementApply("grant", playerUuid, advancementId, mode, criterion);
    }

    boolean advancementRevoke(
            UUID playerUuid, String advancementId, String mode, String criterion) {
        return advancementApply("revoke", playerUuid, advancementId, mode, criterion);
    }

    private boolean advancementApply(
            String verb, UUID playerUuid, String advancementId, String mode, String criterion) {
        String m = mode == null || mode.isBlank() ? "only" : mode.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder("advancement ");
        // Vanilla rejects a bare UUID; wrap as @a[uuid=...].
        sb.append(verb)
                .append(' ')
                .append(AdapterContext.playerSelector(playerUuid))
                .append(' ')
                .append(m)
                .append(' ')
                .append(advancementId);
        if ("only".equals(m) && criterion != null && !criterion.isBlank()) {
            sb.append(' ').append(criterion);
        }
        // /advancement grant/revoke returns successCount = criteria changed; when the
        // player already has (or doesn't have) the criterion it returns 0 with no
        // error. Use commandOk so the no-op case isn't reported as failure.
        return AdapterContext.commandOk(ctx.commandExecute(sb.toString()));
    }

    AdvancementProgressInfo advancementListPlayer(UUID playerUuid) {
        ServerPlayer player = ctx.requireServer().getPlayerList().getPlayer(playerUuid);
        if (player == null) {
            throw new AdapterException("Player not online: " + playerUuid);
        }
        var pa = player.getAdvancements();
        var mgr = ctx.requireServer().getAdvancements();
        List<String> granted = new ArrayList<>();
        List<AdvancementProgressInfo.InProgress> inProgress = new ArrayList<>();
        for (var holder : mgr.getAllAdvancements()) {
            var progress = pa.getOrStartProgress(holder);
            if (progress.isDone()) {
                granted.add(holder.id().toString());
            } else if (progress.hasProgress()) {
                List<String> done = new ArrayList<>();
                progress.getCompletedCriteria().forEach(done::add);
                List<String> remaining = new ArrayList<>();
                progress.getRemainingCriteria().forEach(remaining::add);
                inProgress.add(
                        new AdvancementProgressInfo.InProgress(
                                holder.id().toString(), done, remaining));
            }
        }
        granted.sort(String::compareTo);
        inProgress.sort((a, b) -> a.id().compareTo(b.id()));
        return new AdvancementProgressInfo(granted, inProgress);
    }

    List<String> advancementListAll() {
        List<String> out = new ArrayList<>();
        for (var holder : ctx.requireServer().getAdvancements().getAllAdvancements()) {
            out.add(holder.id().toString());
        }
        out.sort(String::compareTo);
        return out;
    }

    Optional<String> advancementGetDefinition(String advancementId) {
        Identifier rl = AdapterContext.parseIdentifier(advancementId);
        var holder = ctx.requireServer().getAdvancements().get(rl);
        if (holder == null) {
            return Optional.empty();
        }
        var encoded =
                net.minecraft.advancements.Advancement.CODEC
                        .encodeStart(com.mojang.serialization.JsonOps.INSTANCE, holder.value())
                        .resultOrPartial(err -> { });
        return encoded.map(Object::toString);
    }

    // =====================================================================
    // Function (vanilla)
    // =====================================================================

    boolean functionRun(String functionId, UUID asEntity) {
        if (asEntity != null) {
            // /function reports a meaningful successCount (commands executed in the
            // function); a function with no successful commands still ran -- prefer
            // commandOk so an empty function doesn't look like a failure.
            return AdapterContext.commandOk(
                    ctx.commandExecute(
                            "execute as "
                                    + AdapterContext.entitySelector(asEntity)
                                    + " run function "
                                    + functionId));
        }
        return AdapterContext.commandOk(ctx.commandExecute("function " + functionId));
    }

    List<String> functionList(String namespaceFilter) {
        List<String> out = new ArrayList<>();
        for (Identifier id : ctx.requireServer().getFunctions().getFunctionNames()) {
            String s = id.toString();
            if (namespaceFilter == null || namespaceFilter.isBlank()
                    || id.getNamespace().equals(namespaceFilter)) {
                out.add(s);
            }
        }
        out.sort(String::compareTo);
        return out;
    }

    Optional<String> functionGetDefinition(String functionId) {
        Identifier rl = AdapterContext.parseIdentifier(functionId);
        var fn = ctx.requireServer().getFunctions().get(rl);
        if (fn.isEmpty()) {
            return Optional.empty();
        }
        // The CommandFunction internals (entry list) vary across versions; toString() is
        // stable and reports the function id plus entry count which is enough for
        // diagnostic display. A richer view would need version-specific accessors.
        return Optional.of(fn.get().toString());
    }

    // =====================================================================
    // Schedule (vanilla)
    // =====================================================================

    boolean scheduleFunction(String functionId, int ticks, String mode) {
        String m = mode == null || mode.isBlank() ? "replace" : mode.toLowerCase(Locale.ROOT);
        // /schedule function is a void setter.
        return AdapterContext.commandOk(
                ctx.commandExecute("schedule function " + functionId + " " + ticks + "t " + m));
    }

    boolean scheduleClear(String functionId) {
        // /schedule clear is a void setter; returns 0 when no schedule was active.
        return AdapterContext.commandOk(ctx.commandExecute("schedule clear " + functionId));
    }

    List<ScheduledFunctionInfo> scheduleList() {
        // The vanilla scheduler (TimerQueue) does not publish per-entry function ids
        // through a stable accessor across versions. We surface what the /schedule
        // command itself reports — running the bare command returns the list as
        // feedback messages, which CapturingCommandSource collects.
        CommandResult r = ctx.commandExecute("schedule");
        List<ScheduledFunctionInfo> out = new ArrayList<>();
        for (String line : r.output()) {
            // Lines look like: "function:my_pack:tick at 200 ticks" — best-effort parse.
            String[] parts = line.split(" at ");
            if (parts.length == 2) {
                String name = parts[0].replaceFirst("^function:", "").trim();
                String ticksPart = parts[1].replaceAll("[^0-9-]", "");
                long ticks;
                try {
                    ticks = Long.parseLong(ticksPart);
                } catch (NumberFormatException nfe) {
                    ticks = -1L;
                }
                out.add(new ScheduledFunctionInfo(name, ticks));
            }
        }
        return out;
    }

    // =====================================================================
    // Item modify (vanilla)
    // =====================================================================

    boolean itemModifyEntitySlot(UUID entityUuid, String slot, String modifierId) {
        // /item modify entity is a void setter; bare UUIDs are rejected so wrap in
        // an @e[uuid=...] selector.
        return AdapterContext.commandOk(
                ctx.commandExecute(
                        "item modify entity "
                                + AdapterContext.entitySelector(entityUuid)
                                + " "
                                + slot
                                + " "
                                + modifierId));
    }

    boolean itemModifyBlockSlot(
            String dimensionId, Vec3i position, String slot, String modifierId) {
        // /item modify block is a void setter.
        return AdapterContext.commandOk(
                ctx.commandExecute(
                        "execute in "
                                + dimensionId
                                + " run item modify block "
                                + position.x() + " " + position.y() + " " + position.z()
                                + " " + slot + " " + modifierId));
    }
}
