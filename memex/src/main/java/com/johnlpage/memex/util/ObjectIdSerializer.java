package com.johnlpage.memex.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

import org.bson.types.ObjectId;

// ObjectId is a MognoDB GUID Class, an efficient 12 byte UUID starting with a
// timestamp for efficnent indexing - we need to teach Jackson how to convert it
// to JSON nicely

public class ObjectIdSerializer extends StdSerializer<ObjectId> {

    public ObjectIdSerializer() {
        super(ObjectId.class);
    }

    @Override
    public void serialize(ObjectId value, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        gen.writeString(value.toHexString());
    }
}
