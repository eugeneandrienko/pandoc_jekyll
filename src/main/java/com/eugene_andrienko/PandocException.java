package com.eugene_andrienko;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonLocation;


public class PandocException extends JacksonException
{
    final private String message;

    public PandocException(String message)
    {
        super(message);
        this.message = message;
    }

    @Override
    public JsonLocation getLocation()
    {
        return null;
    }

    @Override
    public String getOriginalMessage()
    {
        return message;
    }

    @Override
    public Object getProcessor()
    {
        return null;
    }
}
