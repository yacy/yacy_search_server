/**
 * ChatFormat.java

 * This file was extracted from the llama3/qwen2 projects
 * https://github.com/mukel/llama3.java
 * https://github.com/mukel/qwen2.svm.java
 * 
 * License: MIT License
 * 
 * Copyright (c) 2024 Andrej Karpathy (for llama2.c)
 * Copyright (c) 2024 Alfonso² Peterssen (for llama3/qwen2)
 * Copyright (c) 2023 Georgi Gerganov et al. (for llama.cpp)
 * Copyright (c) 2025 Michael Peter Christen for modifications:
 * The code was modified to fit the YaCy AI project:
 * - back-port to Java 11 (removal of Vector API operations and record types)
 * - removal of interactive mode and system.out printing
 * - separation of the classes in the single java and refactoring
 * - run-time performance optimizations for dot product computation of quantized values
 * - joining of llama3/qwen2 into one code base; multi-arch options
 * - alignment with code from https://github.com/ggml-org/llama.cpp/
 */

package net.yacy.ai.llama3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yacy.ai.llama3.Model.Tokenizer;

/**
 * Utility tailored for Llama 3 instruct prompt format.
 */
public class ChatFormat {

    final Tokenizer tokenizer;
    final int beginOfText;
    final int endHeader;
    final int startHeader;
    final int endOfTurn;
    final int endOfText;
    final int endOfMessage;
    final Set<Integer> stopTokens;

    public ChatFormat(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
        Map<String, Integer> specialTokens = this.tokenizer.getSpecialTokens();
        this.beginOfText = specialTokens.get("<|begin_of_text|>");
        this.startHeader = specialTokens.get("<|start_header_id|>");
        this.endHeader = specialTokens.get("<|end_header_id|>");
        this.endOfTurn = specialTokens.get("<|eot_id|>");
        this.endOfText = specialTokens.get("<|end_of_text|>");
        this.endOfMessage = specialTokens.getOrDefault("<|eom_id|>", -1); // only in 3.1
        this.stopTokens = Set.of(endOfText, endOfTurn);
    }

    public Tokenizer getTokenizer() {
        return tokenizer;
    }

    public Set<Integer> getStopTokens() {
        return stopTokens;
    }

    public List<Integer> encodeHeader(ChatFormat.Message message) {
        List<Integer> tokens = new ArrayList<>();
        tokens.add(startHeader);
        tokens.addAll(this.tokenizer.encodeAsList(message.role().name()));
        tokens.add(endHeader);
        tokens.addAll(this.tokenizer.encodeAsList("\n"));
        return tokens;
    }

    public List<Integer> encodeMessage(ChatFormat.Message message) {
        List<Integer> tokens = this.encodeHeader(message);
        tokens.addAll(this.tokenizer.encodeAsList(message.content().strip()));
        tokens.add(endOfTurn);
        return tokens;
    }

    public List<Integer> encodeDialogPrompt(boolean appendAssistantTurn, List<ChatFormat.Message> dialog) {
        List<Integer> tokens = new ArrayList<>();
        tokens.add(beginOfText);
        for (ChatFormat.Message message : dialog) {
            tokens.addAll(this.encodeMessage(message));
        }
        if (appendAssistantTurn) {
            // Add the start of an assistant message for the model to complete.
            tokens.addAll(this.encodeHeader(new ChatFormat.Message(ChatFormat.Role.ASSISTANT, "")));
        }
        return tokens;
    }

    public static final class Message {
        private final ChatFormat.Role role;
        private final String content;

        public Message(ChatFormat.Role role, String content) {
            this.role = role;
            this.content = content;
        }

        public ChatFormat.Role role() {
            return role;
        }

        public String content() {
            return content;
        }

    }

    public static final class Role {
        private final String name;

        public static final Role SYSTEM = new Role("system");
        public static final Role USER = new Role("user");
        public static final Role ASSISTANT = new Role("assistant");

        public Role(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

    }
}