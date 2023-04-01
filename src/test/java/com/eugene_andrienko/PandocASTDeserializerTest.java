package com.eugene_andrienko;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class PandocASTDeserializerTest
{
    @Test
    @DisplayName("Deserialize test")
    @SneakyThrows
    void deserializeTest()
    {
        PandocASTDeserializer deserializer = mock(PandocASTDeserializer.class);
        when(deserializer.deserialize(any(JsonParser.class), any(DeserializationContext.class)))
                .thenCallRealMethod();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode testNode = mapper.readTree("""
                {
                    "pandoc-api-version": [
                        1,
                        23
                    ],
                    "meta": {
                        "date": {
                            "t": "MetaInlines",
                            "c": [
                                {
                                    "t": "Str",
                                    "c": "2022-03-14"
                                },
                                {
                                    "t": "Space"
                                },
                                {
                                    "t": "Str",
                                    "c": "12:34"
                                }
                            ]
                        }
                    },
                    "blocks": [
                         {
                             "t": "RawBlock",
                             "c": [
                                 "org",
                                 "#+TAGS: test test2"
                             ]
                         },
                         {
                             "t": "RawBlock",
                             "c": [
                                 "org",
                                 "#+COVER: aurora10.jpg"
                             ]
                         },
                         {
                             "t": "Para",
                             "c": [
                                {
                                    "t": "Str",
                                    "c": "Бытует"
                                }
                             ]
                         }
                    ]
                }
                """);
        ObjectCodec mockedCodec = mock(ObjectCodec.class);
        JsonParser mockedParser = mock(JsonParser.class);
        DeserializationContext mockedContext = mock(DeserializationContext.class);

        when(mockedCodec.readTree(any(JsonParser.class))).thenReturn(testNode);
        when(mockedParser.getCodec()).thenReturn(mockedCodec);

        deserializer.deserialize(mockedParser, mockedContext);
    }

    @Test
    @DisplayName("Test for non-existent nodes")
    @SneakyThrows
    void deserializeNonExistentNodesTest()
    {
        PandocASTDeserializer deserializer = mock(PandocASTDeserializer.class);
        when(deserializer.deserialize(any(JsonParser.class), any(DeserializationContext.class)))
                .thenCallRealMethod();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode testNode = mapper.readTree("""
                {
                    "meta": {
                        "date": {
                            "t": "MetaInlines",
                            "c": [
                                {
                                    "t": "Str",
                                    "c": "2022-03-14"
                                }
                            ]
                        }
                    },
                    "blocks": [
                         {
                             "t": "RawBlock",
                             "c": [
                                 "org",
                                 "#+TAGS: test test2"
                             ]
                         }
                    ]
                }
                """);
        ObjectCodec mockedCodec = mock(ObjectCodec.class);
        JsonParser mockedParser = mock(JsonParser.class);
        DeserializationContext mockedContext = mock(DeserializationContext.class);

        when(mockedCodec.readTree(any(JsonParser.class))).thenReturn(testNode);
        when(mockedParser.getCodec()).thenReturn(mockedCodec);

        Assertions.assertThrows(PandocException.class,
                () -> deserializer.deserialize(mockedParser, mockedContext),
                "Should fail when no pandoc-api-version tag");

        testNode = mapper.readTree("""
                {
                    "pandoc-api-version": [
                        1,
                        23
                    ],
                    "blocks": [
                         {
                             "t": "RawBlock",
                             "c": [
                                 "org",
                                 "#+TAGS: test test2"
                             ]
                         }
                    ]
                }
                """);

        when(mockedCodec.readTree(any(JsonParser.class))).thenReturn(testNode);
        when(mockedParser.getCodec()).thenReturn(mockedCodec);

        Assertions.assertThrows(PandocException.class,
                () -> deserializer.deserialize(mockedParser, mockedContext),
                "Should fail when no meta tag");

        testNode = mapper.readTree("""
                {
                    "pandoc-api-version": [
                        1,
                        23
                    ],
                    "meta": {
                        "date": {
                            "t": "MetaInlines",
                            "c": [
                                {
                                    "t": "Str",
                                    "c": "2022-03-14"
                                },
                                {
                                    "t": "Space"
                                },
                                {
                                    "t": "Str",
                                    "c": "12:34"
                                }
                            ]
                        }
                    }
                }
                """);

        when(mockedCodec.readTree(any(JsonParser.class))).thenReturn(testNode);
        when(mockedParser.getCodec()).thenReturn(mockedCodec);

        Assertions.assertThrows(PandocException.class,
                () -> deserializer.deserialize(mockedParser, mockedContext),
                "Should fail when no blocks tag");
    }
}
