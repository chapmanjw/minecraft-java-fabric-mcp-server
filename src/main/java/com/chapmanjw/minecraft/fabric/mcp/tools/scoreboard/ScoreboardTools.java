package com.chapmanjw.minecraft.fabric.mcp.tools.scoreboard;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.ScoreboardObjectiveInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.TeamInfo;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/** Scoreboard tools. */
public final class ScoreboardTools {

    private ScoreboardTools() {}

    @McpTool(name = "scoreboard_list_objectives", description = "Lists every registered scoreboard objective.")
    public static final class ListObjectives extends BaseTool {
        private static final JsonNode SCHEMA = Schemas.object().description("No arguments.").build();

        public ListObjectives() {
            super("scoreboard_list_objectives");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            return onMainThread(
                    context,
                    ignored -> {
                        List<ScoreboardObjectiveInfo> os = context.adapter().scoreboardListObjectives();
                        ArrayNode arr = context.mapper().createArrayNode();
                        for (ScoreboardObjectiveInfo o : os) {
                            ObjectNode n = arr.addObject();
                            n.put("name", o.name());
                            n.put("displayName", o.displayName());
                            n.put("criterion", o.criterion());
                            n.put("displaySlot", o.displaySlot());
                        }
                        return ToolResult.ofToon(arr);
                    });
        }
    }

    @McpTool(name = "scoreboard_get_objective", description = "Returns one objective by name.")
    public static final class GetObjective extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("name", Schemas.string("Objective name")).build();

        public GetObjective() {
            super("scoreboard_get_objective");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            String name = reader(arguments).requireString("name");
            return onMainThread(
                    context,
                    ignored -> {
                        ScoreboardObjectiveInfo o =
                                context.adapter()
                                        .scoreboardGetObjective(name)
                                        .orElseThrow(
                                                () ->
                                                        new McpException(
                                                                ErrorCodes.TOOL_HANDLER_ERROR,
                                                                "Unknown objective: " + name));
                        ObjectNode n = context.mapper().createObjectNode();
                        n.put("name", o.name());
                        n.put("displayName", o.displayName());
                        n.put("criterion", o.criterion());
                        return ToolResult.ofToon(n);
                    });
        }
    }

    @McpTool(name = "scoreboard_add_objective", description = "Creates a scoreboard objective.")
    public static final class AddObjective extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("name", Schemas.string("Objective name"))
                        .required("criterion", Schemas.string("Criterion (e.g. dummy, deathCount)"))
                        .optional("display_name", Schemas.string("Display name"))
                        .build();

        public AddObjective() {
            super("scoreboard_add_objective");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String name = r.requireString("name");
            String criterion = r.requireString("criterion");
            String displayName = r.optString("display_name", null);
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().scoreboardAddObjective(name, criterion, displayName)
                                            ? "added"
                                            : "failed"));
        }
    }

    @McpTool(name = "scoreboard_remove_objective", description = "Removes a scoreboard objective.")
    public static final class RemoveObjective extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("name", Schemas.string("Objective name")).build();

        public RemoveObjective() {
            super("scoreboard_remove_objective");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            String name = reader(arguments).requireString("name");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().scoreboardRemoveObjective(name) ? "removed" : "failed"));
        }
    }

    @McpTool(name = "scoreboard_set_display_slot", description = "Assigns an objective to a display slot.")
    public static final class SetDisplaySlot extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required(
                                "slot",
                                Schemas.enumOf("Display slot", "list", "sidebar", "below_name"))
                        .required("objective", Schemas.string("Objective name (empty to clear)"))
                        .build();

        public SetDisplaySlot() {
            super("scoreboard_set_display_slot");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String slot = r.requireString("slot");
            String obj = r.requireString("objective");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().scoreboardSetDisplaySlot(slot, obj) ? "set" : "failed"));
        }
    }

    @McpTool(name = "scoreboard_get_score", description = "Reads a participant's score on an objective.")
    public static final class GetScore extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("participant", Schemas.string("Participant (name, UUID, or selector target)"))
                        .required("objective", Schemas.string("Objective name"))
                        .build();

        public GetScore() {
            super("scoreboard_get_score");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String p = r.requireString("participant");
            String obj = r.requireString("objective");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(String.valueOf(context.adapter().scoreboardGetScore(p, obj))));
        }
    }

    @McpTool(name = "scoreboard_set_score", description = "Writes a participant's score on an objective.")
    public static final class SetScore extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("participant", Schemas.string("Participant"))
                        .required("objective", Schemas.string("Objective"))
                        .required("score", Schemas.integer("Score value"))
                        .build();

        public SetScore() {
            super("scoreboard_set_score");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String p = r.requireString("participant");
            String obj = r.requireString("objective");
            int score = r.requireInt("score");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().scoreboardSetScore(p, obj, score) ? "set" : "failed"));
        }
    }

    @McpTool(name = "scoreboard_add_score", description = "Increments a participant's score by a delta.")
    public static final class AddScore extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("participant", Schemas.string("Participant"))
                        .required("objective", Schemas.string("Objective"))
                        .required("delta", Schemas.integer("Delta to add"))
                        .build();

        public AddScore() {
            super("scoreboard_add_score");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String p = r.requireString("participant");
            String obj = r.requireString("objective");
            int delta = r.requireInt("delta");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().scoreboardAddScore(p, obj, delta) ? "added" : "failed"));
        }
    }

    @McpTool(name = "scoreboard_reset_participant", description = "Resets a participant's score on an objective.")
    public static final class ResetParticipant extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("participant", Schemas.string("Participant"))
                        .required("objective", Schemas.string("Objective"))
                        .build();

        public ResetParticipant() {
            super("scoreboard_reset_participant");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String p = r.requireString("participant");
            String obj = r.requireString("objective");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().scoreboardResetParticipant(p, obj) ? "reset" : "failed"));
        }
    }

    @McpTool(name = "scoreboard_list_teams", description = "Lists every registered team along with its members.")
    public static final class ListTeams extends BaseTool {
        private static final JsonNode SCHEMA = Schemas.object().description("No arguments.").build();

        public ListTeams() {
            super("scoreboard_list_teams");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            return onMainThread(
                    context,
                    ignored -> {
                        List<TeamInfo> teams = context.adapter().scoreboardListTeams();
                        ArrayNode arr = context.mapper().createArrayNode();
                        for (TeamInfo t : teams) {
                            ObjectNode n = arr.addObject();
                            n.put("name", t.name());
                            n.put("displayName", t.displayName());
                            n.put("color", t.color());
                            n.put("friendlyFire", t.friendlyFire());
                            n.put("seeInvisibles", t.seeInvisibles());
                            ArrayNode members = n.putArray("members");
                            t.members().forEach(members::add);
                        }
                        return ToolResult.ofToon(arr);
                    });
        }
    }

    @McpTool(name = "scoreboard_add_team", description = "Creates a new team.")
    public static final class AddTeam extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("name", Schemas.string("Team name"))
                        .optional("display_name", Schemas.string("Display name"))
                        .build();

        public AddTeam() {
            super("scoreboard_add_team");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String name = r.requireString("name");
            String dn = r.optString("display_name", null);
            return onMainThread(
                    context,
                    ignored -> ToolResult.ofText(context.adapter().scoreboardAddTeam(name, dn) ? "added" : "failed"));
        }
    }

    @McpTool(name = "scoreboard_remove_team", description = "Removes a team.")
    public static final class RemoveTeam extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("name", Schemas.string("Team name")).build();

        public RemoveTeam() {
            super("scoreboard_remove_team");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            String name = reader(arguments).requireString("name");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().scoreboardRemoveTeam(name) ? "removed" : "failed"));
        }
    }

    @McpTool(name = "scoreboard_team_add_member", description = "Adds a participant to a team.")
    public static final class TeamAddMember extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("team", Schemas.string("Team name"))
                        .required("participant", Schemas.string("Participant"))
                        .build();

        public TeamAddMember() {
            super("scoreboard_team_add_member");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String team = r.requireString("team");
            String p = r.requireString("participant");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().scoreboardTeamAddMember(team, p) ? "added" : "failed"));
        }
    }

    @McpTool(name = "scoreboard_team_remove_member", description = "Removes a participant from their team.")
    public static final class TeamRemoveMember extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("team", Schemas.string("Team name"))
                        .required("participant", Schemas.string("Participant"))
                        .build();

        public TeamRemoveMember() {
            super("scoreboard_team_remove_member");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String team = r.requireString("team");
            String p = r.requireString("participant");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().scoreboardTeamRemoveMember(team, p) ? "removed" : "failed"));
        }
    }
}
