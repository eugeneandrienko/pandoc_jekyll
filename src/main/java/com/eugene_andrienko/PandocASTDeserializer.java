package com.eugene_andrienko;

import com.eugene_andrienko.article_entities.Gallery;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;


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
        List<JsonNode> galleryBlocks = listOrgBlocks(mainNode, "json");
        transformGalleryBlocks(galleryBlocks);

        return PandocAST.builder()
                        .pandocApiVersion(mainNode.get(PANDOC_API_VERSION))
                        .meta(mainNode.get(META))
                        .blocks(mainNode.get(BLOCKS))
                        .build();
    }

    /**
     * Read tags from org file if they exist.
     *
     * @param node Main JSON node from pandoc.
     * @return Tags delimited with a space or null.
     */
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

    /**
     * Read cover from org file if it exists.
     *
     * @param node Main JSON node from pandoc.
     * @return Path to cover or null if not exists.
     */
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

    /**
     * Transform gallery JSON to HTML if any found.
     *
     * @param nodes Nodes with {@code "RawBlock"} and {@code "json"} type inside.
     */
    private void transformGalleryBlocks(List<JsonNode> nodes)
    {
        ObjectMapper galleryMapper = new ObjectMapper();
        galleryMapper.enable(DeserializationFeature.UNWRAP_ROOT_VALUE);

        for(JsonNode node : nodes)
        {
            ArrayNode galleryNode = (ArrayNode)node;

            String jsonGalleryData = galleryNode.get(1).textValue();
            String newJsonGalleryData = transformGallery(jsonGalleryData, galleryMapper);
            if(newJsonGalleryData == null)
            {
                log.warn("Failed to transform gallery data! Data: {}", jsonGalleryData);
                continue;
            }

            galleryNode.set(0, "html");
            galleryNode.set(1, newJsonGalleryData);
        }
    }

    /**
     * Insert new node to meta.
     *
     * @param name     Name of new node.
     * @param value    Value for new node.
     * @param mainNode Main node from pandoc.
     *
     * @throws JacksonException Fail to insert a new node.
     */
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

    /**
     * Return and remove from tree raw org block.
     *
     * @param node    Main JSON node from pandoc.
     * @param checker Additional function to filter raw org blocks.
     *
     * @return Found raw org block as {@code JsonNode} or null if none found.
     */
    private JsonNode popOrgBlock(JsonNode node, Function<JsonNode, JsonNode> checker)
    {
        if(node == null || checker == null)
        {
            return null;
        }

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

    /**
     * Returns list of {@code JsonNode} blocks, which matches with given "RawBlock" type and has {@code json} type.
     *
     * @param node         Node to process.
     * @param rawBlockType Raw block type.
     *
     * @return List of matched {@code JsonNode} blocks.
     */
    private List<JsonNode> listOrgBlocks(JsonNode node, String rawBlockType)
    {
        if(rawBlockType == null || node == null)
        {
            return Collections.emptyList();
        }

        // Root node:
        if(node.isContainerNode() && node.hasNonNull("blocks"))
        {
            return listOrgBlocks(node.get("blocks"), rawBlockType);
        }

        // "blocks" node:
        if(node.isArray())
        {
            List<JsonNode> result = new LinkedList<>();
            ArrayNode arrayNode = (ArrayNode)node;
            for(int i = 0; i < arrayNode.size(); i++)
            {
                JsonNode arrayElement = arrayNode.get(i);
                boolean isRawBlockMatches =
                        arrayElement.isContainerNode() &&
                        arrayElement.hasNonNull("t") &&
                        "RawBlock".equals(arrayElement.get("t").textValue()) &&
                        arrayElement.hasNonNull("c") &&
                        arrayElement.get("c").isArray() &&
                        arrayElement.get("c").size() == 2 &&
                        rawBlockType.equals(arrayElement.get("c").get(0).textValue());
                if(isRawBlockMatches)
                {
                    result.add(arrayElement.get("c"));
                }
            }
            return result;
        }

        return Collections.emptyList();
    }

    /**
     * Transform gallery json data to HTML data.
     *
     * @param jsonString Gallery JSON data.
     * @param mapper     Initialized {@code ObjectMapper}.
     * @return HTML for gallery.
     */
    private String transformGallery(String jsonString, ObjectMapper mapper)
    {
        Gallery gallery;
        try
        {
            gallery = mapper.readValue(jsonString, Gallery.class);
        }
        catch(JacksonException e)
        {
            log.error("Given JSON string not with gallery data: {}!", jsonString);
            return null;
        }

        String galleryName = gallery.getGalleryName();
        StringBuilder result = new StringBuilder(String.format("<div class=\"%s\">", galleryName));
        final String galleryItemDiv = """
                <div>
                    <a href="/assets/static/%s" data-lightbox="%s">
                        <img data-lazy="/assets/static/%s"/>
                    </a>
                </div>
                """;

        for(int i = 0; i < gallery.getGalleryItems().size(); i++)
        {
            if(!gallery.getGalleryItems().get(i).isArray())
            {
                log.warn("Gallery item is not an array: {}!", gallery.getGalleryItems().get(i).toPrettyString());
                continue;
            }

            ArrayNode galleryItem = (ArrayNode)gallery.getGalleryItems().get(i);
            if(galleryItem.size() != 1 && galleryItem.size() != 2)
            {
                log.warn("Unexpected size of array with gallery item: {}!", galleryItem.toPrettyString());
                continue;
            }

            String filename = galleryItem.get(0).textValue();
            String thumbnail = galleryItem.get(galleryItem.size() - 1).textValue();
            result.append(String.format(galleryItemDiv, filename, galleryName, thumbnail));
        }

        result.append("</div>");
        result.append(String.format("""
                <script type="text/javascript">
                    $(document).ready(function(){
                        $('.%s').slick({
                            infinite: false,
                            lazyLoad: 'ondemand',
                            dots: true
                        });
                    });
                </script>
                """, galleryName));

        return result.toString();
    }
}
