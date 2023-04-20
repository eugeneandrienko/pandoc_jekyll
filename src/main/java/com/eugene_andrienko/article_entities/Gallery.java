package com.eugene_andrienko.article_entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonRootName(value = "gallery")
public class Gallery
{
    @JsonProperty("gallery-name")
    String galleryName;
    @JsonProperty("gallery-items")
    ArrayNode galleryItems;
}
