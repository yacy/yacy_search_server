/**
 *  UpdatePlanTool
 *  Copyright 2026 by Michael Peter Christen
 *  First released 06.02.2026 at https://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.ai.tools;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.yacy.ai.ToolHandler;

/**
 * A conversation-planning tool for LLM agents.
 * <p>
 * This tool intentionally does not persist plan state itself. In the simplest
 * integration, the chat transcript is the source of truth: the client stores
 * tool calls and tool results as part of the conversation, finds the latest
 * successful {@code update_plan} result, and renders that normalized plan as
 * the current state. No separate plan API is required for that model.
 * <p>
 * Recommended usage: each call should contain the complete current plan
 * snapshot, not a partial patch. This keeps transcript replay, UI rendering,
 * and history compaction straightforward because the latest accepted tool
 * result is authoritative.
 */
public class UpdatePlanTool implements ToolHandler {

    private static final String NAME = "update_plan";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_IN_PROGRESS = "in_progress";
    private static final String STATUS_COMPLETED = "completed";

    @Override
    public JSONObject definition() throws JSONException {
        JSONObject tool = new JSONObject(true);
        tool.put("type", "function");
        JSONObject fn = new JSONObject(true);
        fn.put("name", NAME);
        fn.put("description", "Update the visible task plan. Use this to show progress on multi-step work. At most one plan item should be in_progress at a time.");

        JSONObject params = new JSONObject(true);
        params.put("type", "object");
        JSONObject props = new JSONObject(true);

        JSONObject explanation = new JSONObject(true);
        explanation.put("type", "string");
        explanation.put("description", "Optional short explanation of the current plan or why it changed.");
        props.put("explanation", explanation);

        JSONObject step = new JSONObject(true);
        step.put("type", "string");
        step.put("description", "Short description of the task step.");

        JSONObject status = new JSONObject(true);
        status.put("type", "string");
        status.put("enum", new JSONArray().put(STATUS_PENDING).put(STATUS_IN_PROGRESS).put(STATUS_COMPLETED));
        status.put("description", "Current status of this step.");

        JSONObject itemProps = new JSONObject(true);
        itemProps.put("step", step);
        itemProps.put("status", status);

        JSONObject item = new JSONObject(true);
        item.put("type", "object");
        item.put("properties", itemProps);
        item.put("required", new JSONArray().put("step").put("status"));
        item.put("additionalProperties", false);

        JSONObject plan = new JSONObject(true);
        plan.put("type", "array");
        plan.put("description", "Ordered list of plan items.");
        plan.put("items", item);
        props.put("plan", plan);

        params.put("properties", props);
        params.put("required", new JSONArray().put("plan"));
        params.put("additionalProperties", false);
        fn.put("parameters", params);
        tool.put("function", fn);
        return tool;
    }

    @Override
    public int maxCallsPerTurn() {
        return 10;
    }

    @Override
    public String execute(String arguments) {
        final JSONObject args;
        try {
            args = (arguments == null || arguments.isEmpty()) ? new JSONObject(true) : new JSONObject(arguments);
        } catch (JSONException e) {
            return ToolHandler.errorJson("Invalid arguments JSON");
        }

        final JSONArray plan = args.optJSONArray("plan");
        if (plan == null) return ToolHandler.errorJson("Missing plan");

        final JSONArray normalizedPlan = new JSONArray();
        int inProgressCount = 0;
        try {
            for (int i = 0; i < plan.length(); i++) {
                final JSONObject item = plan.optJSONObject(i);
                if (item == null) return ToolHandler.errorJson("Plan item at index " + i + " must be an object");

                final String step = item.optString("step", "").trim();
                if (step.isEmpty()) return ToolHandler.errorJson("Plan item at index " + i + " is missing step");

                final String status = item.optString("status", "").trim();
                if (!isValidStatus(status)) return ToolHandler.errorJson("Invalid status at index " + i + ": " + status);
                if (STATUS_IN_PROGRESS.equals(status)) inProgressCount++;

                final JSONObject normalizedItem = new JSONObject(true);
                normalizedItem.put("step", step);
                normalizedItem.put("status", status);
                normalizedPlan.put(normalizedItem);
            }

            if (inProgressCount > 1) {
                return ToolHandler.errorJson("Only one plan item can be in_progress");
            }

            final JSONObject result = new JSONObject(true);
            result.put("tool", NAME);
            result.put("accepted", true);
            result.put("step_count", normalizedPlan.length());
            result.put("in_progress_count", inProgressCount);
            return result.toString();
        } catch (JSONException e) {
            return ToolHandler.errorJson("Failed to build update_plan response");
        }
    }

    private static boolean isValidStatus(final String status) {
        return STATUS_PENDING.equals(status) || STATUS_IN_PROGRESS.equals(status) || STATUS_COMPLETED.equals(status);
    }
}
