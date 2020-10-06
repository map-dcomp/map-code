/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018,2019,2020>, <Raytheon BBN Technologies>
To be applied to the DCOMP/MAP Public Source Code Release dated 2018-04-19, with
the exception of the dcop implementation identified below (see notes).

Dispersed Computing (DCOMP)
Mission-oriented Adaptive Placement of Task and Data (MAP) 

All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
BBN_LICENSE_END*/
package com.bbn.map.simulator;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nonnull;

import com.bbn.map.utils.JsonUtils;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.ResolvableDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Represents a simulated failure of a node.
 * 
 * @author jschewe
 *
 */
@SuppressFBWarnings(value = "UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD", justification = "Written by JSON parser")
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeFailure {

    /**
     * Algorithm for choosing which node to shutdown.
     * 
     * @author jschewe
     *
     */
    public enum NodeChooseAlgorithm {
    /**
     * Choose the most loaded node/container.
     */
    MOST_LOADED;
    }

    // CHECKSTYLE:OFF JSON serialized value class
    /**
     * When should the node fail.
     */
    public long time;
    /**
     * Which nodes or containers to choose from for a failure.
     */
    public Collection<String> nodes = new LinkedList<>();

    /**
     * Algorithm used to choose between the nodes in {@link #nodes}.
     */
    public NodeChooseAlgorithm chooseAlgorithm = NodeChooseAlgorithm.MOST_LOADED;

    /**
     * Attribute to use when the choose algorithm needs a node attribute to look
     * at.
     */
    public NodeAttribute nodeMetric = NodeAttribute.TASK_CONTAINERS;
    // CHECKSTYLE:ON

    /**
     * 
     * @param path
     *            where to load the node failures from
     * @return the node failures sorted by time
     * @throws IOException
     *             if there is an error reading the file
     */
    @Nonnull
    public static List<NodeFailure> loadNodeFailures(@Nonnull final Path path) throws IOException {
        if (!Files.exists(path)) {
            return Collections.emptyList();
        }

        final SimpleModule module = new SimpleModule();
        module.setDeserializerModifier(new BeanDeserializerModifier() {
            @Override
            public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config,
                    BeanDescription beanDesc,
                    JsonDeserializer<?> deserializer) {
                if (beanDesc.getBeanClass() == NodeFailure.class)
                    return new DeserializeNodeFailure(deserializer);
                return deserializer;
            }
        });
        final ObjectMapper mapper = JsonUtils.getStandardMapObjectMapper().registerModule(module)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            final List<NodeFailure> list = mapper.readValue(reader, new TypeReference<List<NodeFailure>>() {
            });

            Collections.sort(list, NodeFailureTimeCompare.INSTANCE);
            return list;
        }
    }

    private static final class NodeFailureTimeCompare implements Comparator<NodeFailure> {
        public static final NodeFailureTimeCompare INSTANCE = new NodeFailureTimeCompare();

        @Override
        public int compare(final NodeFailure o1, final NodeFailure o2) {
            return Long.compare(o1.time, o2.time);
        }

    }

    private static final class DeserializeNodeFailure extends StdDeserializer<NodeFailure>
            implements ResolvableDeserializer {

        private static final long serialVersionUID = 1L;

        private final JsonDeserializer<?> delegate;

        DeserializeNodeFailure(final JsonDeserializer<?> delegate) {
            super(NodeFailure.class);
            this.delegate = delegate;
        }

        @Override
        public NodeFailure deserialize(final JsonParser parser, final DeserializationContext ctx)
                throws IOException, JsonProcessingException {

            final ObjectCodec codec = parser.getCodec();
            final JsonNode node = codec.readTree(parser);

            final JsonParser secondParser = codec.treeAsTokens(node);
            // need to call next token since delegate believes it's in the
            // middle of parsing. This logic comes from how
            // ObjectMapper.readValue() initializes the parser for reading.
            JsonToken t = secondParser.getCurrentToken();
            if (t == null) {
                // and then we must get something...
                t = secondParser.nextToken();
                if (t == null) {
                    // Throw mapping exception, since it's failure to map,
                    // not an actual parsing problem
                    throw JsonMappingException.from(secondParser, "No content to map due to end-of-input");
                }
            }
            if (t == JsonToken.VALUE_NULL) {
                return null;
            } else if (t == JsonToken.END_ARRAY || t == JsonToken.END_OBJECT) {
                return null;
            }

            final NodeFailure parsed = (NodeFailure) delegate.deserialize(secondParser, ctx);
            if (node.has("node")) {
                // version 1
                final String singleNode = node.get("node").asText();
                parsed.nodes.add(singleNode);
            }

            return parsed;
        }

        // for some reason you have to implement ResolvableDeserializer when
        // modifying BeanDeserializer
        // otherwise deserializing throws JsonMappingException??
        @Override
        public void resolve(DeserializationContext ctxt) throws JsonMappingException {
            ((ResolvableDeserializer) delegate).resolve(ctxt);
        }
    }

}
