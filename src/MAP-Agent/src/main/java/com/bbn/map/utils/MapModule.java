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
package com.bbn.map.utils;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import com.bbn.map.common.value.ApplicationCoordinates;
import com.bbn.map.dns.DelegateRecord;
import com.bbn.map.dns.DnsRecord;
import com.bbn.map.dns.NameRecord;
import com.bbn.map.simulator.BackgroundNetworkLoad;
import com.bbn.map.simulator.BaseNetworkLoad;
import com.bbn.map.simulator.ClientLoad;
import com.bbn.protelis.networkresourcemanagement.DnsNameIdentifier;
import com.bbn.protelis.networkresourcemanagement.InterfaceIdentifier;
import com.bbn.protelis.networkresourcemanagement.LinkAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeAttribute;
import com.bbn.protelis.networkresourcemanagement.NodeIdentifier;
import com.bbn.protelis.networkresourcemanagement.NodeNetworkFlow;
import com.bbn.protelis.networkresourcemanagement.RegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.RegionNetworkFlow;
import com.bbn.protelis.networkresourcemanagement.ServiceIdentifier;
import com.bbn.protelis.networkresourcemanagement.StringRegionIdentifier;
import com.bbn.protelis.networkresourcemanagement.StringServiceIdentifier;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.collect.ImmutableSet;

/**
 * Provides JSON deserializers and some custom serializers for types in the MAP
 * system that are commonly encoded as JSON.
 * 
 * @author awald
 *
 */
public class MapModule extends SimpleModule {
    private static final long serialVersionUID = 1L;

    /**
     * Constructs an instance of this module, which contains JSON deserializers
     * and serializers.
     */
    public MapModule() {
        // ------------------ Deserializers ------------------

        // Add Identifier deserializers for Node, Region, and Service
        addDeserializer(NodeIdentifier.class, new NodeIdentifierDeserializer());
        addKeyDeserializer(NodeIdentifier.class, new NodeIdentifierKeyDeserializer());

        addDeserializer(RegionIdentifier.class, new RegionIdentifierDeserializer());
        addKeyDeserializer(RegionIdentifier.class, new RegionIdentifierKeyDeserializer());

        addDeserializer(ServiceIdentifier.class, new ServiceIdentifierDeserializer());
        addKeyDeserializer(ServiceIdentifier.class, new ServiceIdentifierKeyDeserializer());

        // Add Metrics deserializers
        addKeyDeserializer(NodeAttribute.class, new NodeAttributeKeyDeserializer());
        addKeyDeserializer(LinkAttribute.class, new LinkAttributeKeyDeserializer());

        // flow key deserializers
        addKeyDeserializer(NodeNetworkFlow.class, new NodeNetworkFlowKeyDeserializer());
        addKeyDeserializer(RegionNetworkFlow.class, new RegionNetworkFlowKeyDeserializer());

        // Add DNS deserializers
        addDeserializer(DnsRecord.class, new DnsRecordDeserializer());

        addDeserializer(BaseNetworkLoad.class, new BaseNetworkLoadDeserializer());

        // ------------------ Custom Serializers ------------------
        addKeySerializer(NodeAttribute.class, new NodeAttributeKeySerializer());
        addKeySerializer(LinkAttribute.class, new LinkAttributeKeySerializer());
        addKeySerializer(NodeNetworkFlow.class, new NodeNetworkFlowSerializer());
        addKeySerializer(RegionNetworkFlow.class, new RegionNetworkFlowSerializer());

        // InterfaceIdentifier
        addKeySerializer(InterfaceIdentifier.class, new InterfaceIdentifierKeySerializer());
        addKeyDeserializer(InterfaceIdentifier.class, new InterfaceIdentifierKeyDeserializer());

    }

    // ------------------ Deserializers ------------------

    /**
     * Used to deserialize a NodeIdentifier found in JSON.
     * 
     * @author awald
     *
     */
    private static final class NodeIdentifierDeserializer extends JsonDeserializer<NodeIdentifier> {

        @Override
        public NodeIdentifier deserialize(final JsonParser jp, final DeserializationContext ctxt)
                throws IOException, JsonProcessingException {
            final ObjectCodec oc = jp.getCodec();
            final JsonNode node = oc.readTree(jp);

            String name = node.get("name").asText();

            return new DnsNameIdentifier(name);
        }

    }

    /**
     * Used to deserialize a NodeIdentifier found in a JSON key.
     * 
     * @author awald
     *
     */
    private static final class NodeIdentifierKeyDeserializer extends KeyDeserializer {
        @Override
        public Object deserializeKey(final String key, final DeserializationContext ctxt)
                throws IOException, JsonProcessingException {
            return new DnsNameIdentifier(key);
        }

    }

    /**
     * Used to deserialize a NodeIdentifier found in JSON.
     * 
     * @author awald
     *
     */
    private static final class RegionIdentifierDeserializer extends JsonDeserializer<RegionIdentifier> {
        @Override
        public RegionIdentifier deserialize(final JsonParser jp, final DeserializationContext ctxt)
                throws IOException, JsonProcessingException {
            final ObjectCodec oc = jp.getCodec();
            final JsonNode node = oc.readTree(jp);

            String name = node.get("name").asText();

            return new StringRegionIdentifier(name);
        }
    }

    /**
     * Used to deserialize a {@link RegionIdentifier} found in a JSON map key.
     * 
     * @author awald
     *
     */
    private static final class RegionIdentifierKeyDeserializer extends KeyDeserializer {
        @Override
        public Object deserializeKey(String key, DeserializationContext ctxt)
                throws IOException, JsonProcessingException {
            return new StringRegionIdentifier(key);
        }
    }

    /**
     * Used to deserialize a ServiceIdentifier found in JSON.
     * 
     * @author awald
     *
     */
    private static final class ServiceIdentifierDeserializer extends JsonDeserializer<ServiceIdentifier<?>> {
        @Override
        public ServiceIdentifier<?> deserialize(final JsonParser jp, final DeserializationContext ctxt)
                throws IOException, JsonProcessingException {
            final ObjectCodec oc = jp.getCodec();
            final JsonNode node = oc.readTree(jp);

            final ObjectMapper mapper = (ObjectMapper) jp.getCodec();

            if (node.has("group")) {
                return mapper.treeToValue(node, ApplicationCoordinates.class);
            } else if (node.has("name")) {
                return mapper.treeToValue(node, StringServiceIdentifier.class);
            } else {
                throw new JsonMappingException(jp, "Cannot determine type of ServiceIdentifier from: " + node);
            }
        }
    }

    private static final Pattern APPLICATION_COORDINATES_KEY_REGEXP = Pattern
            .compile("AppCoordinates \\{([^,]+), ([^,]+), ([^}]+)\\}");

    /**
     * Used to deserialize a ServiceIdentifier found in a JSON key.
     * 
     * @author awald
     *
     */
    private static final class ServiceIdentifierKeyDeserializer extends KeyDeserializer {
        @Override
        public Object deserializeKey(final String key, final DeserializationContext ctxt)
                throws IOException, JsonProcessingException {
            // "AppCoordinates {com.bbn, database-query, 1}"
            final Matcher match = APPLICATION_COORDINATES_KEY_REGEXP.matcher(key);
            if (match.matches()) {
                final String group = match.group(1);
                final String artifact = match.group(2);
                final String version = match.group(3);
                return new ApplicationCoordinates(group, artifact, version);
            } else {
                return new StringServiceIdentifier(key);
            }
        }
    }

    private static Pair<String, Boolean> parseApplicationSpecificName(final String key) {
        final String[] parts = key.split(Pattern.quote(" {") + "|, |" + Pattern.quote("}"));

        final String name;
        final boolean applicationSpecific;
        if (parts.length >= 3) {
            name = parts[1];
            applicationSpecific = Boolean.parseBoolean(parts[2]);
        } else {
            name = key;
            applicationSpecific = false;
        }

        return Pair.of(name, applicationSpecific);
    }

    /**
     * Used to deserialize a NodeAttribute found in a JSON key.
     * 
     * @author awald
     *
     */
    private static final class NodeAttributeKeyDeserializer extends KeyDeserializer {

        @Override
        public Object deserializeKey(final String key, final DeserializationContext ctxt)
                throws IOException, JsonProcessingException {
            final Pair<String, Boolean> parsed = parseApplicationSpecificName(key);

            final String name = parsed.getLeft();
            final boolean applicationSpecific = parsed.getRight();

            return new NodeAttribute(name, applicationSpecific);
        }
    }

    /**
     * Used to deserialize a LinkAttribute found in a JSON key.
     * 
     * @author awald
     *
     */
    private static final class LinkAttributeKeyDeserializer extends KeyDeserializer {

        @Override
        public Object deserializeKey(final String key, final DeserializationContext ctxt)
                throws IOException, JsonProcessingException {
            final Pair<String, Boolean> parsed = parseApplicationSpecificName(key);

            final String name = parsed.getLeft();
            final boolean applicationSpecific = parsed.getRight();

            return new LinkAttribute(name, applicationSpecific);
        }
    }

    private static Triple<String, String, String> parseFlowNames(final String key) {
        final Pattern pattern = Pattern.compile("^\\(\\s*([^,]+)\\s*,\\s*([^,]+),\\s*([^,]+)\\s*\\)$");
        final Matcher matcher = pattern.matcher(key);

        if (matcher.matches()) {
            final String source = matcher.group(1);
            final String dest = matcher.group(2);
            final String server = matcher.group(3);
            return Triple.of(source, dest, server);
        } else {
            return Triple.of(key, key, key);
        }
    }

    private static final class NodeNetworkFlowKeyDeserializer extends KeyDeserializer {
        @Override
        public Object deserializeKey(final String key, final DeserializationContext ctxt)
                throws IOException, JsonProcessingException {
            final Triple<String, String, String> parsed = parseFlowNames(key);

            return new NodeNetworkFlow(new DnsNameIdentifier(parsed.getLeft()),
                    new DnsNameIdentifier(parsed.getMiddle()), new DnsNameIdentifier(parsed.getRight()));
        }
    }

    private static final class RegionNetworkFlowKeyDeserializer extends KeyDeserializer {
        @Override
        public Object deserializeKey(final String key, final DeserializationContext ctxt)
                throws IOException, JsonProcessingException {
            final Triple<String, String, String> parsed = parseFlowNames(key);

            return new RegionNetworkFlow(new StringRegionIdentifier(parsed.getLeft()),
                    new StringRegionIdentifier(parsed.getMiddle()), new StringRegionIdentifier(parsed.getRight()));
        }
    }

    /**
     * Used to deserialize a {@link DnsRecord} object to the appropriate
     * concrete class based on the properties seen.
     * 
     * @author jschewe
     *
     */
    private static final class DnsRecordDeserializer extends JsonDeserializer<DnsRecord> {

        @Override
        public DnsRecord deserialize(final JsonParser jp, final DeserializationContext ctxt)
                throws IOException, JsonProcessingException {
            final ObjectCodec oc = jp.getCodec();
            final JsonNode node = oc.readTree(jp);

            final ObjectMapper mapper = (ObjectMapper) jp.getCodec();

            if (node.has("node")) {
                return mapper.treeToValue(node, NameRecord.class);
            } else if (node.has("delegateRegion")) {
                return mapper.treeToValue(node, DelegateRecord.class);
            } else {
                throw new JsonMappingException(jp, "Cannot determine type of DnsRecord from: " + node);
            }
        }
    }

    private static final class BaseNetworkLoadDeserializer extends JsonDeserializer<BaseNetworkLoad> {

        @Override
        public BaseNetworkLoad deserialize(final JsonParser jp, final DeserializationContext ctxt)
                throws IOException, JsonProcessingException {
            final ObjectCodec oc = jp.getCodec();
            final JsonNode node = oc.readTree(jp);

            final ObjectMapper mapper = (ObjectMapper) jp.getCodec();

            if (node.has("numClients")) {
                return mapper.treeToValue(node, ClientLoad.class);
            } else if (node.has("server")) {
                return mapper.treeToValue(node, BackgroundNetworkLoad.class);
            } else {
                throw new JsonMappingException(jp, "Cannot determine type of BaseNetworkLoad from: " + node);
            }
        }

    }

    // ------------------ Custom Serializers ------------------

    /**
     * Class to serialize a NodeAttribute as key in Map.
     * 
     * @author awald
     *
     */
    private static final class NodeAttributeKeySerializer extends JsonSerializer<NodeAttribute> {
        @Override
        public void serialize(final NodeAttribute value, final JsonGenerator gen, final SerializerProvider serializers)
                throws IOException, JsonProcessingException {
            gen.writeFieldName(value.getName());
        }
    }

    /**
     * Class to serialize a LinkeMetricName as key in Map.
     * 
     * @author awald
     *
     */
    private static final class LinkAttributeKeySerializer extends JsonSerializer<LinkAttribute> {
        @Override
        public void serialize(final LinkAttribute value, final JsonGenerator gen, final SerializerProvider serializers)
                throws IOException, JsonProcessingException {
            gen.writeFieldName(value.getName());
        }
    }

    private static final class NodeNetworkFlowSerializer extends JsonSerializer<NodeNetworkFlow> {
        @Override
        public void serialize(final NodeNetworkFlow value,
                final JsonGenerator gen,
                final SerializerProvider serializers) throws IOException, JsonProcessingException {
            gen.writeFieldName("(" + value.getSource().getName() + ", " + value.getDestination().getName() + ", "
                    + value.getServer().getName() + ")");
        }
    }

    private static final class RegionNetworkFlowSerializer extends JsonSerializer<RegionNetworkFlow> {
        @Override
        public void serialize(final RegionNetworkFlow value,
                final JsonGenerator gen,
                final SerializerProvider serializers) throws IOException, JsonProcessingException {
            gen.writeFieldName("(" + value.getSource().getName() + ", " + value.getDestination().getName() + ", "
                    + value.getServer().getName() + ")");
        }
    }

    private static final class InterfaceIdentifierKeyDeserializer extends KeyDeserializer {
        @Override
        public Object deserializeKey(final String key, final DeserializationContext ctxt)
                throws IOException, JsonProcessingException {
            // ifce-name ( neighbor1, neighbor2, ... )
            final int openParen = key.indexOf('(');
            final int closeParen = key.indexOf(')');
            if (-1 == openParen || -1 == closeParen) {
                throw new JsonParseException(ctxt.getParser(), "Cannot find open or close paren in '" + key + "'");
            } else {
                final String name = key.substring(0, openParen).trim();
                final String neighborsStr = key.substring(openParen + 1, closeParen);
                final String[] tokens = neighborsStr.split(",");
                final ImmutableSet.Builder<NodeIdentifier> neighbors = ImmutableSet.builder();
                for (final String n : tokens) {
                    final NodeIdentifier node = new DnsNameIdentifier(n.trim());
                    neighbors.add(node);
                }

                final InterfaceIdentifier ii = new InterfaceIdentifier(name, neighbors.build());
                return ii;
            }
        }
    }

    private static final class InterfaceIdentifierKeySerializer extends JsonSerializer<InterfaceIdentifier> {
        @Override
        public void serialize(final InterfaceIdentifier value,
                final JsonGenerator gen,
                final SerializerProvider serializers) throws IOException, JsonProcessingException {
            final StringBuffer output = new StringBuffer();
            output.append(value.getName());
            output.append(" ( ");
            output.append(value.getNeighbors().stream().map(n -> n.getName()).collect(Collectors.joining(",")));
            output.append(" )");

            gen.writeFieldName(output.toString());

        }
    }

}
