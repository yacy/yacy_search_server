/**
 * ModelLoader.java

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

package net.yacy.ai.llama3.Model;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import net.yacy.ai.llama3.Llama;
import net.yacy.ai.llama3.Tensor.FloatTensor;

public final class ModelLoader {
    private static final String TOKENIZER_LLAMA_3_MODEL = "gpt2";
    //private static final String TOKENIZER_QWEN2_7B_MODEL = "gpt2";

    private static final String LLAMA_3_PATTERN = "(?i:'s|'t|'re|'ve|'m|'ll|'d)|[^\\r\\n\\p{L}\\p{N}]?\\p{L}+|\\p{N}{1,3}| ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*|\\s*[\\r\\n]+|\\s+(?!\\S)|\\s+";
    private final static String QWEN2_PATTERN = "(?:'[sS]|'[tT]|'[rR][eE]|'[vV][eE]|'[mM]|'[lL][lL]|'[dD])|[^\\r\\n\\p{L}\\p{N}]?\\p{L}+|\\p{N}| ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*|\\s*[\\r\\n]+|\\s+(?!\\S)|\\s+";

    private static Vocabulary loadVocabulary(Map<String, Object> metadata) {
        String model = (String) metadata.get("tokenizer.ggml.model");
        if (!TOKENIZER_LLAMA_3_MODEL.equals(model)) {
            throw new IllegalArgumentException("expected " + TOKENIZER_LLAMA_3_MODEL + " but found " + model);
        }
        String[] tokens = (String[]) metadata.get("tokenizer.ggml.tokens");
        float[] scores = (float[]) metadata.get("tokenizer.ggml.scores");
        return new Vocabulary(tokens, scores);
    }

    public static Llama loadModel(Path ggufPath, int contextLength, boolean loadWeights) throws IOException {
    	String name = ggufPath.getFileName().toString();
    	if (name.contains("Llama")) return loadModelLlama3(ggufPath, contextLength, loadWeights);
    	//if (name.contains("OLMo")) return loadModelLlama3(ggufPath, contextLength, loadWeights);
    	if (name.contains("Qwen3")) return loadModelQwen3(ggufPath, contextLength);
    	//if (name.contains("Qwen2")) return loadModelQwen2(ggufPath, contextLength);
    	throw new IOException("model type unknown");
    }
    
    private static Llama loadModelLlama3(Path ggufPath, int contextLength, boolean loadWeights) throws IOException {
        GGUF gguf = GGUF.loadModel(ggufPath);
        FileChannel fileChannel = FileChannel.open(ggufPath, StandardOpenOption.READ);
        Map<String, Object> metadata = gguf.getMetadata();
        Vocabulary vocabulary = loadVocabulary(metadata);
        Tokenizer tokenizer = createLlama3Tokenizer(metadata, vocabulary);

        Llama.Configuration config = new Llama.Configuration(
        		Arch.LLM_ARCH_LLAMA,
                (int) metadata.get("llama.embedding_length"),
                (int) metadata.get("llama.feed_forward_length"),
                (int) metadata.get("llama.block_count"),
                (int) metadata.get("llama.attention.head_count"),

                metadata.containsKey("llama.attention.head_count_kv")
                        ? (int) metadata.get("llama.attention.head_count_kv")
                        : (int) metadata.get("llama.attention.head_count"),

                vocabulary.size(),
                (int) metadata.get("llama.context_length"),
                false,
                (float) metadata.getOrDefault("llama.attention.layer_norm_rms_epsilon", 1e-5f),
                (float) metadata.getOrDefault("llama.rope.freq_base", 10000f)
        ).withContextLength(contextLength);

        Llama.Weights weights = null;
        if (loadWeights) {
            Map<String, GGMLTensorEntry> tensorEntries = GGUF.loadTensors(fileChannel, gguf.getTensorDataOffset(), gguf.getTensorInfos());
            weights = loadWeightsLlama3(tensorEntries, config);
        }
        return new Llama(config, tokenizer, weights);
    }

    private static Llama.Weights loadWeightsLlama3(Map<String, GGMLTensorEntry> tensorEntries, Llama.Configuration config) {
        boolean ropeScaling = tensorEntries.containsKey("rope_freqs");
        float scaleFactor = 8;
        float loFreqFactor = 1;
        float hiFreqFactor = 3;
        int oldContextLength = 8192;
        Pair<float[], float[]> ropeFreqs = precomputeFreqsCis4Llama3(config.contextLength, config.headSize, config.ropeTheta,
                ropeScaling, scaleFactor, loFreqFactor, hiFreqFactor, oldContextLength);
        float[] ropeFreqsReal = ropeFreqs.first();
        float[] ropeFreqsImag = ropeFreqs.second();

        GGMLTensorEntry tokenEmbeddings = tensorEntries.get("token_embd.weight");
        Llama.Weights qw = new Llama.Weights(
                tokenEmbeddings.loadQuantized(),
                loadArrayOfFloatBuffer(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".attn_norm.weight")),
                loadArrayOfQuantized(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".attn_q.weight")),
                loadArrayOfQuantized(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".attn_k.weight")),
                loadArrayOfQuantized(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".attn_v.weight")),
                null, null, null,
                loadArrayOfQuantized(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".attn_output.weight")),
                loadArrayOfFloatBuffer(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".ffn_norm.weight")),
                loadArrayOfQuantized(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".ffn_gate.weight")), // w1
                loadArrayOfQuantized(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".ffn_down.weight")), // w2
                loadArrayOfQuantized(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".ffn_up.weight")), // w3
                tensorEntries.get("output_norm.weight").toFloatBuffer(),
                FloatBuffer.wrap(ropeFreqsReal),
                FloatBuffer.wrap(ropeFreqsImag),
                // If "output.weight" is not present then the embedding weights are tied/shared with the decoder.
                // This is commonly referred as "tie word embeddings".
                tensorEntries.getOrDefault("output.weight", tokenEmbeddings).loadQuantized()
        );

        return qw;
    }

    private static Llama loadModelQwen3(Path ggufPath, int contextLength) throws IOException {
    	// see https://github.com/ggml-org/llama.cpp/pull/12501/files
        GGUF gguf = GGUF.loadModel(ggufPath);
        Map<String, Object> metadata = gguf.getMetadata();
    
        Vocabulary vocabulary = loadVocabulary(metadata);
        Tokenizer tokenizer = createQwen2Tokenizer(metadata, vocabulary);
    
        int modelContextLength = (int) metadata.get("qwen2.context_length");
        if (contextLength < 0 || modelContextLength < contextLength) {
            contextLength = modelContextLength;
        }
    
        Llama.Configuration config = new Llama.Configuration(
        		Arch.LLM_ARCH_QWEN3,
                (int) metadata.get("qwen2.embedding_length"),
                (int) metadata.get("qwen2.feed_forward_length"),
                (int) metadata.get("qwen2.block_count"),
                (int) metadata.get("qwen2.attention.head_count"),
    
                metadata.containsKey("qwen2.attention.head_count_kv")
                        ? (int) metadata.get("qwen2.attention.head_count_kv")
                        : (int) metadata.get("qwen2.attention.head_count"),
    
                vocabulary.size(),
                contextLength,
                false,
                (float) metadata.get("qwen2.attention.layer_norm_rms_epsilon"),
                (float) metadata.get("qwen2.rope.freq_base")
        );
    
        Map<String, GGMLTensorEntry> tensorEntries = gguf.getTensorEntries();
    
        Pair<float[], float[]> ropeFreqs = precomputeFreqsCis4Qwen2(config.contextLength, config.headSize, config.ropeTheta);
        float[] ropeFreqsReal = ropeFreqs.first();
        float[] ropeFreqsImag = ropeFreqs.second();
    
    
        FloatTensor tokenEmbeddingTable = tensorEntries.get("token_embd.weight").loadQuantized();
        Llama.Weights qw = new Llama.Weights(
                tokenEmbeddingTable,
                loadArrayOfFloatBuffer(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".attn_norm.weight")),
                loadArrayOfQuantized(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".attn_q.weight")),
                loadArrayOfQuantized(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".attn_k.weight")),
                loadArrayOfQuantized(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".attn_v.weight")),
    
                loadArrayOfQuantized(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".attn_q.bias")),
                loadArrayOfQuantized(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".attn_k.bias")),
                loadArrayOfQuantized(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".attn_v.bias")),
    
                loadArrayOfQuantized(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".attn_output.weight")),
                loadArrayOfFloatBuffer(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".ffn_norm.weight")),
                loadArrayOfQuantized(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".ffn_gate.weight")), // w1
                loadArrayOfQuantized(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".ffn_down.weight")), // w2
                loadArrayOfQuantized(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".ffn_up.weight")), // w3
                tensorEntries.get("output_norm.weight").toFloatBuffer(),
                FloatBuffer.wrap(ropeFreqsReal),
                FloatBuffer.wrap(ropeFreqsImag),
                tensorEntries.containsKey("output.weight")
                        ? tensorEntries.get("output.weight").loadQuantized()
                        : tokenEmbeddingTable // weights are shared
        );
    
        return new Llama(config, tokenizer, qw);
    }
    
    private static Tokenizer createLlama3Tokenizer(Map<String, Object> metadata, Vocabulary vocabulary) {
        String[] mergeLines = (String[]) metadata.get("tokenizer.ggml.merges");
        List<Pair<Integer, Integer>> merges = Arrays.stream(mergeLines)
                .map(line -> line.split(" "))
                .map(parts ->
                        new Pair<>(
                                vocabulary.getIndex(parts[0]).orElseThrow(),
                                vocabulary.getIndex(parts[1]).orElseThrow())
                ).collect(Collectors.toList());

        int allTokens = vocabulary.size();
        int baseTokens = 128000; // assume all tokens after the base ones are special.
        //int reservedSpecialTokens = allTokens - baseTokens;
        List<String> specialTokensList = Arrays.stream(vocabulary.tokens(), baseTokens, allTokens).collect(Collectors.toList());

        assert specialTokensList.stream().allMatch(token -> vocabulary.getIndex(token).isPresent());

        Map<String, Integer> specialTokens =
                IntStream.range(0, specialTokensList.size())
                        .boxed()
                        .collect(Collectors.toMap(
                                i -> specialTokensList.get(i),
                                i -> baseTokens + i)
                        );

        return new Tokenizer(vocabulary, merges, LLAMA_3_PATTERN, specialTokens, null);
    }

    private static Tokenizer createQwen2Tokenizer(Map<String, Object> metadata, Vocabulary vocabulary) {
        int[] tokenTypes = (int[]) metadata.get("tokenizer.ggml.token_type");
        String[] mergeLines = (String[]) metadata.get("tokenizer.ggml.merges");
        List<Pair<Integer, Integer>> merges = Arrays.stream(mergeLines)
                .map(line -> line.split(" "))
                .map(parts ->
                    new Pair<>(
                        vocabulary.getIndex(parts[0]).orElseThrow(),
                        vocabulary.getIndex(parts[1]).orElseThrow())
                )
                .collect(Collectors.toList());
        
        int allTokens = vocabulary.size();
        int baseTokens = vocabulary.getIndex("<|endoftext|>").orElseThrow(); // assume all tokens after the base ones are special.
        //int reservedSpecialTokens = allTokens - baseTokens;
        List<String> specialTokensList = Arrays.stream(vocabulary.tokens(), baseTokens, allTokens).collect(Collectors.toList());

        assert specialTokensList.stream().allMatch(token -> vocabulary.getIndex(token).isPresent());

        Map<String, Integer> specialTokens =
                IntStream.range(0, specialTokensList.size())
                        .boxed()
                        .collect(Collectors.toMap(
                                i -> specialTokensList.get(i),
                                i -> baseTokens + i)
                        );

        return new Tokenizer(vocabulary, merges, QWEN2_PATTERN, specialTokens, tokenTypes);
    }
    

    private static FloatTensor[] loadArrayOfQuantized(int size, IntFunction<GGMLTensorEntry> getTensorEntry) {
        FloatTensor[] array = new FloatTensor[size];
        for (int i = 0; i < size; i++) {
            array[i] = getTensorEntry.apply(i).loadQuantized();
        }
        return array;
    }

    private static FloatBuffer[] loadArrayOfFloatBuffer(int size, IntFunction<GGMLTensorEntry> getTensorEntry) {
        FloatBuffer[] array = new FloatBuffer[size];
        for (int i = 0; i < size; i++) {
            array[i] = getTensorEntry.apply(i).toFloatBuffer();
        }
        return array;
    }
    
    // RoPE
    
    // for LLama3
    private static Pair<float[], float[]> precomputeFreqsCis4Llama3(
            int contextLength, int headSize, double theta,
            boolean ropeScaling, float scaleFactor,
            float loFreqFactor, float hiFreqFactor, float oldContextLength) {
        assert headSize % 2 == 0;
        float[] cr = new float[contextLength * (headSize / 2)];
        float[] ci = new float[contextLength * (headSize / 2)];
        int n = 0;
        for (int pos = 0; pos < contextLength; ++pos) {
            for (int i = 0; i < headSize; i += 2) {
                float freq = (float) (1.0 / Math.pow(theta, i / (double) headSize));
                if (ropeScaling) {
                    // Llama 3.1 scaling
                    float loFreqWavelen = oldContextLength / loFreqFactor;
                    float hiFreqWavelen = oldContextLength / hiFreqFactor;
                    float wavelen = (float) (2.0 * Math.PI / freq);
                    if (wavelen < hiFreqWavelen) {
                        //freq = freq;
                    } else if (wavelen > loFreqWavelen) {
                        freq = freq / scaleFactor;
                    } else {
                        float smooth = (oldContextLength / wavelen - loFreqFactor) / (hiFreqFactor - loFreqFactor);
                        freq = (1.0f - smooth) * freq / scaleFactor + smooth * freq;
                    }
                }
                float val = pos * freq;
                cr[n] = (float) Math.cos(val);
                ci[n] = (float) Math.sin(val);
                n++;
            }
        }
        assert contextLength * (headSize / 2) == n;
        return new Pair<>(cr, ci);
    }
    
    // for Qwen3
    private static Pair<float[], float[]> precomputeFreqsCis4Qwen2(
            int contextLength, int headSize, double theta) {
        assert headSize % 2 == 0;
        float[] cr = new float[contextLength * (headSize / 2)];
        float[] ci = new float[contextLength * (headSize / 2)];
        int n = 0;
        for (int pos = 0; pos < contextLength; ++pos) {
            for (int i = 0; i < headSize; i += 2) {
                float freq = (float) (1.0 / Math.pow(theta, i / (double) headSize));
                float val = pos * freq;
                cr[n] = (float) Math.cos(val);
                ci[n] = (float) Math.sin(val);
                n++;
            }
        }
        assert contextLength * (headSize / 2) == n;
        return new Pair<>(cr, ci);
    }
    
}