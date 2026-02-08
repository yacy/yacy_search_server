/**
 *  BeautifulMermaid
 *  Copyright 2026 by Michael Peter Christen
 *  MIT License
 *
 *  Original Go project (beautiful-mermaid is based on mermaid-ascii) 
 *  https://github.com/AlexanderGrooff/mermaid-ascii
 *  Copyright (c) 2024 Alexander Grooff (@AlexanderGrooff), MIT License
 *  
 *  Original TypeScript Project Author of 
 *  https://github.com/lukilabs/beautiful-mermaid: 
 *  Copyright (c) 2026 Luki Labs (@balintorosz), MIT License
 *  
 *  This Java Version was written by Codex, transcoded from the python version from
 *  https://github.com/Orbiter/beautiful-mermaid-py
 *  Copyright (c) 2026 Michael Christen (@orbiterlab), MIT License
 * 
 *  Supports:
 *  - Flowcharts / stateDiagram-v2 (grid + A* pathfinding)
 *  - sequenceDiagram
 *  - classDiagram
 *  - erDiagram
 */

package net.yacy.ai.tools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.yacy.ai.ToolHandler;

public final class Mermaid2ASCIITool implements ToolHandler {

    private static final String NAME = "mermaid_to_ascii";

    private static final Pattern FLOW_HEADER = Pattern.compile("^(?:graph|flowchart)\\s+(TD|TB|LR|BT|RL)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern STATE_HEADER = Pattern.compile("^stateDiagram(?:-v2)?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEQ_HEADER = Pattern.compile("^sequenceDiagram\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLASS_HEADER = Pattern.compile("^classDiagram\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ER_HEADER = Pattern.compile("^erDiagram\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern SUBGRAPH_START = Pattern.compile("^subgraph\\s+([^\\[]+?)(?:\\s*\\[(.+)])?\\s*$");
    private static final Pattern STATE_BLOCK_START = Pattern.compile("^state\\s+([A-Za-z0-9_\\-*]+)\\s*\\{\\s*$");
    private static final Pattern NODE_DEF = Pattern.compile("^([A-Za-z0-9_\\-*]+)(.*)$");
    private static final Pattern BARE_NODE = Pattern.compile("^([A-Za-z0-9_\\-*]+)");
    private static final Pattern CLASS_SHORTHAND = Pattern.compile("^:::([A-Za-z][A-Za-z0-9_-]*)");
    private static final Pattern PARTICIPANT = Pattern.compile("^(participant|actor)\\s+([A-Za-z0-9_\\-*]+)(?:\\s+as\\s+(.+))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEQ_NOTE = Pattern.compile("^Note\\s+(left of|right of|over)\\s+([^:]+)\\s*:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLASS_DEF = Pattern.compile("^class\\s+([A-Za-z0-9_\\-]+)(?:\\s*\\{)?\\s*$");
    private static final Pattern ER_ENTITY = Pattern.compile("^([A-Za-z0-9_\\-]+)\\s*\\{\\s*$");

    private static final String[] EDGE_OPS = {"<-->", "<-.->", "<==>", "-->", "-.->", "==>", "---", "-.-", "===", "--", "->>", "-->>", "-)", "--)"};
    private static final Pattern[] FLOW_NODE_PATTERNS = new Pattern[] {
        Pattern.compile("^([A-Za-z0-9_\\-*]+)\\(\\(\\((.+?)\\)\\)\\)"),
        Pattern.compile("^([A-Za-z0-9_\\-*]+)\\(\\[(.+?)\\]\\)"),
        Pattern.compile("^([A-Za-z0-9_\\-*]+)\\(\\((.+?)\\)\\)"),
        Pattern.compile("^([A-Za-z0-9_\\-*]+)\\[\\[(.+?)\\]\\]"),
        Pattern.compile("^([A-Za-z0-9_\\-*]+)\\[\\((.+?)\\)\\]"),
        Pattern.compile("^([A-Za-z0-9_\\-*]+)\\[/(.+?)\\\\\\]"),
        Pattern.compile("^([A-Za-z0-9_\\-*]+)\\[\\\\(.+?)/\\]"),
        Pattern.compile("^([A-Za-z0-9_\\-*]+)>(.+?)\\]"),
        Pattern.compile("^([A-Za-z0-9_\\-*]+)\\{\\{(.+?)\\}\\}"),
        Pattern.compile("^([A-Za-z0-9_\\-*]+)\\[(.+?)\\]"),
        Pattern.compile("^([A-Za-z0-9_\\-*]+)\\((.+?)\\)"),
        Pattern.compile("^([A-Za-z0-9_\\-*]+)\\{(.+?)\\}")
    };

    @Override
    public JSONObject definition() throws JSONException {
        JSONObject tool = new JSONObject(true);
        tool.put("type", "function");
        JSONObject fn = new JSONObject(true);
        fn.put("name", NAME);
        fn.put("description", "Render Mermaid diagram text as ASCII/Unicode box-art text.");

        JSONObject params = new JSONObject(true);
        params.put("type", "object");
        JSONObject props = new JSONObject(true);

        JSONObject mermaidCode = new JSONObject(true);
        mermaidCode.put("type", "string");
        mermaidCode.put("description", "Mermaid diagram source code.");
        props.put("mermaid_code", mermaidCode);

        JSONObject useAscii = new JSONObject(true);
        useAscii.put("type", "boolean");
        useAscii.put("description", "Use pure ASCII output instead of Unicode box drawing characters.");
        props.put("use_ascii", useAscii);

        JSONObject paddingX = new JSONObject(true);
        paddingX.put("type", "integer");
        paddingX.put("description", "Horizontal padding between nodes.");
        props.put("padding_x", paddingX);

        JSONObject paddingY = new JSONObject(true);
        paddingY.put("type", "integer");
        paddingY.put("description", "Vertical padding between nodes.");
        props.put("padding_y", paddingY);

        JSONObject boxPadding = new JSONObject(true);
        boxPadding.put("type", "integer");
        boxPadding.put("description", "Inner padding inside rendered node boxes.");
        props.put("box_padding", boxPadding);

        params.put("properties", props);
        params.put("required", new JSONArray().put("mermaid_code"));
        fn.put("parameters", params);
        tool.put("function", fn);
        return tool;
    }

    @Override
    public int maxCallsPerTurn() {
        return 1;
    }

    @Override
    public String execute(String arguments) {
        final JSONObject args;
        try {
            args = (arguments == null || arguments.isEmpty()) ? new JSONObject(true) : new JSONObject(arguments);
        } catch (JSONException e) {
            return ToolHandler.errorJson("Invalid arguments JSON");
        }

        final String mermaidCode = args.optString("mermaid_code", "").trim();
        if (mermaidCode.isEmpty()) return ToolHandler.errorJson("Missing mermaid_code");

        final boolean useAscii = args.has("use_ascii") && args.optBoolean("use_ascii", false);
        final int paddingX = args.optInt("padding_x", 3);
        final int paddingY = args.optInt("padding_y", 2);
        final int boxPadding = args.optInt("box_padding", 1);

        try {
            String ascii = renderMermaidAscii(mermaidCode, useAscii, paddingX, paddingY, boxPadding);
            JSONObject result = new JSONObject(true);
            result.put("diagram_type", detectDiagramType(mermaidCode));
            result.put("use_ascii", useAscii);
            result.put("ascii_art", ascii);
            return result.toString();
        } catch (Exception e) {
            return ToolHandler.errorJson("Failed to render Mermaid diagram: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        try {
            CliOptions options = CliOptions.parse(args);
            if (options == null) {
                printUsage();
                System.exit(2);
                return;
            }
            String text = Files.readString(options.input, StandardCharsets.UTF_8);
            String output = renderMermaidAscii(text, options.useAscii, options.paddingX, options.paddingY, options.boxPadding);
            System.out.print(output);
            if (!output.endsWith("\n")) {
                System.out.println();
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    public static String renderMermaidAscii(String text, boolean useAscii, int paddingX, int paddingY, int boxPadding) {
        String type = detectDiagramType(text);
        if ("sequence".equals(type)) {
            return renderSequence(text, useAscii);
        }
        if ("class".equals(type)) {
            return renderClassDiagram(text, useAscii);
        }
        if ("er".equals(type)) {
            return renderErDiagram(text, useAscii);
        }
        return renderFlowOrState(text, useAscii, Math.max(3, paddingX), Math.max(2, paddingY), Math.max(1, boxPadding));
    }

    private static String detectDiagramType(String text) {
        List<String> lines = splitLines(text);
        if (lines.isEmpty()) {
            return "flow";
        }
        String first = lines.get(0).trim();
        if (SEQ_HEADER.matcher(first).matches()) return "sequence";
        if (CLASS_HEADER.matcher(first).matches()) return "class";
        if (ER_HEADER.matcher(first).matches()) return "er";
        return "flow";
    }

    private static List<String> splitLines(String text) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        String[] parts = text.replace("\r\n", "\n").replace('\r', '\n').split("\\n");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty() && !t.startsWith("%%")) out.add(t);
        }
        return out;
    }

    private static String renderFlowOrState(String text, boolean useAscii, int paddingX, int paddingY, int boxPad) {
        List<String> linesRaw = splitLines(text);
        if (linesRaw.isEmpty()) return "";
        List<String> lines = new ArrayList<>();
        for (String line : linesRaw) {
            for (String seg : line.split(";")) {
                String s = seg.trim();
                if (!s.isEmpty() && !s.startsWith("%%")) lines.add(s);
            }
        }
        if (lines.isEmpty()) return "";

        String direction = "TD";
        String header = lines.get(0);
        Matcher fm = FLOW_HEADER.matcher(header);
        boolean isState = false;
        if (fm.matches()) {
            direction = fm.group(1).toUpperCase();
        } else if (STATE_HEADER.matcher(header).matches()) {
            direction = "TD";
            isState = true;
        }

        FlowGraph g = new FlowGraph(direction);
        if (isState) parseStateDiagram(lines, g);
        else parseFlowLike(lines, g);
        if (g.nodes.isEmpty()) return "";

        return renderFlowParity(g, useAscii, paddingX, paddingY, boxPad);
    }

    private static void parseStateDiagram(List<String> lines, FlowGraph g) {
        ArrayDeque<Subgraph> stack = new ArrayDeque<>();
        int startCount = 0;
        int endCount = 0;

        Pattern dirRe = Pattern.compile("^direction\\s+(TD|TB|LR|BT|RL)\\s*$", Pattern.CASE_INSENSITIVE);
        Pattern compStart = Pattern.compile("^state\\s+(?:\"([^\"]+)\"\\s+as\\s+)?(\\w+)\\s*\\{$");
        Pattern aliasRe = Pattern.compile("^state\\s+\"([^\"]+)\"\\s+as\\s+(\\w+)\\s*$");
        Pattern transRe = Pattern.compile("^(\\[\\*\\]|[\\w-]+)\\s*(-->)\\s*(\\[\\*\\]|[\\w-]+)(?:\\s*:\\s*(.+))?$");
        Pattern descRe = Pattern.compile("^([\\w-]+)\\s*:\\s*(.+)$");

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher dm = dirRe.matcher(line);
            if (dm.matches()) {
                String dir = dm.group(1).toUpperCase();
                if (!stack.isEmpty()) stack.peek().direction = dir;
                else g.direction = dir;
                continue;
            }

            Matcher cm = compStart.matcher(line);
            if (cm.matches()) {
                String label = cm.group(1) != null ? cm.group(1) : cm.group(2);
                String id = cm.group(2);
                Subgraph sg = new Subgraph(id, label, stack.peek(), null);
                if (stack.peek() != null) stack.peek().children.add(sg);
                g.subgraphs.add(sg);
                stack.push(sg);
                continue;
            }

            if ("}".equals(line)) {
                if (!stack.isEmpty()) stack.pop();
                continue;
            }

            Matcher am = aliasRe.matcher(line);
            if (am.matches()) {
                ensureNode(g, am.group(2), am.group(1), stack.peek());
                continue;
            }

            Matcher tm = transRe.matcher(line);
            if (tm.matches()) {
                String src = tm.group(1);
                String tgt = tm.group(3);
                String label = tm.group(4) == null ? "" : tm.group(4).trim();

                if ("[*]".equals(src)) {
                    startCount++;
                    src = "_start" + (startCount > 1 ? startCount : "");
                    ensureNode(g, src, "", stack.peek());
                } else {
                    ensureNode(g, src, src, stack.peek());
                }
                if ("[*]".equals(tgt)) {
                    endCount++;
                    tgt = "_end" + (endCount > 1 ? endCount : "");
                    ensureNode(g, tgt, "", stack.peek());
                } else {
                    ensureNode(g, tgt, tgt, stack.peek());
                }
                g.edges.add(new FlowEdge(src, tgt, label, "-->"));
                continue;
            }

            Matcher dsm = descRe.matcher(line);
            if (dsm.matches()) {
                ensureNode(g, dsm.group(1), dsm.group(2).trim(), stack.peek());
            }
        }
    }

    private static void parseFlowLike(List<String> lines, FlowGraph g) {
        ArrayDeque<Subgraph> stack = new ArrayDeque<>();
        boolean inStateBlock = false;

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;

            if (line.toLowerCase().startsWith("subgraph ")) {
                String rest = line.substring("subgraph ".length()).trim();
                String id;
                String label;
                Matcher bracket = Pattern.compile("^([A-Za-z0-9_\\-]+)\\s*\\[(.+)]$").matcher(rest);
                if (bracket.matches()) {
                    id = bracket.group(1);
                    label = bracket.group(2);
                } else {
                    label = rest;
                    id = rest.replaceAll("\\s+", "_").replaceAll("[^A-Za-z0-9_\\-]", "");
                    if (id.isEmpty()) id = label;
                }
                Subgraph sg = new Subgraph(id, label, stack.peek(), null);
                if (stack.peek() != null) stack.peek().children.add(sg);
                g.subgraphs.add(sg);
                stack.push(sg);
                continue;
            }
            Matcher ss = STATE_BLOCK_START.matcher(line);
            if (ss.matches()) {
                inStateBlock = true;
                ensureNode(g, ss.group(1), ss.group(1), stack.peek());
                continue;
            }
            if ("}".equals(line)) {
                inStateBlock = false;
                continue;
            }
            if ("end".equalsIgnoreCase(line)) {
                if (!stack.isEmpty()) stack.pop();
                continue;
            }
            Matcher dirMatch = Pattern.compile("^direction\\s+(TD|TB|LR|BT|RL)\\s*$", Pattern.CASE_INSENSITIVE).matcher(line);
            if (dirMatch.matches()) {
                String dir = dirMatch.group(1).toUpperCase();
                if (!stack.isEmpty()) stack.peek().direction = dir;
                else g.direction = dir;
                continue;
            }
            if (line.toLowerCase().startsWith("classdef ") || line.toLowerCase().startsWith("class ") || line.toLowerCase().startsWith("style ")) {
                continue;
            }

            if (line.contains("-->" ) || line.contains("-.->") || line.contains("==>") || line.contains("---") || line.contains("-.-") || line.contains("===") || line.contains("<-->") || line.contains("<-.->") || line.contains("<==>")) {
                parseFlowEdgeLine(line, g, stack.peek());
            } else {
                parseNodeOnlyLine(line, g, stack.peek(), inStateBlock);
            }
        }
    }

    private static void parseNodeOnlyLine(String line, FlowGraph g, Subgraph current, boolean inStateBlock) {
        String s = line;
        if (s.startsWith("[*]")) {
            ensureNode(g, "START", "", current);
            return;
        }
        NodeConsume nc = consumeNode(s, g, current);
        if (nc != null) return;
        Matcher m = NODE_DEF.matcher(s);
        if (!m.matches()) return;
        String id = m.group(1);
        String label = extractLabel(id, m.group(2));
        ensureNode(g, id, label, current);
    }

    private static void parseFlowEdgeLine(String line, FlowGraph g, Subgraph current) {
        String work = line;
        int pos = findFirstOp(work);
        if (pos < 0) {
            parseNodeOnlyLine(line, g, current, false);
            return;
        }

        String left = work.substring(0, pos).trim();
        String rest = work.substring(pos).trim();
        List<String> prevGroupIds = parseNodeGroup(left, g, current);
        if (prevGroupIds.isEmpty()) return;

        while (true) {
            EdgeToken tok = readEdgeToken(rest);
            if (tok == null) break;
            List<String> nextGroupIds = parseNodeGroup(tok.targetToken, g, current);
            if (nextGroupIds.isEmpty()) break;
            for (String src : prevGroupIds) {
                for (String tgt : nextGroupIds) {
                    g.edges.add(new FlowEdge(src, tgt, tok.label, tok.op));
                }
            }
            prevGroupIds = nextGroupIds;

            rest = tok.remaining.trim();
            if (findFirstOp(rest) < 0) break;
        }
    }

    private static List<String> parseNodeGroup(String token, FlowGraph g, Subgraph current) {
        List<String> ids = new ArrayList<>();
        String rem = token == null ? "" : token.trim();
        NodeConsume first = consumeNode(rem, g, current);
        if (first == null || first.id == null || first.id.isEmpty()) return ids;
        ids.add(first.id);
        rem = first.remaining.trim();
        while (rem.startsWith("&")) {
            rem = ltrim(rem.substring(1));
            NodeConsume nxt = consumeNode(rem, g, current);
            if (nxt == null || nxt.id == null || nxt.id.isEmpty()) break;
            ids.add(nxt.id);
            rem = nxt.remaining.trim();
        }
        return ids;
    }

    private static String parseSingleNodeToken(String token, FlowGraph g, Subgraph current) {
        String t = token.trim();
        if (t.isEmpty()) return "";
        if ("[*]".equals(t)) {
            ensureNode(g, "START", "", current);
            return "START";
        }
        String id = extractNodeId(t);
        String label = extractLabel(id, t.substring(Math.min(id.length(), t.length())));
        ensureNode(g, id, label, current);
        return id;
    }

    private static String parseNodeToken(String token, FlowGraph g, Subgraph current) {
        String t = token.trim();
        if (t.isEmpty()) return "";
        if ("[*]".equals(t)) {
            ensureNode(g, "START", "", current);
            return "START";
        }

        String[] parts = t.split("&");
        String firstId = "";
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) continue;
            String id = extractNodeId(part);
            String label = extractLabel(id, part.substring(Math.min(id.length(), part.length())));
            ensureNode(g, id, label, current);
            if (firstId.isEmpty()) firstId = id;
        }
        return firstId;
    }

    private static NodeConsume consumeNode(String text, FlowGraph g, Subgraph current) {
        String t = ltrim(text == null ? "" : text);
        if (t.isEmpty()) return null;
        if (t.startsWith("[*]")) {
            ensureNode(g, "START", "", current);
            String rem = t.substring(3);
            Matcher cm = CLASS_SHORTHAND.matcher(rem);
            if (cm.find()) rem = rem.substring(cm.end());
            return new NodeConsume("START", rem);
        }

        for (Pattern p : FLOW_NODE_PATTERNS) {
            Matcher m = p.matcher(t);
            if (m.find()) {
                String id = m.group(1);
                String label = m.group(2);
                ensureNode(g, id, label, current);
                String rem = t.substring(m.end());
                Matcher cm = CLASS_SHORTHAND.matcher(ltrim(rem));
                if (cm.find()) rem = ltrim(rem).substring(cm.end());
                return new NodeConsume(id, rem);
            }
        }

        Matcher bare = BARE_NODE.matcher(t);
        if (bare.find()) {
            String id = bare.group(1);
            if (!g.nodes.containsKey(id)) ensureNode(g, id, id, current);
            else if (current != null) {
                current.nodeIds.add(id);
                g.nodes.get(id).subgraphs.add(current);
            }
            String rem = t.substring(bare.end());
            Matcher cm = CLASS_SHORTHAND.matcher(ltrim(rem));
            if (cm.find()) rem = ltrim(rem).substring(cm.end());
            return new NodeConsume(id, rem);
        }
        return null;
    }

    private static String ltrim(String s) {
        return s == null ? "" : s.replaceFirst("^\\s+", "");
    }

    private static int findFirstOp(String s) {
        int best = Integer.MAX_VALUE;
        for (String op : EDGE_OPS) {
            int idx = s.indexOf(op);
            if (idx >= 0 && idx < best) best = idx;
        }
        return best == Integer.MAX_VALUE ? -1 : best;
    }

    private static EdgeToken readEdgeToken(String s) {
        String ws = s.trim();
        int opPos = findFirstOp(ws);
        if (opPos != 0) return null;

        String opFound = null;
        for (String op : EDGE_OPS) {
            if (ws.startsWith(op)) {
                if (opFound == null || op.length() > opFound.length()) opFound = op;
            }
        }
        if (opFound == null) return null;

        String remain = ws.substring(opFound.length());
        String label = "";
        if (remain.startsWith("|")) {
            int end = remain.indexOf('|', 1);
            if (end > 0) {
                label = remain.substring(1, end).trim();
                remain = remain.substring(end + 1);
            }
        }

        int next = findFirstOp(remain);
        String target;
        String rest;
        if (next < 0) {
            target = remain.trim();
            rest = "";
        } else {
            target = remain.substring(0, next).trim();
            rest = remain.substring(next);
        }

        return new EdgeToken(opFound, label, target, rest);
    }

    private static String extractNodeId(String s) {
        String t = s.trim();
        if (t.startsWith("[*]")) return "START";
        int i = 0;
        while (i < t.length()) {
            char c = t.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '*') {
                i++;
            } else {
                break;
            }
        }
        if (i == 0) return t;
        return t.substring(0, i);
    }

    private static String extractLabel(String id, String tail) {
        String t = tail == null ? "" : tail.trim();
        if (t.isEmpty()) return normalizeStateLabel(id);

        String v = t;
        v = v.replaceFirst("(:::([A-Za-z][A-Za-z0-9_-]*))+\\s*$", "").trim();
        if (v.startsWith(":::")) return normalizeStateLabel(id);

        if (v.contains(":")) {
            int c = v.indexOf(':');
            if (c >= 0 && c + 1 < v.length()) return v.substring(c + 1).trim();
        }

        String[] wrappers = {
            "(((", ")))",
            "([", "])",
            "[/", "\\]",
            "[\\", "/]",
            "((", "))",
            "[(", ")]",
            "[[", "]]",
            "{{", "}}",
            "[", "]",
            "(", ")",
            "{", "}"
        };
        for (int i = 0; i + 1 < wrappers.length; i += 2) {
            String a = wrappers[i];
            String b = wrappers[i + 1];
            if (v.startsWith(a) && v.endsWith(b) && v.length() > a.length() + b.length()) {
                return v.substring(a.length(), v.length() - b.length()).trim();
            }
        }
        if (v.startsWith(">") && v.endsWith("]") && v.length() > 2) {
            return v.substring(1, v.length() - 1).trim();
        }
        return normalizeStateLabel(id);
    }

    private static String normalizeStateLabel(String id) {
        if ("START".equals(id)) return "";
        if ("[*]".equals(id)) return "";
        return id;
    }

    private static void ensureNode(FlowGraph g, String id, String label, Subgraph current) {
        FlowNode n = g.nodes.get(id);
        if (n == null) {
            n = new FlowNode(id, label == null ? id : label);
            g.nodes.put(id, n);
        } else if (!n.isPseudo() && (n.label == null || n.label.isBlank() || n.label.equals(n.id))) {
            if (label != null && !label.isBlank()) n.label = label;
        }
        boolean pseudoMarker = id.startsWith("_start") || id.startsWith("_end") || "START".equals(id) || "END".equals(id);
        if (current != null && !pseudoMarker) {
            current.nodeIds.add(id);
            n.subgraphs.add(current);
        }
    }

    private static void layoutFlow(FlowGraph g, int paddingX, int paddingY) {
        Map<String, Integer> indeg = new HashMap<>();
        Map<String, List<String>> out = new HashMap<>();
        for (String id : g.nodes.keySet()) indeg.put(id, 0);
        for (FlowEdge e : g.edges) {
            out.computeIfAbsent(e.from, k -> new ArrayList<>()).add(e.to);
            indeg.put(e.to, indeg.getOrDefault(e.to, 0) + 1);
        }

        Queue<String> q = new ArrayDeque<>();
        for (String id : g.nodes.keySet()) {
            if (indeg.getOrDefault(id, 0) == 0) q.add(id);
        }
        if (q.isEmpty() && !g.nodes.isEmpty()) q.add(g.nodes.keySet().iterator().next());

        Map<String, Integer> level = new HashMap<>();
        for (String id : g.nodes.keySet()) level.put(id, 0);

        Set<String> processed = new HashSet<>();
        Set<String> enqueued = new HashSet<>(q);
        while (!q.isEmpty()) {
            String cur = q.poll();
            if (!processed.add(cur)) {
                continue;
            }
            int lv = level.getOrDefault(cur, 0);
            for (String nxt : out.getOrDefault(cur, Collections.emptyList())) {
                if (level.getOrDefault(nxt, 0) < lv + 1) level.put(nxt, lv + 1);
                indeg.put(nxt, indeg.getOrDefault(nxt, 0) - 1);
                if (indeg.get(nxt) <= 0 && !processed.contains(nxt) && enqueued.add(nxt)) q.add(nxt);
            }
        }

        int maxLv = 0;
        for (int lv : level.values()) maxLv = Math.max(maxLv, lv);
        List<List<FlowNode>> layers = new ArrayList<>();
        for (int i = 0; i <= maxLv; i++) layers.add(new ArrayList<>());
        for (FlowNode n : g.nodes.values()) {
            int lv = level.getOrDefault(n.id, 0);
            if (n.isPseudo()) {
                n.w = 3;
                n.h = 3;
            } else {
                n.w = Math.max(7, n.label.length() + 4);
                n.h = 5;
            }
            layers.get(lv).add(n);
        }
        for (List<FlowNode> l : layers) {
            l.sort(Comparator.comparing(a -> a.id));
        }

        boolean horizontal = "LR".equals(g.direction) || "RL".equals(g.direction);
        int layerGap = horizontal ? paddingX + 12 : paddingY + 6;
        int rowGap = horizontal ? paddingY + 6 : paddingX + 12;

        int primary = 2;
        for (int lv = 0; lv < layers.size(); lv++) {
            List<FlowNode> layer = layers.get(lv);
            int secondary = 2;
            for (FlowNode n : layer) {
                if (horizontal) {
                    n.x = primary;
                    n.y = secondary;
                    secondary += n.h + rowGap;
                } else {
                    n.x = secondary;
                    n.y = primary;
                    secondary += n.w + rowGap;
                }
            }
            if (horizontal) {
                int maxW = layer.stream().mapToInt(a -> a.w).max().orElse(8);
                primary += maxW + layerGap;
            } else {
                int maxH = layer.stream().mapToInt(a -> a.h).max().orElse(5);
                primary += maxH + layerGap;
            }
        }
    }

    private static void drawSubgraphs(Canvas c, FlowGraph g, boolean useAscii) {
        for (Subgraph sg : g.subgraphs) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (String id : sg.nodeIds) {
                FlowNode n = g.nodes.get(id);
                if (n == null) continue;
                minX = Math.min(minX, n.x - 2);
                minY = Math.min(minY, n.y - 2);
                maxX = Math.max(maxX, n.x + n.w + 2);
                maxY = Math.max(maxY, n.y + n.h + 2);
            }
            if (minX == Integer.MAX_VALUE) continue;
            c.drawRect(minX, minY, maxX - minX, maxY - minY, useAscii);
            c.putText(Math.max(minX + 1, 0), Math.max(minY + 1, 0), sg.label);
        }
    }

    private static void drawBox(Canvas c, int x, int y, int w, int h, String label, boolean useAscii) {
        c.drawRect(x, y, w, h, useAscii);
        if (label == null || label.isBlank()) return;
        int tx = x + Math.max(1, (w - label.length()) / 2);
        int ty = y + h / 2;
        c.putText(tx, ty, label);
    }

    private static void routeAndDrawEdges(Canvas c, FlowGraph g, String direction, boolean useAscii) {
        Set<Long> edgeCells = new HashSet<>();
        Set<Long> blocked = new HashSet<>();
        int minX = 0, minY = 0, maxX = c.w - 1, maxY = c.rows.size() - 1;

        for (FlowNode n : g.nodes.values()) {
            for (int x = n.x; x <= n.x + n.w; x++) {
                for (int y = n.y; y <= n.y + n.h; y++) {
                    blocked.add(pack(x, y));
                }
            }
        }

        for (FlowEdge e : g.edges) {
            FlowNode a = g.nodes.get(e.from);
            FlowNode b = g.nodes.get(e.to);
            if (a == null || b == null) continue;

            Point s = anchorFrom(a, b);
            Point t = anchorTo(a, b);
            blocked.remove(pack(s.x, s.y));
            blocked.remove(pack(t.x, t.y));

            List<Point> path = findPath(s, t, blocked, edgeCells, minX, minY, maxX, maxY);
            if (path == null || path.size() < 2) {
                path = new ArrayList<>();
                path.add(s);
                path.add(new Point((s.x + t.x) / 2, s.y));
                path.add(new Point((s.x + t.x) / 2, t.y));
                path.add(t);
            }
            drawPath(c, path, e, useAscii);
            for (Point p : path) edgeCells.add(pack(p.x, p.y));
        }
    }

    private static Point anchorFrom(FlowNode a, FlowNode b) {
        int acx = a.x + a.w / 2, acy = a.y + a.h / 2;
        int bcx = b.x + b.w / 2, bcy = b.y + b.h / 2;
        if (Math.abs(acx - bcx) >= Math.abs(acy - bcy)) {
            if (acx <= bcx) return new Point(a.x + a.w + 1, acy);
            return new Point(a.x - 1, acy);
        }
        if (acy <= bcy) return new Point(acx, a.y + a.h + 1);
        return new Point(acx, a.y - 1);
    }

    private static Point anchorTo(FlowNode a, FlowNode b) {
        int acx = a.x + a.w / 2, acy = a.y + a.h / 2;
        int bcx = b.x + b.w / 2, bcy = b.y + b.h / 2;
        if (Math.abs(acx - bcx) >= Math.abs(acy - bcy)) {
            if (acx <= bcx) return new Point(b.x - 1, bcy);
            return new Point(b.x + b.w + 1, bcy);
        }
        if (acy <= bcy) return new Point(bcx, b.y - 1);
        return new Point(bcx, b.y + b.h + 1);
    }

    private static List<Point> findPath(Point s, Point t, Set<Long> blocked, Set<Long> used, int minX, int minY, int maxX, int maxY) {
        Map<Long, Integer> gScore = new HashMap<>();
        Map<Long, Long> parent = new HashMap<>();
        ArrayDeque<Long> openQ = new ArrayDeque<>();
        Set<Long> inOpen = new HashSet<>();
        Set<Long> closed = new HashSet<>();
        long start = pack(s.x, s.y), goal = pack(t.x, t.y);
        gScore.put(start, 0);
        openQ.add(start);
        inOpen.add(start);

        while (!openQ.isEmpty()) {
            long cur = -1;
            int bestF = Integer.MAX_VALUE;
            for (long id : openQ) {
                Point p = unpack(id);
                int gcur = gScore.getOrDefault(id, Integer.MAX_VALUE / 4);
                int f = gcur + Math.abs(p.x - t.x) + Math.abs(p.y - t.y);
                if (f < bestF) {
                    bestF = f;
                    cur = id;
                }
            }
            openQ.remove(cur);
            inOpen.remove(cur);
            if (cur == goal) {
                List<Point> out = new ArrayList<>();
                long at = cur;
                while (true) {
                    out.add(unpack(at));
                    if (at == start) break;
                    at = parent.get(at);
                }
                Collections.reverse(out);
                return simplifyPath(out);
            }
            closed.add(cur);
            Point p = unpack(cur);
            int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
            for (int[] d : dirs) {
                int nx = p.x + d[0], ny = p.y + d[1];
                if (nx < minX || ny < minY || nx > maxX || ny > maxY) continue;
                long nid = pack(nx, ny);
                if (blocked.contains(nid) && nid != goal) continue;
                if (closed.contains(nid)) continue;
                int step = 1 + (used.contains(nid) ? 30 : 0);
                int tentative = gScore.get(cur) + step;
                if (tentative < gScore.getOrDefault(nid, Integer.MAX_VALUE / 4)) {
                    parent.put(nid, cur);
                    gScore.put(nid, tentative);
                    if (!inOpen.contains(nid)) {
                        openQ.add(nid);
                        inOpen.add(nid);
                    }
                }
            }
        }
        return null;
    }

    private static List<Point> simplifyPath(List<Point> path) {
        if (path.size() <= 2) return path;
        List<Point> out = new ArrayList<>();
        out.add(path.get(0));
        for (int i = 1; i < path.size() - 1; i++) {
            Point a = path.get(i - 1), b = path.get(i), c = path.get(i + 1);
            int dx1 = Integer.compare(b.x - a.x, 0), dy1 = Integer.compare(b.y - a.y, 0);
            int dx2 = Integer.compare(c.x - b.x, 0), dy2 = Integer.compare(c.y - b.y, 0);
            if (dx1 == dx2 && dy1 == dy2) continue;
            out.add(b);
        }
        out.add(path.get(path.size() - 1));
        return out;
    }

    private static void drawPath(Canvas c, List<Point> path, FlowEdge e, boolean useAscii) {
        char hChar = (e.op.contains("-.->") || e.op.contains("-.-") || e.op.contains("<-.->")) ? (useAscii ? '.' : '╌') : (useAscii ? '-' : '─');
        char vChar = (e.op.contains("-.->") || e.op.contains("-.-") || e.op.contains("<-.->")) ? (useAscii ? ':' : '┊') : (useAscii ? '|' : '│');

        for (int i = 0; i < path.size() - 1; i++) {
            Point a = path.get(i), b = path.get(i + 1);
            int x = a.x, y = a.y;
            int dx = Integer.compare(b.x - a.x, 0), dy = Integer.compare(b.y - a.y, 0);
            while (x != b.x || y != b.y) {
                int nx = x + dx, ny = y + dy;
                if (dx != 0) {
                    mergeLine(c, x, y, dx > 0 ? DIR_E : DIR_W, hChar, useAscii);
                    mergeLine(c, nx, ny, dx > 0 ? DIR_W : DIR_E, hChar, useAscii);
                } else {
                    mergeLine(c, x, y, dy > 0 ? DIR_S : DIR_N, vChar, useAscii);
                    mergeLine(c, nx, ny, dy > 0 ? DIR_N : DIR_S, vChar, useAscii);
                }
                x = nx; y = ny;
            }
        }

        if (!e.label.isBlank()) {
            int bestI = -1;
            int bestLen = -1;
            for (int i = 0; i < path.size() - 1; i++) {
                Point a = path.get(i), b = path.get(i + 1);
                int len = Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
                if (len > bestLen) {
                    bestLen = len;
                    bestI = i;
                }
            }
            if (bestI >= 0) {
                Point a = path.get(bestI), b = path.get(bestI + 1);
                if (a.y == b.y) {
                    int x1 = Math.min(a.x, b.x), x2 = Math.max(a.x, b.x);
                    int tx = Math.max(0, (x1 + x2) / 2 - e.label.length() / 2);
                    int ty = Math.max(0, a.y - 1);
                    c.putText(tx, ty, e.label);
                } else {
                    int y1 = Math.min(a.y, b.y), y2 = Math.max(a.y, b.y);
                    int tx = Math.max(0, a.x + 1);
                    int ty = Math.max(0, (y1 + y2) / 2);
                    c.putText(tx, ty, e.label);
                }
            }
        }

        if (e.op.contains(">")) {
            Point a = path.get(path.size() - 2), b = path.get(path.size() - 1);
            char ah;
            if (b.x > a.x) ah = useAscii ? '>' : '►';
            else if (b.x < a.x) ah = useAscii ? '<' : '◄';
            else if (b.y > a.y) ah = useAscii ? 'v' : '▼';
            else ah = useAscii ? '^' : '▲';
            c.put(b.x, b.y, ah);
        }
    }

    private static final int DIR_N = 1;
    private static final int DIR_E = 2;
    private static final int DIR_S = 4;
    private static final int DIR_W = 8;

    private static void mergeLine(Canvas c, int x, int y, int addDir, char fallback, boolean ascii) {
        char cur = c.get(x, y);
        int mask = charMask(cur, ascii);
        mask |= addDir;
        c.put(x, y, charFromMask(mask, ascii, fallback));
    }

    private static int charMask(char ch, boolean ascii) {
        if (ascii) {
            if (ch == '-') return DIR_E | DIR_W;
            if (ch == '|') return DIR_N | DIR_S;
            if (ch == '+') return DIR_N | DIR_E | DIR_S | DIR_W;
            return 0;
        }
        switch (ch) {
            case '─':
                return DIR_E | DIR_W;
            case '│':
                return DIR_N | DIR_S;
            case '┌':
                return DIR_E | DIR_S;
            case '┐':
                return DIR_W | DIR_S;
            case '└':
                return DIR_E | DIR_N;
            case '┘':
                return DIR_W | DIR_N;
            case '├':
                return DIR_N | DIR_E | DIR_S;
            case '┤':
                return DIR_N | DIR_W | DIR_S;
            case '┬':
                return DIR_E | DIR_W | DIR_S;
            case '┴':
                return DIR_E | DIR_W | DIR_N;
            case '┼':
                return DIR_N | DIR_E | DIR_S | DIR_W;
            case '╌':
                return DIR_E | DIR_W;
            case '┊':
                return DIR_N | DIR_S;
            default:
                return 0;
        }
    }

    private static char charFromMask(int m, boolean ascii, char fallback) {
        if (ascii) {
            boolean h = (m & (DIR_E | DIR_W)) != 0;
            boolean v = (m & (DIR_N | DIR_S)) != 0;
            if (h && v) return '+';
            if (h) return '-';
            if (v) return '|';
            return fallback;
        }
        switch (m) {
            case DIR_E | DIR_W:
                return '─';
            case DIR_N | DIR_S:
                return '│';
            case DIR_E | DIR_S:
                return '┌';
            case DIR_W | DIR_S:
                return '┐';
            case DIR_E | DIR_N:
                return '└';
            case DIR_W | DIR_N:
                return '┘';
            case DIR_N | DIR_E | DIR_S:
                return '├';
            case DIR_N | DIR_W | DIR_S:
                return '┤';
            case DIR_E | DIR_W | DIR_S:
                return '┬';
            case DIR_E | DIR_W | DIR_N:
                return '┴';
            case DIR_N | DIR_E | DIR_S | DIR_W:
                return '┼';
            default:
                return fallback;
        }
    }

    private static long pack(int x, int y) {
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }

    private static Point unpack(long id) {
        return new Point((int) (id >> 32), (int) id);
    }

    private static String flipVertical(String text) {
        String[] lines = text.split("\\n", -1);
        List<String> out = Arrays.asList(lines);
        Collections.reverse(out);
        return String.join("\n", out);
    }

    private static String renderSequence(String text, boolean useAscii) {
        List<String> lines = splitLines(text);
        SequenceDiagram diagram = parseSequence(text);
        if (diagram.actors.isEmpty()) return "";

        char H = useAscii ? '-' : '─';
        char V = useAscii ? '|' : '│';
        char TL = useAscii ? '+' : '┌';
        char TR = useAscii ? '+' : '┐';
        char BL = useAscii ? '+' : '└';
        char BR = useAscii ? '+' : '┘';
        char JT = useAscii ? '+' : '┬';
        char JB = useAscii ? '+' : '┴';
        char JL = useAscii ? '+' : '├';
        char JR = useAscii ? '+' : '┤';

        Map<String, Integer> actorIdx = new HashMap<>();
        for (int i = 0; i < diagram.actors.size(); i++) actorIdx.put(diagram.actors.get(i).id, i);

        int boxPad = 1;
        int actorBoxH = 3;
        int n = diagram.actors.size();
        int[] actorBoxW = new int[n];
        int[] halfBox = new int[n];
        for (int i = 0; i < n; i++) {
            actorBoxW[i] = diagram.actors.get(i).label.length() + 2 * boxPad + 2;
            halfBox[i] = (actorBoxW[i] + 1) / 2;
        }

        int[] adjMaxW = new int[Math.max(n - 1, 0)];
        for (SeqMessage msg : diagram.messages) {
            Integer fi = actorIdx.get(msg.from);
            Integer ti = actorIdx.get(msg.to);
            if (fi == null || ti == null || Objects.equals(fi, ti)) continue;
            int lo = Math.min(fi, ti);
            int hi = Math.max(fi, ti);
            int needed = msg.label.length() + 4;
            int numGaps = hi - lo;
            int perGap = (needed + numGaps - 1) / numGaps;
            for (int g = lo; g < hi; g++) adjMaxW[g] = Math.max(adjMaxW[g], perGap);
        }

        int[] llx = new int[n];
        llx[0] = halfBox[0];
        for (int i = 1; i < n; i++) {
            int gap = Math.max(Math.max(halfBox[i - 1] + halfBox[i] + 2, adjMaxW[i - 1] + 2), 8);
            llx[i] = llx[i - 1] + gap;
        }

        List<Integer> msgArrowY = new ArrayList<>();
        List<Integer> msgLabelY = new ArrayList<>();
        Map<Integer, Integer> blockStartY = new HashMap<>();
        Map<Integer, Integer> blockEndY = new HashMap<>();
        Map<String, Integer> divYMap = new HashMap<>();
        List<NotePos> notePositions = new ArrayList<>();

        int curY = actorBoxH;
        for (int mIdx = 0; mIdx < diagram.messages.size(); mIdx++) {
            for (int bIdx = 0; bIdx < diagram.blocks.size(); bIdx++) {
                Block block = diagram.blocks.get(bIdx);
                if (block.startIndex == mIdx) {
                    curY += 2;
                    blockStartY.put(bIdx, curY - 1);
                }
            }
            for (int bIdx = 0; bIdx < diagram.blocks.size(); bIdx++) {
                Block block = diagram.blocks.get(bIdx);
                for (int dIdx = 0; dIdx < block.dividers.size(); dIdx++) {
                    BlockDivider div = block.dividers.get(dIdx);
                    if (div.index == mIdx) {
                        curY += 1;
                        divYMap.put(bIdx + ":" + dIdx, curY);
                        curY += 1;
                    }
                }
            }

            curY += 1;
            SeqMessage msg = diagram.messages.get(mIdx);
            boolean self = msg.from.equals(msg.to);
            if (self) {
                msgLabelY.add(curY + 1);
                msgArrowY.add(curY);
                curY += 3;
            } else {
                msgLabelY.add(curY);
                msgArrowY.add(curY + 1);
                curY += 2;
            }

            for (SeqNote note : diagram.notes) {
                if (note.afterIndex == mIdx) {
                    curY += 1;
                    String[] nLines = note.text.split("\\\\n", -1);
                    int nWidth = 0;
                    for (String nl : nLines) nWidth = Math.max(nWidth, nl.length());
                    nWidth += 4;
                    int nHeight = nLines.length + 2;
                    int aIdx = actorIdx.getOrDefault(note.actorIds.get(0), 0);
                    int nx;
                    if ("left".equals(note.pos)) nx = llx[aIdx] - nWidth - 1;
                    else if ("right".equals(note.pos)) nx = llx[aIdx] + 2;
                    else {
                        if (note.actorIds.size() >= 2) {
                            int a2 = actorIdx.getOrDefault(note.actorIds.get(1), aIdx);
                            nx = (llx[aIdx] + llx[a2]) / 2 - (nWidth / 2);
                        } else {
                            nx = llx[aIdx] - (nWidth / 2);
                        }
                    }
                    nx = Math.max(0, nx);
                    notePositions.add(new NotePos(nx, curY, nWidth, nHeight, Arrays.asList(nLines)));
                    curY += nHeight;
                }
            }

            for (int bIdx = 0; bIdx < diagram.blocks.size(); bIdx++) {
                Block block = diagram.blocks.get(bIdx);
                if (block.endIndex == mIdx) {
                    curY += 1;
                    blockEndY.put(bIdx, curY);
                    curY += 1;
                }
            }
        }

        curY += 1;
        int footerY = curY;
        int totalH = footerY + actorBoxH;
        int totalW = llx[n - 1] + halfBox[n - 1] + 2;
        for (SeqMessage msg : diagram.messages) {
            if (msg.from.equals(msg.to)) {
                int fi = actorIdx.getOrDefault(msg.from, 0);
                int selfRight = llx[fi] + 8 + msg.label.length();
                totalW = Math.max(totalW, selfRight + 1);
            }
        }
        for (NotePos np : notePositions) totalW = Math.max(totalW, np.x + np.width + 1);

        Canvas canvas = new Canvas(totalW + 1, totalH);

        for (int i = 0; i < n; i++) {
            int x = llx[i];
            for (int y = actorBoxH; y <= footerY; y++) canvas.put(x, y, V);
        }

        for (int i = 0; i < n; i++) {
            drawActorBox(canvas, llx[i], 0, diagram.actors.get(i).label, boxPad, H, V, TL, TR, BL, BR);
            drawActorBox(canvas, llx[i], footerY, diagram.actors.get(i).label, boxPad, H, V, TL, TR, BL, BR);
            if (!useAscii) {
                canvas.put(llx[i], actorBoxH - 1, JT);
                canvas.put(llx[i], footerY, JB);
            }
        }

        for (int mIdx = 0; mIdx < diagram.messages.size(); mIdx++) {
            SeqMessage msg = diagram.messages.get(mIdx);
            int fi = actorIdx.get(msg.from);
            int ti = actorIdx.get(msg.to);
            int fromX = llx[fi];
            int toX = llx[ti];
            boolean self = fi == ti;
            char lineChar = msg.dashed ? (useAscii ? '.' : '╌') : H;
            boolean filled = msg.filled;

            if (self) {
                int topY = msgArrowY.get(mIdx);
                int midY = msgLabelY.get(mIdx);
                int botY = topY + 2;
                int loopX = fromX + 6;
                canvas.put(fromX, topY, useAscii ? '+' : JL);
                for (int x = fromX + 1; x < loopX; x++) canvas.put(x, topY, lineChar);
                canvas.put(loopX, topY, useAscii ? '+' : TR);
                for (int y = topY + 1; y < botY; y++) canvas.put(loopX, y, V);
                canvas.put(loopX, botY, useAscii ? '+' : BL);
                for (int x = fromX + 1; x < loopX; x++) canvas.put(x, botY, lineChar);
                canvas.put(fromX, botY, useAscii ? '<' : (filled ? '◄' : '◁'));
                canvas.putText(fromX + 2, midY, msg.label);
                continue;
            }

            int labelY = msgLabelY.get(mIdx);
            int arrowY = msgArrowY.get(mIdx);
            canvas.putText(Math.min(fromX, toX) + 2, labelY, msg.label);
            if (fromX < toX) {
                for (int x = fromX + 1; x < toX; x++) canvas.put(x, arrowY, lineChar);
                canvas.put(toX, arrowY, useAscii ? '>' : (filled ? '▶' : '▷'));
            } else {
                for (int x = toX + 1; x < fromX; x++) canvas.put(x, arrowY, lineChar);
                canvas.put(toX, arrowY, useAscii ? '<' : (filled ? '◀' : '◁'));
            }
        }

        for (int bIdx = 0; bIdx < diagram.blocks.size(); bIdx++) {
            Integer startY = blockStartY.get(bIdx);
            Integer endY = blockEndY.get(bIdx);
            if (startY == null || endY == null) continue;
            int left = Arrays.stream(llx).min().orElse(0);
            int right = Arrays.stream(llx).max().orElse(0);
            int top = startY;
            int bottom = endY;
            canvas.put(left - 2, top, TL);
            for (int x = left - 1; x <= right + 1; x++) canvas.put(x, top, H);
            canvas.put(right + 2, top, TR);
            canvas.put(left - 2, bottom, BL);
            for (int x = left - 1; x <= right + 1; x++) canvas.put(x, bottom, H);
            canvas.put(right + 2, bottom, BR);
            for (int y = top + 1; y < bottom; y++) {
                canvas.put(left - 2, y, V);
                canvas.put(right + 2, y, V);
            }
            String header = (diagram.blocks.get(bIdx).type + " " + diagram.blocks.get(bIdx).label).trim();
            canvas.putText(left - 1, top + 1, header);

            for (int dIdx = 0; dIdx < diagram.blocks.get(bIdx).dividers.size(); dIdx++) {
                Integer dy = divYMap.get(bIdx + ":" + dIdx);
                if (dy == null) continue;
                canvas.put(left - 2, dy, useAscii ? '+' : JL);
                for (int x = left - 1; x <= right + 1; x++) canvas.put(x, dy, H);
                canvas.put(right + 2, dy, useAscii ? '+' : JR);
                String dl = diagram.blocks.get(bIdx).dividers.get(dIdx).label.trim();
                canvas.putText(left - 1, dy + 1, dl);
            }
        }

        for (NotePos np : notePositions) {
            canvas.put(np.x, np.y, TL);
            for (int x = 1; x < np.width - 1; x++) canvas.put(np.x + x, np.y, H);
            canvas.put(np.x + np.width - 1, np.y, TR);
            canvas.put(np.x, np.y + np.height - 1, BL);
            for (int x = 1; x < np.width - 1; x++) canvas.put(np.x + x, np.y + np.height - 1, H);
            canvas.put(np.x + np.width - 1, np.y + np.height - 1, BR);
            for (int y = 1; y < np.height - 1; y++) {
                canvas.put(np.x, np.y + y, V);
                canvas.put(np.x + np.width - 1, np.y + y, V);
            }
            for (int i = 0; i < np.lines.size(); i++) canvas.putText(np.x + 2, np.y + 1 + i, np.lines.get(i));
        }

        return canvasToStringFull(canvas, totalW, totalH);
    }

    private static void drawActorBox(Canvas canvas, int cx, int topY, String label, int boxPad,
                                     char H, char V, char TL, char TR, char BL, char BR) {
        int w = label.length() + 2 * boxPad + 2;
        int left = cx - (w / 2);
        canvas.put(left, topY, TL);
        for (int x = 1; x < w - 1; x++) canvas.put(left + x, topY, H);
        canvas.put(left + w - 1, topY, TR);
        canvas.put(left, topY + 1, V);
        canvas.put(left + w - 1, topY + 1, V);
        canvas.putText(left + 1 + boxPad, topY + 1, label);
        canvas.put(left, topY + 2, BL);
        for (int x = 1; x < w - 1; x++) canvas.put(left + x, topY + 2, H);
        canvas.put(left + w - 1, topY + 2, BR);
    }

    private static void drawSeqBox(Canvas c, int x, int y, int w, int h, String label,
                                   char TL, char TR, char BL, char BR, char H, char V) {
        c.put(x, y, TL);
        c.put(x + w - 1, y, TR);
        c.put(x, y + h - 1, BL);
        c.put(x + w - 1, y + h - 1, BR);
        c.hLine(x + 1, x + w - 2, y, H);
        c.hLine(x + 1, x + w - 2, y + h - 1, H);
        c.vLine(y + 1, y + h - 2, x, V);
        c.vLine(y + 1, y + h - 2, x + w - 1, V);
        c.putText(x + Math.max(1, (w - label.length()) / 2), y + 1, label);
    }

    private static SequenceDiagram parseSequence(String text) {
        List<String> lines = splitLines(text);
        SequenceDiagram diagram = new SequenceDiagram();
        Set<String> actorIds = new HashSet<>();
        List<Map<String, Object>> blockStack = new ArrayList<>();

        Pattern participantRe = Pattern.compile("^(participant|actor)\\s+(\\S+)(?:\\s+as\\s+(.+))?$", Pattern.CASE_INSENSITIVE);
        Pattern noteRe = Pattern.compile("^Note\\s+(right of|left of|over)\\s+(.+?)\\s*:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
        Pattern blockStartRe = Pattern.compile("^(loop|alt|opt|par|critical)\\s*(.*)$");
        Pattern dividerRe = Pattern.compile("^(else|and)\\s*(.*)$");
        Pattern msgRe = Pattern.compile("^(\\S+?)\\s*(--?>?>|--?[)x]|--?>>|--?>)\\s*([+-]?)(\\S+?)\\s*:\\s*(.+)$");
        Pattern simpleMsgRe = Pattern.compile("^(\\S+?)\\s*(->>|-->>|-\\)|--\\)|-x|--x|->|-->)\\s*([+-]?)(\\S+?)\\s*:\\s*(.+)$");

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher pm = participantRe.matcher(line);
            if (pm.matches()) {
                String type = pm.group(1).toLowerCase();
                String id = pm.group(2);
                String label = pm.group(3) != null ? pm.group(3).trim() : id;
                if (actorIds.add(id)) diagram.actors.add(new SeqActor(id, label, type));
                continue;
            }

            Matcher nm = noteRe.matcher(line);
            if (nm.matches()) {
                String posRaw = nm.group(1).toLowerCase();
                String pos = posRaw.startsWith("right") ? "right" : (posRaw.startsWith("left") ? "left" : "over");
                String[] actorTokens = nm.group(2).split(",");
                List<String> nActors = new ArrayList<>();
                for (String t : actorTokens) {
                    String id = t.trim();
                    if (id.isEmpty()) continue;
                    nActors.add(id);
                    ensureActor(diagram, actorIds, id);
                }
                String txt = nm.group(3).trim().replace("<br/>", "\\n");
                diagram.notes.add(new SeqNote(nActors, txt, pos, diagram.messages.size() - 1));
                continue;
            }

            Matcher bm = blockStartRe.matcher(line);
            if (bm.matches()) {
                Map<String, Object> b = new HashMap<>();
                b.put("type", bm.group(1));
                b.put("label", (bm.group(2) == null ? "" : bm.group(2)).trim());
                b.put("startIndex", diagram.messages.size());
                b.put("dividers", new ArrayList<BlockDivider>());
                blockStack.add(b);
                continue;
            }

            Matcher dm = dividerRe.matcher(line);
            if (dm.matches() && !blockStack.isEmpty()) {
                String label = (dm.group(2) == null ? "" : dm.group(2)).trim();
                @SuppressWarnings("unchecked")
                List<BlockDivider> divs = (List<BlockDivider>) blockStack.get(blockStack.size() - 1).get("dividers");
                divs.add(new BlockDivider(diagram.messages.size(), label));
                continue;
            }

            if ("end".equals(line) && !blockStack.isEmpty()) {
                Map<String, Object> b = blockStack.remove(blockStack.size() - 1);
                Block out = new Block();
                out.type = (String) b.get("type");
                out.label = (String) b.get("label");
                out.startIndex = (Integer) b.get("startIndex");
                out.endIndex = Math.max(diagram.messages.size() - 1, out.startIndex);
                @SuppressWarnings("unchecked")
                List<BlockDivider> divs = (List<BlockDivider>) b.get("dividers");
                out.dividers.addAll(divs);
                diagram.blocks.add(out);
                continue;
            }

            Matcher mm = msgRe.matcher(line);
            if (!mm.matches()) mm = simpleMsgRe.matcher(line);
            if (mm.matches()) {
                String from = mm.group(1);
                String arrow = mm.group(2);
                String actMark = mm.group(3);
                String to = mm.group(4);
                String label = mm.group(5).trim();
                ensureActor(diagram, actorIds, from);
                ensureActor(diagram, actorIds, to);
                boolean dashed = arrow.startsWith("--");
                boolean filled = (arrow.contains(">>") || arrow.contains("x"));
                SeqMessage msg = new SeqMessage(from, to, label, dashed, filled);
                msg.activate = "+".equals(actMark);
                msg.deactivate = "-".equals(actMark);
                diagram.messages.add(msg);
            }
        }
        return diagram;
    }

    private static void ensureActor(SequenceDiagram diagram, Set<String> actorIds, String actorId) {
        if (!actorIds.contains(actorId)) {
            actorIds.add(actorId);
            diagram.actors.add(new SeqActor(actorId, actorId, "participant"));
        }
    }

    private static ParsedMessage parseSequenceMessage(String line) {
        String[] ops = {"-->>", "->>", "--)", "-)", "-->", "->"};
        for (String op : ops) {
            int idx = line.indexOf(op);
            if (idx > 0) {
                String left = line.substring(0, idx).trim();
                String rest = line.substring(idx + op.length()).trim();
                String right;
                String label = "";
                int c = rest.indexOf(':');
                if (c >= 0) {
                    right = rest.substring(0, c).trim();
                    label = rest.substring(c + 1).trim();
                } else {
                    right = rest.trim();
                }
                if (left.isEmpty() || right.isEmpty()) return null;
                boolean dashed = op.contains("--");
                boolean filled = op.contains(">>") || op.contains("-->") || op.contains("->");
                return new ParsedMessage(left, right, label, dashed, filled);
            }
        }
        return null;
    }

    private static String renderClassDiagram(String text, boolean useAscii) {
        ClassDiagram d = parseClass(text);
        if (d.classes.isEmpty()) return "";

        int hGap = 4;
        int vGap = 3;

        Map<String, Box> placed = new LinkedHashMap<>();
        Map<String, List<List<String>>> classSections = new HashMap<>();
        Map<String, Integer> classBoxW = new HashMap<>();
        Map<String, Integer> classBoxH = new HashMap<>();
        for (ClassNode cls : d.classes.values()) {
            List<List<String>> sections = buildClassSections(cls);
            classSections.put(cls.id, sections);
            BoxSize sz = computeMultiBoxSize(sections, 1);
            classBoxW.put(cls.id, sz.w);
            classBoxH.put(cls.id, sz.h);
        }

        Map<String, Set<String>> parents = new HashMap<>();
        Map<String, Set<String>> children = new HashMap<>();
        for (ClassRel rel : d.relationships) {
            boolean hier = "inheritance".equals(rel.type) || "realization".equals(rel.type);
            String parentId = (hier && "to".equals(rel.markerAt)) ? rel.to : rel.from;
            String childId = (hier && "to".equals(rel.markerAt)) ? rel.from : rel.to;
            parents.computeIfAbsent(childId, k -> new LinkedHashSet<>()).add(parentId);
            children.computeIfAbsent(parentId, k -> new LinkedHashSet<>()).add(childId);
        }

        Map<String, Integer> level = new HashMap<>();
        List<String> queue = new ArrayList<>();
        for (ClassNode cls : d.classes.values()) {
            if (!parents.containsKey(cls.id) || parents.get(cls.id).isEmpty()) {
                queue.add(cls.id);
                level.put(cls.id, 0);
            }
        }
        int levelCap = Math.max(d.classes.size() - 1, 0);
        int qi = 0;
        while (qi < queue.size()) {
            String cid = queue.get(qi++);
            Set<String> childSet = children.get(cid);
            if (childSet == null) continue;
            for (String childId : childSet) {
                int newLevel = level.getOrDefault(cid, 0) + 1;
                if (newLevel > levelCap) continue;
                if (!level.containsKey(childId) || level.get(childId) < newLevel) {
                    level.put(childId, newLevel);
                    queue.add(childId);
                }
            }
        }
        for (ClassNode cls : d.classes.values()) level.putIfAbsent(cls.id, 0);

        int maxLevel = 0;
        for (int lv : level.values()) maxLevel = Math.max(maxLevel, lv);
        List<List<String>> levelGroups = new ArrayList<>();
        for (int i = 0; i <= maxLevel; i++) levelGroups.add(new ArrayList<>());
        for (ClassNode cls : d.classes.values()) levelGroups.get(level.get(cls.id)).add(cls.id);

        int currentY = 0;
        for (int lv = 0; lv <= maxLevel; lv++) {
            List<String> group = levelGroups.get(lv);
            if (group.isEmpty()) continue;
            int currentX = 0;
            int maxH = 0;
            for (String cid : group) {
                int w = classBoxW.getOrDefault(cid, 8);
                int h = classBoxH.getOrDefault(cid, 5);
                placed.put(cid, new Box(currentX, currentY, w, h, new ArrayList<>()));
                currentX += w + hGap;
                maxH = Math.max(maxH, h);
            }
            currentY += maxH + vGap;
        }

        int totalW = placed.values().stream().mapToInt(b -> b.x + b.w).max().orElse(80) + 2;
        int totalH = placed.values().stream().mapToInt(b -> b.y + b.h).max().orElse(30) + 2;
        Canvas c = new Canvas(totalW, totalH);

        for (Map.Entry<String, Box> ent : placed.entrySet()) {
            String cid = ent.getKey();
            Box b = ent.getValue();
            drawMultiBoxAt(c, b.x, b.y, classSections.get(cid), useAscii, 1);
        }

        List<PendingArrow> pendingMarkers = new ArrayList<>();
        List<PendingLabel> pendingLabels = new ArrayList<>();
        List<int[]> labelSpans = new ArrayList<>();

        for (ClassRel r : d.relationships) {
            Box a = placed.get(r.from);
            Box b = placed.get(r.to);
            if (a == null || b == null) continue;
            int sx = a.x + a.w / 2;
            int sy = a.y + a.h;
            int ex = b.x + b.w / 2;
            int ey = b.y - 1;
            char h = (r.type.equals("dependency") || r.type.equals("realization")) ? (useAscii ? '.' : '╌') : (useAscii ? '-' : '─');
            char v = (r.type.equals("dependency") || r.type.equals("realization")) ? (useAscii ? ':' : '┊') : (useAscii ? '|' : '│');

            int my = (sy + ey) / 2;
            Set<String> skipBoxes = new HashSet<>(Arrays.asList(r.from, r.to));
            if (hSegmentHitsBox(placed, my, sx, ex, skipBoxes)) {
                for (int delta = 1; delta <= totalH; delta++) {
                    boolean moved = false;
                    int[] candidates = {my - delta, my + delta};
                    for (int candidate : candidates) {
                        if (candidate < 0 || candidate >= totalH) continue;
                        if (hSegmentHitsBox(placed, candidate, sx, ex, skipBoxes)) continue;
                        my = candidate;
                        moved = true;
                        break;
                    }
                    if (moved) break;
                }
            }

            // Keep Python behavior exactly: vertical segments are only drawn for increasing ranges.
            for (int y = sy; y <= my; y++) c.put(sx, y, v);
            int step = ex >= sx ? 1 : -1;
            for (int x = sx; x != ex + step; x += step) c.put(x, my, h);
            for (int y = my; y <= ey; y++) c.put(ex, y, v);

            String dir = "up";
            int markerX;
            int markerY;
            if ("from".equals(r.markerAt)) {
                dir = "down";
                markerX = sx;
                markerY = sy - 1;
            } else {
                dir = "up";
                markerX = ex;
                markerY = ey + 1;
            }
            char marker = markerForClassRel(r.type, useAscii, dir);
            pendingMarkers.add(new PendingArrow(markerX, markerY, marker));
            if (r.label != null && !r.label.isBlank()) {
                int tx = Math.max(0, (sx + ex) / 2 - r.label.length() / 2);
                int ty = my - 1;
                int lx1 = tx;
                int lx2 = tx + r.label.length() - 1;
                boolean placedLabel = false;
                int[] dys = {0, -1, 1, -2, 2};
                for (int dy : dys) {
                    int cy = ty + dy;
                    if (cy < 0 || cy >= totalH) continue;
                    boolean overlap = false;
                    for (int[] span : labelSpans) {
                        if (span[0] == cy && !(lx2 < span[1] || lx1 > span[2])) {
                            overlap = true;
                            break;
                        }
                    }
                    if (overlap) continue;
                    pendingLabels.add(new PendingLabel(tx, cy, r.label));
                    labelSpans.add(new int[]{cy, lx1, lx2});
                    placedLabel = true;
                    break;
                }
                if (!placedLabel) pendingLabels.add(new PendingLabel(tx, ty, r.label));
            }
            if (r.fromCardinality != null && !r.fromCardinality.isBlank()) {
                int tx = sx - r.fromCardinality.length() - 1;
                int ty = sy - 1;
                putTextClipped(c, tx, ty, r.fromCardinality, totalW, totalH);
            }
            if (r.toCardinality != null && !r.toCardinality.isBlank()) {
                int tx = ex + 1;
                int ty = ey + 1;
                putTextClipped(c, tx, ty, r.toCardinality, totalW, totalH);
            }
        }

        for (PendingArrow m : pendingMarkers) c.put(m.x, m.y, m.ch);
        for (PendingLabel l : pendingLabels) putTextClipped(c, l.x, l.y, l.text, totalW, totalH);

        return canvasToStringFull(c, totalW, totalH);
    }

    private static boolean hSegmentHitsBox(Map<String, Box> placed, int y, int x1, int x2, Set<String> skip) {
        int a = Math.min(x1, x2);
        int b = Math.max(x1, x2);
        for (Map.Entry<String, Box> e : placed.entrySet()) {
            if (skip.contains(e.getKey())) continue;
            Box box = e.getValue();
            int bx0 = box.x, by0 = box.y, bx1 = box.x + box.w - 1, by1 = box.y + box.h - 1;
            if (by0 <= y && y <= by1 && !(b < bx0 || a > bx1)) return true;
        }
        return false;
    }

    private static void putTextClipped(Canvas c, int x, int y, String s, int maxW, int maxH) {
        if (s == null || s.isEmpty() || y < 0 || y >= maxH) return;
        for (int i = 0; i < s.length(); i++) {
            int xx = x + i;
            if (xx >= 0 && xx < maxW) c.put(xx, y, s.charAt(i));
        }
    }

    private static List<List<String>> buildClassSections(ClassNode cls) {
        List<String> header = new ArrayList<>();
        if (cls.annotation != null && !cls.annotation.isBlank()) header.add("<<" + cls.annotation + ">>");
        header.add(cls.label);
        List<String> attrs = new ArrayList<>();
        for (String a : cls.attributes) if (a != null && !a.trim().isEmpty()) attrs.add(a.trim());
        List<String> methods = new ArrayList<>();
        for (String m : cls.methods) if (m != null && !m.trim().isEmpty()) methods.add(m.trim());
        if (attrs.isEmpty() && methods.isEmpty()) {
            return new ArrayList<>(List.of(header));
        }
        if (methods.isEmpty()) {
            return new ArrayList<>(List.of(header, attrs));
        }
        return new ArrayList<>(List.of(header, attrs, methods));
    }

    private static char markerForClassRel(String type, boolean useAscii, String dir) {
        switch (type) {
            case "inheritance":
            case "realization":
                if ("down".equals(dir)) return useAscii ? '^' : '△';
                if ("up".equals(dir)) return useAscii ? 'v' : '▽';
                if ("left".equals(dir)) return useAscii ? '>' : '◁';
                return useAscii ? '<' : '▷';
            case "composition":
                return useAscii ? '*' : '◆';
            case "aggregation":
                return useAscii ? 'o' : '◇';
            default:
                if ("down".equals(dir)) return useAscii ? 'v' : '▼';
                if ("up".equals(dir)) return useAscii ? '^' : '▲';
                if ("left".equals(dir)) return useAscii ? '<' : '◀';
                return useAscii ? '>' : '▶';
        }
    }

    private static ClassDiagram parseClass(String text) {
        List<String> lines = splitLines(text);
        ClassDiagram d = new ClassDiagram();
        ClassNode current = null;
        int braceDepth = 0;

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (current != null && braceDepth > 0) {
                if ("}".equals(line)) {
                    braceDepth--;
                    if (braceDepth == 0) current = null;
                    continue;
                }
                if (line.startsWith("<<") && line.endsWith(">>")) {
                    String ann = line.substring(2, line.length() - 2).trim();
                    if (!ann.isEmpty()) current.annotation = ann;
                    continue;
                }
                ParsedClassMember member = parseClassMember(line);
                if (member != null) {
                    if (member.isMethod) current.methods.add(member.text);
                    else current.attributes.add(member.text);
                }
                continue;
            }

            Matcher cb = Pattern.compile("^class\\s+(\\S+?)(?:\\s*~(\\w+)~)?\\s*\\{$").matcher(line);
            if (cb.matches()) {
                String id = cb.group(1);
                String generic = cb.group(2);
                current = d.classes.computeIfAbsent(id, k -> new ClassNode(id, id));
                if (generic != null && !generic.isBlank()) current.label = id + "<" + generic + ">";
                braceDepth = 1;
                continue;
            }

            Matcher co = Pattern.compile("^class\\s+(\\S+?)(?:\\s*~(\\w+)~)?\\s*$").matcher(line);
            if (co.matches()) {
                String id = co.group(1);
                String generic = co.group(2);
                ClassNode cls = d.classes.computeIfAbsent(id, k -> new ClassNode(id, id));
                if (generic != null && !generic.isBlank()) cls.label = id + "<" + generic + ">";
                continue;
            }

            Matcher inlineAnnot = Pattern.compile("^class\\s+(\\S+?)\\s*\\{\\s*<<(\\w+)>>\\s*\\}$").matcher(line);
            if (inlineAnnot.matches()) {
                String id = inlineAnnot.group(1);
                ClassNode cls = d.classes.computeIfAbsent(id, k -> new ClassNode(id, id));
                cls.annotation = inlineAnnot.group(2);
                continue;
            }

            Matcher inlineAttr = Pattern.compile("^(\\S+?)\\s*:\\s*(.+)$").matcher(line);
            if (inlineAttr.matches()) {
                String rest = inlineAttr.group(2);
                if (!rest.matches(".*(<\\|--|--|\\*--|o--|-->|\\.\\.>|\\.\\.\\|>).*$")) {
                    ClassNode cls = d.classes.computeIfAbsent(inlineAttr.group(1), k -> new ClassNode(inlineAttr.group(1), inlineAttr.group(1)));
                    ParsedClassMember member = parseClassMember(rest);
                    if (member != null) {
                        if (member.isMethod) cls.methods.add(member.text);
                        else cls.attributes.add(member.text);
                    }
                    continue;
                }
            }

            ClassRel rel = parseClassRelLine(line);
            if (rel != null) {
                d.classes.computeIfAbsent(rel.from, k -> new ClassNode(rel.from, rel.from));
                d.classes.computeIfAbsent(rel.to, k -> new ClassNode(rel.to, rel.to));
                d.relationships.add(rel);
            }
        }

        return d;
    }

    private static ClassRel parseClassRelLine(String line) {
        Pattern p = Pattern.compile("^(\\S+?)\\s+(?:\"([^\"]*?)\"\\s+)?(<\\|--|<\\|\\.\\.|\\*--|o--|-->|--\\*|--o|--|\\.\\.>|\\.\\.\\|>|--)\\s+(?:\"([^\"]*?)\"\\s+)?(\\S+?)(?:\\s*:\\s*(.+))?$");
        Matcher m = p.matcher(line);
        if (!m.matches()) return null;
        String from = m.group(1);
        String fromCard = m.group(2);
        String arrow = m.group(3).trim();
        String toCard = m.group(4);
        String to = m.group(5);
        String label = m.group(6) == null ? null : m.group(6).trim();
        ArrowParsed ap = parseClassArrow(arrow);
        if (ap == null) return null;
        return new ClassRel(from, to, ap.type, ap.markerAt, label, fromCard, toCard);
    }

    private static ArrowParsed parseClassArrow(String arrow) {
        if ("<|--".equals(arrow)) return new ArrowParsed("inheritance", "from");
        if ("<|..".equals(arrow)) return new ArrowParsed("realization", "from");
        if ("*--".equals(arrow)) return new ArrowParsed("composition", "from");
        if ("--*".equals(arrow)) return new ArrowParsed("composition", "to");
        if ("o--".equals(arrow)) return new ArrowParsed("aggregation", "from");
        if ("--o".equals(arrow)) return new ArrowParsed("aggregation", "to");
        if ("-->".equals(arrow)) return new ArrowParsed("association", "to");
        if ("..>".equals(arrow)) return new ArrowParsed("dependency", "to");
        if ("..|>".equals(arrow)) return new ArrowParsed("realization", "to");
        if ("--".equals(arrow)) return new ArrowParsed("association", "to");
        return null;
    }

    private static ParsedClassMember parseClassMember(String line) {
        String trimmed = line.trim();
        while (trimmed.endsWith(";")) trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        if (trimmed.isEmpty()) return null;
        String visibility = "";
        String rest = trimmed;
        if ("+-#~".indexOf(rest.charAt(0)) >= 0) {
            visibility = rest.substring(0, 1);
            rest = rest.substring(1).trim();
        }
        Matcher mm = Pattern.compile("^(.+?)\\(([^)]*)\\)(?:\\s*(.+))?$").matcher(rest);
        if (mm.matches()) {
            String name = mm.group(1).trim();
            String typ = mm.group(3) == null ? "" : mm.group(3).trim();
            boolean isStatic = name.endsWith("$") || rest.contains("$");
            boolean isAbstract = name.endsWith("*") || rest.contains("*");
            name = name.replace("$", "").replace("*", "");
            String text = visibility + name + (typ.isEmpty() ? "" : ": " + typ);
            return new ParsedClassMember(text, true, isStatic, isAbstract);
        }
        String[] parts = rest.split("\\s+");
        String name;
        String typ = "";
        if (parts.length >= 2) {
            name = parts[0];
            typ = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
        } else {
            name = rest;
        }
        if (name.endsWith(":")) name = name.substring(0, name.length() - 1);
        boolean isStatic = name.endsWith("$");
        boolean isAbstract = name.endsWith("*");
        name = name.replace("$", "").replace("*", "");
        String text = visibility + name + (typ.isEmpty() ? "" : ": " + typ);
        return new ParsedClassMember(text, false, isStatic, isAbstract);
    }

    private static String renderErDiagram(String text, boolean useAscii) {
        ErDiagram d = parseEr(text);
        if (d.entities.isEmpty()) return "";

        int perRow = Math.max(2, (int) Math.ceil(Math.sqrt(d.entities.size())));
        int hGap = 6;
        int vGap = 3;

        Map<String, Box> placed = new LinkedHashMap<>();
        Map<String, List<List<String>>> entitySections = new HashMap<>();
        int x = 0, y = 0, col = 0, maxH = 0;
        for (ErEntity e : d.entities.values()) {
            List<List<String>> sections = buildEntitySections(e);
            entitySections.put(e.id, sections);
            BoxSize sz = computeMultiBoxSize(sections, 1);
            int w = sz.w;
            int h = sz.h;
            placed.put(e.id, new Box(x, y, w, h, new ArrayList<>()));
            maxH = Math.max(maxH, h);
            x += w + hGap;
            col++;
            if (col >= perRow) {
                col = 0;
                x = 0;
                y += maxH + vGap;
                maxH = 0;
            }
        }

        int totalW = placed.values().stream().mapToInt(b -> b.x + b.w).max().orElse(80) + 4;
        int totalH = placed.values().stream().mapToInt(b -> b.y + b.h).max().orElse(30) + 2;
        Canvas c = new Canvas(totalW, totalH);

        for (Map.Entry<String, Box> ent : placed.entrySet()) {
            drawMultiBoxAt(c, ent.getValue().x, ent.getValue().y, entitySections.get(ent.getKey()), useAscii, 1);
        }

        char H = useAscii ? '-' : '─';
        char V = useAscii ? '|' : '│';
        char dH = useAscii ? '.' : '╌';
        char dV = useAscii ? ':' : '┊';

        for (ErRel r : d.relationships) {
            Box a = placed.get(r.e1);
            Box b = placed.get(r.e2);
            if (a == null || b == null) continue;

            int ax = a.x + a.w / 2;
            int ay = a.y + a.h / 2;
            int bx = b.x + b.w / 2;
            int by = b.y + b.h / 2;
            boolean sameRow = Math.abs(ay - by) < Math.max(a.h, b.h);

            char hChar = r.identifying ? H : dH;
            char vChar = r.identifying ? V : dV;

            if (sameRow) {
                Box left = ax < bx ? a : b;
                Box right = ax < bx ? b : a;
                String leftCard = ax < bx ? r.c1 : r.c2;
                String rightCard = ax < bx ? r.c2 : r.c1;
                int startX = left.x + left.w;
                int endX = right.x - 1;
                int lineY = left.y + left.h / 2;

                for (int xx = startX; xx <= endX; xx++) c.put(xx, lineY, hChar);

                String leftChars = cardChars(leftCard, useAscii);
                for (int i = 0; i < leftChars.length(); i++) c.put(startX + i, lineY, leftChars.charAt(i));
                String rightChars = cardChars(rightCard, useAscii);
                for (int i = 0; i < rightChars.length(); i++) c.put(endX - rightChars.length() + 1 + i, lineY, rightChars.charAt(i));

                if (r.label != null && !r.label.isBlank()) {
                    int gapMid = (startX + endX) / 2;
                    int labelStart = Math.max(startX, gapMid - r.label.length() / 2);
                    int labelY = lineY - 1;
                    if (labelY >= 0) {
                        for (int i = 0; i < r.label.length(); i++) {
                            int lx = labelStart + i;
                            if (lx >= startX && lx <= endX) c.put(lx, labelY, r.label.charAt(i));
                        }
                    }
                }
            } else {
                Box upper = ay < by ? a : b;
                Box lower = ay < by ? b : a;
                String upperCard = ay < by ? r.c1 : r.c2;
                String lowerCard = ay < by ? r.c2 : r.c1;
                int startY = upper.y + upper.h;
                int endY = lower.y - 1;
                int lineX = upper.x + upper.w / 2;

                for (int yy = startY; yy <= endY; yy++) c.put(lineX, yy, vChar);

                String upChars = cardChars(upperCard, useAscii);
                if (useAscii) {
                    for (int i = 0; i < upChars.length(); i++) c.put(lineX + i, startY, upChars.charAt(i));
                } else {
                    if (upChars.length() == 1) c.put(lineX, startY, upChars.charAt(0));
                    else {
                        c.put(lineX - 1, startY, upChars.charAt(0));
                        c.put(lineX, startY, upChars.charAt(1));
                    }
                }
                String lowChars = cardChars(lowerCard, useAscii);
                if (useAscii) {
                    for (int i = 0; i < lowChars.length(); i++) c.put(lineX + i, endY, lowChars.charAt(i));
                } else {
                    if (lowChars.length() == 1) c.put(lineX, endY, lowChars.charAt(0));
                    else {
                        c.put(lineX - 1, endY, lowChars.charAt(0));
                        c.put(lineX, endY, lowChars.charAt(1));
                    }
                }
                if (r.label != null && !r.label.isBlank()) {
                    int labelY = (startY + endY) / 2;
                    int labelX = lineX + 2;
                    for (int i = 0; i < r.label.length(); i++) {
                        int lx = labelX + i;
                        if (lx < totalW && labelY < totalH) c.put(lx, labelY, r.label.charAt(i));
                    }
                }
            }
        }

        return canvasToStringFull(c, totalW, totalH);
    }

    private static List<List<String>> buildEntitySections(ErEntity entity) {
        List<List<String>> out = new ArrayList<>();
        out.add(List.of(entity.label));
        List<String> attrs = new ArrayList<>();
        for (String a : entity.attributes) if (a != null && !a.trim().isEmpty()) attrs.add(a);
        if (!attrs.isEmpty()) out.add(attrs);
        return out;
    }

    private static BoxSize computeMultiBoxSize(List<List<String>> sections, int padding) {
        int maxText = 0;
        for (List<String> section : sections) {
            for (String line : section) maxText = Math.max(maxText, line.stripTrailing().length());
        }
        int boxW = maxText + 2 * padding + 2;
        int totalLines = 0;
        for (List<String> section : sections) totalLines += Math.max(section.size(), 1);
        int boxH = totalLines + (sections.size() - 1) + 2;
        return new BoxSize(boxW, boxH);
    }

    private static void drawMultiBoxAt(Canvas canvas, int ox, int oy, List<List<String>> sections, boolean useAscii, int padding) {
        BoxSize sz = computeMultiBoxSize(sections, padding);
        int boxW = sz.w;
        int boxH = sz.h;

        char hline = useAscii ? '-' : '─';
        char vline = useAscii ? '|' : '│';
        char tl = useAscii ? '+' : '┌';
        char tr = useAscii ? '+' : '┐';
        char bl = useAscii ? '+' : '└';
        char br = useAscii ? '+' : '┘';
        char dl = useAscii ? '+' : '├';
        char dr = useAscii ? '+' : '┤';

        canvas.put(ox, oy, tl);
        for (int x = 1; x < boxW - 1; x++) canvas.put(ox + x, oy, hline);
        canvas.put(ox + boxW - 1, oy, tr);

        canvas.put(ox, oy + boxH - 1, bl);
        for (int x = 1; x < boxW - 1; x++) canvas.put(ox + x, oy + boxH - 1, hline);
        canvas.put(ox + boxW - 1, oy + boxH - 1, br);

        for (int y = 1; y < boxH - 1; y++) {
            canvas.put(ox, oy + y, vline);
            canvas.put(ox + boxW - 1, oy + y, vline);
        }

        int row = 1;
        for (int s = 0; s < sections.size(); s++) {
            List<String> lines = sections.get(s).isEmpty() ? List.of("") : sections.get(s);
            for (String line : lines) {
                canvas.putText(ox + 1 + padding, oy + row, line);
                row++;
            }
            if (s < sections.size() - 1) {
                canvas.put(ox, oy + row, dl);
                for (int x = 1; x < boxW - 1; x++) canvas.put(ox + x, oy + row, hline);
                canvas.put(ox + boxW - 1, oy + row, dr);
                row++;
            }
        }
    }

    private static String cardChars(String card, boolean useAscii) {
        if (useAscii) {
            switch (card) {
                case "one":
                    return "||";
                case "zero-one":
                    return "o|";
                case "many":
                    return "}|";
                case "zero-many":
                    return "o{";
                default:
                    return "||";
            }
        }
        switch (card) {
            case "one":
                return "║";
            case "zero-one":
                return "o║";
            case "many":
                return "╟";
            case "zero-many":
                return "o╟";
            default:
                return "║";
        }
    }

    private static ErDiagram parseEr(String text) {
        List<String> lines = splitLines(text);
        ErDiagram d = new ErDiagram();
        ErEntity current = null;

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.equals("}")) {
                current = null;
                continue;
            }
            Matcher em = ER_ENTITY.matcher(line);
            if (em.matches()) {
                String id = em.group(1);
                current = d.entities.computeIfAbsent(id, k -> new ErEntity(id, id));
                continue;
            }
            if (current != null && !line.contains("--") && !line.contains("..")) {
                String attr = parseErAttribute(line.trim());
                if (attr != null) current.attributes.add(attr);
                continue;
            }

            ErRel rel = parseErRel(line);
            if (rel != null) {
                d.entities.computeIfAbsent(rel.e1, k -> new ErEntity(rel.e1, rel.e1));
                d.entities.computeIfAbsent(rel.e2, k -> new ErEntity(rel.e2, rel.e2));
                d.relationships.add(rel);
            }
        }

        return d;
    }

    private static String parseErAttribute(String line) {
        Matcher m = Pattern.compile("^(\\S+)\\s+(\\S+)(?:\\s+(.+))?$").matcher(line);
        if (!m.matches()) return null;
        String typ = m.group(1);
        String name = m.group(2);
        String rest = m.group(3) == null ? "" : m.group(3).trim();
        String restNoComment = rest.replaceAll("\"[^\"]*\"", "").trim();
        List<String> keys = new ArrayList<>();
        if (!restNoComment.isEmpty()) {
            for (String p : restNoComment.split("\\s+")) {
                String u = p.toUpperCase();
                if ("PK".equals(u) || "FK".equals(u) || "UK".equals(u)) keys.add(u);
            }
        }
        String keyStr = keys.isEmpty() ? "   " : String.join(",", keys) + " ";
        return keyStr + typ + " " + name;
    }

    private static ErRel parseErRel(String line) {
        Matcher m = Pattern.compile("^(\\S+)\\s+([|o}{]+(?:--|\\.\\.)[|o}{]+)\\s+(\\S+)\\s*:\\s*(.+)$").matcher(line);
        if (!m.matches()) return null;
        String e1 = m.group(1);
        String cardStr = m.group(2);
        String e2 = m.group(3);
        String label = m.group(4).trim();

        Matcher lm = Pattern.compile("^([|o}{]+)(--|\\.\\.?)([|o}{]+)$").matcher(cardStr);
        if (!lm.matches()) return null;
        String leftStr = lm.group(1);
        String lineStyle = lm.group(2);
        String rightStr = lm.group(3);
        String c1 = parseCardinality(leftStr);
        String c2 = parseCardinality(rightStr);
        if (c1 == null || c2 == null) return null;
        boolean identifying = "--".equals(lineStyle);
        return new ErRel(e1, e2, c1, c2, label, identifying);
    }

    private static String parseCardinality(String s) {
        char[] arr = s.toCharArray();
        Arrays.sort(arr);
        String sorted = new String(arr);
        if ("||".equals(sorted)) return "one";
        if ("o|".equals(sorted)) return "zero-one";
        if ("|}".equals(sorted) || "{|".equals(sorted)) return "many";
        if ("{o".equals(sorted) || "o{".equals(sorted)) return "zero-many";
        return null;
    }

    private static String renderFlowParity(FlowGraph source, boolean useAscii, int paddingX, int paddingY, int boxPad) {
        FlowAsciiGraph g = toFlowAsciiGraph(source, useAscii, paddingX, paddingY, boxPad);
        createFlowMapping(g);
        drawFlowGraph(g);
        if ("BT".equals(source.direction)) {
            flipCanvasVertically(g.canvas, g.canvasMaxX, g.canvasMaxY);
        }
        return canvasToStringFull(g.canvas, g.canvasMaxX, g.canvasMaxY);
    }

    private static FlowAsciiGraph toFlowAsciiGraph(FlowGraph source, boolean useAscii, int paddingX, int paddingY, int boxPad) {
        List<FNode> nodes = new ArrayList<>();
        Map<String, FNode> nodeMap = new LinkedHashMap<>();
        int idx = 0;
        for (FlowNode n : source.nodes.values()) {
            FNode an = new FNode(n.id, n.label == null ? "" : n.label, idx++);
            nodeMap.put(n.id, an);
            nodes.add(an);
        }

        List<FEdge> edges = new ArrayList<>();
        for (FlowEdge e : source.edges) {
            FNode from = nodeMap.get(e.from);
            FNode to = nodeMap.get(e.to);
            if (from == null || to == null) continue;
            edges.add(new FEdge(from, to, e.label == null ? "" : e.label));
        }

        List<FSubgraph> subgraphs = new ArrayList<>();
        Map<Subgraph, FSubgraph> sgMap = new HashMap<>();
        for (Subgraph sg : source.subgraphs) {
            FSubgraph asg = sgMap.computeIfAbsent(sg, k -> new FSubgraph(k.label == null ? "" : k.label, new ArrayList<>(), null, new ArrayList<>(), k.direction));
            if (!subgraphs.contains(asg)) subgraphs.add(asg);
            for (String nid : sg.nodeIds) {
                FNode node = nodeMap.get(nid);
                if (node != null && !asg.nodes.contains(node)) asg.nodes.add(node);
            }
            if (sg.parent == null) {
                // root subgraph already added above
            } else {
                FSubgraph parent = sgMap.computeIfAbsent(sg.parent, k -> new FSubgraph(k.label == null ? "" : k.label, new ArrayList<>(), null, new ArrayList<>(), k.direction));
                asg.parent = parent;
                if (!parent.children.contains(asg)) parent.children.add(asg);
                if (!subgraphs.contains(parent)) subgraphs.add(parent);
            }
        }

        for (FSubgraph sg : sgMap.values()) {
            for (FSubgraph child : sg.children) {
                for (FNode node : child.nodes) {
                    if (!sg.nodes.contains(node)) sg.nodes.add(node);
                }
            }
        }

        Map<String, FSubgraph> nodeOwner = new HashMap<>();
        for (Subgraph sg : source.subgraphs) {
            if (sg.parent == null) claimSubgraphNodes(sg, sgMap, nodeOwner);
        }
        for (FSubgraph sg : sgMap.values()) {
            List<FNode> filtered = new ArrayList<>();
            for (FNode node : sg.nodes) {
                FSubgraph owner = nodeOwner.get(node.name);
                if (owner == null || isAncestorOrSelfSubgraph(sg, owner)) filtered.add(node);
            }
            sg.nodes.clear();
            sg.nodes.addAll(filtered);
        }

        String graphDir = ("LR".equals(source.direction) || "RL".equals(source.direction)) ? "LR" : "TD";
        FlowConfig cfg = new FlowConfig(useAscii, paddingX, paddingY, boxPad, graphDir);
        FlowAsciiGraph out = new FlowAsciiGraph(nodes, edges, new Canvas(1, 1), new HashSet<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), subgraphs, cfg);
        for (FNode node : nodes) {
            out.nodeByName.put(node.name, node);
        }
        return out;
    }

    private static void claimSubgraphNodes(Subgraph mermaidSg, Map<Subgraph, FSubgraph> sgMap, Map<String, FSubgraph> nodeOwner) {
        FSubgraph asciiSg = sgMap.get(mermaidSg);
        if (asciiSg == null) return;
        for (Subgraph child : mermaidSg.children) claimSubgraphNodes(child, sgMap, nodeOwner);
        for (String nodeId : mermaidSg.nodeIds) {
            if (!nodeOwner.containsKey(nodeId)) nodeOwner.put(nodeId, asciiSg);
        }
    }

    private static boolean isAncestorOrSelfSubgraph(FSubgraph candidate, FSubgraph target) {
        FSubgraph cur = target;
        while (cur != null) {
            if (cur == candidate) return true;
            cur = cur.parent;
        }
        return false;
    }

    private static void createFlowMapping(FlowAsciiGraph graph) {
        String dirn = graph.config.graphDirection;
        int[] highest = new int[1024];
        highest[0] = 4;
        Set<String> found = new HashSet<>();
        List<FNode> rootNodes = new ArrayList<>();

        for (FNode node : graph.nodes) {
            if (isPseudoStateNode(node)) continue;
            if (!found.contains(node.name)) rootNodes.add(node);
            found.add(node.name);
            for (FNode child : getFlowChildren(graph, node)) {
                if (!isPseudoStateNode(child)) found.add(child.name);
            }
        }

        boolean hasExternalRoots = false;
        boolean hasSubgraphRootsWithEdges = false;
        for (FNode node : rootNodes) {
            if (isNodeInAnySubgraph(graph, node)) {
                if (!getFlowChildren(graph, node).isEmpty()) hasSubgraphRootsWithEdges = true;
            } else {
                hasExternalRoots = true;
            }
        }
        boolean shouldSeparate = hasExternalRoots && hasSubgraphRootsWithEdges;

        List<FNode> externalRoots = new ArrayList<>();
        List<FNode> subgraphRoots = new ArrayList<>();
        for (FNode n : rootNodes) {
            if (shouldSeparate && isNodeInAnySubgraph(graph, n)) subgraphRoots.add(n);
            else externalRoots.add(n);
        }

        for (FNode node : externalRoots) {
            FGridCoord req = "LR".equals(dirn) ? new FGridCoord(0, highest[0]) : new FGridCoord(highest[0], 4);
            reserveSpotInGrid(graph, node, req);
            highest[0] += 4;
        }

        if (shouldSeparate && !subgraphRoots.isEmpty()) {
            int subgraphLevel = "LR".equals(dirn) ? 4 : 10;
            for (FNode node : subgraphRoots) {
                FGridCoord req = "LR".equals(dirn) ? new FGridCoord(subgraphLevel, highest[subgraphLevel]) : new FGridCoord(highest[subgraphLevel], subgraphLevel);
                reserveSpotInGrid(graph, node, req);
                highest[subgraphLevel] += 4;
            }
        }

        for (int iter = 0; iter < graph.nodes.size() + 2; iter++) {
            boolean changed = false;
            for (FNode node : graph.nodes) {
                if (node.gridCoord == null) continue;
                for (FNode child : getFlowChildren(graph, node)) {
                    if (child.gridCoord != null) continue;
                    String effectiveDir = effectiveDirForNodes(graph, node, child, dirn);
                    int childLevel = "LR".equals(effectiveDir) ? node.gridCoord.x + 4 : node.gridCoord.y + 4;
                    if (childLevel >= highest.length) childLevel = highest.length - 1;
                    int basePosition = "LR".equals(effectiveDir) ? node.gridCoord.y : node.gridCoord.x;
                    int high = Math.max(highest[childLevel], basePosition);
                    FGridCoord req = "LR".equals(effectiveDir) ? new FGridCoord(childLevel, high) : new FGridCoord(high, childLevel);
                    reserveSpotInGrid(graph, child, req);
                    highest[childLevel] = high + 4;
                    changed = true;
                }
            }
            if (!changed) break;
        }

        for (int iter = 0; iter < graph.nodes.size() + 2; iter++) {
            boolean changed = false;
            for (FNode node : graph.nodes) {
                if (node.gridCoord != null || !isPseudoStateNode(node)) continue;
                List<FNode> outgoing = new ArrayList<>();
                List<FNode> incoming = new ArrayList<>();
                for (FEdge e : graph.edges) {
                    if (e.from == node && e.to.gridCoord != null) outgoing.add(e.to);
                    if (e.to == node && e.from.gridCoord != null) incoming.add(e.from);
                }
                FNode anchor = !outgoing.isEmpty() ? outgoing.get(0) : (!incoming.isEmpty() ? incoming.get(0) : null);
                if (anchor == null) continue;

                String effectiveDir = effectiveDirForNodes(graph, node, anchor, dirn);
                FGridCoord req;
                if (node.name.startsWith("_start") && !outgoing.isEmpty()) {
                    req = "LR".equals(effectiveDir)
                        ? new FGridCoord(Math.max(0, anchor.gridCoord.x - 2), anchor.gridCoord.y)
                        : new FGridCoord(anchor.gridCoord.x, Math.max(0, anchor.gridCoord.y - 2));
                } else if (node.name.startsWith("_end") && !incoming.isEmpty()) {
                    req = "LR".equals(effectiveDir)
                        ? new FGridCoord(anchor.gridCoord.x + 2, anchor.gridCoord.y)
                        : new FGridCoord(anchor.gridCoord.x, anchor.gridCoord.y + 2);
                } else {
                    req = "LR".equals(effectiveDir)
                        ? new FGridCoord(Math.max(0, anchor.gridCoord.x - 2), anchor.gridCoord.y)
                        : new FGridCoord(anchor.gridCoord.x, Math.max(0, anchor.gridCoord.y - 2));
                }
                reserveSpotInGrid(graph, node, req);
                changed = true;
            }
            if (!changed) break;
        }

        for (FNode node : graph.nodes) {
            if (node.gridCoord != null) continue;
            FGridCoord req = "LR".equals(dirn) ? new FGridCoord(0, highest[0]) : new FGridCoord(highest[0], 4);
            reserveSpotInGrid(graph, node, req);
            highest[0] += 4;
        }

        for (FNode node : graph.nodes) setFlowColumnWidth(graph, node);
        for (FEdge edge : graph.edges) determineFlowPath(graph, edge);
        for (int i = 0; i < 2; i++) {
            for (FEdge edge : graph.edges) determineFlowPath(graph, edge);
        }
        for (FEdge edge : graph.edges) {
            increaseGridForPath(graph, edge.path);
            determineFlowLabelLine(graph, edge);
        }

        for (FNode node : graph.nodes) {
            node.drawingCoord = gridToDrawingCoord(graph, node.gridCoord, null);
            FCanvasSize drawSize = drawFlowBox(graph, node, false);
            node.drawW = drawSize.w;
            node.drawH = drawSize.h;
        }

        setFlowCanvasSizeToGrid(graph);
        calculateSubgraphBoundingBoxes(graph);
        offsetDrawingForSubgraphs(graph);
        int reqX = graph.canvasMaxX;
        int reqY = graph.canvasMaxY;
        for (FNode n : graph.nodes) {
            if (n.drawingCoord == null) continue;
            reqX = Math.max(reqX, n.drawingCoord.x + n.drawW);
            reqY = Math.max(reqY, n.drawingCoord.y + n.drawH);
        }
        for (FSubgraph sg : graph.subgraphs) {
            reqX = Math.max(reqX, sg.maxX);
            reqY = Math.max(reqY, sg.maxY);
        }
        graph.canvasMaxX = reqX;
        graph.canvasMaxY = reqY;
        graph.canvas.ensure(graph.canvasMaxX, graph.canvasMaxY);
    }

    private static boolean isPseudoStateNode(FNode node) {
        return (node.name.startsWith("_start") || node.name.startsWith("_end")) && node.displayLabel.isEmpty();
    }

    private static String effectiveDirForNodes(FlowAsciiGraph graph, FNode a, FNode b, String dirn) {
        FSubgraph aSg = getNodeSubgraph(graph, a);
        FSubgraph bSg = getNodeSubgraph(graph, b);
        if (aSg != null && bSg != null && aSg == bSg && aSg.direction != null) {
            return ("LR".equals(aSg.direction) || "RL".equals(aSg.direction)) ? "LR" : "TD";
        }
        return dirn;
    }

    private static void setFlowCanvasSizeToGrid(FlowAsciiGraph graph) {
        int maxX = 0;
        int maxY = 0;
        for (int w : graph.columnWidth.values()) maxX += w;
        for (int h : graph.rowHeight.values()) maxY += h;
        graph.canvasMaxX = maxX;
        graph.canvasMaxY = maxY;
        graph.canvas.ensure(maxX, maxY);
    }

    private static FGridCoord reserveSpotInGrid(FlowAsciiGraph graph, FNode node, FGridCoord requested) {
        int[][] footprint = isPseudoStateNode(node) ? new int[][]{{0, 0}} : buildFootprint3x3();
        if (!canPlace(graph, requested, footprint)) {
            if ("LR".equals(graph.config.graphDirection)) {
                return reserveSpotInGrid(graph, node, new FGridCoord(requested.x, requested.y + 4));
            }
            return reserveSpotInGrid(graph, node, new FGridCoord(requested.x + 4, requested.y));
        }
        for (int[] p : footprint) {
            long id = pack(requested.x + p[0], requested.y + p[1]);
            graph.grid.add(id);
            graph.gridOwner.put(id, node);
        }
        node.gridCoord = requested;
        return requested;
    }

    private static boolean canPlace(FlowAsciiGraph graph, FGridCoord at, int[][] footprint) {
        for (int[] p : footprint) {
            if (graph.grid.contains(pack(at.x + p[0], at.y + p[1]))) return false;
        }
        return true;
    }

    private static int[][] buildFootprint3x3() {
        int[][] fp = new int[9][2];
        int i = 0;
        for (int dx = 0; dx < 3; dx++) {
            for (int dy = 0; dy < 3; dy++) {
                fp[i][0] = dx;
                fp[i][1] = dy;
                i++;
            }
        }
        return fp;
    }

    private static void setFlowColumnWidth(FlowAsciiGraph graph, FNode node) {
        if (node.gridCoord == null) return;
        int gcx = node.gridCoord.x;
        int gcy = node.gridCoord.y;
        int padding = graph.config.boxBorderPadding;
        int[] cols = {1, 2 * padding + node.displayLabel.length(), 1};
        int[] rows = {1, 1 + 2 * padding, 1};

        for (int i = 0; i < cols.length; i++) {
            int x = gcx + i;
            graph.columnWidth.put(x, Math.max(graph.columnWidth.getOrDefault(x, 0), cols[i]));
        }
        for (int i = 0; i < rows.length; i++) {
            int y = gcy + i;
            graph.rowHeight.put(y, Math.max(graph.rowHeight.getOrDefault(y, 0), rows[i]));
        }

        if (gcx > 0) graph.columnWidth.put(gcx - 1, Math.max(graph.columnWidth.getOrDefault(gcx - 1, 0), graph.config.paddingX));
        if (gcy > 0) {
            int basePad = graph.config.paddingY;
            if (hasIncomingEdgeFromOutsideSubgraph(graph, node)) basePad += 4;
            graph.rowHeight.put(gcy - 1, Math.max(graph.rowHeight.getOrDefault(gcy - 1, 0), basePad));
        }
    }

    private static void increaseGridForPath(FlowAsciiGraph graph, List<FGridCoord> path) {
        int pathPadX = Math.max(1, (graph.config.paddingX + 1) / 3);
        int pathPadY = Math.max(1, graph.config.paddingY / 3);
        for (FGridCoord c : path) {
            graph.columnWidth.putIfAbsent(c.x, pathPadX);
            graph.rowHeight.putIfAbsent(c.y, pathPadY);
        }
    }

    private static boolean hasIncomingEdgeFromOutsideSubgraph(FlowAsciiGraph graph, FNode node) {
        FSubgraph nodeSg = getNodeSubgraph(graph, node);
        if (nodeSg == null || node.gridCoord == null) return false;
        boolean hasExternal = false;
        for (FEdge edge : graph.edges) {
            if (edge.to == node) {
                FSubgraph srcSg = getNodeSubgraph(graph, edge.from);
                if (srcSg != nodeSg) {
                    hasExternal = true;
                    break;
                }
            }
        }
        if (!hasExternal) return false;
        for (FNode other : nodeSg.nodes) {
            if (other == node || other.gridCoord == null) continue;
            boolean otherHasExternal = false;
            for (FEdge edge : graph.edges) {
                if (edge.to == other) {
                    FSubgraph srcSg = getNodeSubgraph(graph, edge.from);
                    if (srcSg != nodeSg) {
                        otherHasExternal = true;
                        break;
                    }
                }
            }
            if (otherHasExternal && other.gridCoord.y < node.gridCoord.y) return false;
        }
        return true;
    }

    private static boolean isNodeInAnySubgraph(FlowAsciiGraph graph, FNode node) {
        for (FSubgraph sg : graph.subgraphs) {
            if (sg.nodes.contains(node)) return true;
        }
        return false;
    }

    private static FSubgraph getNodeSubgraph(FlowAsciiGraph graph, FNode node) {
        FSubgraph owner = null;
        int bestDepth = -1;
        for (FSubgraph sg : graph.subgraphs) {
            if (!sg.nodes.contains(node)) continue;
            int d = subgraphDepth(sg);
            if (d > bestDepth) {
                bestDepth = d;
                owner = sg;
            }
        }
        return owner;
    }

    private static int subgraphDepth(FSubgraph sg) {
        int d = 0;
        FSubgraph cur = sg.parent;
        while (cur != null) {
            d++;
            cur = cur.parent;
        }
        return d;
    }

    private static List<FNode> getFlowChildren(FlowAsciiGraph graph, FNode node) {
        List<FNode> out = new ArrayList<>();
        for (FEdge e : getFlowEdgesFromNode(graph, node)) out.add(e.to);
        return out;
    }

    private static List<FEdge> getFlowEdgesFromNode(FlowAsciiGraph graph, FNode node) {
        List<FEdge> out = new ArrayList<>();
        for (FEdge e : graph.edges) if (e.from == node) out.add(e);
        return out;
    }

    private static void determineFlowPath(FlowAsciiGraph graph, FEdge edge) {
        FDirSet dirs = determineStartAndEndDir(edge, graph.config.graphDirection);
        boolean fromPseudo = isPseudoStateNode(edge.from);
        boolean toPseudo = isPseudoStateNode(edge.to);

        List<FDir> startDirs = fanoutStartDirs(graph, edge, dirs.prefDir, dirs.altDir, fromPseudo);
        List<FDir> endDirs = faninEndDirs(graph, edge, dirs.prefOpp, dirs.altOpp, toPseudo);

        List<PathCandidate> candidates = new ArrayList<>();
        List<PathCandidate> fallbackCandidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (FDir sdir : startDirs) {
            for (FDir edir : endDirs) {
                FGridCoord from = new FGridCoord(edge.from.gridCoord.x + sdir.x, edge.from.gridCoord.y + sdir.y);
                FGridCoord to = new FGridCoord(edge.to.gridCoord.x + edir.x, edge.to.gridCoord.y + edir.y);
                List<FGridCoord> path = getFlowPath(graph.grid, from, to);
                if (path == null) continue;
                List<FGridCoord> merged = mergeFlowPath(path);
                String key = sdir.x + ":" + sdir.y + "|" + edir.x + ":" + edir.y + "|" + flowPathKey(merged);
                if (!seen.add(key)) continue;
                int penalty = overlapPenalty(graph, edge, merged, sdir);
                PathCandidate c = new PathCandidate(penalty, merged.size(), bendCount(merged), sdir, edir, merged);
                if (merged.size() >= 2) candidates.add(c);
                else fallbackCandidates.add(c);
            }
        }

        if (candidates.isEmpty()) {
            if (!fallbackCandidates.isEmpty()) {
                fallbackCandidates.sort(Comparator.comparingInt((PathCandidate c) -> c.penalty).thenComparingInt(c -> c.length));
                PathCandidate best = fallbackCandidates.get(0);
                List<FGridCoord> finalPath = best.path;
                if (finalPath.size() == 1) {
                    FGridCoord p0 = finalPath.get(0);
                    List<FDir> doglegDirs = uniqueDirs(Arrays.asList(best.startDir, best.endDir, F_DOWN, F_RIGHT, F_LEFT, F_UP));
                    for (FDir d : doglegDirs) {
                        FGridCoord n = new FGridCoord(p0.x + d.x, p0.y + d.y);
                        if (n.x < 0 || n.y < 0) continue;
                        if (isFreeInGrid(graph.grid, n)) {
                            finalPath = new ArrayList<>();
                            finalPath.add(p0);
                            finalPath.add(n);
                            finalPath.add(p0);
                            break;
                        }
                    }
                }
                edge.startDir = best.startDir;
                edge.endDir = best.endDir;
                edge.path = finalPath;
                return;
            }
            edge.startDir = dirs.altDir;
            edge.endDir = dirs.altOpp;
            edge.path = new ArrayList<>();
            return;
        }

        candidates.sort(
            Comparator.comparingInt((PathCandidate c) -> c.penalty)
                .thenComparingInt(c -> c.bends)
                .thenComparingInt(c -> c.length)
        );
        PathCandidate best = candidates.get(0);
        edge.startDir = best.startDir;
        edge.endDir = best.endDir;
        edge.path = best.path;
    }

    private static List<FDir> fanoutStartDirs(FlowAsciiGraph graph, FEdge edge, FDir prefDir, FDir altDir, boolean fromPseudo) {
        List<FEdge> outgoing = new ArrayList<>();
        for (FEdge e : graph.edges) if (e.from == edge.from && e.to.gridCoord != null) outgoing.add(e);
        if (fromPseudo || outgoing.size() <= 1) {
            return uniqueDirs(Arrays.asList(prefDir, altDir, F_DOWN, F_RIGHT, F_LEFT, F_UP));
        }

        if ("TD".equals(graph.config.graphDirection)) {
            outgoing.sort(Comparator.comparingInt((FEdge e) -> e.to.gridCoord.x).thenComparingInt(e -> e.to.gridCoord.y));
            int idx = outgoing.indexOf(edge);
            List<FDir> fanout = outgoing.size() == 2 ? Arrays.asList(F_DOWN, F_RIGHT) : Arrays.asList(F_DOWN, F_LEFT, F_RIGHT);
            int pick = Math.min(Math.max(idx, 0), fanout.size() - 1);
            FDir primary = fanout.get(pick);
            return uniqueDirs(Arrays.asList(primary, prefDir, altDir, F_DOWN, F_LEFT, F_RIGHT, F_UP));
        }

        outgoing.sort(Comparator.comparingInt((FEdge e) -> e.to.gridCoord.y).thenComparingInt(e -> e.to.gridCoord.x));
        int idx = outgoing.indexOf(edge);
        List<FDir> fanout = outgoing.size() == 2 ? Arrays.asList(F_UP, F_DOWN) : Arrays.asList(F_UP, F_RIGHT, F_DOWN);
        int pick = Math.min(Math.max(idx, 0), fanout.size() - 1);
        FDir primary = fanout.get(pick);
        return uniqueDirs(Arrays.asList(primary, prefDir, altDir, F_RIGHT, F_UP, F_DOWN, F_LEFT));
    }

    private static List<FDir> faninEndDirs(FlowAsciiGraph graph, FEdge edge, FDir prefOpp, FDir altOpp, boolean toPseudo) {
        List<FEdge> incoming = new ArrayList<>();
        for (FEdge e : graph.edges) if (e.to == edge.to && e.from.gridCoord != null) incoming.add(e);
        if (toPseudo || incoming.size() <= 1) {
            return uniqueDirs(Arrays.asList(prefOpp, altOpp, F_UP, F_LEFT, F_RIGHT, F_DOWN));
        }

        if ("TD".equals(graph.config.graphDirection)) {
            incoming.sort(Comparator.comparingInt((FEdge e) -> e.from.gridCoord.x).thenComparingInt(e -> e.from.gridCoord.y));
            int idx = incoming.indexOf(edge);
            List<FDir> fanin = incoming.size() == 2 ? Arrays.asList(F_LEFT, F_RIGHT) : Arrays.asList(F_LEFT, F_UP, F_RIGHT);
            int pick = Math.min(Math.max(idx, 0), fanin.size() - 1);
            FDir primary = fanin.get(pick);
            return uniqueDirs(Arrays.asList(primary, prefOpp, altOpp, F_UP, F_LEFT, F_RIGHT, F_DOWN));
        }

        incoming.sort(Comparator.comparingInt((FEdge e) -> e.from.gridCoord.y).thenComparingInt(e -> e.from.gridCoord.x));
        int idx = incoming.indexOf(edge);
        List<FDir> fanin = incoming.size() == 2 ? Arrays.asList(F_UP, F_DOWN) : Arrays.asList(F_UP, F_LEFT, F_DOWN);
        int pick = Math.min(Math.max(idx, 0), fanin.size() - 1);
        FDir primary = fanin.get(pick);
        return uniqueDirs(Arrays.asList(primary, prefOpp, altOpp, F_LEFT, F_UP, F_DOWN, F_RIGHT));
    }

    private static List<FDir> uniqueDirs(List<FDir> dirs) {
        List<FDir> out = new ArrayList<>();
        for (FDir d : dirs) {
            boolean exists = false;
            for (FDir e : out) {
                if (dirEq(d, e)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) out.add(d);
        }
        return out;
    }

    private static int overlapPenalty(FlowAsciiGraph graph, FEdge edge, List<FGridCoord> candidate, FDir sdir) {
        Set<Long> me = pathKeys(candidate);
        if (me.isEmpty()) return 0;
        int penalty = 0;

        if ("TD".equals(graph.config.graphDirection)) {
            int dx = edge.to.gridCoord.x - edge.from.gridCoord.x;
            if (dx > 0 && dirEq(sdir, F_LEFT)) penalty += 50;
            else if (dx < 0 && dirEq(sdir, F_RIGHT)) penalty += 50;
            else if (dx == 0 && !dirEq(sdir, F_DOWN)) penalty += 10;
        } else {
            int dy = edge.to.gridCoord.y - edge.from.gridCoord.y;
            if (dy > 0 && dirEq(sdir, F_UP)) penalty += 50;
            else if (dy < 0 && dirEq(sdir, F_DOWN)) penalty += 50;
            else if (dy == 0 && !dirEq(sdir, F_RIGHT)) penalty += 10;
        }

        for (FEdge other : graph.edges) {
            if (other == edge || other.path == null || other.path.isEmpty()) continue;
            Set<Long> inter = new HashSet<>(me);
            inter.retainAll(pathKeys(other.path));
            if (!inter.isEmpty()) penalty += 100 * inter.size();
            if (other.from == edge.from && dirEq(other.startDir, sdir)) penalty += 20;
            if (other.from == edge.from && candidate.size() > 2 && other.path.size() > 2) {
                Set<Long> minear = new HashSet<>();
                Set<Long> otherear = new HashSet<>();
                for (int i = 0; i < Math.min(3, candidate.size()); i++) minear.add(pack(candidate.get(i).x, candidate.get(i).y));
                for (int i = 0; i < Math.min(3, other.path.size()); i++) otherear.add(pack(other.path.get(i).x, other.path.get(i).y));
                minear.retainAll(otherear);
                if (!minear.isEmpty()) penalty += 60 * minear.size();
            }
        }
        return penalty;
    }

    private static Set<Long> pathKeys(List<FGridCoord> path) {
        Set<Long> out = new HashSet<>();
        if (path.size() <= 2) return out;
        for (int i = 1; i < path.size() - 1; i++) out.add(pack(path.get(i).x, path.get(i).y));
        return out;
    }

    private static int bendCount(List<FGridCoord> path) {
        if (path.size() < 3) return 0;
        int bends = 0;
        FDir prev = determineDirection(path.get(0).x, path.get(0).y, path.get(1).x, path.get(1).y);
        for (int i = 2; i < path.size(); i++) {
            FDir cur = determineDirection(path.get(i - 1).x, path.get(i - 1).y, path.get(i).x, path.get(i).y);
            if (!dirEq(cur, prev)) bends++;
            prev = cur;
        }
        return bends;
    }

    private static String flowPathKey(List<FGridCoord> path) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(path.get(i).x).append(':').append(path.get(i).y);
        }
        return sb.toString();
    }

    private static FDirSet determineStartAndEndDir(FEdge edge, String graphDirection) {
        if (edge.from == edge.to) return selfReferenceDirections(graphDirection);
        FDir d = determineDirection(edge.from.gridCoord.x, edge.from.gridCoord.y, edge.to.gridCoord.x, edge.to.gridCoord.y);
        boolean isBackwards = ("LR".equals(graphDirection) && (dirEq(d, F_LEFT) || dirEq(d, F_UPPER_LEFT) || dirEq(d, F_LOWER_LEFT)))
            || ("TD".equals(graphDirection) && (dirEq(d, F_UP) || dirEq(d, F_UPPER_LEFT) || dirEq(d, F_UPPER_RIGHT)));
        FDir prefDir, prefOpp, altDir, altOpp;
        if (dirEq(d, F_LOWER_RIGHT)) {
            if ("LR".equals(graphDirection)) {
                prefDir = F_DOWN; prefOpp = F_LEFT; altDir = F_RIGHT; altOpp = F_UP;
            } else {
                prefDir = F_RIGHT; prefOpp = F_UP; altDir = F_DOWN; altOpp = F_LEFT;
            }
        } else if (dirEq(d, F_UPPER_RIGHT)) {
            if ("LR".equals(graphDirection)) {
                prefDir = F_UP; prefOpp = F_LEFT; altDir = F_RIGHT; altOpp = F_DOWN;
            } else {
                prefDir = F_RIGHT; prefOpp = F_DOWN; altDir = F_UP; altOpp = F_LEFT;
            }
        } else if (dirEq(d, F_LOWER_LEFT)) {
            if ("LR".equals(graphDirection)) {
                prefDir = F_DOWN; prefOpp = F_DOWN; altDir = F_LEFT; altOpp = F_UP;
            } else {
                prefDir = F_LEFT; prefOpp = F_UP; altDir = F_DOWN; altOpp = F_RIGHT;
            }
        } else if (dirEq(d, F_UPPER_LEFT)) {
            if ("LR".equals(graphDirection)) {
                prefDir = F_DOWN; prefOpp = F_DOWN; altDir = F_LEFT; altOpp = F_DOWN;
            } else {
                prefDir = F_RIGHT; prefOpp = F_RIGHT; altDir = F_UP; altOpp = F_RIGHT;
            }
        } else if (isBackwards) {
            if ("LR".equals(graphDirection) && dirEq(d, F_LEFT)) {
                prefDir = F_DOWN; prefOpp = F_DOWN; altDir = F_LEFT; altOpp = F_RIGHT;
            } else if ("TD".equals(graphDirection) && dirEq(d, F_UP)) {
                prefDir = F_RIGHT; prefOpp = F_RIGHT; altDir = F_UP; altOpp = F_DOWN;
            } else {
                prefDir = d; prefOpp = oppositeDir(d); altDir = d; altOpp = oppositeDir(d);
            }
        } else {
            prefDir = d; prefOpp = oppositeDir(d); altDir = d; altOpp = oppositeDir(d);
        }
        return new FDirSet(prefDir, prefOpp, altDir, altOpp);
    }

    private static FDirSet selfReferenceDirections(String graphDirection) {
        if ("LR".equals(graphDirection)) return new FDirSet(F_RIGHT, F_DOWN, F_DOWN, F_RIGHT);
        return new FDirSet(F_DOWN, F_RIGHT, F_RIGHT, F_DOWN);
    }

    private static FDir oppositeDir(FDir d) {
        if (dirEq(d, F_UP)) return F_DOWN;
        if (dirEq(d, F_DOWN)) return F_UP;
        if (dirEq(d, F_LEFT)) return F_RIGHT;
        if (dirEq(d, F_RIGHT)) return F_LEFT;
        if (dirEq(d, F_UPPER_RIGHT)) return F_LOWER_LEFT;
        if (dirEq(d, F_UPPER_LEFT)) return F_LOWER_RIGHT;
        if (dirEq(d, F_LOWER_RIGHT)) return F_UPPER_LEFT;
        if (dirEq(d, F_LOWER_LEFT)) return F_UPPER_RIGHT;
        return F_MIDDLE;
    }

    private static boolean dirEq(FDir a, FDir b) { return a.x == b.x && a.y == b.y; }

    private static FDir determineDirection(int fx, int fy, int tx, int ty) {
        if (fx == tx) return fy < ty ? F_DOWN : F_UP;
        if (fy == ty) return fx < tx ? F_RIGHT : F_LEFT;
        if (fx < tx) return fy < ty ? F_LOWER_RIGHT : F_UPPER_RIGHT;
        return fy < ty ? F_LOWER_LEFT : F_UPPER_LEFT;
    }

    private static List<FGridCoord> getFlowPath(Set<Long> grid, FGridCoord from, FGridCoord to) {
        int dist = Math.abs(from.x - to.x) + Math.abs(from.y - to.y);
        int margin = Math.max(12, dist * 2);
        int minX = Math.max(0, Math.min(from.x, to.x) - margin);
        int maxX = Math.max(from.x, to.x) + margin;
        int minY = Math.max(0, Math.min(from.y, to.y) - margin);
        int maxY = Math.max(from.y, to.y) + margin;
        int maxVisited = 30_000;

        PriorityQueue<FPathQ> pq = new PriorityQueue<>(Comparator.comparingInt((FPathQ a) -> a.priority));
        Map<Long, Integer> cost = new HashMap<>();
        Map<Long, Long> came = new HashMap<>();
        long start = pack(from.x, from.y);
        long goal = pack(to.x, to.y);
        long seq = 0;
        pq.add(new FPathQ(0, seq++, from.x, from.y));
        cost.put(start, 0);
        came.put(start, start);
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        int visited = 0;

        while (!pq.isEmpty()) {
            visited++;
            if (visited > maxVisited) return null;
            FPathQ cur = pq.poll();
            long curId = pack(cur.x, cur.y);
            if (curId == goal) {
                ArrayDeque<FGridCoord> rev = new ArrayDeque<>();
                long at = curId;
                while (true) {
                    int x = (int) (at >> 32);
                    int y = (int) at;
                    rev.addFirst(new FGridCoord(x, y));
                    long prev = came.getOrDefault(at, at);
                    if (at == start) break;
                    at = prev;
                }
                return new ArrayList<>(rev);
            }
            int curCost = cost.getOrDefault(curId, Integer.MAX_VALUE / 4);
            for (int[] d : dirs) {
                int nx = cur.x + d[0];
                int ny = cur.y + d[1];
                if (nx < minX || nx > maxX || ny < minY || ny > maxY) continue;
                long nid = pack(nx, ny);
                if (nid != goal && !isFreeInGrid(grid, nx, ny)) continue;
                int newCost = curCost + 1;
                Integer ex = cost.get(nid);
                if (ex == null || newCost < ex) {
                    cost.put(nid, newCost);
                    int h = heuristic(nx, ny, to.x, to.y);
                    pq.add(new FPathQ(newCost + h, seq++, nx, ny));
                    came.put(nid, curId);
                }
            }
        }
        return null;
    }

    private static boolean isFreeInGrid(Set<Long> grid, int x, int y) {
        if (x < 0 || y < 0) return false;
        return !grid.contains(pack(x, y));
    }

    private static boolean isFreeInGrid(Set<Long> grid, FGridCoord c) {
        return isFreeInGrid(grid, c.x, c.y);
    }

    private static int heuristic(int ax, int ay, int bx, int by) {
        int absX = Math.abs(ax - bx);
        int absY = Math.abs(ay - by);
        if (absX == 0 || absY == 0) return absX + absY;
        return absX + absY + 1;
    }

    private static List<FGridCoord> mergeFlowPath(List<FGridCoord> path) {
        if (path.size() <= 2) return path;
        Set<Integer> remove = new HashSet<>();
        FGridCoord a = path.get(0), b = path.get(1);
        for (int i = 2; i < path.size(); i++) {
            FGridCoord c = path.get(i);
            int pdx = b.x - a.x, pdy = b.y - a.y;
            int dx = c.x - b.x, dy = c.y - b.y;
            if (pdx == dx && pdy == dy) remove.add(i - 1);
            a = b;
            b = c;
        }
        List<FGridCoord> out = new ArrayList<>();
        for (int i = 0; i < path.size(); i++) if (!remove.contains(i)) out.add(path.get(i));
        return out;
    }

    private static void determineFlowLabelLine(FlowAsciiGraph graph, FEdge edge) {
        if (edge.text == null || edge.text.isEmpty() || edge.path.size() < 2) return;
        int labelLen = edge.text.length();
        FGridCoord prev = edge.path.get(0);
        FGridCoord l0 = prev;
        FGridCoord l1 = edge.path.get(1);
        int largest = 0;
        for (int i = 1; i < edge.path.size(); i++) {
            FGridCoord step = edge.path.get(i);
            int width = calculateFlowLineWidth(graph, prev, step);
            if (width >= labelLen) {
                l0 = prev; l1 = step; break;
            } else if (width > largest) {
                largest = width; l0 = prev; l1 = step;
            }
            prev = step;
        }
        int minX = Math.min(l0.x, l1.x);
        int maxX = Math.max(l0.x, l1.x);
        int middleX = minX + (maxX - minX) / 2;
        graph.columnWidth.put(middleX, Math.max(graph.columnWidth.getOrDefault(middleX, 0), labelLen + 2));
        edge.labelLine = Arrays.asList(l0, l1);
    }

    private static int calculateFlowLineWidth(FlowAsciiGraph graph, FGridCoord a, FGridCoord b) {
        int total = 0;
        int start = Math.min(a.x, b.x);
        int end = Math.max(a.x, b.x);
        for (int x = start; x <= end; x++) total += graph.columnWidth.getOrDefault(x, 0);
        return total;
    }

    private static FDrawCoord gridToDrawingCoord(FlowAsciiGraph graph, FGridCoord c, FDir d) {
        FGridCoord target = d == null ? c : new FGridCoord(c.x + d.x, c.y + d.y);
        int x = 0;
        for (int col = 0; col < target.x; col++) x += graph.columnWidth.getOrDefault(col, 0);
        int y = 0;
        for (int row = 0; row < target.y; row++) y += graph.rowHeight.getOrDefault(row, 0);
        int colW = graph.columnWidth.getOrDefault(target.x, 0);
        int rowH = graph.rowHeight.getOrDefault(target.y, 0);
        return new FDrawCoord(x + (colW / 2) + graph.offsetX, y + (rowH / 2) + graph.offsetY);
    }

    private static FCanvasSize drawFlowBox(FlowAsciiGraph graph, FNode node, boolean draw) {
        if (isPseudoStateNode(node)) {
            if (draw && node.drawingCoord != null) {
                graph.canvas.put(node.drawingCoord.x, node.drawingCoord.y, graph.config.useAscii ? '*' : '●');
            }
            return new FCanvasSize(0, 0);
        }
        int gcx = node.gridCoord.x;
        int gcy = node.gridCoord.y;
        int w = graph.columnWidth.getOrDefault(gcx, 0) + graph.columnWidth.getOrDefault(gcx + 1, 0);
        int h = graph.rowHeight.getOrDefault(gcy, 0) + graph.rowHeight.getOrDefault(gcy + 1, 0);
        if (draw) {
            drawRectAt(graph.canvas, node.drawingCoord.x, node.drawingCoord.y, w, h, graph.config.useAscii);
            int textY = node.drawingCoord.y + (h / 2);
            int textX = node.drawingCoord.x + (w / 2) - ((node.displayLabel.length() + 1) / 2) + 1;
            graph.canvas.putText(textX, textY, node.displayLabel);
        }
        return new FCanvasSize(w, h);
    }

    private static void drawRectAt(Canvas c, int x, int y, int w, int h, boolean ascii) {
        char H = ascii ? '-' : '─';
        char V = ascii ? '|' : '│';
        char TL = ascii ? '+' : '┌';
        char TR = ascii ? '+' : '┐';
        char BL = ascii ? '+' : '└';
        char BR = ascii ? '+' : '┘';
        c.put(x, y, TL);
        for (int i = 1; i < w; i++) c.put(x + i, y, H);
        c.put(x + w, y, TR);
        c.put(x, y + h, BL);
        for (int i = 1; i < w; i++) c.put(x + i, y + h, H);
        c.put(x + w, y + h, BR);
        for (int j = 1; j < h; j++) {
            c.put(x, y + j, V);
            c.put(x + w, y + j, V);
        }
    }

    private static void calculateSubgraphBoundingBoxes(FlowAsciiGraph graph) {
        for (FSubgraph sg : graph.subgraphs) calculateSubgraphBoundingBox(graph, sg);
        ensureSubgraphSpacing(graph);
    }

    private static void calculateSubgraphBoundingBox(FlowAsciiGraph graph, FSubgraph sg) {
        if (sg.nodes.isEmpty()) return;
        int minX = 1_000_000, minY = 1_000_000, maxX = -1_000_000, maxY = -1_000_000;
        for (FSubgraph child : sg.children) {
            calculateSubgraphBoundingBox(graph, child);
            if (!child.nodes.isEmpty()) {
                minX = Math.min(minX, child.minX);
                minY = Math.min(minY, child.minY);
                maxX = Math.max(maxX, child.maxX);
                maxY = Math.max(maxY, child.maxY);
            }
        }
        for (FNode node : sg.nodes) {
            if (isPseudoStateNode(node)) continue;
            if (node.drawingCoord == null) continue;
            int nMinX = node.drawingCoord.x;
            int nMinY = node.drawingCoord.y;
            int nMaxX = nMinX + node.drawW;
            int nMaxY = nMinY + node.drawH;
            minX = Math.min(minX, nMinX);
            minY = Math.min(minY, nMinY);
            maxX = Math.max(maxX, nMaxX);
            maxY = Math.max(maxY, nMaxY);
        }
        if (minX == 1_000_000 || minY == 1_000_000 || maxX == -1_000_000 || maxY == -1_000_000) return;
        int subPadding = 1;
        int labelSpace = 1;
        sg.minX = minX - subPadding;
        sg.minY = minY - subPadding - labelSpace;
        sg.maxX = maxX + subPadding;
        sg.maxY = maxY + subPadding;
    }

    private static void ensureSubgraphSpacing(FlowAsciiGraph graph) {
        int minSpacing = 1;
        List<FSubgraph> roots = new ArrayList<>();
        for (FSubgraph sg : graph.subgraphs) if (sg.parent == null && !sg.nodes.isEmpty()) roots.add(sg);
        for (int i = 0; i < roots.size(); i++) {
            for (int j = i + 1; j < roots.size(); j++) {
                FSubgraph a = roots.get(i), b = roots.get(j);
                if (a.minX < b.maxX && a.maxX > b.minX) {
                    if (a.maxY >= b.minY - minSpacing && a.minY < b.minY) b.minY = a.maxY + minSpacing + 1;
                    else if (b.maxY >= a.minY - minSpacing && b.minY < a.minY) a.minY = b.maxY + minSpacing + 1;
                }
                if (a.minY < b.maxY && a.maxY > b.minY) {
                    if (a.maxX >= b.minX - minSpacing && a.minX < b.minX) b.minX = a.maxX + minSpacing + 1;
                    else if (b.maxX >= a.minX - minSpacing && b.minX < a.minX) a.minX = b.maxX + minSpacing + 1;
                }
            }
        }
    }

    private static void offsetDrawingForSubgraphs(FlowAsciiGraph graph) {
        if (graph.subgraphs.isEmpty()) return;
        int minX = 0, minY = 0;
        for (FSubgraph sg : graph.subgraphs) {
            minX = Math.min(minX, sg.minX);
            minY = Math.min(minY, sg.minY);
        }
        int offX = -minX, offY = -minY;
        if (offX == 0 && offY == 0) return;
        graph.offsetX = offX;
        graph.offsetY = offY;
        for (FSubgraph sg : graph.subgraphs) {
            sg.minX += offX; sg.maxX += offX; sg.minY += offY; sg.maxY += offY;
        }
        for (FNode node : graph.nodes) {
            if (node.drawingCoord != null) node.drawingCoord = new FDrawCoord(node.drawingCoord.x + offX, node.drawingCoord.y + offY);
        }
    }

    private static void drawFlowGraph(FlowAsciiGraph graph) {
        graph.canvas = new Canvas(1, 1);
        graph.canvas.ensure(graph.canvasMaxX, graph.canvasMaxY);
        List<FSubgraph> ordered = new ArrayList<>(graph.subgraphs);
        ordered.sort(Comparator.comparingInt(Mermaid2ASCIITool::subgraphDepth));
        for (FSubgraph sg : ordered) drawSubgraphBox(graph.canvas, sg, graph.config.useAscii);

        for (FNode node : graph.nodes) {
            if (!node.drawn && node.drawingCoord != null) {
                drawFlowBox(graph, node, true);
                node.drawn = true;
            }
        }

        List<EdgeDrawData> draws = new ArrayList<>();
        for (FEdge edge : graph.edges) {
            if (edge.path == null || edge.path.isEmpty()) continue;
            DrawPathResult dpr = drawFlowPath(graph, edge.path, edge);
            boolean suppressBoxStart = isPseudoStateNode(edge.from) || getFlowEdgesFromNode(graph, edge.from).size() > 1;
            draws.add(new EdgeDrawData(edge, dpr, suppressBoxStart));
        }
        for (EdgeDrawData d : draws) drawFlowCorners(graph.canvas, graph, d.edge.path);
        for (EdgeDrawData d : draws) {
            PendingArrow a = buildFlowArrowHead(graph.config.useAscii, d.path.lastSegment, d.path.lastDir, d.edge.endDir);
            if (a != null) graph.canvas.put(a.x, a.y, a.ch);
        }
        for (EdgeDrawData d : draws) {
            if (!d.suppressBoxStart) drawFlowBoxStart(graph.canvas, graph.config.useAscii, d.edge.path, d.path.firstSegment);
        }
        for (EdgeDrawData d : draws) {
            PendingLabel l = buildFlowArrowLabel(graph, d.edge);
            if (l != null) graph.canvas.putText(l.x, l.y, l.text);
        }
        for (FSubgraph sg : graph.subgraphs) drawSubgraphLabel(graph.canvas, sg);
    }

    private static void drawSubgraphBox(Canvas canvas, FSubgraph sg, boolean useAscii) {
        int width = sg.maxX - sg.minX;
        int height = sg.maxY - sg.minY;
        if (width <= 0 || height <= 0) return;
        drawRectAt(canvas, sg.minX, sg.minY, width, height, useAscii);
    }

    private static void drawSubgraphLabel(Canvas canvas, FSubgraph sg) {
        int width = sg.maxX - sg.minX;
        int height = sg.maxY - sg.minY;
        if (width <= 0 || height <= 0) return;
        int labelY = sg.minY + 1;
        int labelX = sg.minX + (width / 2) - (sg.name.length() / 2);
        if (labelX < sg.minX + 1) labelX = sg.minX + 1;
        for (int i = 0; i < sg.name.length(); i++) {
            int x = labelX + i;
            if (x < sg.maxX) {
                char ch = sg.name.charAt(i);
                if (ch != ' ') canvas.put(x, labelY, ch);
            }
        }
    }

    private static DrawPathResult drawFlowPath(FlowAsciiGraph graph, List<FGridCoord> path, FEdge edge) {
        List<FDrawCoord> firstSeg = new ArrayList<>();
        List<FDrawCoord> lastSeg = new ArrayList<>();
        FDir lastDir = F_MIDDLE;
        for (int i = 1; i < path.size(); i++) {
            FGridCoord prevGc = path.get(i - 1);
            FGridCoord next = path.get(i);
            FDrawCoord prevDc = gridToDrawingCoord(graph, prevGc, null);
            FDrawCoord nextDc = gridToDrawingCoord(graph, next, null);
            if (prevDc.x == nextDc.x && prevDc.y == nextDc.y) continue;
            FDir dir = determineDirection(prevGc.x, prevGc.y, next.x, next.y);

            boolean isFirst = i == 1;
            boolean isLast = i == path.size() - 1;
            if (isFirst) {
                FNode node = getNodeAtCoord(graph, prevGc);
                if (node != null && node.drawingCoord != null) prevDc = borderCoord(node, dir, prevDc);
            }
            if (isLast) {
                FNode node = getNodeAtCoord(graph, next);
                if (node != null && node.drawingCoord != null) nextDc = borderCoord(node, oppositeDir(dir), nextDc);
            }

            int offsetFrom = isFirst ? 0 : 1;
            int offsetTo = isLast ? 0 : -1;
            List<FDrawCoord> seg = drawFlowLine(graph.canvas, prevDc, nextDc, offsetFrom, offsetTo, graph.config.useAscii);
            if (seg.isEmpty()) seg.add(prevDc);
            if (firstSeg.isEmpty()) firstSeg = seg;
            lastSeg = seg;
            lastDir = dir;
        }
        if (lastSeg.isEmpty() && !path.isEmpty()) {
            FDrawCoord single = gridToDrawingCoord(graph, path.get(path.size() - 1), null);
            lastSeg = new ArrayList<>(List.of(single));
            if (firstSeg.isEmpty()) firstSeg = lastSeg;
        }
        return new DrawPathResult(firstSeg, lastSeg, lastDir);
    }

    private static FNode getNodeAtCoord(FlowAsciiGraph graph, FGridCoord coord) {
        return graph.gridOwner.get(pack(coord.x, coord.y));
    }

    private static FDrawCoord borderCoord(FNode node, FDir side, FDrawCoord lane) {
        int left = node.drawingCoord.x;
        int top = node.drawingCoord.y;
        int width = node.drawW + 1;
        int height = node.drawH + 1;
        int cx = left + width / 2;
        int cy = top + height / 2;
        if (dirEq(side, F_LEFT)) return new FDrawCoord(left, lane.y);
        if (dirEq(side, F_RIGHT)) return new FDrawCoord(left + width - 1, lane.y);
        if (dirEq(side, F_UP)) return new FDrawCoord(lane.x, top);
        if (dirEq(side, F_DOWN)) return new FDrawCoord(lane.x, top + height - 1);
        return new FDrawCoord(cx, cy);
    }

    private static List<FDrawCoord> drawFlowLine(Canvas canvas, FDrawCoord from, FDrawCoord to, int offsetFrom, int offsetTo, boolean useAscii) {
        FDir dir = determineDirection(from.x, from.y, to.x, to.y);
        List<FDrawCoord> drawn = new ArrayList<>();
        char h = useAscii ? '-' : '─';
        char v = useAscii ? '|' : '│';
        char bslash = useAscii ? '\\' : '╲';
        char fslash = useAscii ? '/' : '╱';
        if (dirEq(dir, F_UP)) {
            for (int y = from.y - offsetFrom; y >= to.y - offsetTo; y--) { drawn.add(new FDrawCoord(from.x, y)); putFlowLineChar(canvas, from.x, y, v, useAscii); }
        } else if (dirEq(dir, F_DOWN)) {
            for (int y = from.y + offsetFrom; y <= to.y + offsetTo; y++) { drawn.add(new FDrawCoord(from.x, y)); putFlowLineChar(canvas, from.x, y, v, useAscii); }
        } else if (dirEq(dir, F_LEFT)) {
            for (int x = from.x - offsetFrom; x >= to.x - offsetTo; x--) { drawn.add(new FDrawCoord(x, from.y)); putFlowLineChar(canvas, x, from.y, h, useAscii); }
        } else if (dirEq(dir, F_RIGHT)) {
            for (int x = from.x + offsetFrom; x <= to.x + offsetTo; x++) { drawn.add(new FDrawCoord(x, from.y)); putFlowLineChar(canvas, x, from.y, h, useAscii); }
        } else if (dirEq(dir, F_UPPER_LEFT)) {
            int x = from.x, y = from.y - offsetFrom;
            while (x >= to.x - offsetTo && y >= to.y - offsetTo) { drawn.add(new FDrawCoord(x, y)); putFlowLineChar(canvas, x, y, bslash, useAscii); x--; y--; }
        } else if (dirEq(dir, F_UPPER_RIGHT)) {
            int x = from.x, y = from.y - offsetFrom;
            while (x <= to.x + offsetTo && y >= to.y - offsetTo) { drawn.add(new FDrawCoord(x, y)); putFlowLineChar(canvas, x, y, fslash, useAscii); x++; y--; }
        } else if (dirEq(dir, F_LOWER_LEFT)) {
            int x = from.x, y = from.y + offsetFrom;
            while (x >= to.x - offsetTo && y <= to.y + offsetTo) { drawn.add(new FDrawCoord(x, y)); putFlowLineChar(canvas, x, y, fslash, useAscii); x--; y++; }
        } else if (dirEq(dir, F_LOWER_RIGHT)) {
            int x = from.x, y = from.y + offsetFrom;
            while (x <= to.x + offsetTo && y <= to.y + offsetTo) { drawn.add(new FDrawCoord(x, y)); putFlowLineChar(canvas, x, y, bslash, useAscii); x++; y++; }
        }
        return drawn;
    }

    private static void putFlowLineChar(Canvas canvas, int x, int y, char incoming, boolean useAscii) {
        char cur = canvas.get(x, y);
        if (cur == ' ') {
            canvas.put(x, y, incoming);
            return;
        }
        if (!useAscii && isFlowJunction(cur) && isFlowJunction(incoming)) {
            canvas.put(x, y, mergeFlowJunction(cur, incoming));
            return;
        }
        canvas.put(x, y, incoming);
    }

    private static boolean isFlowJunction(char c) {
        return c == '─' || c == '│' || c == '┌' || c == '┐' || c == '└' || c == '┘'
            || c == '├' || c == '┤' || c == '┬' || c == '┴' || c == '┼'
            || c == '╴' || c == '╵' || c == '╶' || c == '╷';
    }

    private static char mergeFlowJunction(char c1, char c2) {
        if (c1 == '─') {
            if (c2 == '│') return '┼'; if (c2 == '┌') return '┬'; if (c2 == '┐') return '┬';
            if (c2 == '└') return '┴'; if (c2 == '┘') return '┴'; if (c2 == '├') return '┼';
            if (c2 == '┤') return '┼'; if (c2 == '┬') return '┬'; if (c2 == '┴') return '┴';
            return c1;
        }
        if (c1 == '│') {
            if (c2 == '─') return '┼'; if (c2 == '┌') return '├'; if (c2 == '┐') return '┤';
            if (c2 == '└') return '├'; if (c2 == '┘') return '┤'; if (c2 == '├') return '├';
            if (c2 == '┤') return '┤'; if (c2 == '┬') return '┼'; if (c2 == '┴') return '┼';
            return c1;
        }
        if (c1 == '┌') {
            if (c2 == '─' || c2 == '┐' || c2 == '┬') return '┬';
            if (c2 == '│' || c2 == '└' || c2 == '├') return '├';
            if (c2 == '┘' || c2 == '┤' || c2 == '┴') return '┼';
            return c1;
        }
        if (c1 == '┐') {
            if (c2 == '─' || c2 == '┌' || c2 == '┬') return '┬';
            if (c2 == '│' || c2 == '┘' || c2 == '┤') return '┤';
            if (c2 == '└' || c2 == '├' || c2 == '┴') return '┼';
            return c1;
        }
        if (c1 == '└') {
            if (c2 == '─' || c2 == '┘' || c2 == '┴') return '┴';
            if (c2 == '│' || c2 == '┌' || c2 == '├') return '├';
            if (c2 == '┐' || c2 == '┤' || c2 == '┬') return '┼';
            return c1;
        }
        if (c1 == '┘') {
            if (c2 == '─' || c2 == '└' || c2 == '┴') return '┴';
            if (c2 == '│' || c2 == '┐' || c2 == '┤') return '┤';
            if (c2 == '┌' || c2 == '├' || c2 == '┬') return '┼';
            return c1;
        }
        if (c1 == '├') {
            if (c2 == '│' || c2 == '┌' || c2 == '└') return '├';
            if (c2 == '─' || c2 == '┐' || c2 == '┘' || c2 == '┤' || c2 == '┬' || c2 == '┴') return '┼';
            return c1;
        }
        if (c1 == '┤') {
            if (c2 == '│' || c2 == '┐' || c2 == '┘') return '┤';
            if (c2 == '─' || c2 == '┌' || c2 == '└' || c2 == '├' || c2 == '┬' || c2 == '┴') return '┼';
            return c1;
        }
        if (c1 == '┬') {
            if (c2 == '─' || c2 == '┌' || c2 == '┐') return '┬';
            if (c2 == '│' || c2 == '└' || c2 == '┘' || c2 == '├' || c2 == '┤' || c2 == '┴') return '┼';
            return c1;
        }
        if (c1 == '┴') {
            if (c2 == '─' || c2 == '└' || c2 == '┘') return '┴';
            if (c2 == '│' || c2 == '┌' || c2 == '┐' || c2 == '├' || c2 == '┤' || c2 == '┬') return '┼';
            return c1;
        }
        return c1;
    }

    private static void drawFlowBoxStart(Canvas canvas, boolean useAscii, List<FGridCoord> path, List<FDrawCoord> firstLine) {
        if (useAscii || path.size() < 2 || firstLine.isEmpty()) return;
        FDrawCoord from = firstLine.get(0);
        FDir dir = determineDirection(path.get(0).x, path.get(0).y, path.get(1).x, path.get(1).y);
        if (dirEq(dir, F_UP)) putFlowLineChar(canvas, from.x, from.y, '┴', false);
        else if (dirEq(dir, F_DOWN)) putFlowLineChar(canvas, from.x, from.y, '┬', false);
        else if (dirEq(dir, F_LEFT)) putFlowLineChar(canvas, from.x, from.y, '┤', false);
        else if (dirEq(dir, F_RIGHT)) putFlowLineChar(canvas, from.x, from.y, '├', false);
    }

    private static PendingArrow buildFlowArrowHead(boolean useAscii, List<FDrawCoord> lastLine, FDir lastDir, FDir fallbackDir) {
        if (lastLine.isEmpty()) return null;
        FDrawCoord from = lastLine.get(0);
        FDrawCoord pos = lastLine.get(lastLine.size() - 1);
        FDir dir = determineDirection(from.x, from.y, pos.x, pos.y);
        if (lastLine.size() == 1 || dirEq(dir, F_MIDDLE)) dir = fallbackDir != null ? fallbackDir : lastDir;
        char ch;
        if (!useAscii) {
            if (dirEq(dir, F_UP)) ch = '▲';
            else if (dirEq(dir, F_DOWN)) ch = '▼';
            else if (dirEq(dir, F_LEFT)) ch = '◄';
            else if (dirEq(dir, F_RIGHT)) ch = '►';
            else if (dirEq(dir, F_UPPER_RIGHT)) ch = '◥';
            else if (dirEq(dir, F_UPPER_LEFT)) ch = '◤';
            else if (dirEq(dir, F_LOWER_RIGHT)) ch = '◢';
            else if (dirEq(dir, F_LOWER_LEFT)) ch = '◣';
            else ch = '●';
        } else {
            if (dirEq(dir, F_UP)) ch = '^';
            else if (dirEq(dir, F_DOWN)) ch = 'v';
            else if (dirEq(dir, F_LEFT)) ch = '<';
            else if (dirEq(dir, F_RIGHT)) ch = '>';
            else ch = '*';
        }
        return new PendingArrow(pos.x, pos.y, ch);
    }

    private static void drawFlowCorners(Canvas canvas, FlowAsciiGraph graph, List<FGridCoord> path) {
        for (int i = 1; i < path.size() - 1; i++) {
            FGridCoord coord = path.get(i);
            FDrawCoord dc = gridToDrawingCoord(graph, coord, null);
            FDir prev = determineDirection(path.get(i - 1).x, path.get(i - 1).y, coord.x, coord.y);
            FDir next = determineDirection(coord.x, coord.y, path.get(i + 1).x, path.get(i + 1).y);
            char corner;
            if (!graph.config.useAscii) {
                if ((dirEq(prev, F_RIGHT) && dirEq(next, F_DOWN)) || (dirEq(prev, F_UP) && dirEq(next, F_LEFT))) corner = '┐';
                else if ((dirEq(prev, F_RIGHT) && dirEq(next, F_UP)) || (dirEq(prev, F_DOWN) && dirEq(next, F_LEFT))) corner = '┘';
                else if ((dirEq(prev, F_LEFT) && dirEq(next, F_DOWN)) || (dirEq(prev, F_UP) && dirEq(next, F_RIGHT))) corner = '┌';
                else if ((dirEq(prev, F_LEFT) && dirEq(next, F_UP)) || (dirEq(prev, F_DOWN) && dirEq(next, F_RIGHT))) corner = '└';
                else corner = '+';
            } else corner = '+';
            putFlowLineChar(canvas, dc.x, dc.y, corner, graph.config.useAscii);
        }
    }

    private static PendingLabel buildFlowArrowLabel(FlowAsciiGraph graph, FEdge edge) {
        if (edge.text == null || edge.text.isEmpty() || edge.labelLine == null || edge.labelLine.size() < 2) return null;
        FDrawCoord a = gridToDrawingCoord(graph, edge.labelLine.get(0), null);
        FDrawCoord b = gridToDrawingCoord(graph, edge.labelLine.get(1), null);
        int minX = Math.min(a.x, b.x), maxX = Math.max(a.x, b.x);
        int minY = Math.min(a.y, b.y), maxY = Math.max(a.y, b.y);
        int midX = minX + (maxX - minX) / 2;
        int midY = minY + (maxY - minY) / 2;
        int startX = midX - (edge.text.length() / 2);
        return new PendingLabel(startX, midY, edge.text);
    }

    private static void flipCanvasVertically(Canvas canvas, int maxX, int maxY) {
        Map<Character, Character> flip = new HashMap<>();
        flip.put('▲', '▼'); flip.put('▼', '▲');
        flip.put('◤', '◣'); flip.put('◣', '◤');
        flip.put('◥', '◢'); flip.put('◢', '◥');
        flip.put('^', 'v'); flip.put('v', '^');
        flip.put('┌', '└'); flip.put('└', '┌');
        flip.put('┐', '┘'); flip.put('┘', '┐');
        flip.put('┬', '┴'); flip.put('┴', '┬');
        flip.put('╵', '╷'); flip.put('╷', '╵');

        for (int y = 0; y <= maxY / 2; y++) {
            int y2 = maxY - y;
            for (int x = 0; x <= maxX; x++) {
                char a = canvas.get(x, y);
                char b = canvas.get(x, y2);
                canvas.put(x, y, b);
                canvas.put(x, y2, a);
            }
        }
        for (int y = 0; y <= maxY; y++) {
            for (int x = 0; x <= maxX; x++) {
                char ch = canvas.get(x, y);
                if (flip.containsKey(ch)) canvas.put(x, y, flip.get(ch));
            }
        }
    }

    private static String canvasToStringFull(Canvas canvas, int maxX, int maxY) {
        int minX = maxX + 1;
        int minY = maxY + 1;
        int usedMaxX = -1;
        int usedMaxY = -1;
        for (int x = 0; x <= maxX; x++) {
            for (int y = 0; y <= maxY; y++) {
                if (canvas.get(x, y) != ' ') {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    usedMaxX = Math.max(usedMaxX, x);
                    usedMaxY = Math.max(usedMaxY, y);
                }
            }
        }
        if (usedMaxX < 0 || usedMaxY < 0) return "";

        StringBuilder sb = new StringBuilder();
        for (int y = minY; y <= usedMaxY; y++) {
            int endX = usedMaxX;
            while (endX >= minX && canvas.get(endX, y) == ' ') endX--;
            for (int x = minX; x <= endX; x++) sb.append(canvas.get(x, y));
            if (y < usedMaxY) sb.append('\n');
        }
        return sb.toString();
    }

    private static final FDir F_UP = new FDir(1, 0);
    private static final FDir F_DOWN = new FDir(1, 2);
    private static final FDir F_LEFT = new FDir(0, 1);
    private static final FDir F_RIGHT = new FDir(2, 1);
    private static final FDir F_UPPER_RIGHT = new FDir(2, 0);
    private static final FDir F_UPPER_LEFT = new FDir(0, 0);
    private static final FDir F_LOWER_RIGHT = new FDir(2, 2);
    private static final FDir F_LOWER_LEFT = new FDir(0, 2);
    private static final FDir F_MIDDLE = new FDir(1, 1);

    private static void printUsage() {
        System.err.println("Usage: java BeautifulMermaid <input.mmd> [--ascii] [--padding-x N] [--padding-y N] [--box-padding N]");
    }

    private static final class CliOptions {
        final Path input;
        final boolean useAscii;
        final int paddingX;
        final int paddingY;
        final int boxPadding;

        CliOptions(Path input, boolean useAscii, int paddingX, int paddingY, int boxPadding) {
            this.input = input;
            this.useAscii = useAscii;
            this.paddingX = paddingX;
            this.paddingY = paddingY;
            this.boxPadding = boxPadding;
        }

        static CliOptions parse(String[] args) {
            if (args.length == 0) return null;
            Path input = null;
            boolean useAscii = false;
            int paddingX = 6;
            int paddingY = 4;
            int boxPadding = 1;

            for (int i = 0; i < args.length; i++) {
                String cur = args[i];
                if ("--ascii".equals(cur)) {
                    useAscii = true;
                } else if ("--padding-x".equals(cur) || "--padding-y".equals(cur) || "--box-padding".equals(cur)) {
                    if (i + 1 >= args.length) return null;
                    int val;
                    try {
                        val = Integer.parseInt(args[++i]);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                    if ("--padding-x".equals(cur)) paddingX = val;
                    else if ("--padding-y".equals(cur)) paddingY = val;
                    else boxPadding = val;
                } else if (cur.startsWith("--")) {
                    return null;
                } else {
                    if (input != null) return null;
                    input = Path.of(cur);
                }
            }
            if (input == null) return null;
            return new CliOptions(input, useAscii, paddingX, paddingY, boxPadding);
        }
    }

    private static final class Canvas {
        private final List<char[]> rows = new ArrayList<>();
        private int w;
        private int baseH;

        Canvas(int width, int height) {
            this.w = Math.max(4, width);
            int h = Math.max(4, height);
            this.baseH = h;
            for (int i = 0; i < h; i++) {
                char[] r = new char[this.w];
                Arrays.fill(r, ' ');
                rows.add(r);
            }
        }

        void ensure(int x, int y) {
            if (x < 0 || y < 0) return;
            while (y >= rows.size()) {
                char[] r = new char[w];
                Arrays.fill(r, ' ');
                rows.add(r);
            }
            if (x >= w) {
                int nw = Math.max(x + 1, w * 2);
                for (int i = 0; i < rows.size(); i++) {
                    char[] old = rows.get(i);
                    char[] nr = new char[nw];
                    Arrays.fill(nr, ' ');
                    System.arraycopy(old, 0, nr, 0, old.length);
                    rows.set(i, nr);
                }
                w = nw;
            }
        }

        void put(int x, int y, char ch) {
            if (x < 0 || y < 0) return;
            ensure(x, y);
            rows.get(y)[x] = ch;
        }

        char get(int x, int y) {
            if (x < 0 || y < 0 || y >= rows.size() || x >= w) return ' ';
            return rows.get(y)[x];
        }

        void putText(int x, int y, String s) {
            if (s == null || s.isEmpty()) return;
            for (int i = 0; i < s.length(); i++) put(x + i, y, s.charAt(i));
        }

        void hLine(int x1, int x2, int y, char ch) {
            if (y < 0) return;
            int a = Math.min(x1, x2);
            int b = Math.max(x1, x2);
            for (int x = a; x <= b; x++) put(x, y, ch);
        }

        void vLine(int y1, int y2, int x, char ch) {
            if (x < 0) return;
            int a = Math.min(y1, y2);
            int b = Math.max(y1, y2);
            for (int y = a; y <= b; y++) put(x, y, ch);
        }

        void drawRect(int x, int y, int w, int h, boolean ascii) {
            if (w < 2 || h < 2) return;
            char H = ascii ? '-' : '─';
            char V = ascii ? '|' : '│';
            char TL = ascii ? '+' : '┌';
            char TR = ascii ? '+' : '┐';
            char BL = ascii ? '+' : '└';
            char BR = ascii ? '+' : '┘';
            put(x, y, TL);
            put(x + w, y, TR);
            put(x, y + h, BL);
            put(x + w, y + h, BR);
            hLine(x + 1, x + w - 1, y, H);
            hLine(x + 1, x + w - 1, y + h, H);
            vLine(y + 1, y + h - 1, x, V);
            vLine(y + 1, y + h - 1, x + w, V);
        }

        String render() {
            int maxY = rows.size() - 1;
            int maxX = 0;
            for (char[] r : rows) {
                for (int x = r.length - 1; x >= 0; x--) {
                    if (r[x] != ' ') {
                        maxX = Math.max(maxX, x);
                        break;
                    }
                }
            }
            while (maxY >= 0) {
                char[] r = rows.get(maxY);
                boolean any = false;
                for (int x = 0; x <= maxX; x++) if (x < r.length && r[x] != ' ') { any = true; break; }
                if (any) break;
                maxY--;
            }
            if (maxY < 0 || maxX == 0) return "";
            StringBuilder sb = new StringBuilder();
            for (int y = 0; y <= maxY; y++) {
                char[] r = rows.get(y);
                int right = maxX;
                while (right >= 0 && (right >= r.length || r[right] == ' ')) right--;
                if (right < 0) {
                    sb.append('\n');
                    continue;
                }
                sb.append(r, 0, right + 1).append('\n');
            }
            return sb.toString();
        }

        String renderFixed() {
            StringBuilder sb = new StringBuilder();
            int h = Math.max(baseH, rows.size());
            for (int y = 0; y < h; y++) {
                char[] r = y < rows.size() ? rows.get(y) : null;
                if (r == null) {
                    for (int x = 0; x < w; x++) sb.append(' ');
                } else if (r.length >= w) {
                    sb.append(r, 0, w);
                } else {
                    sb.append(r, 0, r.length);
                    for (int x = r.length; x < w; x++) sb.append(' ');
                }
                sb.append('\n');
            }
            return sb.toString();
        }
    }

    private static final class FlowGraph {
        String direction;
        final Map<String, FlowNode> nodes = new LinkedHashMap<>();
        final List<FlowEdge> edges = new ArrayList<>();
        final List<Subgraph> subgraphs = new ArrayList<>();

        FlowGraph(String direction) { this.direction = direction; }
    }

    private static final class Point {
        final int x;
        final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class FlowNode {
        final String id;
        String label;
        int x;
        int y;
        int w;
        int h;
        final Set<Subgraph> subgraphs = new HashSet<>();

        FlowNode(String id, String label) {
            this.id = id;
            if ((id.startsWith("_start") || id.startsWith("_end")) && (label == null || label.isBlank())) {
                this.label = "";
            } else {
                this.label = (label == null) ? id : label;
            }
        }

        boolean isPseudo() {
            return id.startsWith("_start") || id.startsWith("_end") || "START".equals(id) || "END".equals(id);
        }
    }

    private static final class FlowEdge {
        final String from;
        final String to;
        final String label;
        final String op;

        FlowEdge(String from, String to, String label, String op) {
            this.from = from;
            this.to = to;
            this.label = label == null ? "" : label;
            this.op = op == null ? "-->" : op;
        }
    }

    private static final class FGridCoord {
        final int x;
        final int y;
        FGridCoord(int x, int y) { this.x = x; this.y = y; }
    }

    private static final class FDrawCoord {
        final int x;
        final int y;
        FDrawCoord(int x, int y) { this.x = x; this.y = y; }
    }

    private static final class FDir {
        final int x;
        final int y;
        FDir(int x, int y) { this.x = x; this.y = y; }
    }

    private static final class FDirSet {
        final FDir prefDir;
        final FDir prefOpp;
        final FDir altDir;
        final FDir altOpp;
        FDirSet(FDir prefDir, FDir prefOpp, FDir altDir, FDir altOpp) {
            this.prefDir = prefDir; this.prefOpp = prefOpp; this.altDir = altDir; this.altOpp = altOpp;
        }
    }

    private static final class FPathQ {
        final int priority;
        final long seq;
        final int x;
        final int y;
        FPathQ(int priority, long seq, int x, int y) { this.priority = priority; this.seq = seq; this.x = x; this.y = y; }
    }

    private static final class FCanvasSize {
        final int w;
        final int h;
        FCanvasSize(int w, int h) { this.w = w; this.h = h; }
    }

    private static final class PathCandidate {
        final int penalty;
        final int length;
        final int bends;
        final FDir startDir;
        final FDir endDir;
        final List<FGridCoord> path;

        PathCandidate(int penalty, int length, int bends, FDir startDir, FDir endDir, List<FGridCoord> path) {
            this.penalty = penalty;
            this.length = length;
            this.bends = bends;
            this.startDir = startDir;
            this.endDir = endDir;
            this.path = path;
        }
    }

    private static final class DrawPathResult {
        final List<FDrawCoord> firstSegment;
        final List<FDrawCoord> lastSegment;
        final FDir lastDir;
        DrawPathResult(List<FDrawCoord> firstSegment, List<FDrawCoord> lastSegment, FDir lastDir) {
            this.firstSegment = firstSegment;
            this.lastSegment = lastSegment;
            this.lastDir = lastDir;
        }
    }

    private static final class EdgeDrawData {
        final FEdge edge;
        final DrawPathResult path;
        final boolean suppressBoxStart;
        EdgeDrawData(FEdge edge, DrawPathResult path, boolean suppressBoxStart) {
            this.edge = edge;
            this.path = path;
            this.suppressBoxStart = suppressBoxStart;
        }
    }

    private static final class PendingArrow {
        final int x;
        final int y;
        final char ch;
        PendingArrow(int x, int y, char ch) { this.x = x; this.y = y; this.ch = ch; }
    }

    private static final class PendingLabel {
        final int x;
        final int y;
        final String text;
        PendingLabel(int x, int y, String text) { this.x = x; this.y = y; this.text = text; }
    }

    private static final class FNode {
        final String name;
        final String displayLabel;
        final int index;
        FGridCoord gridCoord;
        FDrawCoord drawingCoord;
        int drawW;
        int drawH;
        boolean drawn;
        FNode(String name, String displayLabel, int index) {
            this.name = name;
            this.displayLabel = displayLabel == null ? "" : displayLabel;
            this.index = index;
        }
    }

    private static final class FEdge {
        final FNode from;
        final FNode to;
        final String text;
        List<FGridCoord> path = new ArrayList<>();
        List<FGridCoord> labelLine = new ArrayList<>();
        FDir startDir = F_MIDDLE;
        FDir endDir = F_MIDDLE;
        FEdge(FNode from, FNode to, String text) {
            this.from = from; this.to = to; this.text = text == null ? "" : text;
        }
    }

    private static final class FSubgraph {
        final String name;
        final List<FNode> nodes;
        FSubgraph parent;
        final List<FSubgraph> children;
        String direction;
        int minX;
        int minY;
        int maxX;
        int maxY;
        FSubgraph(String name, List<FNode> nodes, FSubgraph parent, List<FSubgraph> children, String direction) {
            this.name = name == null ? "" : name;
            this.nodes = nodes;
            this.parent = parent;
            this.children = children;
            this.direction = direction;
        }
    }

    private static final class FlowConfig {
        final boolean useAscii;
        final int paddingX;
        final int paddingY;
        final int boxBorderPadding;
        final String graphDirection;
        FlowConfig(boolean useAscii, int paddingX, int paddingY, int boxBorderPadding, String graphDirection) {
            this.useAscii = useAscii;
            this.paddingX = paddingX;
            this.paddingY = paddingY;
            this.boxBorderPadding = boxBorderPadding;
            this.graphDirection = graphDirection;
        }
    }

    private static final class FlowAsciiGraph {
        final List<FNode> nodes;
        final List<FEdge> edges;
        Canvas canvas;
        final Set<Long> grid;
        final Map<Long, FNode> gridOwner;
        final Map<Integer, Integer> columnWidth;
        final Map<Integer, Integer> rowHeight;
        final List<FSubgraph> subgraphs;
        final FlowConfig config;
        int offsetX;
        int offsetY;
        int canvasMaxX;
        int canvasMaxY;
        final Map<String, FNode> nodeByName = new HashMap<>();
        FlowAsciiGraph(List<FNode> nodes, List<FEdge> edges, Canvas canvas, Set<Long> grid,
                       Map<Integer, Integer> columnWidth, Map<Integer, Integer> rowHeight, Map<Long, FNode> gridOwner,
                       List<FSubgraph> subgraphs, FlowConfig config) {
            this.nodes = nodes;
            this.edges = edges;
            this.canvas = canvas;
            this.grid = grid;
            this.gridOwner = gridOwner;
            this.columnWidth = columnWidth;
            this.rowHeight = rowHeight;
            this.subgraphs = subgraphs;
            this.config = config;
            this.offsetX = 0;
            this.offsetY = 0;
            this.canvasMaxX = 0;
            this.canvasMaxY = 0;
        }
    }

    private static final class Subgraph {
        final String id;
        final String label;
        final Subgraph parent;
        String direction;
        final List<Subgraph> children = new ArrayList<>();
        final Set<String> nodeIds = new LinkedHashSet<>();

        Subgraph(String id, String label, Subgraph parent, String direction) {
            this.id = id;
            this.label = label;
            this.parent = parent;
            this.direction = direction;
        }
    }

    private static final class EdgeToken {
        final String op;
        final String label;
        final String targetToken;
        final String remaining;

        EdgeToken(String op, String label, String targetToken, String remaining) {
            this.op = op;
            this.label = label == null ? "" : label;
            this.targetToken = targetToken == null ? "" : targetToken;
            this.remaining = remaining == null ? "" : remaining;
        }
    }

    private static final class NodeConsume {
        final String id;
        final String remaining;
        NodeConsume(String id, String remaining) {
            this.id = id;
            this.remaining = remaining == null ? "" : remaining;
        }
    }

    private static final class SequenceDiagram {
        final List<SeqActor> actors = new ArrayList<>();
        final List<SeqMessage> messages = new ArrayList<>();
        final List<Block> blocks = new ArrayList<>();
        final List<SeqNote> notes = new ArrayList<>();
    }

    private static final class SeqActor {
        final String id;
        final String label;
        final String type;

        SeqActor(String id, String label, String type) {
            this.id = id;
            this.label = label;
            this.type = type;
        }
    }

    private static final class SeqMessage {
        final String from;
        final String to;
        final String label;
        final boolean dashed;
        final boolean filled;
        boolean activate;
        boolean deactivate;

        SeqMessage(String from, String to, String label, boolean dashed, boolean filled) {
            this.from = from;
            this.to = to;
            this.label = label == null ? "" : label;
            this.dashed = dashed;
            this.filled = filled;
            this.activate = false;
            this.deactivate = false;
        }
    }

    private static final class ParsedMessage {
        final String from;
        final String to;
        final String label;
        final boolean dashed;
        final boolean filled;

        ParsedMessage(String from, String to, String label, boolean dashed, boolean filled) {
            this.from = from;
            this.to = to;
            this.label = label;
            this.dashed = dashed;
            this.filled = filled;
        }
    }

    private static final class Block {
        String type;
        String label;
        int startIndex;
        int endIndex;
        final List<BlockDivider> dividers = new ArrayList<>();
    }

    private static final class BlockDivider {
        final int index;
        final String label;

        BlockDivider(int index, String label) {
            this.index = index;
            this.label = label;
        }
    }

    private static final class BlockCtx {
        final Block block;

        BlockCtx(Block b) {
            this.block = b;
        }
    }

    private static final class SeqNote {
        final List<String> actorIds;
        final String text;
        final String pos;
        final int afterIndex;

        SeqNote(List<String> actorIds, String text, String pos, int afterIndex) {
            this.actorIds = actorIds;
            this.text = text;
            this.pos = pos;
            this.afterIndex = afterIndex;
        }
    }

    private static final class NotePos {
        final int x;
        final int y;
        final int width;
        final int height;
        final List<String> lines;

        NotePos(int x, int y, int width, int height, List<String> lines) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.lines = lines;
        }
    }

    private static final class ClassDiagram {
        final Map<String, ClassNode> classes = new LinkedHashMap<>();
        final List<ClassRel> relationships = new ArrayList<>();
    }

    private static final class ClassNode {
        final String id;
        String label;
        String annotation;
        final List<String> attributes = new ArrayList<>();
        final List<String> methods = new ArrayList<>();

        ClassNode(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    private static final class ClassRel {
        final String from;
        final String to;
        final String type;
        final String markerAt;
        final String label;
        final String fromCardinality;
        final String toCardinality;

        ClassRel(String from, String to, String type, String markerAt, String label, String fromCardinality, String toCardinality) {
            this.from = from;
            this.to = to;
            this.type = type;
            this.markerAt = markerAt;
            this.label = label;
            this.fromCardinality = fromCardinality;
            this.toCardinality = toCardinality;
        }
    }

    private static final class ArrowParsed {
        final String type;
        final String markerAt;

        ArrowParsed(String type, String markerAt) {
            this.type = type;
            this.markerAt = markerAt;
        }
    }

    private static final class ParsedClassMember {
        final String text;
        final boolean isMethod;
        final boolean isStatic;
        final boolean isAbstract;

        ParsedClassMember(String text, boolean isMethod, boolean isStatic, boolean isAbstract) {
            this.text = text;
            this.isMethod = isMethod;
            this.isStatic = isStatic;
            this.isAbstract = isAbstract;
        }
    }

    private static final class ErDiagram {
        final Map<String, ErEntity> entities = new LinkedHashMap<>();
        final List<ErRel> relationships = new ArrayList<>();
    }

    private static final class ErEntity {
        final String id;
        final String label;
        final List<String> attributes = new ArrayList<>();

        ErEntity(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    private static final class ErRel {
        final String e1;
        final String e2;
        final String c1;
        final String c2;
        final String label;
        final boolean identifying;

        ErRel(String e1, String e2, String c1, String c2, String label, boolean identifying) {
            this.e1 = e1;
            this.e2 = e2;
            this.c1 = c1;
            this.c2 = c2;
            this.label = label;
            this.identifying = identifying;
        }
    }

    private static final class Box {
        final int x;
        final int y;
        final int w;
        final int h;
        final List<String> lines;

        Box(int x, int y, int w, int h, List<String> lines) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.lines = lines;
        }
    }

    private static final class BoxSize {
        final int w;
        final int h;

        BoxSize(int w, int h) {
            this.w = w;
            this.h = h;
        }
    }
}
