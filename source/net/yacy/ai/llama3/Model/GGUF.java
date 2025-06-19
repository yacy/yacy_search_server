/**
 * GGUF.java

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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Collectors;

import net.yacy.ai.llama3.Tensor.FloatTensor;

/*
 * GGUF File Reader. For specification see https://github.com/ggml-org/ggml/blob/master/docs/gguf.md
 */
public final class GGUF {
    private static final int GGUF_MAGIC = 0x46554747;
    private static final int DEFAULT_ALIGNMENT = 32; // must be a power of 2
    private static final List<Integer> SUPPORTED_GGUF_VERSIONS = List.of(2, 3);
    private int magic;
    private int version;
    private int tensorCount; // uint64_t
    private int alignment;
    private int metadata_kv_count; // uint64_t
    private Map<String, Object> metadata;
    private Map<String, GGUFTensorInfo> tensorInfos;
    private long tensorDataOffset;
    private Map<String, GGMLTensorEntry> tensorEntries;

    public Map<String, GGUFTensorInfo> getTensorInfos() {
        return tensorInfos;
    }

    public long getTensorDataOffset() {
        return tensorDataOffset;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    private final ByteBuffer BB_1 = ByteBuffer.allocate(Byte.BYTES).order(ByteOrder.LITTLE_ENDIAN);
    private final ByteBuffer BB_2 = ByteBuffer.allocate(Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
    private final ByteBuffer BB_4 = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
    private final ByteBuffer BB_8 = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN);

    public Map<String, GGMLTensorEntry> getTensorEntries() {
        return tensorEntries;
    }
    
    public static GGUF loadModel(Path modelPath) throws IOException {
        try (FileChannel fileChannel = FileChannel.open(modelPath)) {
            GGUF gguf = new GGUF();
            gguf.loadModelImpl(fileChannel);
            return gguf;
        }
    }

    enum MetadataValueType {
        // The value is a 8-bit unsigned integer.
        UINT8(1),
        // The value is a 8-bit signed integer.
        INT8(1),
        // The value is a 16-bit unsigned little-endian integer.
        UINT16(2),
        // The value is a 16-bit signed little-endian integer.
        INT16(2),
        // The value is a 32-bit unsigned little-endian integer.
        UINT32(4),
        // The value is a 32-bit signed little-endian integer.
        INT32(4),
        // The value is a 32-bit IEEE754 floating point number.
        FLOAT32(4),
        // The value is a boolean.
        // 1-byte value where 0 is false and 1 is true.
        // Anything else is invalid, and should be treated as either the model being invalid or the reader being buggy.
        BOOL(1),
        // The value is a UTF-8 non-null-terminated string, with length prepended.
        STRING(-8),
        // The value is an array of other values, with the length and type prepended.
        // Arrays can be nested, and the length of the array is the number of elements in the array, not the number of bytes.
        ARRAY(-8),
        // The value is a 64-bit unsigned little-endian integer.
        UINT64(8),
        // The value is a 64-bit signed little-endian integer.
        INT64(8),
        // The value is a 64-bit IEEE754 floating point number.
        FLOAT64(8);
        private final int byteSize;

        MetadataValueType(int byteSize) {
            this.byteSize = byteSize;
        }

        private static final MetadataValueType[] VALUES = values();

        public static MetadataValueType fromIndex(int index) {
            return VALUES[index];
        }

        public int byteSize() {
            return byteSize;
        }
    }

    private void loadModelImpl(FileChannel fileChannel) throws IOException {
        // The header of the file.
        readHeader(fileChannel); // gguf_header_t header;
        // Tensor infos, which can be used to locate the tensor data.
        // gguf_tensor_info_t tensor_infos[header.tensor_count];
        this.tensorInfos = new HashMap<>(tensorCount);
        for (int i = 0; i < tensorCount; ++i) {
            GGUF.GGUFTensorInfo ti = readTensorInfo(fileChannel);
            assert !tensorInfos.containsKey(ti.name);
            tensorInfos.put(ti.name, ti);
        }
        // Padding to the nearest multiple of `ALIGNMENT`.
        // uint8_t _padding[ALIGNMENT - (sizeof(header + tensor_infos) % ALIGNMENT)];
        //long _padding = -fileChannel.position() & (ALIGNMENT - 1);
        long _padding = getAlignment() - (fileChannel.position() % getAlignment());
        fileChannel.position(fileChannel.position() + _padding);
        this.tensorDataOffset = fileChannel.position();
    }
    /*
    public static Map<String, GGMLTensorEntry> loadTensors(FileChannel fileChannel, long tensorDataOffset, Map<String, GGUFTensorInfo> tensorInfos) throws IOException {
        // Map the whole remaining file region into memory
        MappedByteBuffer mappedBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, tensorDataOffset, fileChannel.size() - tensorDataOffset);
        mappedBuffer.order(ByteOrder.nativeOrder());

        Map<String, GGMLTensorEntry> tensorEntries = new HashMap<>(tensorInfos.size());

        for (Map.Entry<String, GGUFTensorInfo> entry : tensorInfos.entrySet()) {
            GGUFTensorInfo ti = entry.getValue();
            int numberOfElements = FloatTensor.numberOfElements(ti.dimensions());
            int sizeInBytes = Math.toIntExact(ti.ggmlType().byteSizeFor(numberOfElements));
            int offset = Math.toIntExact(ti.offset()); // assumes offset is within Integer range

            // Create a slice of the mapped buffer for this tensor
            MappedByteBuffer buffer = (MappedByteBuffer) mappedBuffer.duplicate();
            buffer.position(offset);
            buffer.limit(offset + sizeInBytes);
            
            // copy this into a DirectByteBuffer (tests show that this is not faster than using the MappedByteBuffer directly)
            //ByteBuffer directBuffer = ByteBuffer.allocateDirect(sizeInBytes).order(ByteOrder.nativeOrder());
            //directBuffer.put(buffer);
            //directBuffer.flip();
            //tensorEntries.put(ti.name(), new GGMLTensorEntry(ti.name(), ti.ggmlType(), ti.dimensions(), directBuffer));

            // Create a slice of the mapped buffer for this tensor
            MappedByteBuffer tensorBuffer = (MappedByteBuffer) buffer.slice().order(ByteOrder.nativeOrder());
            tensorEntries.put(ti.name(), new GGMLTensorEntry(ti.name(), ti.ggmlType(), ti.dimensions(), tensorBuffer));
        }

        return tensorEntries;
    }
*/
    
    static Map<String, GGMLTensorEntry> loadTensors(FileChannel fileChannel, long tensorDataOffset,
            Map<String, GGUFTensorInfo> tensorInfos) throws IOException {
        
        final long totalDataSize = fileChannel.size() - tensorDataOffset;
        Map<String, GGMLTensorEntry> tensorEntries = new HashMap<>(tensorInfos.size());

        // Fast path: if the entire tensor data fits in one mapped buffer
        if (totalDataSize <= Integer.MAX_VALUE) {
            MappedByteBuffer fullBuffer = (MappedByteBuffer) fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                tensorDataOffset,
                totalDataSize
            ).order(ByteOrder.nativeOrder());

            for (Map.Entry<String, GGUFTensorInfo> entry : tensorInfos.entrySet()) {
                GGUFTensorInfo ti = entry.getValue();
                long offset = ti.offset();
                int sizeInBytes = Math.toIntExact(ti.ggmlType().byteSizeFor(
                    FloatTensor.numberOfElements(ti.dimensions())));
                
                MappedByteBuffer tensorBuffer = (MappedByteBuffer) fullBuffer.duplicate();
                tensorBuffer.position((int)offset);
                tensorBuffer.limit((int)offset + sizeInBytes);
                tensorBuffer = (MappedByteBuffer) tensorBuffer.slice().order(ByteOrder.nativeOrder());
                
                tensorEntries.put(ti.name(), new GGMLTensorEntry(
                    ti.name(), ti.ggmlType(), ti.dimensions(), tensorBuffer));
            }
            return tensorEntries;
        }

        // Slow path: only for large files > 2GB:        
        // Models can be very large, so we cannot map the entire tensor data at once.
        // Instead, we will map segments of the tensor data as needed.
        
        // First pass: collect all tensor boundaries and sort them
        List<Long> boundaries = new ArrayList<>();
        for (GGUFTensorInfo ti : tensorInfos.values()) {
            long start = ti.offset();
            long end = start + ti.ggmlType().byteSizeFor(FloatTensor.numberOfElements(ti.dimensions()));
            boundaries.add(start);
            boundaries.add(end);
        }
        boundaries = boundaries.stream()
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        
        // Second pass: create memory mappings for each segment between boundaries
        List<MappedSegment> mappings = new ArrayList<>();
        long currentPos = tensorDataOffset;
        
        for (long boundary : boundaries) {
            if (boundary <= currentPos) continue;
            
            long mappingSize = boundary - currentPos;
            // Split into chunks no larger than Integer.MAX_VALUE
            while (mappingSize > 0) {
                long chunkSize = Math.min(mappingSize, Integer.MAX_VALUE);
                MappedByteBuffer buffer = (MappedByteBuffer) fileChannel.map(
                    FileChannel.MapMode.READ_ONLY,
                    currentPos,
                    chunkSize
                ).order(ByteOrder.nativeOrder());
                mappings.add(new MappedSegment(currentPos, buffer));
                currentPos += chunkSize;
                mappingSize -= chunkSize;
            }
        }
        
        // Handle any remaining data after last boundary
        if (currentPos < fileChannel.size()) {
            long remaining = fileChannel.size() - currentPos;
            while (remaining > 0) {
                long chunkSize = Math.min(remaining, Integer.MAX_VALUE);
                MappedByteBuffer buffer = (MappedByteBuffer) fileChannel.map(
                    FileChannel.MapMode.READ_ONLY,
                    currentPos,
                    chunkSize
                ).order(ByteOrder.nativeOrder());
                mappings.add(new MappedSegment(currentPos, buffer));
                currentPos += chunkSize;
                remaining -= chunkSize;
            }
        }
        
        // Third pass: create tensor views
        for (Map.Entry<String, GGUFTensorInfo> entry : tensorInfos.entrySet()) {
            GGUFTensorInfo ti = entry.getValue();
            String name = ti.name();
            long tensorOffset = ti.offset() + tensorDataOffset;
            long tensorSize = ti.ggmlType().byteSizeFor(FloatTensor.numberOfElements(ti.dimensions()));
            long tensorEnd = tensorOffset + tensorSize;
            
            // Find all segments that overlap with this tensor
            List<MappedSegment> overlappingSegments = mappings.stream()
                    .filter(seg -> seg.startOffset <= tensorEnd && 
                                  seg.startOffset + seg.buffer.capacity() > tensorOffset)
                    .sorted(Comparator.comparingLong(seg -> seg.startOffset))
                    .collect(Collectors.toList());
            
            if (overlappingSegments.isEmpty()) {
                throw new IOException("Tensor " + name + " not found in any mapped segment");
            }
            
            if (overlappingSegments.size() == 1) {
                // Simple case - tensor fits entirely in one segment
                MappedSegment segment = overlappingSegments.get(0);
                int bufferOffset = (int)(tensorOffset - segment.startOffset);
                int sizeInBytes = (int)tensorSize;
                
                MappedByteBuffer tensorBuffer = (MappedByteBuffer) segment.buffer.duplicate();
                tensorBuffer.position(bufferOffset);
                tensorBuffer.limit(bufferOffset + sizeInBytes);
                tensorBuffer = (MappedByteBuffer) tensorBuffer.slice().order(ByteOrder.nativeOrder());
                
                tensorEntries.put(name, new GGMLTensorEntry(
                    name, ti.ggmlType(), ti.dimensions(), tensorBuffer));
            } else {
                // Complex case - tensor spans multiple segments
                ByteBuffer combinedBuffer = ByteBuffer.allocateDirect((int)tensorSize)
                    .order(ByteOrder.nativeOrder());
                
                long remainingBytes = tensorSize;
                long currentTensorPos = tensorOffset;
                
                for (MappedSegment segment : overlappingSegments) {
                    long segmentEnd = segment.startOffset + segment.buffer.capacity();
                    long copyStart = Math.max(currentTensorPos, segment.startOffset);
                    long copyEnd = Math.min(tensorEnd, segmentEnd);
                    int bytesToCopy = (int)(copyEnd - copyStart);
                    
                    int srcPos = (int)(copyStart - segment.startOffset);
                    segment.buffer.position(srcPos);
                    
                    byte[] temp = new byte[bytesToCopy];
                    segment.buffer.get(temp);
                    combinedBuffer.put(temp);
                    
                    remainingBytes -= bytesToCopy;
                    currentTensorPos += bytesToCopy;
                    
                    if (remainingBytes <= 0) break;
                }
                
                combinedBuffer.flip();
                tensorEntries.put(name, new GGMLTensorEntry(
                    name, ti.ggmlType(), ti.dimensions(), combinedBuffer));
            }
        }
        
        return tensorEntries;
    }

    // Helper class to track mapped segments
    private static class MappedSegment {
        final long startOffset;
        final MappedByteBuffer buffer;
        
        MappedSegment(long startOffset, MappedByteBuffer buffer) {
            this.startOffset = startOffset;
            this.buffer = buffer;
        }
    }
    
    public static final class GGUFTensorInfo {
        private final String name;
        private final int[] dimensions;
        private final GGMLType ggmlType;
        private final long offset;

        public GGUFTensorInfo(String name, int[] dimensions, GGMLType ggmlType, long offset) {
            this.name = name;
            this.dimensions = dimensions != null ? dimensions.clone() : null;
            this.ggmlType = ggmlType;
            this.offset = offset;
        }

        public String name() {
            return name;
        }

        public int[] dimensions() {
            return dimensions != null ? dimensions.clone() : null;
        }

        public GGMLType ggmlType() {
            return ggmlType;
        }

        public long offset() {
            return offset;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GGUFTensorInfo that = (GGUFTensorInfo) o;
            return offset == that.offset &&
                    Objects.equals(name, that.name) &&
                    Arrays.equals(dimensions, that.dimensions) &&
                    Objects.equals(ggmlType, that.ggmlType);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(name, ggmlType, offset);
            result = 31 * result + Arrays.hashCode(dimensions);
            return result;
        }

        @Override
        public String toString() {
            return "GGUFTensorInfo[" +
                    "name=" + name +
                    ", dimensions=" + Arrays.toString(dimensions) +
                    ", ggmlType=" + ggmlType +
                    ", offset=" + offset +
                    ']';
        }
    }

    private GGMLType readGGMLType(FileChannel fileChannel) throws IOException {
        int ggmlTypeId = readInt(fileChannel); // ggml_type type;
        return GGMLType.fromId(ggmlTypeId);
    }

    private GGUF.GGUFTensorInfo readTensorInfo(FileChannel fileChannel) throws IOException {
        // The name of the tensor. It is a standard GGUF string, with the caveat that
        // it must be at most 64 bytes long.
        String name = readString(fileChannel); // gguf_string_t name;
        assert name.length() <= 64;
        // The number of dimensions in the tensor.
        // Currently at most 4, but this may change in the future.
        int n_dimensions = readInt(fileChannel); // uint32_t n_dimensions;
        assert n_dimensions <= 4;
        // The dimensions of the tensor.
        int[] dimensions = new int[n_dimensions]; // uint64_t dimensions[n_dimensions];
        for (int i = 0; i < n_dimensions; ++i) {
            dimensions[i] = Math.toIntExact(readLong(fileChannel));
        }
        // The type of the tensor.
        GGMLType ggmlType = readGGMLType(fileChannel); // ggml_type type;
        // The offset of the tensor's data in this file in bytes.
        // This offset is relative to `tensor_data`, not to the start
        // of the file, to make it easier for writers to write the file.
        // Readers should consider exposing this offset relative to the
        // file to make it easier to read the data.
        // Must be a multiple of `ALIGNMENT`.
        long offset = readLong(fileChannel); // uint64_t offset;
        assert offset % getAlignment() == 0;
        return new GGUF.GGUFTensorInfo(name, dimensions, ggmlType, offset);
    }

    private String readString(FileChannel fileChannel) throws IOException {
        // A string in GGUF.
        // The length of the string, in bytes.
        int len = Math.toIntExact(readLong(fileChannel)); // uint64_t len;
        // The string as a UTF-8 non-null-terminated string.
        byte[] bytes = new byte[len]; // char string[len];
        int bytesRead = fileChannel.read(ByteBuffer.wrap(bytes));
        assert len == bytesRead;
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private Pair<String, Object> readKeyValuePair(FileChannel fileChannel) throws IOException {
        // The key of the metadata. It is a standard GGUF string, with the following caveats:
        // - It must be a valid ASCII string.
        // - It must be a hierarchical key, where each segment is `lower_snake_case` and separated by a `.`.
        // - It must be at most 2^16-1/65535 bytes long.
        // Any keys that do not follow these rules are invalid.
        String key = readString(fileChannel); // gguf_string_t key;
        assert key.length() < (1 << 16);
        assert key.codePoints().allMatch(cp -> ('a' <= cp && cp <= 'z') || ('0' <= cp && cp <= '9') || cp == '_' || cp == '.');
        Object value = readMetadataValue(fileChannel);
        return new Pair<>(key, value);
    }

    private Object readMetadataValue(FileChannel fileChannel) throws IOException {
        // The type of the value.
        // Must be one of the `gguf_metadata_value_type` values.
        MetadataValueType value_type = readMetadataValueType(fileChannel); // gguf_metadata_value_type value_type;
        // The value.
        return readMetadataValueOfType(value_type, fileChannel); // gguf_metadata_value_t value;
    }

    // compare this with https://github.com/ggml-org/llama.cpp/blob/master/gguf-py/gguf/gguf_reader.py#L132
    void readHeader(FileChannel fileChannel) throws IOException {
        // Magic number to announce that this is a GGUF file.
        // Must be `GGUF` at the byte level: `0x47` `0x47` `0x55` `0x46`.
        // Your executor might do little-endian byte order, so it might be
        // check for 0x46554747 and letting the endianness cancel out.
        // Consider being *very* explicit about the byte order here.
        this.magic = readInt(fileChannel); //    uint32_t magic;
        if (magic != GGUF_MAGIC) {
            throw new IllegalArgumentException("unsupported header.magic " + magic);
        }
        // The version of the format implemented.
        // Must be `3` for version described in this spec.
        //
        // This version should only be increased for structural changes to the format.
        // Changes that do not affect the structure of the file should instead update the metadata
        // to signify the change.
        this.version = readInt(fileChannel); // uint32_t version;
        if (!SUPPORTED_GGUF_VERSIONS.contains(version)) {
            throw new IllegalArgumentException("unsupported header.version " + version);
        }
        // The number of tensors in the file.
        // This is explicit, instead of being included in the metadata, to ensure it is always present
        // for loading the tensors.
        // https://github.com/ggml-org/llama.cpp/blob/master/gguf-py/gguf/gguf_reader.py#L165
        this.tensorCount = Math.toIntExact(readLong(fileChannel)); // uint64_t tensor_count;
        // The number of metadata key-value pairs.
        this.metadata_kv_count = Math.toIntExact(readLong(fileChannel)); // uint64_t metadata_kv_count;
        // The metadata key-value pairs.
        // gguf_metadata_kv_t metadata_kv[metadata_kv_count];
        this.metadata = new HashMap<>(metadata_kv_count);
        for (int i = 0; i < metadata_kv_count; ++i) {
            Pair<String, Object> keyValue = readKeyValuePair(fileChannel);
            assert !metadata.containsKey(keyValue.first());
            metadata.put(keyValue.first(), keyValue.second());
        }
    }

    private Object readArray(FileChannel fileChannel) throws IOException {
        // Any value type is valid, including arrays.
        MetadataValueType value_type = readMetadataValueType(fileChannel); // gguf_metadata_value_type type;
        // Number of elements, not bytes
        int len = Math.toIntExact(readLong(fileChannel)); // uint64_t len;
        // The array of values.
        // gguf_metadata_value_t array[len];
        switch (value_type) {
            case UINT8:
            case INT8: {
                byte[] bytes = new byte[len];
                for (int i = 0; i < len; ++i) {
                    bytes[i] = readByte(fileChannel);
                }
                return bytes;
            }
            case UINT16:
            case INT16: {
                short[] shorts = new short[len];
                for (int i = 0; i < len; ++i) {
                    shorts[i] = readShort(fileChannel);
                }
                return shorts;
            }
            case UINT32:
            case INT32: {
                int[] ints = new int[len];
                for (int i = 0; i < len; ++i) {
                    ints[i] = readInt(fileChannel);
                }
                return ints;
            }
            case FLOAT32: {
                float[] floats = new float[len];
                for (int i = 0; i < len; ++i) {
                    floats[i] = readFloat(fileChannel);
                }
                return floats;
            }
            case BOOL: {
                boolean[] booleans = new boolean[len];
                for (int i = 0; i < len; ++i) {
                    booleans[i] = readBoolean(fileChannel);
                }
                return booleans;
            }
            case STRING: {
                String[] strings = new String[len];
                for (int i = 0; i < len; ++i) {
                    strings[i] = readString(fileChannel);
                }
                return strings;
            }
            case ARRAY: {
                Object[] arrays = new Object[len];
                for (int i = 0; i < len; ++i) {
                    arrays[i] = readArray(fileChannel);
                }
                return arrays;
            }
            default: throw new UnsupportedOperationException("read array of " + value_type);
        }
    }

    private Object readMetadataValueOfType(MetadataValueType valueType, FileChannel fileChannel) throws IOException {
        switch (valueType) {
            case UINT8:
            case INT8: return readByte(fileChannel);
            case UINT16:
            case INT16: return readShort(fileChannel);
            case UINT32:
            case INT32: return readInt(fileChannel);
            case FLOAT32: return readFloat(fileChannel);
            case UINT64:
            case INT64: return readLong(fileChannel);
            case FLOAT64: return readDouble(fileChannel);
            case BOOL: return readBoolean(fileChannel);
            case STRING: return readString(fileChannel);
            case ARRAY: return readArray(fileChannel);
            default: throw new AssertionError();
        }
    }

    private byte readByte(FileChannel fileChannel) throws IOException {
        int bytesRead = fileChannel.read(BB_1);
        assert bytesRead == 1;
        return BB_1.clear().get(0);
    }

    private boolean readBoolean(FileChannel fileChannel) throws IOException {
        return readByte(fileChannel) != 0;
    }

    private short readShort(FileChannel fileChannel) throws IOException {
        int bytesRead = fileChannel.read(BB_2);
        assert bytesRead == 2;
        return BB_2.clear().getShort(0);
    }

    private int readInt(FileChannel fileChannel) throws IOException {
        int bytesRead = fileChannel.read(BB_4);
        assert bytesRead == 4;
        return BB_4.clear().getInt(0);
    }

    private long readLong(FileChannel fileChannel) throws IOException {
        int bytesRead = fileChannel.read(BB_8);
        assert bytesRead == 8;
        return BB_8.clear().getLong(0);
    }

    private float readFloat(FileChannel fileChannel) throws IOException {
        return Float.intBitsToFloat(readInt(fileChannel));
    }

    private double readDouble(FileChannel fileChannel) throws IOException {
        return Double.longBitsToDouble(readLong(fileChannel));
    }

    private MetadataValueType readMetadataValueType(FileChannel fileChannel) throws IOException {
        int index = readInt(fileChannel);
        return MetadataValueType.fromIndex(index);
    }

    public int getAlignment() {
        if (alignment != 0) {
            return alignment;
        }
        alignment = (int) metadata.getOrDefault("general.alignment", DEFAULT_ALIGNMENT);
        assert Integer.bitCount(alignment) == 1 : "alignment must be a power of two";
        return alignment;
    }
}