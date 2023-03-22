package com.eugene_andrienko;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import lombok.extern.log4j.Log4j2;


@Log4j2
public class PandocASTDeserializer extends StdDeserializer<PandocAST>
{
    private final static String PANDOC_API_VERSION = "pandoc-api-version";
    private final static String META = "meta";
    private final static String BLOCKS = "blocks";

    public PandocASTDeserializer()
    {
        this(PandocAST.class);
    }

    protected PandocASTDeserializer(final Class<?> vc)
    {
        super(vc);
    }

    @Override
    public PandocAST deserialize(final JsonParser jsonParser,
            final DeserializationContext deserializationContext)
            throws IOException, JacksonException
    {
        ObjectCodec codec = jsonParser.getCodec();
        JsonNode mainNode = codec.readTree(jsonParser);

        if(!mainNode.hasNonNull(PANDOC_API_VERSION) ||
           !mainNode.hasNonNull(META) ||
           !mainNode.hasNonNull(BLOCKS))
        {
            log.error("There is not a pandoc-generated AST");
            throw new PandocException("There is not a pandoc-generated AST");
        }

        String tags = popTags(mainNode);
        insertToMeta("tags", tags, mainNode);
        String cover = popCover(mainNode);
        insertToMeta("cover", cover, mainNode);

        return PandocAST.builder()
                        .pandocApiVersion(mainNode.get(PANDOC_API_VERSION))
                        .meta(mainNode.get(META))
                        .blocks(mainNode.get(BLOCKS))
                        .build();
    }

    private String popTags(JsonNode node)
    {
        JsonNode orgNode = popOrgBlock(node, (n) -> {
            if(n.isTextual() && n.textValue().startsWith("#+TAGS: "))
            {
                return n;
            }
            return null;
        });

        if(orgNode != null)
        {
            String tags = orgNode.textValue();
            return tags.substring(8);
        }
        return null;
    }

    private String popCover(JsonNode node)
    {
        JsonNode orgNode = popOrgBlock(node, (n) -> {
            if(n.isTextual() && n.textValue().startsWith("#+COVER: "))
            {
                return n;
            }
            return null;
        });

        if(orgNode != null)
        {
            String cover = orgNode.textValue();
            return cover.substring(9);
        }
        return null;
    }

    private void insertToMeta(String name, String value, JsonNode mainNode) throws JacksonException
    {
        if(name == null || name.isEmpty())
        {
            log.error("Name for new tag not exists");
            throw new PandocException("Name for new tag not exists");
        }
        if(value == null || value.isEmpty())
        {
            log.warn("No value found for {} meta key - skipping it", name);
            return;
        }

        JsonNode meta = mainNode.get("meta");
        if(meta == null)
        {
            log.error("There are not \"meta\" node in AST!");
            throw new PandocException("No \"meta\" node");
        }
        if(meta.getNodeType() != JsonNodeType.OBJECT)
        {
            log.error("\"meta\" node type is not object");
            throw new PandocException("\"meta\" node type is not object");
        }

        String newTree = """
                {
                    "t": "MetaInlines",
                    "c": [%s]
                }
                """;
        List<String> stringElements = Arrays.stream(value.split(" "))
                                            .map(element ->
                                                    String.format("""
                                                            {
                                                                "t": "Str",
                                                                "c": "%s"
                                                            }
                                                            """, element)).toList();
        String jsonRawValues = String.join("""
                ,
                {
                    "t": "Space"
                },
                """, stringElements);

        JsonNode newNode = new ObjectMapper().readTree(String.format(newTree, jsonRawValues));
        ((ObjectNode)meta).putIfAbsent(name, newNode);
    }

    private JsonNode popOrgBlock(JsonNode node, Function<JsonNode, JsonNode> checker)
    {
        // Root node here:
        if(node.isContainerNode() && node.hasNonNull("blocks"))
        {
            return popOrgBlock(node.get("blocks"), checker);
        }

        // "blocks" node here:
        if(node.isArray())
        {
            ArrayNode arrayNode = (ArrayNode)node;
            for(int i = 0; i < arrayNode.size(); i++)
            {
                JsonNode arrayElement = arrayNode.get(i);
                if(arrayElement.isContainerNode() && arrayElement.hasNonNull("t") &&
                   "RawBlock".equals(arrayElement.get("t").textValue()))
                {
                    JsonNode result = popOrgBlock(arrayElement, checker);
                    if(result != null)
                    {
                        arrayNode.remove(i);
                        return result;
                    }
                }
            }
        }

        // "RawBlock" node here:
        if(node.isContainerNode() && node.hasNonNull("c") && node.get("c").isArray())
        {
            return popOrgBlock(node.get("c"), checker);
        }

        // Array from "RawBlock":
        if(node.isArray() && node.size() > 1 &&
           node.get(0).isTextual() && "org".equals(node.get(0).textValue()) &&
           node.get(1).isTextual())
        {
            return popOrgBlock(node.get(1), checker);
        }

        // Checks last element:
        return checker.apply(node);
    }
}
