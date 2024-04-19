package com.eugene_andrienko;

import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class PandocJekyll
{
    final private ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args)
    {
        new PandocJekyll().start();
    }

    void start()
    {
        PandocAST ast = parseJsonFromStdin();
        writeJsonToStdout(ast);
    }

    /**
     * Parse Pandoc's AST from STDIN.
     * Convert JSON to {@code PandocAST} object.
     *
     * @return Parsed {@code PandocAST} object.
     */
    PandocAST parseJsonFromStdin()
    {
        try
        {
            return objectMapper.readValue(System.in, PandocAST.class);
        }
        catch(IOException e)
        {
            log.error("Failed to parse JSON from stdin", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Write converted Pandoc's AST to STDOUT as JSON.
     *
     * @param pandocAST {@code PandocAST} object.
     */
    void writeJsonToStdout(PandocAST pandocAST)
    {
        try
        {
            objectMapper.writeValue(System.out, pandocAST);
        }
        catch(IOException e)
        {
            log.error("Failed to write well-formed JSON to stdout", e);
            throw new RuntimeException(e);
        }
    }
}
