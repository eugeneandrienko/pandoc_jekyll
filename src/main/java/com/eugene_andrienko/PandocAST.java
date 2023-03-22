package com.eugene_andrienko;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;


@JsonDeserialize(using = PandocASTDeserializer.class)
@JsonPropertyOrder({"pandoc-api-version", "meta", "blocks"})
@Getter
@Setter
@Builder
public class PandocAST
{
    @JsonProperty("pandoc-api-version")
    JsonNode pandocApiVersion;
    JsonNode meta;
    JsonNode blocks;
}
