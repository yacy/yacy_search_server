/**
 * Llama3.java

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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.function.IntConsumer;

import net.yacy.ai.llama3.Model.ModelLoader;

public class Llama3 {
    
    // Batch-size used in prompt evaluation.
    private static final int BATCH_SIZE = Integer.getInteger("llama.BatchSize", 16);


    static void runInteractive(Llama model, Sampler sampler, Context options) {
        Llama.State state = null;
        List<Integer> conversationTokens = new ArrayList<>();
        ChatFormat chatFormat = new ChatFormat(model.tokenizer());
        conversationTokens.add(chatFormat.beginOfText);
        if (options.systemPrompt != null) {
            conversationTokens.addAll(chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.SYSTEM, options.systemPrompt)));
        }
        int startPosition = 0;
        @SuppressWarnings("resource")
        Scanner in = new Scanner(System.in);
        while (true) {
            System.out.print("> ");
            System.out.flush();
            String userText = in.nextLine();
            if (state == null) {
                state = model.createNewState(BATCH_SIZE);
            }
            conversationTokens.addAll(chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.USER, userText)));
            conversationTokens.addAll(chatFormat.encodeHeader(new ChatFormat.Message(ChatFormat.Role.ASSISTANT, "")));
            Set<Integer> stopTokens = chatFormat.getStopTokens();
            List<Integer> responseTokens = Llama.generateTokens(model, state, startPosition, conversationTokens.subList(startPosition, conversationTokens.size()), stopTokens, options.maxTokens, sampler, token -> {
                if (!model.tokenizer().isSpecialToken(token)) {
                    System.out.print(model.tokenizer().decode(List.of(token)));
                }
            });
            // Include stop token in the prompt history, but not in the response displayed to the user.
            conversationTokens.addAll(responseTokens);
            startPosition = conversationTokens.size();
            Integer stopToken = null;
            if (!responseTokens.isEmpty() && stopTokens.contains(responseTokens.get(responseTokens.size()-1))) {
                stopToken = responseTokens.get(responseTokens.size()-1);
                responseTokens.remove(responseTokens.size()-1);
            }
            //System.out.println(model.tokenizer().decode(responseTokens));
            if (stopToken == null) {
                System.out.println("Ran out of context length...");
                break;
            }
        }
    }

    public static List<Integer> runInstructOnce(Llama model, Sampler sampler, Context options, IntConsumer onTokenGenerated) {
        Llama.State state = model.createNewState(BATCH_SIZE);
        ChatFormat chatFormat = new ChatFormat(model.tokenizer());

        List<Integer> promptTokens = new ArrayList<>();
        promptTokens.add(chatFormat.beginOfText);
        if (options.systemPrompt != null) {
            promptTokens.addAll(chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.SYSTEM, options.systemPrompt)));
        }
        //System.out.println("Context after System Prompt: " + toString(model, promptTokens));
        promptTokens.addAll(chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.USER, options.prompt)));
        //System.out.println("Context after User Prompt: " + toString(model, promptTokens));
        promptTokens.addAll(chatFormat.encodeHeader(new ChatFormat.Message(ChatFormat.Role.ASSISTANT, "")));
        //System.out.println("Context after Assitant Prompt: " + toString(model, promptTokens));

        Set<Integer> stopTokens = chatFormat.getStopTokens();
        List<Integer> responseTokens = Llama.generateTokens(model, state, 0, promptTokens, stopTokens, options.maxTokens, sampler, onTokenGenerated);

        // remove stop token at the end of the response, if present
        if (!responseTokens.isEmpty() && stopTokens.contains(responseTokens.get(responseTokens.size()-1))) {
            responseTokens.remove(responseTokens.size()-1);
        }
        //System.out.println(model.tokenizer().decode(responseTokens));
        return responseTokens;
    }
    
    public static String toString(Llama model, List<Integer> tokens) {
        return model.tokenizer().decode(tokens);
    }

    public static void main(String[] args) throws IOException {
        // model download paths:
        // https://huggingface.co/mukel/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_0.gguf
        // https://huggingface.co/mukel/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q8_0.gguf
        // https://huggingface.co/mukel/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_0.gguf
    	
    	// performance on iMac x86:
    	// semeru 11 : 1.40 T/s
    	// semeru 21 : 1.13 T/s
    	// GraalVM 21: 2.27 T/s
    	// openjdk 21: 3.02 T/s; 3.2 with VarHandle

        Path modelPath = Path.of("/Users/admin/git/yacy_search_server", "DATA", "LLMS", "Llama-3.2-1B-Instruct-Q4_0.gguf"); // 26.7 T/s/M4 orig jdk 21; 24.9 T/s/M4 Temurin 24; 25.8 T/s/M4 jdk 21; 22.2 T/s/M4 GraalVM21; 9.2 T/s/M4 Semeru 11; 9.2 T/s/M1 Ultra jdk 11
        //Path modelPath = Path.of("/Users/admin/git/yacy_search_server", "DATA", "LLMS", "Llama-3.2-1B-Instruct-Q8_0.gguf"); // 10.7 T/s/M4 orig jdk 21; 21.2 T/s/M4 Temurin 24; 22 T/s/M4 jdk 21; 18 T/s/M4 GraalVM21; 5.8 T/s/M4 Semeru 11; 6.7 T/s/M1 Ultra jdk 11
        //Path modelPath = Path.of("/Users/admin/git/yacy_search_server", "DATA", "LLMS", "Llama-3.2-3B-Instruct-Q4_0.gguf"); // 9.8 T/s/M4 orig jdk 21; 9.6 T/s/M4 Temurin 24; 9.3 T/s/M4 jdk 21; 8.0 T/s/M4 GraalVM21; 3.2 T/s/M4 Semeru 11; 3.8 T/s/M1 Ultra jdk 11
        //Path modelPath = Path.of("/Users/admin/git/yacy_search_server", "DATA", "LLMS", "Llama-3.2-3B-Instruct-Q8_0.gguf"); // 7.2 T/s/M4 jdk 24
        //Path modelPath = Path.of("/Users/admin/git/yacy_search_server", "DATA", "LLMS", "Meta-Llama-3-8B-Instruct-Q4_0.gguf"); // 3.6 T/s/M4 jdk 24;
        //Path modelPath = Path.of("/Users/admin/git/yacy_search_server", "DATA", "LLMS", "OLMo-2-0425-1B-Instruct-Q4_0.gguf");
        Context options = new Context("Write a Java program which computes the first 42 prime numbers.", "Be a very good programmer.", 0.0f, 0.95f, 0, 1024);
        Llama model = ModelLoader.loadModel(modelPath, 1024, true);
        // get time
        long startTime = System.currentTimeMillis();
        Sampler sampler = Sampler.selectSampler(model.configuration().vocabularySize, options.temp, options.topp, options.seed);
        List<Integer> resultToken = runInstructOnce(model, sampler, options, token -> {
            if (!model.tokenizer().isSpecialToken(token)) {
                System.out.print(model.tokenizer().decode(List.of(token)));
            }
        });
        long endTime = System.currentTimeMillis();
        System.out.println("\nToken: " + resultToken.size() + ", " + ((double) resultToken.size()) * 1000.0d / ((double) (endTime - startTime)) + " Tokens per second");
    }
}











